import csv
import json
import pytest
from src.dataset import read_dataset, fingerprint
from prepare_labeled import prepare
from train import train
from evaluate_labeled import evaluate_labeled


def test_ai_label_provenance_and_group_isolation(tmp_path, monkeypatch):
    import prepare_labeled
    monkeypatch.setattr(prepare_labeled, '__file__', str(tmp_path / 'prepare_labeled.py'))
    private = tmp_path / 'data/processed'
    private.mkdir(parents=True)
    review = private / 'review.csv'
    rows = [{'id': f'planilha-{i:04d}', 'grupo': f'grupo-{(i-1)//2}',
             'texto': f'Pedido ficticio de suporte numero {i}.', 'impacto': '', 'urgencia': '',
             'particao': '', 'origem': ''} for i in range(1, 13)]
    with review.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    labels = private / 'labels.json'
    config = {'review_sha256': fingerprint(review), 'unir_grupos_por_ids': [[1, 3]],
              'rotulos': [[i, 'BAIXO', 'MEDIO', 'BAIXA', 'Exemplo ficticio.'] for i in range(1, 13)]}
    labels.write_text(json.dumps(config), encoding='utf-8')
    output = private / 'mixed.csv'
    summary = prepare(review, labels, 'examples/chamados-sinteticos.csv', output)
    assert summary['real_rotulado_ia'] == 12 and not summary['homologado']
    real = [r for r in read_dataset(output) if r['origem'] == 'REAL_ROTULADO_IA']
    assert len(real) == 12 and len({r['grupo'] for r in real[:4]}) == 1
    group_partitions = {}
    for row in real:
        group_partitions.setdefault(row['grupo'], set()).add(row['particao'])
    assert all(len(parts) == 1 for parts in group_partitions.values())
    assert {r['particao'] for r in real} == {'treino', 'validacao', 'teste'}
    model_path = tmp_path / 'model.joblib'
    trained = train(output, model_path)
    assert trained['rotulagem_ia'] is True and trained['sintetico'] is True
    assert trained['homologado'] is False
    report = evaluate_labeled(output, model_path, model_path, tmp_path / 'report.json')
    assert report['reais_rotulados_ia']['total'] == sum(r['particao'] == 'teste' for r in real)
    # A revisão só pode ser aplicada ao texto que foi efetivamente rotulado.
    with review.open('a', encoding='utf-8') as handle:
        handle.write('\n')
    with pytest.raises(ValueError, match='alterada'):
        prepare(review, labels, 'examples/chamados-sinteticos.csv', private / 'changed.csv')
