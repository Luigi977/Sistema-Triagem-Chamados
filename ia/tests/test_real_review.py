import csv
import json
import zipfile
from xml.etree import ElementTree as ET
import pytest
from review_real_dataset import review
from src.dataset import read_dataset, fingerprint


def test_review_preserves_source_groups_and_human_provenance(tmp_path, monkeypatch):
    import review_real_dataset
    monkeypatch.setattr(review_real_dataset, '__file__', str(tmp_path / 'review_real_dataset.py'))
    source = tmp_path / 'source.csv'
    rows = [{'id': f'real-fixture-{i}', 'id_planilha': f'T{i:03d}', 'grupo': f'grupo-real-{i}',
             'texto': f'Exemplo ficticio de atendimento numero {i}.', 'impacto': 'BAIXO',
             'urgencia': 'ALTO', 'particao': part, 'origem': 'REAL_ROTULADO_IA',
             'revisao_humana': 'PENDENTE', 'confianca_rotulo': 'BAIXA',
             'justificativa_rotulo': 'Classificacao inicial ficticia.'}
            for i, part in enumerate(('treino', 'validacao', 'teste'), 1)]
    with source.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    source_hash = fingerprint(source)
    config = {'source_sha256': source_hash, 'versao': 'revisao-teste', 'revisor': 'IA',
              'alteracoes': {'T001': {'urgencia': 'MEDIO'}},
              'pontos_a_confirmar': {r['id_planilha']: 'Confirmar alcance e prazo.' for r in rows}}
    annotations = tmp_path / 'review.json'
    annotations.write_text(json.dumps(config), encoding='utf-8')
    destination = tmp_path / 'data/processed/reviewed'
    summary = review(source, annotations, 'examples/chamados-sinteticos.csv', destination)
    assert summary['registros'] == 84 and summary['reais'] == 3
    assert summary['revisao_humana'] == 'PENDENTE' and not summary['homologado']
    assert fingerprint(source) == source_hash
    combined = read_dataset(destination / 'treinamento-com-reais.csv')
    revised = [r for r in combined if r['origem'] == 'REAL_ROTULADO_IA']
    assert revised[0]['urgencia'] == 'MEDIO' and revised[0]['prioridade'] == 'BAIXA'
    for before, after in zip(rows, revised):
        assert all(before[k] == after[k] for k in ('id', 'grupo', 'texto', 'particao'))
        assert after['revisor'] == 'IA' and after['status_revisao_ia'] == 'CONCLUIDA'
        assert after['revisao_humana'] == 'PENDENTE'
    with zipfile.ZipFile(destination / 'tabela-com-reais.xlsx') as archive:
        assert archive.testzip() is None
        ns = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        sheet = ET.fromstring(archive.read('xl/worksheets/sheet3.xml'))
        assert len(sheet.findall('m:sheetData/m:row', ns)) == 4
    with pytest.raises(FileExistsError):
        review(source, annotations, 'examples/chamados-sinteticos.csv', destination)
    config['alteracoes']['T001']['revisao_humana'] = 'APROVADA'
    annotations.write_text(json.dumps(config), encoding='utf-8')
    with pytest.raises(ValueError, match='autoria humana'):
        review(source, annotations, 'examples/chamados-sinteticos.csv', tmp_path / 'data/processed/invalid')
