import { Link } from 'react-router-dom'
import { PageTitle } from '../components/ui/States'
export function NotFoundPage() {
  return (
    <div className="not-found">
      <span className="eyebrow">404</span>
      <PageTitle
        title="Página não encontrada"
        description="Confira o endereço ou volte para a central de atendimento."
      />
      <Link className="button" to="/">
        Voltar ao dashboard
      </Link>
    </div>
  )
}
