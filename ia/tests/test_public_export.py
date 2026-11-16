import csv
import json
import zipfile
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree as ET

import pytest

from export_public_dataset import FIELDS, export_public
from src.dataset import fingerprint, read_dataset


def fixture_files(tmp_path):
    rows = [{'id': f'privado-{i}', 'grupo': f'contato-secreto-{i}',
             'texto': f'Mensagem privada de Pessoa Exemplo {i}',
             'impacto': 'BAIXO', 'urgencia': 'MEDIO', 'particao': part,
             'origem': 'REAL_ROTULADO_IA', 'revisor': 'IA',
             'revisao_humana': 'PENDENTE', 'confianca_rotulo': 'BAIXA',
             'id_planilha': 'REFERENCIA_PRIVADA', 'justificativa_rotulo': 'CONTEUDO_PRIVADO'}
            for i, part in enumerate(('treino', 'validacao', 'teste'))]
    source = tmp_path / 'private.csv'
    with source.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    config = {'source_sha256': fingerprint(source),
              'textos': {r['id']: f'Falha técnica genérica no equipamento de categoria {i}.'
                         for i, r in enumerate(rows)}}
    rewrites = tmp_path / 'rewrites.json'
    rewrites.write_text(json.dumps(config), encoding='utf-8')
    synthetic = Path(__file__).resolve().parents[1] / 'examples/chamados-sinteticos.csv'
    return source, rewrites, synthetic, config


def test_export_does_not_copy_private_fields_or_contact_links(tmp_path):
    source, rewrites, synthetic, _ = fixture_files(tmp_path)
    original_hash = fingerprint(source)
    output = tmp_path / 'public'
    summary = export_public(source, rewrites, synthetic, output)
    assert fingerprint(source) == original_hash
    data = read_dataset(output / 'treinamento-publico.csv')
    real = [r for r in data if r['fonte'] == 'RELATO_GENERALIZADO_IA']
    assert len(real) == 3
    assert Counter(r['particao'] for r in real) == dict(treino=1, validacao=1, teste=1)
    assert all(set(r) == set(FIELDS) for r in data)
    assert all(r['grupo'] == 'generalizados-' + r['particao'] for r in real)
    assert all(r['revisao_humana'] == 'PENDENTE' for r in real)
    assert not summary['homologado']
    text_outputs = ''.join(p.read_text(encoding='utf-8-sig') for p in output.iterdir() if p.suffix != '.xlsx')
    with zipfile.ZipFile(output / 'tabela-publica.xlsx') as archive:
        assert not any('docProps' in name for name in archive.namelist())
        for name in archive.namelist():
            ET.fromstring(archive.read(name))
            text_outputs += archive.read(name).decode('utf-8')
    for secret in ('Pessoa Exemplo', 'contato-secreto', 'privado-', 'REFERENCIA_PRIVADA', 'CONTEUDO_PRIVADO', original_hash):
        assert secret not in text_outputs
    for name, digest in summary['arquivos_sha256'].items():
        assert fingerprint(output / name) == digest
    with pytest.raises(FileExistsError):
        export_public(source, rewrites, synthetic, output)


@pytest.mark.parametrize('problem', ['changed_source', 'missing_rewrite', 'literal_copy', 'private_metadata'])
def test_export_refuses_unreviewed_or_changed_inputs(tmp_path, problem):
    source, rewrites, synthetic, config = fixture_files(tmp_path)
    if problem == 'changed_source':
        source.write_text(source.read_text(encoding='utf-8') + '\n', encoding='utf-8')
    elif problem == 'missing_rewrite':
        config['textos'].pop('privado-0')
    elif problem == 'literal_copy':
        config['textos']['privado-0'] = 'Mensagem privada de Pessoa Exemplo 0'
    else:
        source.write_text(source.read_text(encoding='utf-8').replace('PENDENTE', 'REFERENCIA_PRIVADA'), encoding='utf-8')
        config['source_sha256'] = fingerprint(source)
    rewrites.write_text(json.dumps(config), encoding='utf-8')
    output = tmp_path / 'public'
    with pytest.raises(ValueError):
        export_public(source, rewrites, synthetic, output)
    assert not output.exists()
