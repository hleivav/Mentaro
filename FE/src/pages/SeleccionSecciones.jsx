import { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { api, TextoFuenteExpiradoError } from '../lib/api'
import { useMapaDocumento } from '../hooks/useMapaDocumento'
import { ArbolSecciones } from '../components/ArbolSecciones'
import { SelectorProfundidad } from '../components/SelectorProfundidad'
import { NIVELES_INCLUIDOS_POR_PROFUNDIDAD } from '../lib/profundidad'

const MINUTOS_POR_UNIDAD = 1.5
const SECCIONES_VACIO = []

function hijosDe(seccionId, secciones) {
  return secciones.filter((seccion) => seccion.padre_id === seccionId)
}

// Una seccion seleccionada arrastra todo su subarbol (ver diseño del
// endpoint /mapa) - asi el usuario puede marcar un capitulo entero sin
// tener que tildar cada sub-seccion a mano.
function unidadesEnSubarbol(seccionId, secciones, nivelesIncluidos) {
  const seccion = secciones.find((s) => s.id === seccionId)
  if (!seccion) return []
  const propias = seccion.unidades.filter((u) => nivelesIncluidos.includes(u.nivel_importancia))
  const deHijos = hijosDe(seccionId, secciones).flatMap((hijo) =>
    unidadesEnSubarbol(hijo.id, secciones, nivelesIncluidos)
  )
  return [...propias, ...deHijos]
}

function unidadIdsSeleccionados(seccionesSeleccionadas, secciones, profundidad) {
  const nivelesIncluidos = NIVELES_INCLUIDOS_POR_PROFUNDIDAD[profundidad]
  const ids = new Set()
  for (const seccionId of seccionesSeleccionadas) {
    unidadesEnSubarbol(seccionId, secciones, nivelesIncluidos).forEach((u) => ids.add(u.id))
  }
  return [...ids]
}

export function SeleccionSecciones() {
  const { id: documentoId } = useParams()
  const navigate = useNavigate()
  const mapa = useMapaDocumento(documentoId)
  const [seleccionadas, setSeleccionadas] = useState(new Set())
  const [profundidad, setProfundidad] = useState('esencial')
  const [errorTextoExpirado, setErrorTextoExpirado] = useState(false)

  const secciones = mapa.data?.secciones ?? SECCIONES_VACIO

  const unidadIds = useMemo(
    () => unidadIdsSeleccionados(seleccionadas, secciones, profundidad),
    [seleccionadas, secciones, profundidad]
  )
  const minutosEstimados = Math.round(unidadIds.length * MINUTOS_POR_UNIDAD)

  const generar = useMutation({
    mutationFn: () => api(`/api/documentos/${documentoId}/generar`, {
      method: 'POST',
      body: JSON.stringify({ unidad_ids: unidadIds })
    }),
    onSuccess: () => navigate(`/documentos/${documentoId}/jugar`),
    onError: (error) => {
      if (error instanceof TextoFuenteExpiradoError) {
        setErrorTextoExpirado(true)
      }
    }
  })

  function alternarSeccion(seccionId) {
    setSeleccionadas((actual) => {
      const nuevo = new Set(actual)
      if (nuevo.has(seccionId)) {
        nuevo.delete(seccionId)
      } else {
        nuevo.add(seccionId)
      }
      return nuevo
    })
  }

  if (errorTextoExpirado) {
    return (
      <main className="seleccion-secciones">
        <p role="alert">Este documento expiró — súbelo de nuevo para seguir.</p>
        <button type="button" onClick={() => navigate('/subir')}>
          Subir de nuevo
        </button>
      </main>
    )
  }

  if (mapa.isLoading) return <p>Cargando mapa del documento…</p>
  if (mapa.isError) return <p role="alert">No se pudo cargar el mapa del documento.</p>

  return (
    <main className="seleccion-secciones">
      <p className="etiqueta">Paso 2 de 2</p>
      <h1>Elige qué jugar</h1>
      <ArbolSecciones secciones={secciones} seleccionadas={seleccionadas} onToggle={alternarSeccion} />
      <SelectorProfundidad valor={profundidad} onChange={setProfundidad} />

      <div className="tarjeta seleccion-secciones__resumen">
        <p>
          {unidadIds.length} unidades seleccionadas — ~{minutosEstimados} min
        </p>
        <button type="button" disabled={unidadIds.length === 0 || generar.isPending} onClick={() => generar.mutate()}>
          Empezar
        </button>
      </div>

      {generar.isError && !errorTextoExpirado && <p role="alert">No se pudo iniciar la generación.</p>}
    </main>
  )
}
