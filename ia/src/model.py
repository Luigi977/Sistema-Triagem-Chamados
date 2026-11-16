import os
from functools import lru_cache
from pathlib import Path
import joblib
import sklearn
from src.rules import LEVELS, PRIORITIES


class ModelUnavailable(Exception):
    pass


def load_model():
    path = Path(os.getenv('IA_MODEL_PATH', 'models/priority.joblib'))
    try:
        return _cached_model(str(path.resolve()), path.stat().st_mtime_ns)
    except Exception as error:
        raise ModelUnavailable('Modelo ausente, incompatível ou não habilitado') from error


@lru_cache(maxsize=1)
def _cached_model(path, modified):
    try:
        # joblib contém código Python: carregar somente artefato local confiável.
        model = joblib.load(path)
        if model['schema'] != 1 or model['sklearn'] != sklearn.__version__:
            raise ValueError('Modelo incompatível')
        for dimension in ('impacto', 'urgencia'):
            if set(model[dimension].classes_) != set(LEVELS):
                raise ValueError('Classes incompatíveis')
        if 'priority_feedback' in model and set(model['priority_feedback'].classes_) != set(PRIORITIES):
            raise ValueError('Classes de prioridade incompatíveis')
        if model['sintetico'] and os.getenv('IA_ALLOW_SYNTHETIC_MODEL', 'false').lower() != 'true':
            raise ValueError('Modelo sintético não habilitado')
        return model
    except Exception as error:
        raise ModelUnavailable('Modelo ausente, incompatível ou não habilitado') from error


load_model.cache_clear = _cached_model.cache_clear


def infer(model, dimension, text):
    probabilities = model[dimension].predict_proba([text])[0]
    index = probabilities.argmax()
    return str(model[dimension].classes_[index]), float(probabilities[index])
