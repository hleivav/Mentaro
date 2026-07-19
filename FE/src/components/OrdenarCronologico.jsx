import { useState } from 'react'

// Tocar para seleccionar, sin arrastrar (ver decision de interaccion):
// se toca cada elemento disponible en el orden en que se cree que va, y
// se arma "tu secuencia" abajo. Tocar un elemento ya puesto en la
// secuencia lo devuelve al montón, corriendo el resto un lugar. Los
// indices que se mandan son los de "items" tal cual llego (ya
// desordenado por el backend, ver PasadaBService) - "orden_correcto" es
// una lista de esos mismos indices en la secuencia real.
export function OrdenarCronologico({ pregunta, onResponder, deshabilitado, retroalimentacion }) {
  const [seleccionados, setSeleccionados] = useState([])

  const completo = seleccionados.length === pregunta.items.length
  const bloqueado = deshabilitado || Boolean(retroalimentacion)

  function agregar(indice) {
    if (bloqueado || seleccionados.includes(indice)) return
    setSeleccionados((actual) => [...actual, indice])
  }

  function quitar(posicion) {
    if (bloqueado) return
    setSeleccionados((actual) => actual.filter((_, i) => i !== posicion))
  }

  function comprobar() {
    if (!completo || bloqueado) return
    onResponder(seleccionados)
  }

  const clase = retroalimentacion
    ? retroalimentacion.correcto ? 'ordenar-cronologico__secuencia--correcta' : 'ordenar-cronologico__secuencia--incorrecta'
    : ''

  return (
    <div className="ordenar-cronologico">
      <p>{pregunta.enunciado}</p>

      <ol className={`ordenar-cronologico__secuencia ${clase}`}>
        {seleccionados.length === 0 && <li className="ordenar-cronologico__vacio">Tocá los elementos en orden</li>}
        {seleccionados.map((indice, posicion) => (
          <li key={indice}>
            <button type="button" disabled={bloqueado} onClick={() => quitar(posicion)}>
              {pregunta.items[indice]}
            </button>
          </li>
        ))}
      </ol>

      {retroalimentacion && (
        <p className={retroalimentacion.correcto ? 'ordenar-cronologico__retro--correcta' : 'ordenar-cronologico__retro--incorrecta'}>
          {retroalimentacion.correcto ? '¡Correcto!' : 'No es así'}
        </p>
      )}

      <ul className="ordenar-cronologico__disponibles">
        {pregunta.items.map((item, indice) =>
          seleccionados.includes(indice) ? null : (
            <li key={indice}>
              <button type="button" disabled={bloqueado} onClick={() => agregar(indice)}>
                {item}
              </button>
            </li>
          )
        )}
      </ul>

      {!retroalimentacion && (
        <button type="button" className="secundario" disabled={!completo || bloqueado} onClick={comprobar}>
          Comprobar orden
        </button>
      )}
    </div>
  )
}
