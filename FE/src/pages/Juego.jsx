import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useSesion } from '../hooks/useSesion'
import { ExplicacionUnidad } from '../components/ExplicacionUnidad'
import { PreguntaOpcionMultiple } from '../components/PreguntaOpcionMultiple'

export function Juego() {
  const { id: documentoId } = useParams()
  const { sesion, responder, avanzar } = useSesion(documentoId)
  const [intentoNumero, setIntentoNumero] = useState(1)
  const [explicacionAlternativa, setExplicacionAlternativa] = useState(null)

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

  if (!elemento) {
    return (
      <main className="juego">
        <p>¡Completaste todo lo disponible por ahora!</p>
        <Link className="volver-a-seleccion" to={`/documentos/${documentoId}/seleccion`}>
          Elegir más secciones
        </Link>
      </main>
    )
  }

  const esNueva = elemento.tipo_elemento === 'nueva'

  return (
    <main className="juego">
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
      />
    </main>
  )
}
