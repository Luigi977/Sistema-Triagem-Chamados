import csv
import pytest
from fastapi.testclient import TestClient
from src.main import app
from src.model import load_model
from src.rules import priority
from src.dataset import read_dataset
from train import train
from evaluate import evaluate

client = TestClient(app)


@pytest.mark.parametrize('impact,urgency,expected', [
    ('BAIXO', 'BAIXO', 'BAIXA'), ('BAIXO', 'MEDIO', 'BAIXA'), ('BAIXO', 'ALTO', 'MEDIA'),
    ('MEDIO', 'BAIXO', 'BAIXA'), ('MEDIO', 'MEDIO', 'MEDIA'), ('MEDIO', 'ALTO', 'ALTA'),
    ('ALTO', 'BAIXO', 'MEDIA'), ('ALTO', 'MEDIO', 'ALTA'), ('ALTO', 'ALTO', 'CRITICA')])
def test_matrix(impact, urgency, expected):
    response = client.post('/predict/priority', json={'texto': 'Exemplo', 'impacto_informado': impact, 'urgencia_informada': urgency})
    assert response.status_code == 200
    data = response.json()
    assert data['prioridade_recomendada'] == expected == priority(impact, urgency)
    assert data['origem'] == 'INFORMADO'
    assert data['confianca_impacto'] is None
    assert data['revisao_humana_obrigatoria'] is True


@pytest.mark.parametrize('body', [{'texto': ' '}, {'texto': 'x', 'impacto_informado': 'URGENTE'}, {'texto': 'x', 'extra': 1}, {'texto': 'x' * 10001}])
def test_invalid(body):
    assert client.post('/predict/priority', json=body).status_code == 422


def test_no_model(monkeypatch, tmp_path):
    monkeypatch.setenv('IA_MODEL_PATH', str(tmp_path / 'missing'))
    load_model.cache_clear()
    assert client.post('/predict/priority', json={'texto': 'Servidor caiu'}).status_code == 503


def test_context():
    body = {'texto': 'Instalação', 'impacto_informado': 'BAIXO', 'urgencia_informada': 'BAIXO',
            'contexto': {'cargo': 'Diretor', 'setor': 'Financeiro'}}
    assert client.post('/predict/priority', json=body).json()['prioridade_recomendada'] == 'BAIXA'
    body['contexto'].update(criticidade_servico='ALTA', confirmado=False)
    assert client.post('/predict/priority', json=body).json()['regras_aplicadas'] == []
    body['contexto']['confirmado'] = True
    data = client.post('/predict/priority', json=body).json()
    assert data['prioridade_base'] == 'BAIXA' and data['prioridade_recomendada'] == 'ALTA'
    assert len(data['regras_aplicadas']) == 1


def test_training_roundtrip_and_evaluation(tmp_path, monkeypatch):
    model = tmp_path / 'model.joblib'
    train('examples/chamados-sinteticos.csv', model)
    monkeypatch.setenv('IA_MODEL_PATH', str(model))
    monkeypatch.setenv('IA_ALLOW_SYNTHETIC_MODEL', 'true')
    load_model.cache_clear()
    try:
        data = client.post('/predict/priority', json={'texto': 'Empresa inteira parou agora', 'impacto_informado': 'ALTO'}).json()
        assert data['origem'] == 'MISTO'
        assert data['confianca_impacto'] is None and 0 <= data['confianca_urgencia'] <= 1
        assert data['modelo_sintetico'] is True
        report = evaluate('examples/chamados-sinteticos.csv', model, tmp_path / 'report')
        assert report['amostras_teste'] > 0 and report['homologado'] is False
        monkeypatch.setenv('IA_ALLOW_SYNTHETIC_MODEL', 'false')
        load_model.cache_clear()
        assert client.post('/predict/priority', json={'texto': 'teste'}).status_code == 503
    finally:
        load_model.cache_clear()


def test_leakage_rejected(tmp_path):
    rows = read_dataset('examples/chamados-sinteticos.csv')
    rows[-1]['grupo'] = rows[0]['grupo']
    path = tmp_path / 'invalid.csv'
    with path.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    with pytest.raises(ValueError, match='Vazamento'):
        read_dataset(path)
