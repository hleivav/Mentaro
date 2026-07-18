import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export function Layout() {
  const { cerrarSesion } = useAuth()

  return (
    <>
      <header className="app-header">
        <Link to="/documentos">
          <img className="app-header__icono" src="/icons/icono-192x192.png" alt="" />
          Mentaro
        </Link>
        <button type="button" className="secundario" onClick={cerrarSesion}>
          Cerrar sesión
        </button>
      </header>
      <Outlet />
    </>
  )
}
