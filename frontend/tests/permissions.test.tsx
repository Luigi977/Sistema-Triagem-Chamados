import { renderHook } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { AuthContext } from '../src/auth/context'
import { usePermissions } from '../src/hooks/usePermissions'
import { chamado, tecnico } from './fixtures'
import type { Chamado, Tecnico } from '../src/types/api'
it.each<{ title: string; ticket: Chamado; user: Tecnico; edit: boolean }>([
  {
    title: 'técnico assume chamado livre da própria equipe',
    ticket: chamado,
    user: tecnico,
    edit: true,
  },
  {
    title: 'técnico atua no chamado atribuído a si',
    ticket: { ...chamado, responsavelId: tecnico.id },
    user: tecnico,
    edit: true,
  },
  {
    title: 'técnico não edita chamado de outra equipe',
    ticket: { ...chamado, equipe: 'N2' },
    user: tecnico,
    edit: false,
  },
  {
    title: 'técnico não edita chamado atribuído a colega',
    ticket: { ...chamado, responsavelId: tecnico.id + 1 },
    user: tecnico,
    edit: false,
  },
  {
    title: 'administrador edita entre equipes',
    ticket: { ...chamado, equipe: 'N2', responsavelId: tecnico.id + 1 },
    user: { ...tecnico, perfil: 'ADMIN' },
    edit: true,
  },
  {
    title: 'encerramento bloqueia técnico',
    ticket: { ...chamado, status: 'ENCERRADO' },
    user: tecnico,
    edit: false,
  },
  {
    title: 'encerramento bloqueia administrador',
    ticket: { ...chamado, status: 'ENCERRADO' },
    user: { ...tecnico, perfil: 'ADMIN' },
    edit: false,
  },
])('$title', ({ ticket, user, edit }) => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <AuthContext.Provider value={{ tecnico: user, login: vi.fn(), logout: vi.fn() }}>
      {children}
    </AuthContext.Provider>
  )
  const { result } = renderHook(() => usePermissions(ticket), { wrapper })
  expect(result.current.canEdit).toBe(edit)
})
