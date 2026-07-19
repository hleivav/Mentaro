import { useId } from 'react'

const TRAZO_CORRECTA = ['M3,18 C6,21 9,24 13,29 C19,20 25,11 32,2']
const TRAZO_INCORRECTA = ['M4,3 C12,12 20,20 30,30', 'M31,4 C23,13 15,21 2,29']

// Marca dibujada a mano tipo pincelada, no un glifo de fuente ni una
// linea recta perfecta. Dos capas del mismo trazo - una mas ancha y
// translucida "de fondo", otra mas fina y opaca encima, cada una con su
// propio filtro de temblor (semilla distinta) - simulan el sangrado
// irregular de una pincelada real en vez de una linea vectorial
// prolija. Curvas Bezier en vez de segmentos rectos por la misma razon:
// una mano real casi nunca traza algo perfectamente recto.
export function MarcaCasilla({ tipo }) {
  const idFiltroFondo = `marca-temblor-fondo-${useId()}`
  const idFiltroTrazo = `marca-temblor-trazo-${useId()}`
  const trazos = tipo === 'correcta' ? TRAZO_CORRECTA : TRAZO_INCORRECTA

  return (
    <svg className={`marca-casilla marca-casilla--${tipo}`} viewBox="0 0 34 34" aria-hidden="true">
      <defs>
        <filter id={idFiltroFondo} x="-50%" y="-50%" width="200%" height="200%">
          <feTurbulence type="fractalNoise" baseFrequency="0.1" numOctaves="3" seed="11" result="textura" />
          <feDisplacementMap in="SourceGraphic" in2="textura" scale="5" />
        </filter>
        <filter id={idFiltroTrazo} x="-50%" y="-50%" width="200%" height="200%">
          <feTurbulence type="fractalNoise" baseFrequency="0.14" numOctaves="3" seed="26" result="textura" />
          <feDisplacementMap in="SourceGraphic" in2="textura" scale="3.5" />
        </filter>
      </defs>
      {trazos.map((d) => (
        <path
          key={`fondo-${d}`}
          d={d}
          className="marca-casilla__capa marca-casilla__capa--fondo"
          filter={`url(#${idFiltroFondo})`}
        />
      ))}
      {trazos.map((d) => (
        <path
          key={`trazo-${d}`}
          d={d}
          className="marca-casilla__capa marca-casilla__capa--trazo"
          filter={`url(#${idFiltroTrazo})`}
        />
      ))}
    </svg>
  )
}
