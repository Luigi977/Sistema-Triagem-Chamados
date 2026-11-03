import { useState } from 'react'
import { PageTitle } from '../components/ui/States'
import { TriagemPanel } from '../components/triagem/TriagemPanel'
import { demoEnabled, triagemDemoService } from '../services/triagemDemoService'
export function TriagemPage() {
  const [fixture] = useState(() => (demoEnabled ? triagemDemoService.get() : undefined))
  return (
    <>
      <PageTitle
        title="Triagem inteligente"
        description="Apoio à decisão do técnico · integração em preparação."
      />
      <div className="narrow">
        <TriagemPanel fixture={fixture} />
      </div>
    </>
  )
}
