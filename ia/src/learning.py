"""Feedback humano persistente e retreinamento em lotes da prioridade final."""
import hashlib
import json
import os
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
import joblib
from sklearn.metrics import f1_score, recall_score
from import_xlsx import redact
from train import build_pipeline
from src.dataset import normalized, read_dataset, fingerprint
from src.model import load_model, ModelUnavailable
from src.rules import PRIORITIES, priority


def _open_connection():
    path = Path(os.getenv('IA_FEEDBACK_DB', 'data/learning/feedback.sqlite3'))
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path, timeout=15)
    db.row_factory = sqlite3.Row
    db.executescript('''
        CREATE TABLE IF NOT EXISTS groups_feedback (grupo TEXT PRIMARY KEY, particao TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS feedback (
            seq INTEGER PRIMARY KEY AUTOINCREMENT, evento_id TEXT UNIQUE NOT NULL,
            grupo TEXT NOT NULL, texto TEXT NOT NULL, texto_hash TEXT NOT NULL,
            prioridade TEXT NOT NULL, acao TEXT NOT NULL, critico INTEGER NOT NULL,
            payload_hash TEXT NOT NULL, criada REAL NOT NULL, decidida REAL NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_feedback_texto ON feedback(texto_hash);
        CREATE TABLE IF NOT EXISTS learning_state (chave TEXT PRIMARY KEY, valor TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS training_lock (id INTEGER PRIMARY KEY, owner TEXT, expires REAL NOT NULL);
        INSERT OR IGNORE INTO training_lock(id,owner,expires) VALUES (1,'',0);
    ''')
    if 'decidida' not in {r[1] for r in db.execute('PRAGMA table_info(feedback)')}:
        db.execute('ALTER TABLE feedback ADD COLUMN decidida REAL')
        db.execute('UPDATE feedback SET decidida=criada WHERE decidida IS NULL')
        db.commit()
    return db


@contextmanager
def connection():
    db = _open_connection()
    try:
        with db:
            yield db
    finally:
        db.close()


def save_state(db, key, value):
    db.execute('INSERT INTO learning_state(chave,valor) VALUES (?,?) ON CONFLICT(chave) DO UPDATE SET valor=excluded.valor',
               (key, json.dumps(value, ensure_ascii=False)))


def state(db, key, default=None):
    row = db.execute('SELECT valor FROM learning_state WHERE chave=?', (key,)).fetchone()
    return json.loads(row[0]) if row else default


def ingest(feedback):
    data = feedback.model_dump(mode='json')
    digest = hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()
    text = redact(data['texto'], [])
    key = hashlib.sha256(normalized(text).encode()).hexdigest()
    with connection() as db:
        db.execute('BEGIN IMMEDIATE')
        existing = db.execute('SELECT payload_hash FROM feedback WHERE evento_id=?', (data['evento_id'],)).fetchone()
        if existing:
            if existing[0] != digest:
                raise ValueError('Evento já recebido com conteúdo diferente')
            return False
        group = db.execute('SELECT particao FROM groups_feedback WHERE grupo=?', (data['grupo'],)).fetchone()
        partition = group[0] if group else ('validacao' if int(hashlib.sha256(data['grupo'].encode()).hexdigest(), 16) % 5 == 0 else 'treino')
        others = db.execute('SELECT DISTINCT g.particao FROM feedback f JOIN groups_feedback g ON f.grupo=g.grupo WHERE f.texto_hash=?', (key,)).fetchall()
        if any(row[0] != partition for row in others):
            partition = 'quarentena'
        if data['servico_critico_confirmado'] and data['prioridade_final'] in ('BAIXA', 'MEDIA'):
            # A correção contradiz o piso do contexto. Preserva o evento para revisão.
            partition = 'quarentena'
        db.execute('INSERT INTO groups_feedback(grupo,particao) VALUES (?,?) ON CONFLICT(grupo) DO UPDATE SET particao=excluded.particao',
                   (data['grupo'], partition))
        db.execute('INSERT INTO feedback(evento_id,grupo,texto,texto_hash,prioridade,acao,critico,payload_hash,criada,decidida) VALUES (?,?,?,?,?,?,?,?,?,?)',
                   (data['evento_id'], data['grupo'], text, key, data['prioridade_final'], data['acao'],
                    int(data['servico_critico_confirmado']), digest, time.time(), feedback.decidida_em.timestamp()))
    return True


def status():
    with connection() as db:
        total = db.execute('SELECT COUNT(*) FROM feedback').fetchone()[0]
        parts = {r[0]: r[1] for r in db.execute('SELECT g.particao,COUNT(*) FROM feedback f JOIN groups_feedback g ON f.grupo=g.grupo GROUP BY g.particao')}
        return {'feedbacks_recebidos': total, 'particoes': parts, 'ultimo_treinamento': state(db, 'report'),
                'minimo_feedbacks': int(os.getenv('IA_LEARNING_MIN_FEEDBACK', '20')),
                'minimo_grupos_validacao': int(os.getenv('IA_LEARNING_MIN_VALIDATION_GROUPS', '3'))}


def features(text, critical=False):
    return text + (' contexto_servico_critico_confirmado' if critical else '')


def final_predictions(model, texts, critical):
    if 'priority_feedback' in model:
        predictions = list(model['priority_feedback'].predict([features(t, c) for t, c in zip(texts, critical)]))
    else:
        predictions = [priority(i, u) for i, u in zip(model['impacto'].predict(texts), model['urgencia'].predict(texts))]
    return ['ALTA' if c and p in ('BAIXA', 'MEDIA') else str(p) for p, c in zip(predictions, critical)]


def scores(truth, predicted):
    return {'macro_f1': float(f1_score(truth, predicted, labels=list(PRIORITIES), average='macro', zero_division=0)),
            'recall_critica': float(recall_score(truth, predicted, labels=['CRITICA'], average='macro', zero_division=0))}


def acceptable(before, after):
    return after['macro_f1'] + 1e-12 >= before['macro_f1'] and after['recall_critica'] + 1e-12 >= before['recall_critica']


def train_pending():
    owner = str(uuid.uuid4())
    with connection() as db:
        db.execute('BEGIN IMMEDIATE')
        if db.execute('UPDATE training_lock SET owner=?,expires=? WHERE id=1 AND expires<?',
                      (owner, time.time() + 3600, time.time())).rowcount == 0:
            return
    try:
        _train_pending()
    finally:
        with connection() as db:
            db.execute('UPDATE training_lock SET expires=0 WHERE id=1 AND owner=?', (owner,))


def _train_pending():
    with connection() as db:
        watermark = db.execute('SELECT COALESCE(MAX(seq),0) FROM feedback').fetchone()[0]
        if watermark <= state(db, 'last_seq', 0):
            return
        # Usa a última decisão para cada texto; histórico completo permanece no banco.
        feedback = [dict(r) for r in db.execute('''SELECT f.*,g.particao FROM feedback f JOIN groups_feedback g ON f.grupo=g.grupo
            WHERE f.seq=(SELECT f2.seq FROM feedback f2
                         WHERE f2.texto_hash=f.texto_hash AND f2.seq<=? ORDER BY f2.decidida DESC,f2.seq DESC LIMIT 1)
            AND g.particao<>'quarentena' AND f.seq<=? ORDER BY f.seq''', (watermark, watermark))]
        if len(feedback) < int(os.getenv('IA_LEARNING_MIN_FEEDBACK', '20')):
            save_state(db, 'report', {'estado': 'AGUARDANDO_DADOS', 'amostras_elegiveis': len(feedback)})
            return
    try:
        current = load_model()
    except ModelUnavailable:
        with connection() as db:
            save_state(db, 'report', {'estado': 'AGUARDANDO_MODELO_BASE'})
        return
    private_base = Path('data/processed/base-ampla-com-reais-v1/treinamento-com-reais.csv')
    dataset = os.getenv('IA_LEARNING_DATASET') or (str(private_base) if private_base.exists() else 'examples/chamados-contextuais.csv')
    seed = read_dataset(dataset)
    heldout = [r for r in seed if r['particao'] != 'treino']
    reserved = {hashlib.sha256(normalized(r['texto']).encode()).hexdigest() for r in heldout}
    fitted_texts = {hashlib.sha256(normalized(r['texto']).encode()).hexdigest() for r in seed if r['particao'] == 'treino'}
    excluded_groups = {r['grupo'] for r in feedback if r['texto_hash'] in reserved
                       or (r['particao'] == 'validacao' and r['texto_hash'] in fitted_texts)}
    feedback = [r for r in feedback if r['grupo'] not in excluded_groups]
    if len(feedback) < int(os.getenv('IA_LEARNING_MIN_FEEDBACK', '20')):
        with connection() as db:
            save_state(db, 'report', {'estado': 'AGUARDANDO_DADOS', 'amostras_elegiveis': len(feedback),
                                     'grupos_excluidos_colisao': len(excluded_groups)})
        return
    training = [r for r in feedback if r['particao'] == 'treino']
    validation = [r for r in feedback if r['particao'] == 'validacao']
    valid_seed = [r for r in seed if r['particao'] == 'validacao']
    used_groups = set(current['train_groups']) | set(current.get('feedback', {}).get('seed_train_groups', []))
    if used_groups & {r['grupo'] for r in valid_seed}:
        raise ValueError('Base de validação contém grupo já usado pelo modelo')
    if not training or len({r['grupo'] for r in validation}) < int(os.getenv('IA_LEARNING_MIN_VALIDATION_GROUPS', '3')):
        with connection() as db:
            save_state(db, 'report', {'estado': 'AGUARDANDO_VALIDACAO', 'treino': len(training), 'validacao': len(validation)})
        return
    human_training_texts = {r['texto_hash'] for r in training}
    # A decisão humana substitui o rótulo inicial do mesmo texto no treino.
    seed_train = [r for r in seed if r['particao'] == 'treino'
                  and hashlib.sha256(normalized(r['texto']).encode()).hexdigest() not in human_training_texts]
    texts = [features(r['texto']) for r in seed_train] + [features(r['texto'], r['critico']) for r in training]
    labels = [priority(r['impacto'], r['urgencia']) for r in seed_train] + [r['prioridade'] for r in training]
    if set(labels) != set(PRIORITIES):
        with connection() as db:
            save_state(db, 'report', {'estado': 'AGUARDANDO_CLASSES'})
        return
    with connection() as db:
        save_state(db, 'report', {'estado': 'TREINANDO', 'feedbacks_treino': len(training),
                                 'feedbacks_validacao': len(validation)})
    pipeline = build_pipeline()
    pipeline.fit(texts, labels, classifier__sample_weight=[1.0] * len(seed_train) + [3.0] * len(training))
    candidate = dict(current)
    candidate['priority_feedback'] = pipeline
    candidate['sintetico'] = current['sintetico'] or any(r['origem'] == 'SINTETICO' for r in seed_train)
    candidate['rotulagem_ia'] = current.get('rotulagem_ia', False) or any(r['origem'] == 'REAL_ROTULADO_IA' for r in seed_train)
    candidate['base_version'] = current.get('base_version', current['version'])
    candidate['homologado'] = False
    digest = hashlib.sha256(json.dumps([(r['evento_id'], r['prioridade']) for r in feedback]).encode()).hexdigest()[:12]
    candidate['version'] = candidate['base_version'] + '-feedback-' + digest
    candidate['feedback'] = {'watermark': watermark, 'dataset_sha256': fingerprint(dataset),
                             'algoritmo': 'tfidf-lr-priority-feedback-v1', 'peso_feedback': 3.0,
                             'seed_train_groups': sorted({r['grupo'] for r in seed_train}),
                             'train_groups': sorted({r['grupo'] for r in training}),
                             'train_events': [r['evento_id'] for r in training],
                             'validation_groups': sorted({r['grupo'] for r in validation}), 'homologado': False}
    report = {'estado': 'REJEITADO_METRICAS', 'versao': candidate['version'], 'feedbacks_treino': len(training),
              'feedbacks_validacao': len(validation), 'grupos_excluidos_colisao': len(excluded_groups), 'coortes': {}}
    promote = True
    for cohort, rows, human in [('base_validacao', valid_seed, False), ('feedback_validacao', validation, True)]:
        if not rows:
            continue
        xs = [r['texto'] for r in rows]
        contexts = [bool(r['critico']) if human else False for r in rows]
        truth = [r['prioridade'] if human else priority(r['impacto'], r['urgencia']) for r in rows]
        before = scores(truth, final_predictions(current, xs, contexts))
        after = scores(truth, final_predictions(candidate, xs, contexts))
        report['coortes'][cohort] = {'antes': before, 'depois': after, 'amostras': len(rows)}
        promote = promote and acceptable(before, after)
    if candidate['sintetico'] and os.getenv('IA_ALLOW_SYNTHETIC_MODEL', 'false').lower() != 'true':
        promote = False
        report['estado'] = 'BLOQUEADO_MODELO_SINTETICO'
    target = Path(os.getenv('IA_MODEL_PATH', 'models/priority.joblib'))
    candidates = target.parent / 'feedback-candidates'
    candidates.mkdir(parents=True, exist_ok=True)
    saved = candidates / f'{candidate["version"]}.joblib'
    joblib.dump(candidate, saved)
    if promote:
        # Substituição atômica: requisições em andamento continuam com o objeto anterior.
        previous = candidates / ('previous-' + hashlib.sha256(current['version'].encode()).hexdigest()[:16] + '.joblib')
        if not previous.exists():
            joblib.dump(current, previous)
        report['versao_anterior'] = current['version']
        temporary = target.with_name(target.name + '.' + owner_suffix() + '.tmp')
        joblib.dump(candidate, temporary)
        os.replace(temporary, target)
        load_model.cache_clear()
        report['estado'] = 'ATIVADO'
    saved.with_suffix('.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    with connection() as db:
        save_state(db, 'report', report)
        save_state(db, 'last_seq', watermark)


def owner_suffix():
    return uuid.uuid4().hex


def start_worker():
    stop = threading.Event()
    def run():
        while not stop.is_set():
            try:
                train_pending()
            except Exception as error:
                # Falhas de aprendizado nunca interrompem inferência/decisão.
                with connection() as db:
                    save_state(db, 'report', {'estado': 'FALHA_TREINAMENTO', 'tipo': type(error).__name__})
            stop.wait(max(1, float(os.getenv('IA_LEARNING_INTERVAL_SECONDS', '60'))))
    thread = threading.Thread(target=run, name='feedback-training', daemon=True)
    thread.start()
    return stop, thread
