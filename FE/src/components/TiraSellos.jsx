import { useVistoUnaVez } from '../hooks/useVistoUnaVez'

const EXPLICACION_DOMINADO =
  'Dominado: acertaste esta unidad y también su repaso espaciado (con un ejemplo distinto) — no solo la primera vez que la viste.'

// Toque secundario y liviano (ver sistema de diseño: "no agregar mas
// mecanicas que estas dos") - un sello de cera por concepto dominado,
// mismo lenguaje visual que los botones (efecto de sello). Siempre con
// denominador (total = alcance de esta pantalla: seccion actual durante
// el juego, documento completo en la pantalla de cierre) - "X dominados"
// sin de-cuantos es indistinguible de un contador de respuestas correctas.
export function TiraSellos({ cantidad, total }) {
  const explicacionVista = useVistoUnaVez('explicacion-dominado')

  if (cantidad === 0) return null

  return (
    <div className="tira-sellos" aria-label={`${cantidad} de ${total} conceptos dominados`}>
      {Array.from({ length: cantidad }, (_, indice) => (
        <span key={indice} className="tira-sellos__sello" />
      ))}
      <span className="tira-sellos__contador" title={EXPLICACION_DOMINADO}>
        {cantidad} dominados de {total}
      </span>
      {!explicacionVista && <p className="etiqueta tira-sellos__explicacion">{EXPLICACION_DOMINADO}</p>}
    </div>
  )
}
