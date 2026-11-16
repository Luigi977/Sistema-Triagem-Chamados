"""Compara antes/depois no mesmo teste, separando rótulos IA e sintéticos."""
import argparse
import json
from pathlib import Path
import joblib
from sklearn.metrics import accuracy_score, classification_report
from src.dataset import read_dataset, fingerprint
from src.rules import LEVELS, PRIORITIES, priority


def evaluate_labeled(dataset, before_path, after_path, output):
    rows = read_dataset(dataset)
    models = {'anterior': joblib.load(before_path), 'retreinado': joblib.load(after_path)}
    if models['retreinado']['dataset_sha256'] != fingerprint(dataset):
        raise ValueError('Modelo retreinado não corresponde ao dataset')
    test = [r for r in rows if r['particao'] == 'teste']
    result = {'homologado': False, 'referencia_real': 'Rótulos propostos por IA, sem validação humana',
              'dataset_sha256': fingerprint(dataset), 'coortes': {}}
    for cohort, selected in [('reais_rotulados_ia', [r for r in test if r['origem'] == 'REAL_ROTULADO_IA']),
                             ('sinteticos', [r for r in test if r['origem'] == 'SINTETICO'])]:
        if not selected:
            continue
        cohort_report = {'total': len(selected), 'modelos': {}}
        truths = {d: [r[d] for r in selected] for d in ('impacto', 'urgencia')}
        truths['prioridade'] = [priority(r['impacto'], r['urgencia']) for r in selected]
        for name, model in models.items():
            if set(model['train_groups']) & {r['grupo'] for r in selected}:
                raise ValueError('Contaminação: grupo de treino no teste')
            probabilities = {d: model[d].predict_proba([r['texto'] for r in selected]) for d in ('impacto', 'urgencia')}
            predicted = {d: [str(model[d].classes_[p.argmax()]) for p in probabilities[d]] for d in probabilities}
            predicted['prioridade'] = [priority(i, u) for i, u in zip(predicted['impacto'], predicted['urgencia'])]
            metrics = {}
            for dimension, classes in [('impacto', LEVELS), ('urgencia', LEVELS), ('prioridade', PRIORITIES)]:
                metrics[dimension] = classification_report(truths[dimension], predicted[dimension],
                                                          labels=list(classes), output_dict=True, zero_division=0)
                metrics[dimension]['acuracia'] = accuracy_score(truths[dimension], predicted[dimension])
            training = [r for r in rows if r['particao'] == 'treino']
            training_priorities = [priority(r['impacto'], r['urgencia']) for r in training
                                   if cohort != 'reais_rotulados_ia' or r['origem'] == 'REAL_ROTULADO_IA']
            majority = max(PRIORITIES, key=training_priorities.count)
            cohort_report['modelos'][name] = {
                'versao': model['version'], 'metricas': metrics,
                'baseline_majoritaria': {'classe': majority, 'acuracia_prioridade': accuracy_score(
                    truths['prioridade'], [majority] * len(selected))},
                'baixa_confianca': sum(any(probabilities[d][i].max() < 0.65 for d in probabilities) for i in range(len(selected))),
                'criticos_referencia': truths['prioridade'].count('CRITICA'),
                'criticos_subpriorizados': sum(t == 'CRITICA' and p != 'CRITICA'
                                             for t, p in zip(truths['prioridade'], predicted['prioridade'])),
                'casos': [{'id': r['id'], 'impacto_referencia': r['impacto'], 'urgencia_referencia': r['urgencia'],
                           'impacto_predito': predicted['impacto'][i], 'urgencia_predita': predicted['urgencia'][i],
                           'prioridade_referencia': truths['prioridade'][i], 'prioridade_predita': predicted['prioridade'][i]}
                          for i, r in enumerate(selected)]}
        result['coortes'][cohort] = cohort_report
    destination = Path(output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    return {cohort: {'total': r['total'], **{name: {
        'acuracia_impacto': m['metricas']['impacto']['acuracia'],
        'acuracia_urgencia': m['metricas']['urgencia']['acuracia'],
        'acuracia_prioridade': m['metricas']['prioridade']['acuracia'],
        'baixa_confianca': m['baixa_confianca']}
        for name, m in r['modelos'].items()}} for cohort, r in result['coortes'].items()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset', required=True)
    parser.add_argument('--before', default='models/priority-contextual.joblib')
    parser.add_argument('--after', default='models/priority-misto-ia-v1.joblib')
    parser.add_argument('--output', default='reports/misto-ia-v1/comparacao.json')
    args = parser.parse_args()
    print(json.dumps(evaluate_labeled(args.dataset, args.before, args.after, args.output), ensure_ascii=False, indent=2))
