import argparse
import json
from pathlib import Path
import joblib
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix, ConfusionMatrixDisplay
from src.dataset import read_dataset, fingerprint
from src.rules import LEVELS, PRIORITIES, priority


def evaluate(dataset, model_path, output):
    rows = read_dataset(dataset)
    model = joblib.load(model_path)  # Somente artefatos locais confiáveis.
    if model['dataset_sha256'] != fingerprint(dataset):
        raise ValueError('Dataset difere do usado no treino; partições não verificáveis')
    test = [r for r in rows if r['particao'] == 'teste']
    if set(model['train_groups']) & {r['grupo'] for r in test}:
        raise ValueError('Grupo de treino presente no teste')
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    predictions = {d: model[d].predict([r['texto'] for r in test]) for d in ('impacto', 'urgencia')}
    report = {'modelo': model['version'], 'dataset_sha256': fingerprint(dataset),
              'sintetico': model['sintetico'], 'amostras_teste': len(test),
              'rotulagem_ia': model.get('rotulagem_ia', False),
              'homologado': False,
              'limitacao': 'Dados sintéticos não demonstram qualidade operacional.' if model['sintetico'] else
                           'Metas de homologação devem ser definidas pelo responsável pelo atendimento.',
              'metricas': {}, 'baseline': {}}
    truth_priority = [priority(r['impacto'], r['urgencia']) for r in test]
    predicted_priority = [priority(i, u) for i, u in zip(predictions['impacto'], predictions['urgencia'])]
    for dimension, labels, truth, predicted in (
        ('impacto', LEVELS, [r['impacto'] for r in test], predictions['impacto']),
        ('urgencia', LEVELS, [r['urgencia'] for r in test], predictions['urgencia']),
        ('prioridade', PRIORITIES, truth_priority, predicted_priority)):
        report['metricas'][dimension] = classification_report(truth, predicted, labels=list(labels), output_dict=True, zero_division=0)
        matrix = confusion_matrix(truth, predicted, labels=list(labels))
        report['metricas'][dimension]['matriz_confusao'] = matrix.tolist()
        report['metricas'][dimension]['ordem_classes'] = list(labels)
        training_labels = [priority(r['impacto'], r['urgencia']) if dimension == 'prioridade' else r[dimension]
                           for r in rows if r['particao'] == 'treino']
        majority = max(labels, key=training_labels.count)
        report['baseline'][dimension] = classification_report(truth, [majority] * len(truth), labels=list(labels), output_dict=True, zero_division=0)
        ConfusionMatrixDisplay(matrix, display_labels=labels).plot(cmap='Blues', colorbar=False)
        plt.tight_layout()
        plt.savefig(destination / f'{dimension}.png', dpi=160)
        plt.close()
    report['criticos_subpriorizados'] = sum(t == 'CRITICA' and p != 'CRITICA' for t, p in zip(truth_priority, predicted_priority))
    report['criticos_referencia'] = truth_priority.count('CRITICA')
    (destination / 'metricas.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--dataset', default='examples/chamados-sinteticos.csv')
    parser.add_argument('--model', default='models/priority.joblib')
    parser.add_argument('--output', default='reports/local')
    args = parser.parse_args()
    print(json.dumps(evaluate(args.dataset, args.model, args.output), ensure_ascii=False, indent=2))
