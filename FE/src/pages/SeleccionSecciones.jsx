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

function subarbolIds(seccionId, secciones) {
  return [seccionId, ...hijosDe(seccionId, secciones).flatMap((hijo) => subarbolIds(hijo.id, secciones))]
}

// Una seccion seleccionada arrastra todo su subarbol (ver diseño del
// endpoint /mapa) - asi el usuario puede marcar un capitulo entero sin
// tener que tildar cada sub-seccion a mano. Ademas, cada seccion en ese
// subarbol puede depender de otras (campo depende_de, derivado por el
// backend de las unidades individuales) - esas se auto-incluyen tambien,
// para que el usuario nunca entre a un concepto que asume algo que no
// selecciono. cerrarDependencias hace ese cierre transitivo (A depende de
// B, B depende de C -> las tres quedan incluidas) y recuerda, para cada
// seccion auto-incluida, el titulo de la seccion que el usuario marco
// directamente y que la trajo (para mostrar "incluida por X" en la UI).
function cerrarDependencias(seleccionadas, secciones) {
  const porId = new Map(secciones.map((s) => [s.id, s]))
  const origenDirectoPorSeccion = new Map()
  for (const seccionId of seleccionadas) {
    const directa = porId.get(seccionId)
    for (const id of subarbolIds(seccionId, secciones)) {
      if (!origenDirectoPorSeccion.has(id)) origenDirectoPorSeccion.set(id, directa?.titulo)
    }
  }

  const base = new Set(origenDirectoPorSeccion.keys())
  const porDependencia = new Map()
  const pendientes = [...base]
  while (pendientes.length > 0) {
    const actual = pendientes.pop()
    const seccionActual = porId.get(actual)
    for (const dependeId of seccionActual?.depende_de ?? []) {
      if (base.has(dependeId) || porDependencia.has(dependeId)) continue
      porDependencia.set(dependeId, origenDirectoPorSeccion.get(actual) ?? porDependencia.get(actual))
      pendientes.push(dependeId)
    }
  }

  return { base, porDependencia }
}

function unidadIdsDeSecciones(seccionIds, secciones, nivelesIncluidos) {
  const ids = new Set()
  for (const seccionId of seccionIds) {
    const seccion = secciones.find((s) => s.id === seccionId)
    if (!seccion) continue
    seccion.unidades
      .filter((u) => nivelesIncluidos.includes(u.nivel_importancia))
      .forEach((u) => ids.add(u.id))
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

  const { base: seccionesBase, porDependencia: seccionesPorDependencia } = useMemo(
    () => cerrarDependencias(seleccionadas, secciones),
    [seleccionadas, secciones]
  )

  const unidadIds = useMemo(() => {
    const nivelesIncluidos = NIVELES_INCLUIDOS_POR_PROFUNDIDAD[profundidad]
    const todasIncluidas = [...seccionesBase, ...seccionesPorDependencia.keys()]
    return unidadIdsDeSecciones(todasIncluidas, secciones, nivelesIncluidos)
  }, [seccionesBase, seccionesPorDependencia, secciones, profundidad])
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
      <ArbolSecciones
        secciones={secciones}
        seleccionadas={seleccionadas}
        onToggle={alternarSeccion}
        incluidasPorDependencia={seccionesPorDependencia}
      />
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
