import { useEffect, useId, useRef, useState } from 'react'

// Trazado irregular a proposito (nunca una onda perfectamente repetida)
// - se busca que se lea como un garabato hecho a mano para recordar
// donde ibas, no como un elemento vectorial de UI diseñado (ver
// identidad visual v2, "Camino de Tinta -> garabato de margen").
const CAMINO_PATH =
  'M8,32 C35,10 55,4 85,26 C110,44 125,50 155,28 ' +
  'C180,10 200,6 230,24 C258,42 270,48 300,26 C325,10 340,8 365,22 C380,30 390,16 400,10'

// modo: "barra" (delgada, persistente durante el juego) o "completa"
// (pagina completa, momento de cierre). progreso: 0..1. totalNodos: la
// cantidad de secciones reales del documento - los nodos representan
// secciones de verdad, no una subdivision decorativa arbitraria (si el
// numero de nodos no coincide con la estructura real, la proporcion
// visual miente).
export function CaminoDeTinta({ progreso, modo = 'barra', enRefuerzo = false, totalNodos = 1 }) {
  const pathRef = useRef(null)
  const [longitud, setLongitud] = useState(0)
  const [nodos, setNodos] = useState([])
  const cantidadNodos = Math.max(1, totalNodos)
  // Id unico por instancia - un <filter> vive en el DOM global del
  // documento, sin esto dos garabatos en pantalla a la vez colisionarian
  // de id.
  const idFiltro = `garabato-temblor-${useId()}`

  useEffect(() => {
    if (pathRef.current) {
      const total = pathRef.current.getTotalLength()
      setLongitud(total)
      setNodos(
        Array.from({ length: cantidadNodos }, (_, indice) => {
          const fraccion = cantidadNodos > 1 ? indice / (cantidadNodos - 1) : 0
          const punto = pathRef.current.getPointAtLength(total * fraccion)
          return { x: punto.x, y: punto.y, fraccion }
        })
      )
    }
  }, [cantidadNodos])

  const progresoAcotado = Math.min(1, Math.max(0, progreso))
  const dashoffset = longitud - longitud * progresoAcotado
  const indiceNodoActivo = Math.max(0, Math.round(progresoAcotado * (cantidadNodos - 1)) - (enRefuerzo ? 1 : 0))
  const nodoActivo = nodos[indiceNodoActivo]

  return (
    <svg
      className={`camino-de-tinta camino-de-tinta--${modo}`}
      viewBox="0 0 408 60"
      role="img"
      aria-label={`Progreso: ${Math.round(progresoAcotado * 100)}%`}
    >
      <defs>
        {/* Temblor sutil (no ruidoso) - la firma de un trazo hecho a mano
            en vez de una curva Bezier perfecta. prefers-reduced-motion no
            aplica aca (es un desplazamiento estatico, no una animacion). */}
        <filter id={idFiltro} x="-10%" y="-50%" width="120%" height="200%">
          <feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="2" seed="4" result="textura" />
          <feDisplacementMap in="SourceGraphic" in2="textura" scale="2.2" />
        </filter>
      </defs>
      <g filter={`url(#${idFiltro})`}>
        <path className="camino-de-tinta__fondo" d={CAMINO_PATH} />
        <path
          ref={pathRef}
          className="camino-de-tinta__trazo"
          d={CAMINO_PATH}
          style={{
            strokeDasharray: longitud,
            strokeDashoffset: dashoffset
          }}
        />
      </g>
      {nodos.map((nodo, indice) => (
        <circle
          key={indice}
          cx={nodo.x}
          cy={nodo.y}
          r={4}
          className={nodo.fraccion <= progresoAcotado ? 'camino-de-tinta__nodo camino-de-tinta__nodo--alcanzado' : 'camino-de-tinta__nodo'}
        />
      ))}
      {enRefuerzo && nodoActivo && (
        <circle className="camino-de-tinta__lazo-refuerzo" cx={nodoActivo.x} cy={nodoActivo.y} r={9} />
      )}
    </svg>
  )
}
