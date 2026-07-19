import { useState } from 'react'
import { ImagenPregunta } from './ImagenPregunta'

// Tocar izquierda, despues tocar derecha (ver decision de interaccion):
// se toca un elemento de la izquierda para "activarlo", despues uno de
// la derecha para formar el par. Tocar un par ya formado lo deshace y
// libera ambos elementos. Los pares se mandan como [indice_izquierda,
// indice_derecha] - el orden de los pares entre si no importa (ver
// SesionService.aConjuntoDePares), solo el conjunto.
export function Emparejar({ pregunta, onResponder, deshabilitado, retroalimentacion, documentoId }) {
  const [pares, setPares] = useState([])
  const [izquierdaActiva, setIzquierdaActiva] = useState(null)

  const bloqueado = deshabilitado || Boolean(retroalimentacion)
  const completo = pares.length === pregunta.columna_izquierda.length

  function indiceDerechaDe(indiceIzquierda) {
    return pares.find(([izq]) => izq === indiceIzquierda)?.[1]
  }

  function indiceIzquierdaDe(indiceDerecha) {
    return pares.find(([, der]) => der === indiceDerecha)?.[0]
  }

  function tocarIzquierda(indice) {
    if (bloqueado || indiceDerechaDe(indice) !== undefined) return
    setIzquierdaActiva((actual) => (actual === indice ? null : indice))
  }

  function tocarDerecha(indice) {
    if (bloqueado) return
    const parExistente = indiceIzquierdaDe(indice)
    if (parExistente !== undefined) {
      setPares((actual) => actual.filter(([izq]) => izq !== parExistente))
      return
    }
    if (izquierdaActiva === null) return
    setPares((actual) => [...actual, [izquierdaActiva, indice]])
    setIzquierdaActiva(null)
  }

  function deshacerPar(indiceIzquierda) {
    if (bloqueado) return
    setPares((actual) => actual.filter(([izq]) => izq !== indiceIzquierda))
  }

  function comprobar() {
    if (!completo || bloqueado) return
    onResponder(pares)
  }

  const claseRetro = retroalimentacion
    ? retroalimentacion.correcto ? 'emparejar--correcta' : 'emparejar--incorrecta'
    : ''

  return (
    <div className={`emparejar ${claseRetro}`}>
      <p>{pregunta.enunciado}</p>
      <ImagenPregunta documentoId={documentoId} imagenId={pregunta.imagen_id} />

      <div className="emparejar__columnas">
        <ul className="emparejar__columna">
          {pregunta.columna_izquierda.map((elemento, indice) => {
            const emparejado = indiceDerechaDe(indice) !== undefined
            return (
              <li key={indice}>
                <button
                  type="button"
                  className={[
                    izquierdaActiva === indice ? 'emparejar__item--activo' : '',
                    emparejado ? 'emparejar__item--emparejado' : ''
                  ].join(' ')}
                  disabled={bloqueado || emparejado}
                  onClick={() => (emparejado ? deshacerPar(indice) : tocarIzquierda(indice))}
                >
                  {elemento}
                </button>
              </li>
            )
          })}
        </ul>

        <ul className="emparejar__columna">
          {pregunta.columna_derecha_desordenada.map((elemento, indice) => {
            const emparejado = indiceIzquierdaDe(indice) !== undefined
            return (
              <li key={indice}>
                <button
                  type="button"
                  className={emparejado ? 'emparejar__item--emparejado' : ''}
                  disabled={bloqueado || (emparejado ? false : izquierdaActiva === null)}
                  onClick={() => tocarDerecha(indice)}
                >
                  {elemento}
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      {retroalimentacion && (
        <p className={retroalimentacion.correcto ? 'emparejar__retro--correcta' : 'emparejar__retro--incorrecta'}>
          {retroalimentacion.correcto ? '¡Correcto!' : 'No es así'}
        </p>
      )}

      {!retroalimentacion && (
        <button type="button" className="secundario" disabled={!completo || bloqueado} onClick={comprobar}>
          Comprobar parejas
        </button>
      )}
    </div>
  )
}
