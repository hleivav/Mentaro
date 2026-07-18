import { useState } from 'react'

const CLAVE = 'mentaro:sonido-activado'

// Preferencia global, apagada por defecto (ver sistema de diseño: un
// efecto de audio inesperado no es deseable en una app de estudio que
// puede usarse en transporte publico o espacios silenciosos) - persiste
// en localStorage entre sesiones.
export function usePreferenciaSonido() {
  const [activado, setActivado] = useState(() => {
    try {
      return localStorage.getItem(CLAVE) === '1'
    } catch {
      return false
    }
  })

  function alternar() {
    setActivado((actual) => {
      const nuevo = !actual
      try {
        localStorage.setItem(CLAVE, nuevo ? '1' : '0')
      } catch {
        // localStorage no disponible - la preferencia solo dura esta sesion.
      }
      return nuevo
    })
  }

  return [activado, alternar]
}
