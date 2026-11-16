import os
import secrets
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Header, Depends, Response
from src.model import ModelUnavailable, infer, load_model
from src.rules import MATRIX_VERSION, CONTEXT_VERSION, priority, contextualize
from src.schemas import PredictionRequest, PredictionResponse, PriorityFeedback
from src import learning

@asynccontextmanager
async def lifespan(app):
    worker = None
    if len(os.getenv('IA_FEEDBACK_TOKEN', '')) >= 32 and os.getenv('IA_LEARNING_ENABLED', 'true').lower() == 'true':
        worker = learning.start_worker()
    yield
    if worker:
        worker[0].set()
        worker[1].join(timeout=2)


app = FastAPI(title='Triagem — recomendação de prioridade', version='1.1.0', lifespan=lifespan)


def feedback_auth(x_feedback_token: str = Header(default='')):
    token = os.getenv('IA_FEEDBACK_TOKEN', '')
    if len(token) < 32:
        raise HTTPException(503, 'Recebimento de feedback não configurado')
    if not secrets.compare_digest(token.encode(), x_feedback_token.encode()):
        raise HTTPException(401, 'Credencial interna inválida')


@app.post('/learning/feedback', dependencies=[Depends(feedback_auth)])
def receive_feedback(request: PriorityFeedback, response: Response):
    try:
        created = learning.ingest(request)
    except ValueError as error:
        raise HTTPException(409, str(error)) from error
    response.status_code = 201 if created else 200
    return {'evento_id': str(request.evento_id), 'estado': 'RECEBIDO', 'duplicado': not created}


@app.get('/learning/status', dependencies=[Depends(feedback_auth)])
def learning_status():
    return learning.status()


@app.get('/health')
def health():
    try:
        model = load_model()
        return {'status': 'OK', 'modelo_disponivel': True, 'modelo_sintetico': model['sintetico']}
    except ModelUnavailable:
        return {'status': 'OK', 'modelo_disponivel': False, 'modelo_sintetico': False}


@app.post('/predict/priority', response_model=PredictionResponse)
def predict(request: PredictionRequest):
    impact, urgency = request.impacto_informado, request.urgencia_informada
    model = None
    ci = cu = None
    oi = 'INFORMADO' if impact else 'MODELO'
    ou = 'INFORMADO' if urgency else 'MODELO'
    if impact is None or urgency is None:
        try:
            model = load_model()
        except ModelUnavailable as error:
            raise HTTPException(503, str(error)) from error
        if impact is None:
            impact, ci = infer(model, 'impacto', request.texto)
        if urgency is None:
            urgency, cu = infer(model, 'urgencia', request.texto)
    matrix = priority(impact, urgency)
    base, method, cp = matrix, 'MATRIZ', None
    if model and 'priority_feedback' in model and request.impacto_informado is None and request.urgencia_informada is None:
        critical = request.contexto.confirmado and request.contexto.criticidade_servico == 'ALTA'
        probabilities = model['priority_feedback'].predict_proba([learning.features(request.texto, critical)])[0]
        index = probabilities.argmax()
        base = str(model['priority_feedback'].classes_[index])
        cp = float(probabilities[index])
        method = 'FEEDBACK_TECNICO'
    recommended, rules = contextualize(base, request.contexto)
    return PredictionResponse(
        prioridade_recomendada=recommended, prioridade_base=base, impacto=impact, urgencia=urgency,
        prioridade_matriz=matrix, metodo_prioridade=method, confianca_prioridade=cp,
        origem=oi if oi == ou else 'MISTO', origem_impacto=oi, origem_urgencia=ou,
        confianca_impacto=ci, confianca_urgencia=cu,
        baixa_confianca=any(c < 0.65 for c in (ci, cu, cp) if c is not None),
        versao_modelo=model['version'] if model else None,
        modelo_sintetico=bool(model and model['sintetico']), versao_matriz=MATRIX_VERSION,
        versao_regras=CONTEXT_VERSION, regras_aplicadas=rules)
