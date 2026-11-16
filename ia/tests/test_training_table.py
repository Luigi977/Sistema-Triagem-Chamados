import csv
import json
from collections import Counter
from xml.etree import ElementTree as ET
import zipfile
import pytest
from generate_training_table import generate
from src.dataset import read_dataset
from src.rules import priority


def test_large_table_contract_and_family_isolation(tmp_path):
    summary = generate(tmp_path / 'table', variants=1)
    assert summary['cenarios_catalogo'] == 200 and summary['familias'] == 40
    assert summary['registros'] == 1808
    assert summary['fontes']['EXEMPLO_USUARIO'] == 8
    assert summary['homologado'] is False and summary['sintetico'] is True
    rows = read_dataset(tmp_path / 'table/treinamento-amplo.csv')
    families = {}
    for row in rows:
        families.setdefault(row['familia'], set()).add(row['particao'])
        assert row['prioridade'] == priority(row['impacto'], row['urgencia'])
    assert all(len(parts) == 1 for parts in families.values())
    assert Counter(next(iter(v)) for v in families.values()) == {'treino': 24, 'validacao': 8, 'teste': 8}
    assert all(r['particao'] == 'treino' for r in rows if r['fonte'] == 'EXEMPLO_USUARIO')
    with (tmp_path / 'table/tabela-chamados.csv').open(encoding='utf-8-sig', newline='') as handle:
        human = list(csv.DictReader(handle))
    assert [r['Mensagem do chamado'] for r in human] == [r['texto'] for r in rows]
    assert human[0]['Urgência'] == rows[0]['urgencia']
    with zipfile.ZipFile(tmp_path / 'table/tabela-chamados.xlsx') as archive:
        assert archive.testzip() is None
        for name in archive.namelist():
            ET.fromstring(archive.read(name))
        sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        ns = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        assert len(sheet.findall('m:sheetData/m:row', ns)) == 1809
    assert json.loads((tmp_path / 'table/resumo.json').read_text(encoding='utf-8'))['registros'] == 1808
    with pytest.raises(FileExistsError):
        generate(tmp_path / 'table', variants=1)


@pytest.mark.parametrize('variants', [0, 61])
def test_unsupported_variant_count_rejected(tmp_path, variants):
    with pytest.raises(ValueError, match='entre 1 e 60'):
        generate(tmp_path / 'invalid', variants=variants)
