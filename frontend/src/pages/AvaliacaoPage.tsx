import { useState } from 'react'
import { Check, Minus, X } from 'lucide-react'
import { PageTitle } from '../components/ui/States'
import { demoEnabled } from '../services/triagemDemoService'
export function AvaliacaoPage() {
  const [rating, setRating] = useState('')
  return (
    <>
      <PageTitle
        title="Avaliação da IA"
        description="Retorno humano sobre as recomendações de triagem."
      />
      <section className="card stack narrow">
        {demoEnabled ? (
          <>
            <div className="notice demo">
              <strong>
                DEMONSTRAÇÃO · Dados simulados · sem persistência ou indicadores reais
              </strong>
            </div>
            <h2>Cenário fictício DEMO-LAB-001</h2>
            <p>Como você avalia as hipóteses do laboratório fictício?</p>
            <div className="actions">
              {[
                { text: 'Correta', Icon: Check },
                { text: 'Parcialmente útil', Icon: Minus },
                { text: 'Incorreta', Icon: X },
              ].map(({ text, Icon }) => (
                <button
                  key={text}
                  aria-pressed={rating === text}
                  className={`button ${rating === text ? '' : 'secondary'}`}
                  onClick={() => setRating(text)}
                >
                  <Icon size={16} />
                  {text}
                </button>
              ))}
            </div>
            {rating && (
              <div role="status" className="notice success">
                Avaliação demonstrativa: {rating}. Não salva no servidor. Recarregar restaura a
                demonstração.
              </div>
            )}
          </>
        ) : (
          <>
            <h2>Avaliação ainda não disponível</h2>
            <p className="muted">
              A avaliação depende da integração real de triagem. Não há avaliações ou indicadores de
              IA persistidos nesta etapa.
            </p>
          </>
        )}
      </section>
    </>
  )
}
