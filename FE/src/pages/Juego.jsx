import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useSesion } from '../hooks/useSesion'
import { useProgresoDocumento } from '../hooks/useProgresoDocumento'
import { useMapaDocumento } from '../hooks/useMapaDocumento'
import { usePreferenciaSonido } from '../hooks/usePreferenciaSonido'
import { ExplicacionUnidad } from '../components/ExplicacionUnidad'
import { PreguntaOpcionMultiple } from '../components/PreguntaOpcionMultiple'
import { CaminoDeTinta } from '../components/CaminoDeTinta'
import { IndiceIluminado } from '../components/IndiceIluminado'
import { TiraSellos } from '../components/TiraSellos'
import { reproducirAcierto, reproducirError, reproducirPling, reproducirPlong } from '../lib/sonido'

const SECCIONES_VACIO = []

// GET /sesion no trae la seccion de la unidad activa - se busca en el
// arbol ya cargado por /mapa en vez de agregar el campo al backend, para
// no duplicar la misma info en dos endpoints.
function buscarSeccionDeUnidad(unidadId, secciones) {
  return secciones.find((seccion) => seccion.unidades.some((u) => u.id === unidadId))
}

// "Capitulo · Seccion" - sin inventar numeracion decorativa: los titulos
// que genera la Pasada A ya traen su propio numeral ("II. IA en la
// sociedad"), asi que concatenar padre + seccion alcanza como orientacion.
function tituloBreadcrumb(seccion, secciones) {
  const padre = seccion.padre_id ? secciones.find((s) => s.id === seccion.padre_id) : null
  return padre ? `${padre.titulo} · ${seccion.titulo}` : seccion.titulo
}

export function Juego() {
  const { id: documentoId } = useParams()
  const { sesion, responder, avanzar } = useSesion(documentoId)
  const progreso = useProgresoDocumento(documentoId)
  const mapa = useMapaDocumento(documentoId)
  const [intentoNumero, setIntentoNumero] = useState(1)
  const [explicacionAlternativa, setExplicacionAlternativa] = useState(null)
  const [retroalimentacion, setRetroalimentacion] = useState(null)
  const [mensajeSegundoFallo, setMensajeSegundoFallo] = useState(false)
  const [mostrarIndice, setMostrarIndice] = useState(false)
  const [sonidoActivado] = usePreferenciaSonido()

  // GET /sesion trae varios elementos por adelantado, pero solo el primero
  // esta activo (posicion_actual apunta ahi) - el resto es solo para que
  // el hook no tenga que refetchear en cada respuesta si el backend
  // decidiera precargar mas adelante.
  const elemento = sesion.data?.elementos?.[0]
  const unidadIdActiva = elemento?.unidad_id
  const tipoElementoActivo = elemento?.tipo_elemento

  // Se resetea el estado de reintento solo cuando cambia el elemento
  // activo de verdad (nueva unidad o pasa de "nueva" a "refuerzo") - no en
  // cada respuesta, porque un reintento deliberadamente mantiene el mismo
  // elemento en pantalla. El mismo momento dispara el aviso sonoro
  // opcional (pling = concepto nuevo, plong = repaso) - nunca por defecto,
  // ver sistema de diseño. Se usan los campos primitivos (no "elemento")
  // como dependencia a proposito: /sesion devuelve un objeto nuevo en
  // cada refetch aunque el contenido no cambie, y comparar por referencia
  // reiniciaria el intento/sonido sin que el usuario avanzara de verdad.
  useEffect(() => {
    setIntentoNumero(1)
    setExplicacionAlternativa(null)
    setRetroalimentacion(null)
    setMensajeSegundoFallo(false)
    if (sonidoActivado && unidadIdActiva) {
      if (tipoElementoActivo === 'nueva') reproducirPling()
      else reproducirPlong()
    }
  }, [unidadIdActiva, tipoElementoActivo, sonidoActivado])

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
          if (sonidoActivado) {
            if (resultado.correcto) reproducirAcierto()
            else reproducirError()
          }
          if (resultado.reintentar) {
            // Primer fallo: sin cambios - explicacion alternativa + otra
            // oportunidad ahora mismo.
            setExplicacionAlternativa(resultado.explicacion_alternativa)
            setIntentoNumero(2)
          } else if (resultado.avanzar) {
            // Segundo fallo (o un repaso fallado): el backend ya nunca
            // bloquea el progreso, pero avanzar en silencio se siente
            // como que el sistema "se rindio" con la pregunta. Pausar acá
            // con un mensaje explicito antes de seguir - el usuario
            // confirma con "Continuar" en vez de un salto automatico.
            setMensajeSegundoFallo(true)
          } else {
            avanzar()
          }
        }
      }
    )
  }

  function manejarContinuarTrasFallo() {
    setMensajeSegundoFallo(false)
    avanzar()
  }

  if (sesion.isLoading) return <p>Cargando sesión…</p>
  if (sesion.isError) return <p role="alert">No se pudo cargar la sesión.</p>

  const secciones = mapa.data?.secciones ?? SECCIONES_VACIO
  const seccionesProgreso = progreso.data?.secciones ?? SECCIONES_VACIO
  // Solo para la pantalla de cierre (documento completado) - ahi si tiene
  // sentido representar el documento entero, es un momento de "todo
  // terminado". Durante el juego el Camino de Tinta representa unicamente
  // la seccion actual (ver mas abajo) - mezclar las dos escalas de
  // progreso en un solo lugar resulto ilegible probando de verdad.
  const totalNodosDocumento = seccionesProgreso.length || 1

  if (!elemento) {
    return (
      <main className="juego juego--completo">
        <CaminoDeTinta progreso={1} modo="completa" totalNodos={totalNodosDocumento} />
        <p>¡Completaste todo lo disponible por ahora!</p>
        <TiraSellos cantidad={progreso.data?.unidades_dominadas ?? 0} total={progreso.data?.unidades_totales ?? 0} />
        {secciones.length > 0 && (
          <IndiceIluminado documentoId={documentoId} secciones={secciones} progresoSecciones={seccionesProgreso} />
        )}
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
  const unidadesTotalesSeccion = progresoSeccionActual?.unidades_totales || 1
  const unidadesPasadasSeccion = progresoSeccionActual?.unidades_pasadas ?? 0
  // El repaso espaciado NUNCA incrementa este contador (ver correccion del
  // sistema de diseño: antes ambos tipos de pregunta sumaban a la misma
  // cuenta, lo que producia saltos como "2 de 4" a "4 de 4" sin pasar por
  // "3 de 4"). Una unidad "nueva" todavia no esta contada en pasadas (su
  // posicion NUEVA == posicion actual, no < posicion actual) - por eso
  // pasadas + 1. Un refuerzo es la reaparicion de una unidad YA contada en
  // pasadas - mostrar ese mismo numero, sin sumar.
  const numeroPreguntaActual = esNueva
    ? Math.min(unidadesPasadasSeccion + 1, unidadesTotalesSeccion)
    : unidadesPasadasSeccion

  return (
    <main className="juego">
      {/* Aviso visual de "concepto nuevo" vs "repaso" antes de que la
          pantalla se asiente - key fuerza un remontaje (y por lo tanto
          reinicia la animacion CSS) en cada elemento nuevo, sin necesidad
          de un timer en JS. prefers-reduced-motion ya lo vuelve instantaneo
          via el override global en index.css. */}
      <div
        key={`${elemento.unidad_id}-${elemento.tipo_elemento}`}
        className={`destello destello--${esNueva ? 'nuevo' : 'repaso'}`}
        aria-hidden="true"
      />
      <div className="camino-de-tinta-envoltura">
        <div className="camino-de-tinta-envoltura__cabecera">
          {seccionActual && <p className="etiqueta">{tituloBreadcrumb(seccionActual, secciones)}</p>}
          {!esNueva && <span className="camino-de-tinta-envoltura__repaso">↺ Repaso</span>}
        </div>
        <CaminoDeTinta
          progreso={unidadesPasadasSeccion / unidadesTotalesSeccion}
          modo="barra"
          enRefuerzo={!esNueva}
          totalNodos={unidadesTotalesSeccion}
        />
        {progresoSeccionActual && (
          <p className="camino-de-tinta-envoltura__respaldo">
            Pregunta {numeroPreguntaActual} de {unidadesTotalesSeccion}
          </p>
        )}
      </div>
      <Link className="volver-a-seleccion" to={`/documentos/${documentoId}/seleccion`}>
        Elegir más secciones
      </Link>

      {esNueva && (
        <ExplicacionUnidad titulo={elemento.titulo} explicacion={explicacionAlternativa ?? elemento.explicacion} />
      )}
      <PreguntaOpcionMultiple
        pregunta={elemento.pregunta}
        onResponder={manejarRespuesta}
        deshabilitado={responder.isPending || mensajeSegundoFallo}
        retroalimentacion={retroalimentacion}
      />

      {mensajeSegundoFallo && (
        <div className="tarjeta juego__mensaje-segundo-fallo">
          <p>
            No te preocupes — este concepto va a volver a aparecer más adelante, con otro ejemplo, para que lo
            confirmes con calma.
          </p>
          <button type="button" onClick={manejarContinuarTrasFallo}>
            Continuar
          </button>
        </div>
      )}

      <TiraSellos
        cantidad={progresoSeccionActual?.unidades_dominadas ?? 0}
        total={unidadesTotalesSeccion}
      />

      {secciones.length > 0 && (
        <>
          <button type="button" className="secundario" onClick={() => setMostrarIndice((actual) => !actual)}>
            {mostrarIndice ? 'Ocultar índice' : 'Ver índice iluminado'}
          </button>
          {mostrarIndice && (
            <IndiceIluminado documentoId={documentoId} secciones={secciones} progresoSecciones={seccionesProgreso} />
          )}
        </>
      )}
    </main>
  )
}
