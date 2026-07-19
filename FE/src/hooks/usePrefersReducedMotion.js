import { useEffect, useState } from 'react'

const CONSULTA = '(prefers-reduced-motion: reduce)'

// A diferencia del resto de las animaciones del sistema (que se apoyan
// en el override global de CSS para caer a 0.01ms), la vuelta de pagina
// necesita saberlo tambien en JS: mantiene dos caras superpuestas
// mientras dura la animacion, y con reduced-motion no hay que armar esa
// estructura en absoluto - el contenido nuevo se muestra directo, sin
// una version "reducida" del efecto.
export function usePrefersReducedMotion() {
  const [reducido, setReducido] = useState(() => window.matchMedia(CONSULTA).matches)

  useEffect(() => {
    const medio = window.matchMedia(CONSULTA)
    const manejar = (evento) => setReducido(evento.matches)
    medio.addEventListener('change', manejar)
    return () => medio.removeEventListener('change', manejar)
  }, [])

  return reducido
}
