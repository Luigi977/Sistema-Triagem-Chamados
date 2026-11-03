import type { ReactNode } from 'react'
import { AlertCircle, Inbox, LoaderCircle } from 'lucide-react'
import { label } from '../../utils/presentation'
export function Loading() {
  return (
    <div className="state" role="status">
      <LoaderCircle className="spin" size={24} />
      Carregando dados…
    </div>
  )
}
export function ErrorState({ error, retry }: { error: string; retry?: () => void }) {
  return (
    <div className="notice error" role="alert">
      <AlertCircle size={18} />
      <div>
        {error}
        {retry && (
          <button className="text-button" onClick={retry}>
            Tentar novamente
          </button>
        )}
      </div>
    </div>
  )
}
export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="state">
      <Inbox size={30} />
      <p>{children}</p>
    </div>
  )
}
export function Badge({ value }: { value: string | null | undefined }) {
  return <span className={`badge ${value?.toLowerCase() ?? ''}`}>{label(value)}</span>
}
export function PageTitle({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children?: ReactNode
}) {
  return (
    <div className="page-title">
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </div>
  )
}
