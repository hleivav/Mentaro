import { useEffect, useRef, useState } from 'react'

// Linea serpenteante LIBRE - a proposito distinta de la silueta de
// cabeza/cerebro del icono de la app (esa queda exclusiva del icono
// estatico; ver aclaracion post-implementacion del sistema de diseño).
// Misma forma para "barra" y "completa", solo cambia la escala via CSS.
const CAMINO_PATH =
  'M10,30 C40,5 70,5 100,30 C130,55 160,55 190,30 ' +
  'C220,5 250,5 280,30 C310,55 340,55 370,30 C385,20 393,14 398,8'

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
      {nodos.map((nodo, indice) => (
        <circle
          key={indice}
          cx={nodo.x}
          cy={nodo.y}
          r={4}
          className={nodo.fraccion <= progresoAcotado ? 'camino-de-tinta__nodo camino-de-tinta__nodo--alcanzado' : 'camino-de-tinta__nodo'}
        />
      ))}
      {/* El "lazo hacia atras" del refuerzo espaciado se representa como un
          pequeño trazo circular junto al nodo activo - una version
          simplificada de "la linea se dobla sobre si misma", ya que
          deformar el path principal con precision de longitud de arco no
          es viable sobre un trazado aproximado a mano. */}
      {enRefuerzo && nodoActivo && (
        <circle className="camino-de-tinta__lazo-refuerzo" cx={nodoActivo.x} cy={nodoActivo.y} r={9} />
      )}
    </svg>
  )
}
