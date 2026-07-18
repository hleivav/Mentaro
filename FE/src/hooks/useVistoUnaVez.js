import { useEffect, useState } from 'react'

const PREFIJO_CLAVE = 'mentaro:visto:'

// Para explicaciones que solo tiene sentido mostrar una vez (ver "dominado"
// en TiraSellos) - el valor devuelto queda fijo durante todo el ciclo de
// vida del componente (no se oculta a mitad de la sesion en curso), pero
// el efecto persiste en localStorage que ya se vio, asi que el proximo
// montaje (recarga, otra seccion) ya no la muestra.
export function useVistoUnaVez(clave) {
  const claveCompleta = PREFIJO_CLAVE + clave
  const [yaVisto] = useState(() => {
    try {
      return localStorage.getItem(claveCompleta) === '1'
    } catch {
      return true
    }
  })

  useEffect(() => {
    if (yaVisto) return
    try {
      localStorage.setItem(claveCompleta, '1')
    } catch {
      // localStorage no disponible (modo privado, etc.) - no es critico,
      // la explicacion simplemente se repetiria en la proxima sesion.
    }
  }, [yaVisto, claveCompleta])

  return yaVisto
}
