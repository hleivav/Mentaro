import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Documentos } from './pages/Documentos'
import { Subir } from './pages/Subir'
import { SeleccionSecciones } from './pages/SeleccionSecciones'
import { Juego } from './pages/Juego'
import './App.css'

const queryClient = new QueryClient()

function RutaProtegida() {
  const { usuario, cargando } = useAuth()
  if (cargando) return <p>Cargando…</p>
  if (!usuario) return <Navigate to="/" replace />
  return <Layout />
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Login />} />
          <Route element={<RutaProtegida />}>
            <Route path="/documentos" element={<Documentos />} />
            <Route path="/subir" element={<Subir />} />
            <Route path="/documentos/:id/seleccion" element={<SeleccionSecciones />} />
            <Route path="/documentos/:id/jugar" element={<Juego />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
