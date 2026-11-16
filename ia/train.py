import argparse
import json
import hashlib
from collections import Counter
from pathlib import Path
import joblib
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline, FeatureUnion
from sklearn.metrics import classification_report, f1_score
from src.dataset import read_dataset, fingerprint


def build_pipeline(features='palavras', c=1.0):
    words = TfidfVectorizer(strip_accents='unicode', ngram_range=(1, 2), sublinear_tf=True)
    chars = TfidfVectorizer(strip_accents='unicode', analyzer='char_wb', ngram_range=(3, 5), sublinear_tf=True)
    vectorizer = words if features == 'palavras' else FeatureUnion([('palavras', words), ('caracteres', chars)])
    return Pipeline([('tfidf', vectorizer), ('classifier', LogisticRegression(
        C=c, max_iter=2000, class_weight='balanced', random_state=42))])


def train(dataset, output):
    rows = read_dataset(dataset)
    training = [r for r in rows if r['particao'] == 'treino']
    validation = [r for r in rows if r['particao'] == 'validacao']
    digest = fingerprint(dataset)
    artifact = {'schema': 1, 'sklearn': sklearn.__version__, 'dataset_sha256': digest,
                'version': 'tfidf-lr-v2-' + digest[:12],
                'sintetico': any(r['origem'] == 'SINTETICO' for r in rows),
                'rotulagem_ia': any(r['origem'] == 'REAL_ROTULADO_IA' for r in rows),
                'homologado': False,
                'seed': 42, 'train_groups': sorted({r['grupo'] for r in training}),
                'validation_groups': sorted({r['grupo'] for r in validation}), 'selecao': {}}
    summary = {'version': artifact['version'], 'sintetico': artifact['sintetico'], 'validacao': {},
               'rotulagem_ia': artifact['rotulagem_ia'], 'homologado': False,
               'origens': dict(Counter(r['origem'] for r in rows)),
               'dataset_sha256': digest, 'sklearn': sklearn.__version__, 'seed': 42,
               'selecao': artifact['selecao'], 'criterio': 'macro-F1 na validacao; empate preserva candidato anterior',
               'contagens': {p: dict(Counter(r['impacto'] + '/' + r['urgencia'] for r in rows if r['particao'] == p))
                            for p in ('treino', 'validacao', 'teste')}}
    for dimension in ('impacto', 'urgencia'):
        best_score, best_pipeline, best_config = -1, None, None
        scores = []
        for features, c in [('palavras', 1.0), ('palavras_caracteres', 0.5),
                            ('palavras_caracteres', 2.0), ('palavras_caracteres', 8.0)]:
            pipeline = build_pipeline(features, c)
            pipeline.fit([r['texto'] for r in training], [r[dimension] for r in training])
            score = f1_score([r[dimension] for r in validation],
                             pipeline.predict([r['texto'] for r in validation]), average='macro', zero_division=0)
            config = {'features': features, 'C': c}
            scores.append({**config, 'macro_f1': float(score)})
            if score > best_score:
                best_score, best_pipeline, best_config = score, pipeline, config
        artifact[dimension] = best_pipeline
        artifact['selecao'][dimension] = {'escolhido': best_config, 'candidatos': scores}
        summary['validacao'][dimension] = classification_report(
            [r[dimension] for r in validation], best_pipeline.predict([r['texto'] for r in validation]),
            output_dict=True, zero_division=0)
    config_digest = hashlib.sha256(json.dumps(artifact['selecao'], sort_keys=True).encode()).hexdigest()[:8]
    artifact['version'] += '-' + config_digest
    summary['version'] = artifact['version']
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, target)
    target.with_suffix('.metadata.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Treina sem usar a partição teste')
    parser.add_argument('--dataset', default='examples/chamados-sinteticos.csv')
    parser.add_argument('--output', default='models/priority.joblib')
    args = parser.parse_args()
    print(json.dumps(train(args.dataset, args.output), ensure_ascii=False, indent=2))
