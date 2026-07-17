import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export function Layout() {
  const { cerrarSesion } = useAuth()

  return (
    <>
      <header className="app-header">
        <Link to="/documentos">Mentaro</Link>
        <button type="button" onClick={cerrarSesion}>
          Cerrar sesión
        </button>
      </header>
      <Outlet />
    </>
  )
}
