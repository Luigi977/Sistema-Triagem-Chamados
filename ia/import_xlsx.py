"""Prepara uma fila privada de rotulagem; nunca inventa impacto/urgência."""
import argparse
import csv
import hashlib
import json
import re
import unicodedata
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}


def read_xlsx(path):
    # Leitura dos valores da primeira aba, sem executar fórmulas ou macros.
    with zipfile.ZipFile(path) as archive:
        shared = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            shared = [''.join(t.text or '' for t in item.findall('.//m:t', NS))
                      for item in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('m:si', NS)]
        workbook = ET.fromstring(archive.read('xl/workbook.xml'))
        sheet = workbook.find('m:sheets/m:sheet', NS)
        relationship = sheet.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')
        links = ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
        target = next(item.get('Target') for item in links if item.get('Id') == relationship)
        target = target.lstrip('/') if target.startswith('/') else 'xl/' + target
        result = []
        for row in ET.fromstring(archive.read(target)).findall('m:sheetData/m:row', NS):
            values = {}
            for cell in row.findall('m:c', NS):
                if cell.find('m:f', NS) is not None:
                    raise ValueError('Fórmula encontrada; exporte somente valores antes de importar')
                column = re.match(r'[A-Z]+', cell.get('r')).group()
                value = cell.find('m:v', NS)
                text = value.text or '' if value is not None else ''.join(
                    t.text or '' for t in cell.findall('.//m:t', NS))
                values[column] = shared[int(text)] if cell.get('t') == 's' else text
            result.append(values)
    if not result:
        raise ValueError('Planilha vazia')
    header, *rows = result
    return [{title: row.get(column, '').strip() for column, title in header.items()} for row in rows]


def redact(text, names):
    text = unicodedata.normalize('NFKC', text)
    text = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[EMAIL]', text)
    text = re.sub(r'https?://\S+', '[URL]', text, flags=re.I)
    text = re.sub(r'(?<!\w)(?:\+?55[\s.-]?)?\(?\d{2}\)?[\s.-]?\d{4,5}[\s.-]?\d{4}(?!\w)', '[TELEFONE]', text)
    text = re.sub(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', '[IP]', text)
    if names:
        alternatives = '|'.join(re.escape(name) for name in sorted(set(names), key=len, reverse=True) if name)
        # Uma substituição única evita mascarar novamente o marcador [PESSOA].
        text = re.sub(r'(?<![\w\[])(' + alternatives + r')(?![\w\]])', '[PESSOA]', text, flags=re.I)
    return text


def import_workbook(source, output):
    rows = read_xlsx(source)
    required = {'ID', 'Nome do contato', 'Destino', 'Natureza sugerida', 'Natureza validada', 'Texto inicial'}
    if not rows or not required <= rows[0].keys():
        raise ValueError('Cabeçalho da planilha incompatível')
    names = {unicodedata.normalize('NFKC', r['Nome do contato']) for r in rows if r['Nome do contato']}
    tokens = names | {token for name in names for token in name.split() if len(token) > 2}
    tokens = sorted(tokens, key=len, reverse=True)
    destination = Path(output)
    # Dados privados nunca devem ser exportados para exemplos versionados.
    private = Path(__file__).resolve().parent / 'data' / 'processed'
    if not destination.resolve().is_relative_to(private):
        raise ValueError('A fila deve ficar em ia/data/processed/ (fora do Git)')
    prepared = []
    for index, row in enumerate(rows, 1):
        if not row['Texto inicial']:
            continue
        contact = ' '.join(unicodedata.normalize('NFKC', row['Nome do contato']).casefold().split())
        # Conservador: todas as mensagens do mesmo contato permanecem juntas.
        group = hashlib.sha256((contact or 'linha-' + str(index)).encode()).hexdigest()[:16]
        prepared.append({'id': f'planilha-{index:04d}', 'grupo': 'contato-' + group,
                         'texto': redact(row['Texto inicial'], tokens),
                         'impacto': '', 'urgencia': '', 'particao': '', 'origem': '',
                         'destino_revisao': row['Destino'],
                         'natureza_sugerida': row['Natureza sugerida'],
                         'natureza_validada': row['Natureza validada']})
    if not prepared:
        raise ValueError('Nenhum texto para revisão')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open('x', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=prepared[0].keys())
        writer.writeheader()
        writer.writerows(prepared)
    summary = {'linhas': len(prepared), 'grupos_contato': len({r['grupo'] for r in prepared}),
               'rotulos_prioridade_validados': 0, 'pronto_para_treino': False,
               'aviso': 'Mascaramento automático exige revisão manual de nomes, credenciais e contexto identificável.'}
    destination.with_suffix('.summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', default='data/processed/chamados-revisao.csv')
    args = parser.parse_args()
    print(json.dumps(import_workbook(args.input, args.output), ensure_ascii=False, indent=2))
