"""Avalia cenários públicos sintéticos pela mesma função utilizada pela API."""
import argparse
import json
import os
from pathlib import Path
from fastapi.testclient import TestClient
from src.main import app
from src.model import load_model
from src.rules import priority


def check(model_path, cases_path, output):
    previous = {key: os.environ.get(key) for key in ('IA_MODEL_PATH', 'IA_ALLOW_SYNTHETIC_MODEL')}
    cases = json.loads(Path(cases_path).read_text(encoding='utf-8'))
    results = []
    try:
        os.environ['IA_MODEL_PATH'] = str(Path(model_path).resolve())
        os.environ['IA_ALLOW_SYNTHETIC_MODEL'] = 'true'
        load_model.cache_clear()
        with TestClient(app) as client:
            for case in cases:
                response = client.post('/predict/priority', json={'texto': case['texto']})
                response.raise_for_status()
                result = response.json()
                expected = priority(case['impacto'], case['urgencia'])
                results.append({'id': case['id'], 'impacto_esperado': case['impacto'],
                                'urgencia_esperada': case['urgencia'], 'prioridade_esperada': expected,
                                'acertou': result['prioridade_recomendada'] == expected, 'resultado': result})
        report = {'sintetico': True, 'homologado': False, 'total': len(results),
                  'acertos_prioridade': sum(r['acertou'] for r in results),
                  'baixa_confianca': sum(r['resultado']['baixa_confianca'] for r in results),
                  'aviso': 'Cenários exploratórios; não usar para escolher modelos ou comprovar qualidade operacional.',
                  'casos': results}
        destination = Path(output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        return {k: v for k, v in report.items() if k != 'casos'}
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        load_model.cache_clear()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', default='models/priority-contextual.joblib')
    parser.add_argument('--cases', default='examples/cenarios-prioridade.json')
    parser.add_argument('--output', default='reports/contextual/cenarios.json')
    args = parser.parse_args()
    print(json.dumps(check(args.model, args.cases, args.output), ensure_ascii=False, indent=2))
