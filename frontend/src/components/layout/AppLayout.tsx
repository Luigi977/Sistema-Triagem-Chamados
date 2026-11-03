import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  MessagesSquare,
  Sparkles,
  ClipboardCheck,
  LogOut,
  Menu,
  X,
  Wifi,
  ExternalLink,
} from 'lucide-react'
import { useAuth } from '../../auth/context'
import { useQuery } from '../../hooks/useQuery'
import { whatsappService } from '../../services/whatsappService'
import { initials, label } from '../../utils/presentation'
export function AppLayout() {
  const { tecnico, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
    document.getElementById('conteudo')?.focus({ preventScroll: true })
  }, [location.pathname])
  const connection = useQuery(
    useCallback((signal: AbortSignal) => whatsappService.status(signal), []),
    30000,
  )
  const state = connection.error ? 'unavailable' : connection.data?.estado
  const connectionText =
    state === 'open'
      ? 'Conectado'
      : state === 'close'
        ? 'Desconectado'
        : state === 'connecting'
          ? 'Conectando'
          : connection.loading
            ? 'Consultando'
            : 'Indisponível'
  async function exit() {
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      navigate('/login', {
        replace: true,
        state: {
          notice: 'Sessão local encerrada. Não foi possível confirmar a revogação no servidor.',
        },
      })
    }
  }
  const links = [
    { to: '/', title: 'Dashboard', Icon: LayoutDashboard },
    { to: '/chamados', title: 'Atendimento', Icon: MessagesSquare },
    { to: '/triagem', title: 'Triagem inteligente', Icon: Sparkles },
    { to: '/avaliacao', title: 'Avaliação da IA', Icon: ClipboardCheck },
  ]
  return (
    <div className="app-shell">
      <a className="skip-link" href="#conteudo">
        Pular para o conteúdo
      </a>
      <header className="mobile-header">
        <span className="brand">
          <span className="brand-icon">T</span>Triagem TI
        </span>
        <button
          className="icon-button"
          aria-label={open ? 'Fechar menu' : 'Abrir menu'}
          aria-expanded={open}
          aria-controls="sidebar"
          onClick={() => setOpen(!open)}
        >
          {open ? <X /> : <Menu />}
        </button>
      </header>
      {open && (
        <button className="menu-overlay" aria-label="Fechar menu" onClick={() => setOpen(false)} />
      )}
      <aside id="sidebar" className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="brand">
          <span className="brand-icon">T</span>
          <div>
            Triagem TI<small>Central de atendimento</small>
          </div>
        </div>
        <div className="nav-caption">ÁREA DE TRABALHO</div>
        <nav aria-label="Navegação principal">
          {links.map(({ to, title, Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} onClick={() => setOpen(false)}>
              <Icon size={18} />
              {title}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">
          <span className="note-dot" />
          Decisões com contexto.
          <br />
          Atendimento com pessoas.
        </div>
        <div className="identity">
          <span className="avatar">{initials(tecnico?.nome ?? '')}</span>
          <div>
            <strong>{tecnico?.nome}</strong>
            <small>
              {label(tecnico?.perfil)} · {tecnico?.equipe}
            </small>
          </div>
        </div>
        <button className="logout" onClick={() => void exit()}>
          <LogOut size={17} />
          Sair da conta
        </button>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Central de suporte <span className="muted">/ Área de trabalho</span>
          </span>
          <div className="connection-group">
            <span
              className={`connection ${state === 'open' ? 'connected' : ''}`}
              title="Estado da conexão do WhatsApp"
            >
              <Wifi size={14} />
              WhatsApp · {connectionText}
            </span>
            {tecnico?.perfil === 'ADMIN' && (
              <a
                className="pairing"
                href={`${import.meta.env.VITE_BACKEND_ORIGIN || 'http://localhost:8085'}/whatsapp.html`}
                target="_blank"
                rel="noreferrer"
                title="Gerenciar conexão WhatsApp"
              >
                Pareamento
                <ExternalLink size={13} />
              </a>
            )}
          </div>
        </header>
        <main id="conteudo" tabIndex={-1}>
          <Outlet />
        </main>
        <footer>
          Triagem TI <span>Atendimento real · IA em preparação</span>
        </footer>
      </div>
    </div>
  )
}
