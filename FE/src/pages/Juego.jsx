import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useSesion } from '../hooks/useSesion'
import { useProgresoDocumento } from '../hooks/useProgresoDocumento'
import { useMapaDocumento } from '../hooks/useMapaDocumento'
import { ExplicacionUnidad } from '../components/ExplicacionUnidad'
import { PreguntaOpcionMultiple } from '../components/PreguntaOpcionMultiple'
import { CaminoDeTinta } from '../components/CaminoDeTinta'
import { IndiceIluminado } from '../components/IndiceIluminado'
import { TiraSellos } from '../components/TiraSellos'

const SECCIONES_VACIO = []

// GET /sesion no trae la seccion de la unidad activa - se busca en el
// arbol ya cargado por /mapa en vez de agregar el campo al backend, para
// no duplicar la misma info en dos endpoints.
function buscarSeccionDeUnidad(unidadId, secciones) {
  return secciones.find((seccion) => seccion.unidades.some((u) => u.id === unidadId))
}

export function Juego() {
  const { id: documentoId } = useParams()
  const { sesion, responder, avanzar } = useSesion(documentoId)
  const progreso = useProgresoDocumento(documentoId)
  const mapa = useMapaDocumento(documentoId)
  const [intentoNumero, setIntentoNumero] = useState(1)
  const [explicacionAlternativa, setExplicacionAlternativa] = useState(null)
  const [retroalimentacion, setRetroalimentacion] = useState(null)
  const [mostrarIndice, setMostrarIndice] = useState(false)

  // GET /sesion trae varios elementos por adelantado, pero solo el primero
  // esta activo (posicion_actual apunta ahi) - el resto es solo para que
  // el hook no tenga que refetchear en cada respuesta si el backend
  // decidiera precargar mas adelante.
  const elemento = sesion.data?.elementos?.[0]

  // Se resetea el estado de reintento solo cuando cambia el elemento
  // activo de verdad (nueva unidad o pasa de "nueva" a "refuerzo") - no en
  // cada respuesta, porque un reintento deliberadamente mantiene el mismo
  // elemento en pantalla.
  useEffect(() => {
    setIntentoNumero(1)
    setExplicacionAlternativa(null)
    setRetroalimentacion(null)
  }, [elemento?.unidad_id, elemento?.tipo_elemento])

  function manejarRespuesta(respuestaIndex) {
    responder.mutate(
      {
        unidad_id: elemento.unidad_id,
        tipo_elemento: elemento.tipo_elemento,
        respuesta_index: respuestaIndex,
        intento_numero: intentoNumero
      },
      {
        onSuccess: (resultado) => {
          setRetroalimentacion({ indice: respuestaIndex, correcto: resultado.correcto })
          if (resultado.reintentar) {
            setExplicacionAlternativa(resultado.explicacion_alternativa)
            setIntentoNumero(2)
          } else {
            // correcto, o avanzar sin bloquear tras el segundo intento -
            // en ambos casos el progreso ya avanzo en el backend.
            avanzar()
          }
        }
      }
    )
  }

  if (sesion.isLoading) return <p>Cargando sesión…</p>
  if (sesion.isError) return <p role="alert">No se pudo cargar la sesión.</p>

  const secciones = mapa.data?.secciones ?? SECCIONES_VACIO
  const seccionesProgreso = progreso.data?.secciones ?? SECCIONES_VACIO
  // Nodos del Camino de Tinta = secciones reales con contenido jugable,
  // no un numero decorativo fijo - si no coincide con la estructura real
  // del documento, la proporcion visual del progreso miente.
  const totalNodos = seccionesProgreso.length || 1

  if (!elemento) {
    return (
      <main className="juego juego--completo">
        <CaminoDeTinta progreso={1} modo="completa" totalNodos={totalNodos} />
        <p>¡Completaste todo lo disponible por ahora!</p>
        <TiraSellos cantidad={progreso.data?.unidades_dominadas ?? 0} />
        {secciones.length > 0 && <IndiceIluminado secciones={secciones} progresoSecciones={seccionesProgreso} />}
        <Link className="volver-a-seleccion" to={`/documentos/${documentoId}/seleccion`}>
          Elegir más secciones
        </Link>
      </main>
    )
  }

  const esNueva = elemento.tipo_elemento === 'nueva'
  const seccionActual = buscarSeccionDeUnidad(elemento.unidad_id, secciones)
  const progresoSeccionActual = seccionActual
    ? seccionesProgreso.find((s) => s.id === seccionActual.id)
    : null

  return (
    <main className="juego">
      <CaminoDeTinta
        progreso={progreso.data?.fraccion_avance ?? 0}
        modo="barra"
        enRefuerzo={!esNueva}
        totalNodos={totalNodos}
      />
      {/* Progreso del documento completo (la linea de arriba) vs progreso
          de la seccion que se esta jugando ahora - son fracciones
          distintas a proposito (ver discusion del "camino ya recorrido"
          al empezar una seccion nueva con profundizar). */}
      {progresoSeccionActual && (
        <p className="etiqueta juego__seccion-actual">
          {seccionActual.titulo}: {progresoSeccionActual.unidades_pasadas}/{progresoSeccionActual.unidades_totales}
        </p>
      )}
      <Link className="volver-a-seleccion" to={`/documentos/${documentoId}/seleccion`}>
        Elegir más secciones
      </Link>

      {esNueva && (
        <ExplicacionUnidad titulo={elemento.titulo} explicacion={explicacionAlternativa ?? elemento.explicacion} />
      )}
      <PreguntaOpcionMultiple
        pregunta={elemento.pregunta}
        onResponder={manejarRespuesta}
        deshabilitado={responder.isPending}
        retroalimentacion={retroalimentacion}
      />

      <TiraSellos cantidad={progreso.data?.unidades_dominadas ?? 0} />

      {secciones.length > 0 && (
        <>
          <button type="button" className="secundario" onClick={() => setMostrarIndice((actual) => !actual)}>
            {mostrarIndice ? 'Ocultar índice' : 'Ver índice iluminado'}
          </button>
          {mostrarIndice && <IndiceIluminado secciones={secciones} progresoSecciones={seccionesProgreso} />}
        </>
      )}
    </main>
  )
}
