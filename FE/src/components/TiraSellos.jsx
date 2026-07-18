// Toque secundario y liviano (ver sistema de diseño: "no agregar mas
// mecanicas que estas dos") - un sello de cera por concepto dominado,
// mismo lenguaje visual que los botones (efecto de sello).
export function TiraSellos({ cantidad }) {
  if (cantidad === 0) return null

  return (
    <div className="tira-sellos" aria-label={`${cantidad} conceptos dominados`}>
      {Array.from({ length: cantidad }, (_, indice) => (
        <span key={indice} className="tira-sellos__sello" />
      ))}
      <span className="tira-sellos__contador">{cantidad} dominados</span>
    </div>
  )
}
