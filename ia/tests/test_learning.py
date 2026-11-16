import hashlib
import os
import threading
import uuid
from datetime import datetime, timezone, timedelta
import joblib
import pytest
from fastapi.testclient import TestClient
from src import learning
from src.main import app
from src.model import load_model
from src.schemas import PriorityFeedback
from train import train, build_pipeline

TOKEN = 'credencial-ficticia-exclusiva-do-teste-12345'


@pytest.fixture
def setup(monkeypatch, tmp_path):
    monkeypatch.setenv('IA_FEEDBACK_TOKEN', TOKEN)
    monkeypatch.setenv('IA_LEARNING_ENABLED', 'false')
    monkeypatch.setenv('IA_FEEDBACK_DB', str(tmp_path / 'feedback.sqlite3'))
    monkeypatch.setenv('IA_MODEL_PATH', str(tmp_path / 'model.joblib'))
    monkeypatch.setenv('IA_LEARNING_DATASET', 'examples/chamados-sinteticos.csv')
    monkeypatch.setenv('IA_ALLOW_SYNTHETIC_MODEL', 'true')
    load_model.cache_clear()
    yield tmp_path
    load_model.cache_clear()


def group(partition):
    for _ in range(100):
        value = str(uuid.uuid4())
        actual = 'validacao' if int(hashlib.sha256(value.encode()).hexdigest(), 16) % 5 == 0 else 'treino'
        if actual == partition:
            return value
    raise AssertionError('Não encontrou grupo para o teste')


def payload(text='Impressora individual com falha', priority='MEDIA', partition='treino'):
    return {'evento_id': str(uuid.uuid4()), 'grupo': group(partition), 'texto': text,
            'acao': 'CORRIGIR', 'prioridade_final': priority, 'servico_critico_confirmado': False,
            'decidida_em': datetime.now(timezone.utc).isoformat()}


def test_authenticated_durable_idempotent_feedback(setup):
    headers = {'X-Feedback-Token': TOKEN}
    data = payload('E-mail usuario@example.com falhou; telefone (11) 99999-1234')
    with TestClient(app) as client:
        assert client.post('/learning/feedback', json=data).status_code == 401
        assert client.post('/learning/feedback', json={**data, 'impacto': 'ALTO'}, headers=headers).status_code == 422
        assert client.post('/learning/feedback', json=data, headers=headers).status_code == 201
        replay = client.post('/learning/feedback', json=data, headers=headers)
        assert replay.status_code == 200 and replay.json()['duplicado'] is True
        assert client.post('/learning/feedback', json={**data, 'prioridade_final': 'BAIXA'}, headers=headers).status_code == 409
    # Nova conexão simula reinício do processo; não há dados pessoais explícitos armazenados.
    with learning.connection() as db:
        rows = db.execute('SELECT * FROM feedback').fetchall()
        assert len(rows) == 1 and rows[0]['prioridade'] == 'MEDIA'
        assert 'example.com' not in rows[0]['texto'] and '99999' not in rows[0]['texto']
    with TestClient(app) as client:
        assert client.get('/learning/status', headers=headers).json()['feedbacks_recebidos'] == 1


def test_unconfigured_secret_disables_feedback_endpoint(setup, monkeypatch):
    monkeypatch.delenv('IA_FEEDBACK_TOKEN')
    with TestClient(app) as client:
        assert client.post('/learning/feedback', json=payload()).status_code == 503


def test_replaced_model_is_loaded_without_process_restart(setup):
    train('examples/chamados-sinteticos.csv', setup / 'model.joblib')
    current = load_model()
    changed = dict(current, version=current['version'] + '-substituido')
    joblib.dump(changed, setup / 'replacement.joblib')
    os.replace(setup / 'replacement.joblib', setup / 'model.joblib')
    assert load_model()['version'] == changed['version']


def test_conflicting_partitions_and_policy_are_quarantined(setup):
    data = payload()
    learning.ingest(PriorityFeedback(**data))
    second = {**data, 'evento_id': str(uuid.uuid4()), 'grupo': group('validacao')}
    learning.ingest(PriorityFeedback(**second))
    conflict = payload('Serviço crítico confirmado com decisão incompatível', 'BAIXA')
    conflict['servico_critico_confirmado'] = True
    learning.ingest(PriorityFeedback(**conflict))
    assert learning.status()['particoes']['quarentena'] == 2


def test_batch_training_versioning_promotion_and_rejection(setup, monkeypatch):
    from src.schemas import PriorityFeedback
    train('examples/chamados-sinteticos.csv', setup / 'model.joblib')
    initial = load_model()
    assert 'priority_feedback' not in initial
    for i in range(24):
        label = ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')[i % 4]
        data = payload(f'Exemplo fictício independente {i}: atividade {label} confirmada pelo técnico.', label,
                       'validacao' if i >= 20 else 'treino')
        learning.ingest(PriorityFeedback(**data))
    # Exercita publicação atômica sem depender do escore aleatório deste fixture.
    monkeypatch.setattr(learning, 'acceptable', lambda before, after: True)
    learning.train_pending()
    updated = load_model()
    assert updated['version'] != initial['version'] and 'priority_feedback' in updated
    assert learning.status()['ultimo_treinamento']['estado'] == 'ATIVADO'
    assert not set(updated['feedback']['train_groups']) & set(updated['feedback']['validation_groups'])
    # Retentativa sem feedback novo não treina nem gera outro artefato.
    learning.train_pending()
    assert len(list((setup / 'feedback-candidates').glob('*feedback-*.joblib'))) == 1
    learning.ingest(PriorityFeedback(**payload('Novo exemplo humano que não passou nas métricas', 'ALTA')))
    monkeypatch.setattr(learning, 'acceptable', lambda before, after: False)
    learning.train_pending()
    assert load_model()['version'] == updated['version']
    assert learning.status()['ultimo_treinamento']['estado'] == 'REJEITADO_METRICAS'


def test_quality_gate_rejects_critical_regression():
    assert not learning.acceptable({'macro_f1': 0.8, 'recall_critica': 1.0}, {'macro_f1': 0.9, 'recall_critica': 0.9})
    assert not learning.acceptable({'macro_f1': 0.8, 'recall_critica': 1.0}, {'macro_f1': 0.7, 'recall_critica': 1.0})
    assert learning.acceptable({'macro_f1': 0.8, 'recall_critica': 1.0}, {'macro_f1': 0.8, 'recall_critica': 1.0})


def test_learned_priority_does_not_override_informed_dimensions(setup):
    train('examples/chamados-sinteticos.csv', setup / 'model.joblib')
    model = joblib.load(setup / 'model.joblib')
    model['priority_feedback'] = build_pipeline().fit(['parada geral', 'pedido simples', 'prazo de setor', 'falha individual'],
                                                     ['CRITICA', 'BAIXA', 'ALTA', 'MEDIA'])
    joblib.dump(model, setup / 'model.joblib')
    with TestClient(app) as client:
        predicted = client.post('/predict/priority', json={'texto': 'parada geral'}).json()
        assert predicted['metodo_prioridade'] == 'FEEDBACK_TECNICO'
        assert predicted['confianca_prioridade'] is not None
        explicit = client.post('/predict/priority', json={'texto': 'parada geral', 'impacto_informado': 'BAIXO', 'urgencia_informada': 'BAIXO'}).json()
        assert explicit['prioridade_recomendada'] == explicit['prioridade_matriz'] == 'BAIXA'
        assert explicit['metodo_prioridade'] == 'MATRIZ' and explicit['confianca_prioridade'] is None


def test_worker_is_started_and_stopped_by_lifespan(setup, monkeypatch):
    entered = threading.Event()
    monkeypatch.setenv('IA_LEARNING_ENABLED', 'true')
    monkeypatch.setattr(learning, 'train_pending', entered.set)
    with TestClient(app):
        assert entered.wait(3)


def test_reserved_text_excludes_entire_conversation_from_training(setup, monkeypatch):
    from src.dataset import read_dataset
    train('examples/chamados-sinteticos.csv', setup / 'model.joblib')
    seed = read_dataset('examples/chamados-sinteticos.csv')
    reserved = next(r for r in seed if r['particao'] == 'teste')
    first = payload(reserved['texto'], 'MEDIA')
    learning.ingest(PriorityFeedback(**first))
    learning.ingest(PriorityFeedback(**{**first, 'evento_id': str(uuid.uuid4()), 'texto': 'marcadorreservadointeiro da mesma conversa'}))
    learning.ingest(PriorityFeedback(**payload('Pedido humano independente com texto para treinamento', 'BAIXA')))
    monkeypatch.setenv('IA_LEARNING_MIN_FEEDBACK', '1')
    monkeypatch.setenv('IA_LEARNING_MIN_VALIDATION_GROUPS', '0')
    monkeypatch.setattr(learning, 'acceptable', lambda before, after: True)
    learning.train_pending()
    assert learning.status()['ultimo_treinamento']['grupos_excluidos_colisao'] == 1
    assert learning.status()['ultimo_treinamento']['feedbacks_treino'] == 1
    assert 'marcadorreservadointeiro' not in load_model()['priority_feedback']['tfidf'].vocabulary_


def test_backfilled_old_decision_never_replaces_more_recent_correction(setup, monkeypatch):
    train('examples/chamados-sinteticos.csv', setup / 'model.joblib')
    recent = payload('Correção humana fictícia mais recente para este processo', 'ALTA')
    old = {**recent, 'evento_id': str(uuid.uuid4()), 'prioridade_final': 'BAIXA',
           'decidida_em': (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()}
    learning.ingest(PriorityFeedback(**recent))
    learning.ingest(PriorityFeedback(**old))
    monkeypatch.setenv('IA_LEARNING_MIN_FEEDBACK', '1')
    monkeypatch.setenv('IA_LEARNING_MIN_VALIDATION_GROUPS', '0')
    monkeypatch.setattr(learning, 'acceptable', lambda before, after: True)
    learning.train_pending()
    assert load_model()['feedback']['train_events'] == [recent['evento_id']]
    assert learning.status()['feedbacks_recebidos'] == 2
