import csv
import zipfile
import joblib
import pytest
from import_xlsx import import_workbook, redact
from src.dataset import read_dataset
from train import train
from check_scenarios import check


def test_selection_preserves_holdout_and_best_validation(tmp_path):
    rows = read_dataset('examples/chamados-sinteticos.csv')
    # A palavra de controle nunca deve entrar no vocabulário ajustado.
    for row in rows:
        if row['particao'] == 'teste':
            row['texto'] += ' palavratestesigilosa'
    dataset = tmp_path / 'dataset.csv'
    with dataset.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    target = tmp_path / 'model.joblib'
    summary = train(dataset, target)
    model = joblib.load(target)
    for dimension in ('impacto', 'urgencia'):
        selection = summary['selecao'][dimension]
        selected = next(c for c in selection['candidatos'] if all(c[k] == v for k, v in selection['escolhido'].items()))
        assert selected['macro_f1'] == max(c['macro_f1'] for c in selection['candidatos'])
        vectorizer = model[dimension]['tfidf']
        vectorizers = [v for _, v in vectorizer.transformer_list] if hasattr(vectorizer, 'transformer_list') else [vectorizer]
        assert all('palavratestesigilosa' not in v.vocabulary_ for v in vectorizers)
    # Alterar apenas o teste preserva os escores e a configuração selecionada.
    reference = train('examples/chamados-sinteticos.csv', tmp_path / 'reference.joblib')
    assert summary['selecao'] == reference['selecao']
    report = check(target, 'examples/cenarios-prioridade.json', tmp_path / 'scenarios.json')
    assert report['total'] == 12
    assert report['homologado'] is False


def test_contradictory_labels_rejected(tmp_path):
    rows = read_dataset('examples/chamados-sinteticos.csv')
    duplicate = {**rows[0], 'id': 'conflict', 'impacto': 'ALTO'}
    target = tmp_path / 'conflict.csv'
    with target.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows + [duplicate])
    with pytest.raises(ValueError, match='contraditórios'):
        read_dataset(target)


def test_redaction():
    text = 'Ana Teste: ana@example.com, (11) 99999-1234, http://example.com, 192.168.1.2'
    result = redact(text, ['Ana Teste', 'Teste', 'Ana'])
    assert 'Ana Teste' not in result and 'example.com' not in result and '99999' not in result
    assert '[PESSOA]' in result and '[IP]' in result


def test_workbook_sparse_cells_and_pending_labels(tmp_path, monkeypatch):
    # Fixture artificial, sem registros reais. Simula células vazias omitidas pelo Excel.
    import import_xlsx
    monkeypatch.setattr(import_xlsx, '__file__', str(tmp_path / 'import_xlsx.py'))
    workbook = tmp_path / 'fixture.xlsx'
    headers = ['ID', 'Nome do contato', 'Destino', 'Natureza sugerida', 'Natureza validada', 'Texto inicial']
    cells = ''.join(f'<c r="{chr(65+i)}1" t="inlineStr"><is><t>{h}</t></is></c>' for i, h in enumerate(headers))
    sheet = ('<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
             f'<row r="1">{cells}</row><row r="2">'
             '<c r="A2"><v>1</v></c><c r="B2" t="inlineStr"><is><t>Pessoa Ficticia</t></is></c>'
             '<c r="F2" t="inlineStr"><is><t>Pessoa Ficticia pede suporte.</t></is></c>'
             '</row></sheetData></worksheet>')
    with zipfile.ZipFile(workbook, 'w') as archive:
        archive.writestr('xl/workbook.xml', '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Dados" r:id="r1"/></sheets></workbook>')
        archive.writestr('xl/_rels/workbook.xml.rels', '<Relationships><Relationship Id="r1" Target="worksheets/sheet1.xml"/></Relationships>')
        archive.writestr('xl/worksheets/sheet1.xml', sheet)
    target = tmp_path / 'data/processed/review.csv'
    summary = import_workbook(workbook, target)
    assert summary['linhas'] == 1 and not summary['pronto_para_treino']
    with target.open(encoding='utf-8', newline='') as handle:
        row = next(csv.DictReader(handle))
    assert 'Pessoa Ficticia' not in target.read_text(encoding='utf-8')
    assert row['texto'] == '[PESSOA] pede suporte.'
    assert row['impacto'] == row['urgencia'] == row['origem'] == row['particao'] == ''
    with pytest.raises(ValueError, match='fora do Git'):
        import_workbook(workbook, tmp_path / 'public.csv')
