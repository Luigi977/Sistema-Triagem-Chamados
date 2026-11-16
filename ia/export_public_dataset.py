"""Exporta somente campos permitidos e relatos generalizados revisados para publicação."""
import argparse
import csv
import json
import random
from collections import Counter
from pathlib import Path

from generate_training_table import export_excel
from src.dataset import fingerprint, normalized, read_dataset
from src.rules import priority

FIELDS = ('id', 'grupo', 'texto', 'impacto', 'urgencia', 'prioridade', 'particao',
          'origem', 'fonte', 'revisor', 'revisao_humana', 'confianca_rotulo')


def write_csv(path, rows):
    with path.open('x', encoding='utf-8-sig', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def export_public(source, rewrites, synthetic, output):
    with Path(source).open(encoding='utf-8-sig', newline='') as handle:
        original = list(csv.DictReader(handle))
    config = json.loads(Path(rewrites).read_text(encoding='utf-8'))
    if config['source_sha256'] != fingerprint(source):
        raise ValueError('Fonte alterada após revisão dos relatos')
    texts = config['textos']
    if not original or len({r['id'] for r in original}) != len(original):
        raise ValueError('Fonte vazia ou IDs duplicados')
    if set(texts) != {r['id'] for r in original}:
        raise ValueError('Cada registro deve ter exatamente uma reescrita revisada')
    originals = {normalized(r['texto']) for r in original}
    public = []
    for row in original:
        text = texts[row['id']].strip()
        if not text or normalized(text) in originals:
            raise ValueError('Relato vazio ou cópia de texto original')
        # Nenhum nome, ID, hash de contato, justificativa ou metadado livre é copiado.
        if (row['origem'] != 'REAL_ROTULADO_IA' or row['revisor'] != 'IA'
                or row['revisao_humana'] != 'PENDENTE'
                or row['confianca_rotulo'] not in ('BAIXA', 'MEDIA', 'ALTA')
                or row['particao'] not in ('treino', 'validacao', 'teste')):
            raise ValueError('Metadados de origem/revisão incompatíveis com esta exportação')
        public.append({'texto': text, 'impacto': row['impacto'], 'urgencia': row['urgencia'],
                       'prioridade': priority(row['impacto'], row['urgencia']),
                       'particao': row['particao'], 'origem': 'REAL_ROTULADO_IA',
                       'fonte': 'RELATO_GENERALIZADO_IA', 'revisor': 'IA',
                       'revisao_humana': 'PENDENTE', 'confianca_rotulo': row['confianca_rotulo']})
    # Ordem nova; não exporta a correspondência com as linhas da planilha privada.
    random.SystemRandom().shuffle(public)
    for index, row in enumerate(public, 1):
        row['id'] = f'publico-{index:04d}'
        # Agrupamento conservador por partição, sem vínculos individuais de contato.
        row['grupo'] = 'generalizados-' + row['particao']
    generated = read_dataset(synthetic)
    if any(r['origem'] != 'SINTETICO' for r in generated):
        raise ValueError('A base complementar deve conter somente sintéticos')
    generated = [{key: row.get(key, '') for key in FIELDS} for row in generated]
    destination = Path(output)
    if destination.exists():
        raise FileExistsError('Escolha um destino novo; arquivos anteriores são preservados')
    destination.mkdir(parents=True)
    real_csv = destination / 'relatos-generalizados.csv'
    combined = destination / 'treinamento-publico.csv'
    write_csv(real_csv, public)
    write_csv(combined, public + generated)
    read_dataset(combined)
    summary = {'registros': len(public) + len(generated), 'reais': len(public),
               'sinteticos': len(generated), 'particoes_generalizados': dict(Counter(r['particao'] for r in public)),
               'revisao_humana': 'PENDENTE', 'homologado': False,
               'tratamento': 'Relatos reescritos e generalizados por IA; não são transcrições.',
               'grupos': 'Contato removido; generalizados agrupados somente por partição.',
               'limite': 'Revisão automática não certifica anonimato nem autorização de publicação.'}
    export_excel(combined, destination / 'tabela-publica.xlsx', summary,
                 notes=[('Tratamento', summary['tratamento']), ('Grupos', summary['grupos']),
                        ('Revisão humana', 'PENDENTE'), ('Limitação', summary['limite']),
                        ('Rótulos', 'Hipóteses da revisão por IA, preservadas após generalização.'),
                        ('Uso', 'Treinamento e demonstração; não usar como teste de mensagens reais originais.')],
                 real_table=real_csv)
    summary['arquivos_sha256'] = {p.name: fingerprint(p) for p in destination.iterdir()}
    (destination / 'resumo.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--rewrites', required=True, help='JSON privado com hash da fonte e textos revisados')
    parser.add_argument('--synthetic', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(export_public(args.source, args.rewrites, args.synthetic, args.output), ensure_ascii=False, indent=2))
