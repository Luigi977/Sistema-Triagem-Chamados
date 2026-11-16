"""Acrescenta exemplos explícitos ao treino, preservando validação e teste."""
import argparse
import csv
import json
import tempfile
from collections import Counter
from pathlib import Path
from src.dataset import read_dataset, fingerprint
from src.rules import priority

FIELDS = ('id', 'grupo', 'texto', 'impacto', 'urgencia', 'particao', 'origem')


def augment(base, examples, output):
    rows = read_dataset(base)
    with Path(examples).open(encoding='utf-8-sig', newline='') as handle:
        additions = list(csv.DictReader(handle))
    if not additions:
        raise ValueError('Nenhum exemplo para acrescentar')
    for row in additions:
        if row.get('particao') != 'treino':
            raise ValueError('Novos exemplos devem pertencer somente ao treino')
        expected = priority(row.get('impacto'), row.get('urgencia'))
        if row.get('prioridade') != expected:
            raise ValueError('Prioridade fornecida não corresponde à matriz de impacto/urgência')
        if row.get('rotulador') != 'USUARIO' or row.get('origem') != 'SINTETICO':
            raise ValueError('Exemplos ilustrativos devem identificar rotulador USUARIO e origem SINTETICO')
    destination = Path(output).resolve()
    private = Path(__file__).resolve().parent / 'data' / 'processed'
    if not destination.is_relative_to(private):
        raise ValueError('Dataset deve permanecer em ia/data/processed/')
    if destination.exists():
        raise FileExistsError('Destino já existe; escolha outro nome para preservar a versão anterior')
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Valida o conjunto antes de publicar o arquivo de saída.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', encoding='utf-8', newline='',
                                         dir=destination.parent, delete=False) as handle:
            temporary = Path(handle.name)
            writer = csv.DictWriter(handle, fieldnames=FIELDS, extrasaction='ignore')
            writer.writeheader()
            writer.writerows(rows + additions)
        combined = read_dataset(temporary)
        temporary.rename(destination)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    summary = {'base_sha256': fingerprint(base), 'exemplos_sha256': fingerprint(examples),
               'dataset_sha256': fingerprint(destination), 'adicionados_treino': len(additions),
               'total': len(combined), 'particoes': dict(Counter(r['particao'] for r in combined)),
               'origens': dict(Counter(r['origem'] for r in combined)),
               'rotulador_adicoes': 'USUARIO', 'validacao_teste_preservados': True, 'homologado': False}
    destination.with_suffix('.summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True)
    parser.add_argument('--examples', default='examples/exemplos-usuario.csv')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(augment(args.base, args.examples, args.output), ensure_ascii=False, indent=2))
