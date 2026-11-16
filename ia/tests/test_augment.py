import csv
import pytest
from augment_training import augment
from src.dataset import read_dataset


def test_user_examples_preserve_holdouts_and_matrix(tmp_path, monkeypatch):
    import augment_training
    monkeypatch.setattr(augment_training, '__file__', str(tmp_path / 'augment_training.py'))
    base = 'examples/chamados-contextuais.csv'
    output = tmp_path / 'data/processed/expanded.csv'
    summary = augment(base, 'examples/exemplos-usuario.csv', output)
    assert summary['adicionados_treino'] == 8 and summary['total'] == 413
    original, expanded = read_dataset(base), read_dataset(output)
    assert [r for r in original if r['particao'] != 'treino'] == [r for r in expanded if r['particao'] != 'treino']
    with pytest.raises(FileExistsError):
        augment(base, 'examples/exemplos-usuario.csv', output)
    with open('examples/exemplos-usuario.csv', encoding='utf-8', newline='') as handle:
        invalid = list(csv.DictReader(handle))
    invalid[0]['prioridade'] = 'BAIXA'
    invalid_path = tmp_path / 'invalid.csv'
    with invalid_path.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=invalid[0].keys())
        writer.writeheader()
        writer.writerows(invalid)
    with pytest.raises(ValueError, match='matriz'):
        augment(base, invalid_path, tmp_path / 'data/processed/rejected.csv')
    assert not (tmp_path / 'data/processed/rejected.csv').exists()
