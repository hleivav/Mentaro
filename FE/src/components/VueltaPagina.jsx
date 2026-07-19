import { useEffect, useState } from 'react'
import { usePrefersReducedMotion } from '../hooks/usePrefersReducedMotion'

const DURACION_MS = 900

// Efecto de "vuelta de pagina" al cambiar de pregunta - transformacion
// 3D de CSS (perspective + rotateY sobre un par de caras), no una
// simulacion de curvatura de pagina realista (eso si seria un esfuerzo
// de libreria dedicada, no vale la pena aca). claveContenido identifica
// que es "una pagina distinta" (ej. unidad_id+tipo_elemento) - si
// cambia, la cara actual gira revelando la nueva detras. Si los
// children cambian pero la clave es la misma (ej. reintentar tras un
// primer fallo), no hay transicion - se sigue mostrando "children" en
// vivo, sin desfase de un render.
export function VueltaPagina({ claveContenido, children }) {
  const reducido = usePrefersReducedMotion()
  const [previo, setPrevio] = useState({ clave: claveContenido, children })
  const [transicion, setTransicion] = useState(null)

  // Ajustar estado durante el render en respuesta a un cambio de props
  // (patron oficial de React para "recordar" el valor anterior sin
  // perder un frame en un efecto).
  if (previo.clave !== claveContenido) {
    if (!reducido) {
      setTransicion({ clave: claveContenido, contenidoSaliente: previo.children })
    }
    setPrevio({ clave: claveContenido, children })
  }

  useEffect(() => {
    if (!transicion) return undefined
    const temporizador = setTimeout(() => setTransicion(null), DURACION_MS)
    return () => clearTimeout(temporizador)
  }, [transicion])

  if (!transicion || transicion.clave !== claveContenido) {
    return children
  }

  return (
    <div className="vuelta-pagina">
      <div key={transicion.clave} className="vuelta-pagina__interior">
        <div className="vuelta-pagina__cara vuelta-pagina__cara--saliente">{transicion.contenidoSaliente}</div>
        <div className="vuelta-pagina__cara vuelta-pagina__cara--entrante">{children}</div>
      </div>
    </div>
  )
}
