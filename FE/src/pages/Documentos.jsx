import { Link } from 'react-router-dom'
import { useDocumentos } from '../hooks/useDocumentos'

const ACCION_POR_ESTADO = {
  mapeado: (id) => ({ to: `/documentos/${id}/seleccion`, etiqueta: 'Elegir qué jugar' }),
  generando: (id) => ({ to: `/documentos/${id}/seleccion`, etiqueta: 'Elegir qué jugar' }),
  listo: (id) => ({ to: `/documentos/${id}/jugar`, etiqueta: 'Jugar' })
}

function EstadoDocumento({ documento }) {
  const accion = ACCION_POR_ESTADO[documento.estado]?.(documento.id)
  if (accion) {
    return <Link to={accion.to}>{accion.etiqueta}</Link>
  }
  if (documento.estado === 'procesando') return <span>Procesando…</span>
  if (documento.estado === 'error') return <span role="alert">Hubo un error al procesar este documento</span>
  return <span>{documento.estado}</span>
}

export function Documentos() {
  const documentos = useDocumentos()

  return (
    <main className="documentos">
      <h1>Tus documentos</h1>
      <Link to="/subir">Subir un documento nuevo</Link>

      {documentos.isLoading && <p>Cargando…</p>}
      {documentos.isError && <p role="alert">No se pudieron cargar tus documentos.</p>}
      {documentos.data?.length === 0 && <p>Todavía no subiste ningún documento.</p>}

      <ul>
        {documentos.data?.map((documento) => (
          <li key={documento.id}>
            {documento.titulo} — <EstadoDocumento documento={documento} />
          </li>
        ))}
      </ul>
    </main>
  )
}
