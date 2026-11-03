import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, ShieldCheck, MessagesSquare } from 'lucide-react'
import { useAuth } from '../auth/context'
import { errorMessage } from '../services/api'
import { ErrorState } from '../components/ui/States'
export function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { from?: string; notice?: string } | null
  const target =
    state?.from?.startsWith('/') && !state.from.startsWith('//') && !state.from.startsWith('/login')
      ? state.from
      : '/'
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (auth.tecnico) return <Navigate to={target} replace />
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    try {
      await auth.login(String(data.get('login')), String(data.get('senha')))
      navigate(target, { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="login-page">
      <section className="login-story">
        <div className="brand">
          <span className="brand-icon">T</span>
          <div>
            Triagem TI<small>Central de atendimento</small>
          </div>
        </div>
        <div className="story-content">
          <span className="eyebrow">SUPORTE QUE CONECTA</span>
          <h1>
            Cada conversa.
            <br />
            Uma solução
            <br />
            <span>mais próxima.</span>
          </h1>
          <p>
            Organize os chamados, acompanhe o contexto e cuide de cada atendimento em um só lugar.
          </p>
          <div className="story-card">
            <MessagesSquare size={28} />
            <div>
              <strong>Do primeiro contato à resolução</strong>
              <span>Sua equipe e seus clientes, conectados.</span>
            </div>
          </div>
        </div>
        <small>Uma central feita para quem resolve.</small>
      </section>
      <section className="login-form-wrap">
        <form className="login-form" onSubmit={(event) => void submit(event)}>
          <span className="eyebrow">BEM-VINDO À CENTRAL</span>
          <h2>Acesse seu workspace</h2>
          <p>Entre com sua conta de técnico para continuar.</p>
          {state?.notice && (
            <div className="notice" role="status">
              {state.notice}
            </div>
          )}
          {error && <ErrorState error={error} />}
          <label>
            Login
            <input
              name="login"
              autoComplete="username"
              required
              maxLength={100}
              placeholder="Seu login"
              disabled={busy}
            />
          </label>
          <label>
            Senha
            <input
              name="senha"
              type="password"
              autoComplete="current-password"
              required
              maxLength={72}
              placeholder="Sua senha"
              disabled={busy}
            />
          </label>
          <button className="button" disabled={busy}>
            {busy ? 'Entrando…' : 'Entrar na central'}
            <ArrowRight size={17} />
          </button>
          <div className="login-security">
            <ShieldCheck size={16} />
            Acesso restrito à equipe de suporte
          </div>
          <p className="small">Ao recarregar a página, será necessário entrar novamente.</p>
        </form>
      </section>
    </div>
  )
}
