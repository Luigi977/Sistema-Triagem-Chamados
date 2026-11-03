import { useState, type FormEvent } from 'react'
import { Sparkles, FlaskConical } from 'lucide-react'
import { natures, priorities } from '../../types/api'
import type { Natureza, Prioridade } from '../../types/api'
import type { Recomendacao } from '../../types/triagem'
import { label } from '../../utils/presentation'
import { Badge } from '../ui/States'
export function TriagemPanel({ fixture }: { fixture?: Recomendacao }) {
  const [recommendation, setRecommendation] = useState(fixture)
  const [editing, setEditing] = useState(false)
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!recommendation) return
    const fields = new FormData(event.currentTarget)
    setRecommendation({
      ...recommendation,
      natureza: String(fields.get('natureza')) as Natureza,
      prioridade: String(fields.get('prioridade')) as Prioridade,
      impacto: String(fields.get('impacto')).trim(),
      urgencia: String(fields.get('urgencia')).trim(),
      causas: String(fields.get('causas'))
        .split('\n')
        .map((value) => value.trim())
        .filter(Boolean),
      estado: 'PENDENTE',
    })
    setEditing(false)
  }
  return (
    <section className="card triage-panel stack">
      <h2>
        <Sparkles size={19} />
        {fixture ? 'Recomendação demonstrativa' : 'Apoio à triagem'}
      </h2>
      {!fixture || !recommendation ? (
        <>
          <div className="integration-icon">
            <Sparkles size={27} />
          </div>
          <h3>Triagem automática ainda não disponível</h3>
          <p className="muted">
            O atendimento funciona sem IA. Recomendações serão exibidas quando a integração real
            estiver disponível.
          </p>
        </>
      ) : (
        <>
          <div className="notice demo">
            <FlaskConical size={19} />
            <div>
              <strong>DEMONSTRAÇÃO · Dados simulados</strong>
              <br />
              Cenário fictício {recommendation.idDemonstrativo}. Não corresponde a um cliente real.
              Nenhuma alteração é salva no servidor.
            </div>
          </div>
          {editing ? (
            <form onSubmit={save} className="stack">
              <label>
                Natureza
                <select name="natureza" defaultValue={recommendation.natureza}>
                  {natures.map((value) => (
                    <option key={value} value={value}>
                      {label(value)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Prioridade
                <select name="prioridade" defaultValue={recommendation.prioridade}>
                  {priorities.map((value) => (
                    <option key={value} value={value}>
                      {label(value)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Impacto
                <textarea
                  name="impacto"
                  required
                  maxLength={1000}
                  defaultValue={recommendation.impacto}
                />
              </label>
              <label>
                Urgência
                <textarea
                  name="urgencia"
                  required
                  maxLength={1000}
                  defaultValue={recommendation.urgencia}
                />
              </label>
              <label>
                Causas (uma por linha)
                <textarea
                  name="causas"
                  required
                  maxLength={3000}
                  defaultValue={recommendation.causas.join('\n')}
                />
              </label>
              <div className="actions">
                <button className="button">Aplicar edição local</button>
                <button
                  className="button secondary"
                  type="button"
                  onClick={() => setEditing(false)}
                >
                  Cancelar
                </button>
              </div>
            </form>
          ) : (
            <>
              <div className="actions">
                <Badge value={recommendation.natureza} />
                <Badge value={recommendation.prioridade} />
              </div>
              <dl>
                <dt>Impacto</dt>
                <dd>{recommendation.impacto}</dd>
                <dt>Urgência</dt>
                <dd>{recommendation.urgencia}</dd>
              </dl>
              <h3>Hipóteses demonstrativas</h3>
              <ul>
                {recommendation.causas.map((cause, i) => (
                  <li key={i}>{cause}</li>
                ))}
              </ul>
              {recommendation.estado === 'ACEITA' ? (
                <div className="notice success" role="status">
                  Aceite demonstrativo; não salvo no servidor. Recarregar restaura as fixtures.
                </div>
              ) : (
                <div className="actions">
                  <button
                    className="button"
                    onClick={() => setRecommendation({ ...recommendation, estado: 'ACEITA' })}
                  >
                    Aceitar
                  </button>
                  <button className="button secondary" onClick={() => setEditing(true)}>
                    Editar
                  </button>
                </div>
              )}
              <details>
                <summary>Ver recomendação original</summary>
                <p>
                  {label(fixture.natureza)} · {label(fixture.prioridade)}
                </p>
                <p>{fixture.impacto}</p>
                <p>{fixture.urgencia}</p>
                <ul>
                  {fixture.causas.map((cause, i) => (
                    <li key={i}>{cause}</li>
                  ))}
                </ul>
              </details>
            </>
          )}
        </>
      )}
    </section>
  )
}
