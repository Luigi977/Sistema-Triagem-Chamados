import { recommendationFixture } from '../mocks/triagem'
import type { Recomendacao } from '../types/triagem'
// Limite de integração: sem HTTP nem relação com IDs de chamados reais.
export const demoEnabled = import.meta.env.VITE_ENABLE_AI_DEMO === 'true'
export const triagemDemoService = {
  get: (): Recomendacao => structuredClone(recommendationFixture),
}
