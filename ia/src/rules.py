"""Regras determinísticas; cargo nunca determina prioridade sozinho."""
LEVELS = ('BAIXO', 'MEDIO', 'ALTO')
PRIORITIES = ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')
MATRIX_VERSION = 'impacto-urgencia-v1'
CONTEXT_VERSION = 'contexto-v1'
MATRIX = (
    ('BAIXA', 'BAIXA', 'MEDIA'),
    ('BAIXA', 'MEDIA', 'ALTA'),
    ('MEDIA', 'ALTA', 'CRITICA'),
)


def priority(impact, urgency):
    return MATRIX[LEVELS.index(impact)][LEVELS.index(urgency)]


def contextualize(base, context):
    # Apenas serviço explicitamente crítico com contexto confirmado pelo técnico.
    # Piso ALTA; nunca reduz uma prioridade CRITICA produzida pela matriz.
    applied = []
    result = base
    if context.confirmado and context.criticidade_servico == 'ALTA' and PRIORITIES.index(base) < 2:
        result = 'ALTA'
        applied.append({'id': 'SERVICO_CRITICO_CONFIRMADO',
                        'justificativa': 'Criticidade alta confirmada: piso de atendimento ALTA.'})
    return result, applied
