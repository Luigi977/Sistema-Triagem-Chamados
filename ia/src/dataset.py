import csv
import hashlib
import unicodedata
from pathlib import Path
from src.rules import LEVELS


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).lower().split())


def read_dataset(path):
    with Path(path).open(encoding='utf-8-sig', newline='') as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise ValueError('Dataset vazio')
    ids, groups, texts, labels = set(), {}, {}, {}
    for row in rows:
        if not all(row.get(k, '').strip() for k in ('id', 'grupo', 'texto', 'impacto', 'urgencia', 'particao', 'origem')):
            raise ValueError('Campos obrigatórios ausentes')
        if row['impacto'] not in LEVELS or row['urgencia'] not in LEVELS:
            raise ValueError('Rótulo inválido')
        if row['particao'] not in ('treino', 'validacao', 'teste') or row['origem'] not in ('SINTETICO', 'REAL_ANONIMIZADO', 'REAL_ROTULADO_IA'):
            raise ValueError('Partição/origem inválida')
        if row['id'] in ids:
            raise ValueError('ID duplicado')
        if len(row['texto']) > 10000:
            raise ValueError('Texto acima do limite da API (10000 caracteres)')
        text_key = normalized(row['texto'])
        label = (row['impacto'], row['urgencia'])
        if text_key in labels and labels[text_key] != label:
            raise ValueError('Mesmo texto com rótulos contraditórios')
        labels[text_key] = label
        ids.add(row['id'])
        for key, mapping in ((row['grupo'], groups), (normalized(row['texto']), texts)):
            if key in mapping and mapping[key] != row['particao']:
                raise ValueError('Vazamento entre partições: grupo ou texto duplicado')
            mapping[key] = row['particao']
    for part in ('treino', 'validacao', 'teste'):
        for dimension in ('impacto', 'urgencia'):
            if {r[dimension] for r in rows if r['particao'] == part} != set(LEVELS):
                raise ValueError(f'{part}/{dimension} deve conter as três classes')
    return rows


def fingerprint(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
