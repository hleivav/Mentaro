import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { usePreferenciaSonido } from '../hooks/usePreferenciaSonido'

export function Layout() {
  const { cerrarSesion } = useAuth()
  const [sonidoActivado, alternarSonido] = usePreferenciaSonido()

  return (
    <>
      <header className="app-header">
        <Link to="/documentos">
          <img className="app-header__icono" src="/icons/icono-192x192.png" alt="" />
          Mentaro
        </Link>
        <div className="app-header__acciones">
          <button
            type="button"
            className="secundario app-header__sonido"
            onClick={alternarSonido}
            aria-pressed={sonidoActivado}
            title="Sonido de concepto nuevo/repaso (apagado por defecto)"
          >
            {sonidoActivado ? '🔊 Sonido' : '🔇 Sonido'}
          </button>
          <button type="button" className="secundario" onClick={cerrarSesion}>
            Cerrar sesión
          </button>
        </div>
      </header>
      <Outlet />
    </>
  )
}
