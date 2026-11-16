from typing import Literal
from uuid import UUID
from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field, field_validator

Level = Literal['BAIXO', 'MEDIO', 'ALTO']


class Context(BaseModel):
    model_config = ConfigDict(extra='forbid')
    cargo: str | None = Field(default=None, max_length=100)
    setor: str | None = Field(default=None, max_length=150)
    criticidade_servico: Literal['BAIXA', 'MEDIA', 'ALTA'] | None = None
    confirmado: bool = False


class PredictionRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    texto: str = Field(min_length=1, max_length=10000)
    impacto_informado: Level | None = None
    urgencia_informada: Level | None = None
    contexto: Context = Field(default_factory=Context)

    @field_validator('texto')
    @classmethod
    def text_not_blank(cls, value):
        if not value.strip():
            raise ValueError('Texto deve conter conteúdo')
        return value

    @field_validator('impacto_informado', 'urgencia_informada', mode='before')
    @classmethod
    def normalize_level(cls, value):
        return value.strip().upper() if isinstance(value, str) else value


class PredictionResponse(BaseModel):
    prioridade_recomendada: Literal['BAIXA', 'MEDIA', 'ALTA', 'CRITICA']
    prioridade_base: Literal['BAIXA', 'MEDIA', 'ALTA', 'CRITICA']
    prioridade_matriz: Literal['BAIXA', 'MEDIA', 'ALTA', 'CRITICA']
    metodo_prioridade: Literal['MATRIZ', 'FEEDBACK_TECNICO']
    confianca_prioridade: float | None
    impacto: Level
    urgencia: Level
    origem: Literal['INFORMADO', 'MODELO', 'MISTO']
    origem_impacto: Literal['INFORMADO', 'MODELO']
    origem_urgencia: Literal['INFORMADO', 'MODELO']
    confianca_impacto: float | None
    confianca_urgencia: float | None
    revisao_humana_obrigatoria: Literal[True] = True
    baixa_confianca: bool
    versao_modelo: str | None
    modelo_sintetico: bool
    versao_matriz: str
    versao_regras: str
    regras_aplicadas: list[dict[str, str]]


class PriorityFeedback(BaseModel):
    model_config = ConfigDict(extra='forbid')
    evento_id: UUID
    grupo: UUID
    texto: str = Field(min_length=1, max_length=10000)
    acao: Literal['ACEITAR', 'CORRIGIR']
    prioridade_final: Literal['BAIXA', 'MEDIA', 'ALTA', 'CRITICA']
    decidida_em: datetime
    servico_critico_confirmado: bool = False

    @field_validator('decidida_em')
    @classmethod
    def timezone_required(cls, value):
        if value.tzinfo is None:
            raise ValueError('Data da decisão deve conter fuso horário')
        return value

    @field_validator('texto')
    @classmethod
    def meaningful_text(cls, value):
        if not value.strip():
            raise ValueError('Texto vazio')
        return value
