import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './components/layout/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { ChamadosPage } from './pages/ChamadosPage'
import { AtendimentoPage } from './pages/AtendimentoPage'
import { EncerramentoPage } from './pages/EncerramentoPage'
import { TriagemPage } from './pages/TriagemPage'
import { AvaliacaoPage } from './pages/AvaliacaoPage'
import { NotFoundPage } from './pages/NotFoundPage'
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="chamados" element={<ChamadosPage />} />
          <Route path="chamados/:id" element={<AtendimentoPage />} />
          <Route path="chamados/:id/encerrar" element={<EncerramentoPage />} />
          <Route path="triagem" element={<TriagemPage />} />
          <Route path="avaliacao" element={<AvaliacaoPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
