import type { Recomendacao } from '../types/triagem'
export const recommendationFixture: Recomendacao = {
  idDemonstrativo: 'DEMO-LAB-001',
  natureza: 'INCIDENTE',
  prioridade: 'ALTA',
  impacto: 'Uma estação fictícia do Laboratório Aurora está indisponível.',
  urgencia: 'Atividade demonstrativa prevista para hoje.',
  causas: [
    'Possível falha na alimentação elétrica da estação fictícia.',
    'Possível falha no cabo de energia do cenário de laboratório.',
  ],
  estado: 'PENDENTE',
  origem: 'SIMULADA',
}
