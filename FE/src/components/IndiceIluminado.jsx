import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

function hijosDe(seccionId, secciones) {
  return secciones.filter((seccion) => (seccion.padre_id ?? null) === seccionId)
}

// Tres estados, no dos: sin esto, una seccion recien seleccionada (0
// dominadas todavia) se veia identica a una que el usuario nunca eligio -
// "0 de 2 iluminadas" no daba ninguna pista de cuales eran esas 2 entre
// todo el documento (ver correccion del sistema de diseño).
function estadoDeIluminacion(seccionId, progresoPorSeccion) {
  const datos = progresoPorSeccion.get(seccionId)
  if (!datos || datos.unidades_totales === 0) return 'sin-seleccionar'
  if (datos.unidades_dominadas >= datos.unidades_totales) return 'iluminada'
  return 'en-seleccion'
}

function Nodo({ seccion, secciones, progresoPorSeccion }) {
  const hijos = hijosDe(seccion.id, secciones)
  const estado = estadoDeIluminacion(seccion.id, progresoPorSeccion)

  return (
    <li className={`indice-iluminado__nodo indice-iluminado__nodo--${estado}`}>
      <span>{seccion.titulo}</span>
      {hijos.length > 0 && (
        <ul>
          {hijos.map((hijo) => (
            <Nodo key={hijo.id} seccion={hijo} secciones={secciones} progresoPorSeccion={progresoPorSeccion} />
          ))}
        </ul>
      )}
    </li>
  )
}

// Segundo elemento de juego (ademas del Camino de Tinta): ArbolSecciones
// reutilizado como objeto coleccionable, en version solo-lectura - cada
// seccion empieza atenuada y se "ilumina" cuando el usuario realmente la
// domina (no solo la vio), como un manuscrito iluminado que se completa
// con el estudio.
//
// Tambien es, a proposito, el UNICO lugar de la pantalla de juego que
// muestra el avance del documento completo (ver correccion del sistema
// de diseño: mezclarlo con el Camino de Tinta - que ahora es solo la
// seccion actual - resulto ilegible probando de verdad).
//
// Vive aca tambien "Reiniciar progreso": borra solo progreso_usuario y
// resultado_unidad en el backend (nunca el contenido generado), pensado
// tanto para reprobar cambios de UI sin regenerar con DeepSeek como para
// que un usuario real pueda rejugar un documento desde cero.
export function IndiceIluminado({ documentoId, secciones, progresoSecciones }) {
  const queryClient = useQueryClient()
  const raices = hijosDe(null, secciones)
  const progresoPorSeccion = new Map(progresoSecciones.map((s) => [s.id, s]))

  const conDatos = progresoSecciones.filter((s) => s.unidades_totales > 0)
  const iluminadas = conDatos.filter((s) => s.unidades_dominadas >= s.unidades_totales).length

  const reiniciar = useMutation({
    mutationFn: () => api(`/api/documentos/${documentoId}/progreso`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sesion', documentoId] })
      queryClient.invalidateQueries({ queryKey: ['progreso', documentoId] })
    }
  })

  function manejarReiniciar() {
    if (
      window.confirm(
        '¿Reiniciar tu progreso en este documento? Perdés las unidades dominadas y volvés al principio. El contenido ya generado no se toca — no hace falta subir el documento de nuevo.'
      )
    ) {
      reiniciar.mutate()
    }
  }

  return (
    <div className="indice-iluminado-envoltura">
      {conDatos.length > 0 && (
        <p className="etiqueta indice-iluminado__resumen">
          {iluminadas} de {conDatos.length} secciones iluminadas
        </p>
      )}
      <ul className="indice-iluminado">
        {raices.map((raiz) => (
          <Nodo key={raiz.id} seccion={raiz} secciones={secciones} progresoPorSeccion={progresoPorSeccion} />
        ))}
      </ul>
      <button
        type="button"
        className="secundario indice-iluminado__reiniciar"
        disabled={reiniciar.isPending}
        onClick={manejarReiniciar}
      >
        Reiniciar progreso de este documento
      </button>
      {reiniciar.isError && <p role="alert">No se pudo reiniciar el progreso.</p>}
    </div>
  )
}
