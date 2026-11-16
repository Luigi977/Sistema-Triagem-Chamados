"""Aplica revisão IA explícita e integra dados reais à tabela sintética."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path
from generate_training_table import FIELDS, export_excel
from src.dataset import read_dataset, fingerprint
from src.rules import LEVELS, priority

EXTRA = ('id_planilha', 'revisor', 'status_revisao_ia', 'revisao_humana', 'confianca_rotulo',
         'justificativa_rotulo', 'pontos_a_confirmar', 'versao_revisao')
HUMAN_FIELDS = ('texto', 'impacto', 'urgencia', 'prioridade', 'id_planilha', 'grupo', 'particao',
                'origem', 'fonte', 'revisor', 'status_revisao_ia', 'revisao_humana',
                'confianca_rotulo', 'justificativa_rotulo', 'pontos_a_confirmar')
HEADERS = ('Mensagem do chamado', 'Impacto', 'Urgência', 'Prioridade', 'ID', 'Grupo', 'Partição',
           'Origem', 'Fonte', 'Revisor', 'Revisão IA', 'Revisão humana', 'Confiança do rótulo',
           'Justificativa', 'Pontos a confirmar')


def write_csv(path, rows, human=False):
    with path.open('x', encoding='utf-8-sig', newline='') as handle:
        if human:
            writer = csv.writer(handle)
            writer.writerow(HEADERS)
            for row in rows:
                writer.writerow([row.get(k, '') for k in HUMAN_FIELDS])
        else:
            writer = csv.DictWriter(handle, fieldnames=FIELDS + EXTRA, extrasaction='ignore')
            writer.writeheader()
            writer.writerows(rows)


def review(source, review_file, synthetic, output):
    with Path(source).open(encoding='utf-8-sig', newline='') as handle:
        rows = list(csv.DictReader(handle))
    config = json.loads(Path(review_file).read_text(encoding='utf-8'))
    if config.get('revisor') != 'IA' or config.get('source_sha256') != fingerprint(source):
        raise ValueError('A revisão deve identificar IA e o hash exato dos textos rotulados')
    ids = {r['id_planilha'] for r in rows}
    if len(ids) != len(rows) or ids != set(config['pontos_a_confirmar']) or not set(config['alteracoes']) <= ids:
        raise ValueError('Revisão deve cobrir todos os IDs exatamente uma vez')
    destination = Path(output).resolve()
    private = Path(__file__).resolve().parent / 'data' / 'processed'
    if not destination.is_relative_to(private):
        raise ValueError('Dados reais devem permanecer em ia/data/processed/')
    if destination.exists():
        raise FileExistsError('Use um novo diretório para preservar as versões anteriores')
    changes = []
    for row in rows:
        if row['origem'] != 'REAL_ROTULADO_IA' or row['revisao_humana'] != 'PENDENTE':
            raise ValueError('Esta rotina revisa somente rótulos IA ainda sem revisão humana')
        old = (row['impacto'], row['urgencia'])
        updates = config['alteracoes'].get(row['id_planilha'], {})
        if not set(updates) <= {'impacto', 'urgencia', 'confianca_rotulo', 'justificativa_rotulo'}:
            raise ValueError('Revisão não pode alterar texto, grupo, partição ou autoria humana')
        row.update(updates)
        if row['impacto'] not in LEVELS or row['urgencia'] not in LEVELS:
            raise ValueError('Dimensões inválidas')
        if not config['pontos_a_confirmar'][row['id_planilha']].strip() or not row['justificativa_rotulo'].strip():
            raise ValueError('Justificativas e pontos de confirmação devem estar preenchidos')
        if row['confianca_rotulo'] not in ('BAIXA', 'MEDIA', 'ALTA'):
            raise ValueError('Confiança qualitativa inválida')
        if old != (row['impacto'], row['urgencia']):
            changes.append({'id': row['id_planilha'], 'antes': list(old), 'depois': [row['impacto'], row['urgencia']]})
        row.update(prioridade=priority(row['impacto'], row['urgencia']),
                   revisor='IA', rotulador='IA', status_revisao_ia='CONCLUIDA', revisao_humana='PENDENTE',
                   pontos_a_confirmar=config['pontos_a_confirmar'][row['id_planilha']],
                   versao_revisao=config['versao'], fonte='PLANILHA_REAL_REVISAO_IA',
                   familia='REAIS_PLANILHA', cenario=row['id_planilha'], variante='original')
    base = read_dataset(synthetic)
    if any(r['origem'] != 'SINTETICO' for r in base):
        raise ValueError('A base de destino deve conter somente exemplos sintéticos, evitando duplicar os reais')
    for row in base:
        expected = priority(row['impacto'], row['urgencia'])
        if row.get('prioridade', expected) != expected:
            raise ValueError('Prioridade sintética incompatível com a matriz')
        row['prioridade'] = expected
        row.setdefault('fonte', 'SINTETICO')
        row['id_planilha'] = row['id']
    destination.mkdir(parents=True)
    real_csv = destination / 'chamados-reais-revisados.csv'
    real_technical = destination / 'reais-rotulados-ia.csv'
    combined = destination / 'treinamento-com-reais.csv'
    full_table = destination / 'tabela-com-reais.csv'
    # Os reais ficam primeiro na tabela para facilitar a conferência.
    write_csv(real_csv, rows, human=True)
    write_csv(real_technical, rows)
    write_csv(combined, rows + base)
    write_csv(full_table, rows + base, human=True)
    del base
    validated = read_dataset(combined)
    if any(r['prioridade'] != priority(r['impacto'], r['urgencia']) for r in validated):
        raise ValueError('Prioridade incompatível com a matriz')
    summary = {'registros': len(validated), 'reais': len(rows),
               'sinteticos': sum(r['origem'] == 'SINTETICO' for r in validated),
               'revisor': 'IA', 'revisao_humana': 'PENDENTE', 'homologado': False,
               'impacto_reais': dict(Counter(r['impacto'] for r in rows)),
               'urgencia_reais': dict(Counter(r['urgencia'] for r in rows)),
               'prioridade_reais': dict(Counter(r['prioridade'] for r in rows)),
               'confianca_rotulos': dict(Counter(r['confianca_rotulo'] for r in rows)),
               'particoes_reais': dict(Counter(r['particao'] for r in rows)),
               'particoes_total': dict(Counter(r['particao'] for r in validated)),
               'alteracoes_rotulos': changes, 'source_sha256': fingerprint(source),
               'revisao_sha256': fingerprint(review_file), 'base_sha256': fingerprint(synthetic),
               'dataset_sha256': fingerprint(combined)}
    del validated
    notes = [('Registros', summary['registros']), ('Chamados reais', len(rows)),
             ('Sintéticos', summary['sinteticos']), ('Revisor', 'IA; não equivale à validação humana do atendimento.'),
             ('Origem dos reais', 'REAL_ROTULADO_IA; revisão humana PENDENTE; homologado=false.'),
             ('Pendências', 'Campos Pontos a confirmar descrevem o que falta sobre alcance, prazo e alternativa.'),
             ('Divisão', 'Partições dos reais e dos sintéticos foram preservadas; não movidos para o treino.'),
             ('Limitação', 'Os rótulos reais são provisórios e os sintéticos reutilizam modelos de frase.'),
             ('Uso', 'treinamento-com-reais.csv tem o schema técnico; a tabela Excel contém os reais nas primeiras linhas e em aba própria.')]
    export_excel(full_table, destination / 'tabela-com-reais.xlsx', summary, notes=notes, real_table=real_csv)
    export_excel(real_csv, destination / 'chamados-reais-revisados.xlsx',
                 {'registros': len(rows)}, notes=notes[3:])
    (destination / 'resumo.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--review', required=True)
    parser.add_argument('--synthetic', default='data/processed/base-ampla-v1/treinamento-amplo.csv')
    parser.add_argument('--output', default='data/processed/base-ampla-com-reais-v1')
    args = parser.parse_args()
    print(json.dumps(review(args.source, args.review, args.synthetic, args.output), ensure_ascii=False, indent=2))
