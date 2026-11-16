"""Compara dados/configuração anteriores e novos em um teste comum."""
import argparse
import json
from pathlib import Path
import joblib
from sklearn.metrics import classification_report
from src.dataset import read_dataset, fingerprint
from src.rules import priority, PRIORITIES
from train import build_pipeline


def compare(original, expanded, model_path, output):
    original_rows, expanded_rows = read_dataset(original), read_dataset(expanded)
    test = [r for r in expanded_rows if r['particao'] == 'teste']
    selected = joblib.load(model_path)  # Somente artefatos locais confiáveis.
    if selected['dataset_sha256'] != fingerprint(expanded):
        raise ValueError('Modelo e dataset contextual não correspondem')
    candidates = {'anterior_81': (original_rows, None), 'fixo_405': (expanded_rows, None),
                  'selecionado_405': (expanded_rows, selected)}
    report = {'sintetico': True, 'homologado': False, 'amostras_teste_comum': len(test),
              'dataset_original_sha256': fingerprint(original), 'dataset_contextual_sha256': fingerprint(expanded),
              'limitacao': 'Templates de alcance/prazo são reutilizados. Resultados não estimam desempenho real.',
              'comparacao': {}}
    for name, (rows, model) in candidates.items():
        training = [r for r in rows if r['particao'] == 'treino']
        if {r['grupo'] for r in training} & {r['grupo'] for r in test}:
            raise ValueError('Grupo de treino presente no teste comum')
        if model is None:
            model = {d: build_pipeline().fit([r['texto'] for r in training], [r[d] for r in training])
                     for d in ('impacto', 'urgencia')}
        predictions = {d: model[d].predict([r['texto'] for r in test]) for d in ('impacto', 'urgencia')}
        truth = [priority(r['impacto'], r['urgencia']) for r in test]
        predicted = [priority(i, u) for i, u in zip(predictions['impacto'], predictions['urgencia'])]
        metrics = classification_report(truth, predicted, labels=list(PRIORITIES), output_dict=True, zero_division=0)
        report['comparacao'][name] = {'amostras_treino': len(training), 'prioridade': metrics,
                                     'criticos_subpriorizados': sum(t == 'CRITICA' and p != 'CRITICA'
                                                                  for t, p in zip(truth, predicted)),
                                     'criticos_referencia': truth.count('CRITICA')}
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    return {name: {'acuracia_prioridade': item['prioridade']['accuracy'],
                   'macro_f1_prioridade': item['prioridade']['macro avg']['f1-score'],
                   'criticos_subpriorizados': item['criticos_subpriorizados']}
            for name, item in report['comparacao'].items()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original', default='examples/chamados-sinteticos.csv')
    parser.add_argument('--expanded', default='examples/chamados-contextuais.csv')
    parser.add_argument('--model', default='models/priority-contextual.joblib')
    parser.add_argument('--output', default='reports/contextual/comparacao.json')
    args = parser.parse_args()
    print(json.dumps(compare(args.original, args.expanded, args.model, args.output), ensure_ascii=False, indent=2))
