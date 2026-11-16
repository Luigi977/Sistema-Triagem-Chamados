"""Converte uma revisão privada explícita em dataset, sem gerar rótulos."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path
from sklearn.model_selection import GroupShuffleSplit
from src.dataset import normalized, read_dataset, fingerprint
from src.rules import LEVELS, priority

PARTITIONS = ('treino', 'validacao', 'teste')
FIELDS = ('id', 'grupo', 'texto', 'impacto', 'urgencia', 'particao', 'origem')


def split_groups(rows):
    """Escolhe divisão por distribuição de rótulos/tamanho, nunca pelo desempenho."""
    groups = [r['grupo'] for r in rows]
    if len(set(groups)) < 3:
        raise ValueError('São necessários pelo menos três grupos independentes')
    totals = {d: Counter(r[d] for r in rows) for d in ('impacto', 'urgencia')}
    best = None
    for seed in range(256):
        remaining, test = next(GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=seed).split(rows, groups=groups))
        train, validation = next(GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=seed).split(
            remaining, groups=[groups[i] for i in remaining]))
        parts = [list(remaining[train]), list(remaining[validation]), list(test)]
        score = 0.0
        for indices, ratio in zip(parts, (0.6, 0.2, 0.2)):
            score += abs(len(indices) / len(rows) - ratio)
            for dimension, counts in totals.items():
                actual = Counter(rows[i][dimension] for i in indices)
                score += sum(abs(actual[level] / count - ratio) for level, count in counts.items())
        if best is None or score < best[0]:
            best = (score, seed, parts)
    for partition, indices in zip(PARTITIONS, best[2]):
        for index in indices:
            rows[index]['particao'] = partition
    return best[1]


def prepare(review, labels, synthetic, output):
    destination = Path(output).resolve()
    private = Path(__file__).resolve().parent / 'data' / 'processed'
    if not destination.is_relative_to(private):
        raise ValueError('Exportar somente em ia/data/processed/')
    if destination.exists():
        raise FileExistsError('Dataset de destino já existe; use outro nome para preservar a revisão')
    with Path(review).open(encoding='utf-8-sig', newline='') as handle:
        rows = list(csv.DictReader(handle))
    annotations = json.loads(Path(labels).read_text(encoding='utf-8'))
    label_map = {f'planilha-{item[0]:04d}': item[1:] for item in annotations['rotulos']}
    if len(label_map) != len(annotations['rotulos']) or set(label_map) != {r['id'] for r in rows}:
        raise ValueError('Rótulos devem cobrir todas as linhas exatamente uma vez')
    expected_hash = annotations.get('review_sha256')
    if expected_hash and expected_hash != fingerprint(review):
        raise ValueError('Fila foi alterada após a rotulagem')
    parent = {r['grupo']: r['grupo'] for r in rows}

    def root(group):
        while parent[group] != group:
            group = parent[group]
        return group

    def merge(groups):
        roots = sorted({root(g) for g in groups})
        for group in roots:
            parent[group] = roots[0]

    by_id = {r['id']: r for r in rows}
    for linked in annotations.get('unir_grupos_por_ids', []):
        merge([by_id[f'planilha-{i:04d}']['grupo'] for i in linked])
    texts = {}
    for row in rows:
        text = normalized(row['texto'])
        if text in texts:
            merge([row['grupo'], texts[text]])
        texts[text] = row['grupo']
    for row in rows:
        impact, urgency, confidence, reason = label_map[row['id']]
        if impact not in LEVELS or urgency not in LEVELS or confidence not in ('BAIXA', 'MEDIA', 'ALTA') or not reason:
            raise ValueError('Anotação incompatível')
        row.update(impacto=impact, urgencia=urgency, origem='REAL_ROTULADO_IA', grupo=root(row['grupo']),
                   prioridade_referencia=priority(impact, urgency), confianca_rotulo=confidence,
                   justificativa_rotulo=reason, rotulador='IA', revisao_humana='PENDENTE',
                   id_planilha='T' + row['id'].split('-')[-1][-3:])
    seed = split_groups(rows)
    fixtures = read_dataset(synthetic)
    combined = [{k: r[k] for k in FIELDS} for r in fixtures + rows]
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open('x', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(combined)
    read_dataset(destination)
    labeled_path = destination.with_name(destination.stem + '-rotulados.csv')
    with labeled_path.open('x', encoding='utf-8-sig', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    summary = {'homologado': False, 'rotulador': 'IA', 'real_rotulado_ia': len(rows),
               'sinteticos': len(fixtures), 'total': len(combined), 'seed_divisao': seed,
               'review_sha256': fingerprint(review), 'labels_sha256': fingerprint(labels),
               'dataset_sha256': fingerprint(destination),
               'grupos_reais': len({r['grupo'] for r in rows}),
               'particoes_reais': dict(Counter(r['particao'] for r in rows)),
               'impacto': dict(Counter(r['impacto'] for r in rows)),
               'urgencia': dict(Counter(r['urgencia'] for r in rows)),
               'confianca_rotulo': dict(Counter(r['confianca_rotulo'] for r in rows))}
    destination.with_suffix('.summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    # Relatório legível sem copiar o texto privado para arquivos versionados.
    lines = [f'# Classificação provisória dos {len(rows)} chamados', '',
             'Rótulos inferidos por IA a partir do texto. Revisão humana pendente. Confiança qualitativa, não probabilidade.', '',
             '| ID | Impacto | Urgência | Prioridade | Confiança do rótulo | Partição | Justificativa |',
             '| --- | --- | --- | --- | --- | --- | --- |']
    for r in rows:
        lines.append('| ' + ' | '.join(str(r[k]).replace('|', '/') for k in
                     ('id_planilha', 'impacto', 'urgencia', 'prioridade_referencia', 'confianca_rotulo', 'particao', 'justificativa_rotulo')) + ' |')
    destination.with_suffix('.rotulos.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--review', required=True)
    parser.add_argument('--labels', required=True)
    parser.add_argument('--synthetic', default='examples/chamados-contextuais.csv')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.review, args.labels, args.synthetic, args.output), ensure_ascii=False, indent=2))
