"""Gera tabela sintética ampla, CSV de treinamento e Excel, sem dados privados."""
import argparse
import csv
import json
import random
import zipfile
from collections import Counter
from itertools import product
from pathlib import Path
from xml.sax.saxutils import escape
from src.dataset import read_dataset, fingerprint
from src.rules import LEVELS, priority

ROOT = Path(__file__).resolve().parent
SCOPES = {
    'BAIXO': [
        'O atendimento envolve apenas minha estação; as demais pessoas trabalham normalmente.',
        'Somente uma pessoa está afetada, sem consequência para a equipe ou outros setores.',
        'O alcance foi confirmado em um único posto de trabalho, sem afetar processos compartilhados.',
        'A solicitação é individual; o restante do departamento consegue continuar suas atividades.',
        'O pedido é do diretor, mas afeta apenas sua estação e não compromete entregas da equipe.',
    ],
    'MEDIO': [
        'O atendimento afeta toda uma equipe de um único departamento; os outros setores estão normais.',
        'O alcance está restrito ao processo compartilhado por um setor, sem atingir os demais.',
        'As atividades de uma equipe dependem deste atendimento, mas as outras equipes não foram afetadas.',
        'A consequência foi confirmada para um departamento inteiro, sem alcance na organização toda.',
        'O problema interfere na entrega coletiva de um setor, embora esteja localizado em um equipamento.',
    ],
    'ALTO': [
        'O alcance foi confirmado em todos os setores da empresa e nas atividades da organização inteira.',
        'Vários departamentos e unidades dependem deste atendimento; a consequência é organizacional.',
        'As atividades compartilhadas por toda a empresa foram afetadas, sem restrição a uma equipe.',
        'Foi confirmado que o atendimento tem consequência em múltiplos setores da organização.',
        'O problema afeta o processo corporativo utilizado por todas as unidades da empresa.',
    ],
}
DEADLINES = {
    'BAIXO': [
        'Há uma alternativa segura e as atividades continuam; o atendimento pode esperar a próxima semana.',
        'Não há prazo próximo; o trabalho segue por outro procedimento e pode aguardar vários dias.',
        'O procedimento alternativo está funcionando, não há perda em curso e o prazo é para a próxima semana.',
        'Podemos agendar para daqui a alguns dias; as atividades estão preservadas por uma alternativa confirmada.',
    ],
    'MEDIO': [
        'O prazo é até amanhã e existe um procedimento temporário para continuar o trabalho.',
        'Precisamos concluir no próximo dia útil; a alternativa está disponível por enquanto.',
        'O prazo se aproxima, mas conseguimos seguir temporariamente por outra ferramenta.',
        'Existe prazo para amanhã e uma solução temporária já foi confirmada pelo atendimento.',
    ],
    'ALTO': [
        'A atividade está interrompida agora, não existe alternativa e a solução é necessária imediatamente.',
        'O prazo confirmado vence nos próximos minutos e não há contorno para concluir a atividade.',
        'Não conseguimos continuar o trabalho e o atendimento confirmou que não existe procedimento alternativo.',
        'A operação está parada neste momento, sem alternativa disponível e com necessidade imediata de retomada.',
    ],
}
# Os oito exemplos do usuário ficam com as famílias correspondentes no treino.
USER_FAMILIES = ('servidores', 'financeiro', 'sistema-operacional', 'impressoras',
                 'desempenho', 'instalacao', 'rede-cabeada', 'recuperacao-arquivos')
FIELDS = ('id', 'grupo', 'texto', 'impacto', 'urgencia', 'prioridade', 'particao',
          'origem', 'familia', 'cenario', 'variante', 'fonte')


def partition_families(catalog):
    families = [item['familia'] for item in catalog]
    if len(families) != len(set(families)) or not set(USER_FAMILIES) <= set(families):
        raise ValueError('Catálogo com famílias ausentes ou repetidas')
    remaining = sorted(set(families) - set(USER_FAMILIES))
    random.Random(42).shuffle(remaining)
    train = list(USER_FAMILIES) + remaining[:16]
    return {family: part for part, names in
            [('treino', train), ('validacao', remaining[16:24]), ('teste', remaining[24:])]
            for family in names}


def iter_cases(catalog, variants=60):
    partitions = partition_families(catalog)
    for item in catalog:
        family = item['familia']
        for index, description in enumerate(item['cenarios'], 1):
            scenario = f'{family}-{index:02d}'
            for impact, urgency in product(LEVELS, repeat=2):
                combinations = list(product(SCOPES[impact], DEADLINES[urgency], range(3)))
                for variant, (scope, deadline, order) in enumerate(combinations[:variants], 1):
                    if order == 0:
                        text = f'{description} {scope} {deadline}'
                    elif order == 1:
                        text = f'{scope} {description} {deadline}'
                    else:
                        text = f'{deadline} Relato do atendimento: {description} {scope}'
                    yield {'id': f'amplo-{scenario}-{impact}-{urgency}-{variant:02d}',
                           'grupo': 'familia-' + family, 'texto': text, 'impacto': impact,
                           'urgencia': urgency, 'prioridade': priority(impact, urgency),
                           'particao': partitions[family], 'origem': 'SINTETICO',
                           'familia': family, 'cenario': scenario, 'variante': str(variant),
                           'fonte': 'GERACAO_POR_CENARIOS'}
    with (ROOT / 'examples/exemplos-usuario.csv').open(encoding='utf-8', newline='') as handle:
        examples = list(csv.DictReader(handle))
    for example, family in zip(examples, USER_FAMILIES):
        if priority(example['impacto'], example['urgencia']) != example['prioridade']:
            raise ValueError('Exemplo do usuário incompatível com a matriz')
        yield {**{k: example[k] for k in ('texto', 'impacto', 'urgencia', 'prioridade')},
               'id': 'amplo-' + example['id'], 'grupo': 'familia-' + family,
               'particao': 'treino', 'origem': 'SINTETICO', 'familia': family,
               'cenario': example['id'], 'variante': 'original', 'fonte': 'EXEMPLO_USUARIO'}


def column(index):
    result = ''
    while index:
        index, remainder = divmod(index - 1, 26)
        result = chr(65 + remainder) + result
    return result


def write_sheet(archive, name, headers, rows, widths, count):
    ns = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
    with archive.open(name, 'w') as handle:
        def write(value):
            handle.write(value.encode('utf-8'))
        write(f'<worksheet xmlns="{ns}"><sheetViews><sheetView workbookViewId="0">'
              '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>'
              '</sheetView></sheetViews><cols>')
        for index, width in enumerate(widths, 1):
            write(f'<col min="{index}" max="{index}" width="{width}" customWidth="1"/>')
        write('</cols><sheetData>')
        for index, row in enumerate(iter_with_header(headers, rows), 1):
            write(f'<row r="{index}">')
            for j, value in enumerate(row, 1):
                style = 1 if index == 1 else 0
                write(f'<c r="{column(j)}{index}" t="inlineStr" s="{style}"><is><t xml:space="preserve">'
                      + escape(str(value)) + '</t></is></c>')
            write('</row>')
        write(f'</sheetData><autoFilter ref="A1:{column(len(headers))}{count + 1}"/></worksheet>')


def iter_with_header(headers, rows):
    yield headers
    yield from rows


def export_excel(table, target, summary, notes=None, real_table=None):
    ns = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
    rel = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
    notes = notes if notes is not None else [
        ('Total de registros', summary['registros']), ('Cenários definidos', summary['cenarios_catalogo']),
        ('Famílias de atendimento', summary['familias']), ('Variações por combinação', summary['variantes_por_combinacao']),
        ('Origem', 'SINTETICO: exemplos artificiais; não representam chamados reais validados.'),
        ('Limitação', 'Modelos de frase de alcance e prazo são reutilizados entre partições. As métricas podem ser otimistas.'),
        ('Rótulos', 'Impacto e urgência são explícitos no contexto; prioridade calculada pela matriz existente.'),
        ('Separação', 'Cada família e suas variações pertencem a uma única partição; oito exemplos do usuário ficam no treino.'),
        ('Uso', 'CSV tecnico: treinamento-amplo.csv, compatível com train.py. O Excel e a tabela CSV têm cabeçalhos legíveis.'),
        ('Avaliação', 'Preserve também um teste externo com chamados reais revisados. Não use esta base para homologação.'),
    ]
    sheet_count = 3 if real_table is not None else 2
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('[Content_Types].xml',
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            + ''.join(f'<Override PartName="/xl/worksheets/sheet{i}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' for i in range(1, sheet_count + 1)) + '</Types>')
        archive.writestr('_rels/.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            f'<Relationship Id="rId1" Type="{rel}/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        archive.writestr('xl/workbook.xml', f'<workbook xmlns="{ns}" xmlns:r="{rel}"><sheets>'
            '<sheet name="Chamados" sheetId="1" r:id="rId1"/>'
            '<sheet name="Leia-me" sheetId="2" r:id="rId2"/>'
            + ('<sheet name="Reais revisados por IA" sheetId="3" r:id="rId3"/>' if real_table is not None else '')
            + '</sheets></workbook>')
        archive.writestr('xl/_rels/workbook.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            + ''.join(f'<Relationship Id="rId{i}" Type="{rel}/worksheet" Target="worksheets/sheet{i}.xml"/>' for i in range(1, sheet_count + 1))
            + f'<Relationship Id="rId{sheet_count + 1}" Type="{rel}/styles" Target="styles.xml"/></Relationships>')
        archive.writestr('xl/styles.xml', f'<styleSheet xmlns="{ns}">'
            '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>'
            '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>'
            '<borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
            '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>'
            '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>'
            '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>')
        with table.open(encoding='utf-8-sig', newline='') as handle:
            reader = csv.reader(handle)
            headers = next(reader)
            write_sheet(archive, 'xl/worksheets/sheet1.xml', headers, reader,
                        [100, 12, 12, 14] + [30] * (len(headers) - 4), summary['registros'])
        write_sheet(archive, 'xl/worksheets/sheet2.xml', ['Item', 'Descrição'], notes, [32, 120], len(notes))
        if real_table is not None:
            with Path(real_table).open(encoding='utf-8-sig', newline='') as handle:
                reader = csv.reader(handle)
                headers = next(reader)
                write_sheet(archive, 'xl/worksheets/sheet3.xml', headers, reader,
                            [100, 12, 12, 14] + [38] * (len(headers) - 4), summary['reais'])


def generate(output, variants=60):
    if not 1 <= variants <= 60:
        raise ValueError('Variantes deve estar entre 1 e 60')
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    technical = destination / 'treinamento-amplo.csv'
    table = destination / 'tabela-chamados.csv'
    workbook = destination / 'tabela-chamados.xlsx'
    targets = (technical, table, workbook, destination / 'resumo.json')
    if any(target.exists() for target in targets):
        raise FileExistsError('Saída existente; escolha outro diretório para preservar os arquivos')
    catalog = json.loads((ROOT / 'examples/catalogo-cenarios.json').read_text(encoding='utf-8'))
    counts, pairs, partitions = Counter(), Counter(), Counter()
    with technical.open('x', encoding='utf-8-sig', newline='') as tech_handle, table.open('x', encoding='utf-8-sig', newline='') as table_handle:
        writer = csv.DictWriter(tech_handle, fieldnames=FIELDS)
        writer.writeheader()
        human = csv.writer(table_handle)
        human.writerow(['Mensagem do chamado', 'Impacto', 'Urgência', 'Prioridade', 'ID', 'Família', 'Partição', 'Origem', 'Fonte'])
        for row in iter_cases(catalog, variants):
            writer.writerow(row)
            human.writerow([row[k] for k in ('texto', 'impacto', 'urgencia', 'prioridade', 'id', 'familia', 'particao', 'origem', 'fonte')])
            counts[row['fonte']] += 1
            pairs[row['impacto'] + '/' + row['urgencia']] += 1
            partitions[row['particao']] += 1
    validated = read_dataset(technical)
    seen = set()
    from src.dataset import normalized
    for row in validated:
        key = normalized(row['texto'])
        if key in seen:
            raise ValueError('Texto duplicado na base gerada')
        seen.add(key)
        if priority(row['impacto'], row['urgencia']) != row['prioridade']:
            raise ValueError('Prioridade incompatível com a matriz')
    del validated, seen
    summary = {'registros': sum(counts.values()), 'cenarios_catalogo': sum(len(item['cenarios']) for item in catalog),
               'familias': len(catalog), 'variantes_por_combinacao': variants, 'fontes': dict(counts),
               'combinacoes': dict(pairs), 'particoes': dict(partitions), 'sintetico': True, 'homologado': False,
               'dataset_sha256': fingerprint(technical), 'seed': 42,
               'limitacao': 'Casos combinatórios com modelos de frase reutilizados; não equivalem a registros reais independentes.'}
    export_excel(table, workbook, summary)
    (destination / 'resumo.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='data/processed/base-ampla-v1')
    parser.add_argument('--variantes', type=int, default=60)
    args = parser.parse_args()
    print(json.dumps(generate(args.output, args.variantes), ensure_ascii=False, indent=2))
