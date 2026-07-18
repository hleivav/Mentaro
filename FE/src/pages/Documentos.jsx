import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useDocumentos } from '../hooks/useDocumentos'
import { IndicadorCarga } from '../components/IndicadorCarga'

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
  if (documento.estado === 'procesando') return <IndicadorCarga texto="Procesando…" />
  if (documento.estado === 'error') return <span role="alert">Hubo un error al procesar este documento</span>
  return <span className="etiqueta">{documento.estado}</span>
}

export function Documentos() {
  const documentos = useDocumentos()
  const queryClient = useQueryClient()

  const eliminar = useMutation({
    mutationFn: (id) => api(`/api/documentos/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documentos'] })
  })

  function manejarEliminar(documento) {
    // Borrado real e irreversible (secciones, unidades, progreso, todo) -
    // nunca sin confirmar primero.
    if (window.confirm(`¿Eliminar "${documento.titulo}"? Esto borra todo tu progreso en el documento.`)) {
      eliminar.mutate(documento.id)
    }
  }

  return (
    <main className="documentos">
      <p className="etiqueta">Tu biblioteca</p>
      <h1>Tus documentos</h1>
      <Link to="/subir">Subir un documento nuevo</Link>

      {documentos.isLoading && <p>Cargando…</p>}
      {documentos.isError && <p role="alert">No se pudieron cargar tus documentos.</p>}
      {documentos.data?.length === 0 && <p>Todavía no subiste ningún documento.</p>}
      {eliminar.isError && <p role="alert">No se pudo eliminar el documento.</p>}

      <ul>
        {documentos.data?.map((documento) => (
          <li key={documento.id}>
            <strong>{documento.titulo}</strong>
            <EstadoDocumento documento={documento} />
            <button
              type="button"
              className="secundario documentos__eliminar"
              disabled={eliminar.isPending}
              onClick={() => manejarEliminar(documento)}
            >
              Eliminar
            </button>
          </li>
        ))}
      </ul>
    </main>
  )
}
