// Spinner indeterminado - "esto sigue vivo", sin necesitar progreso real
// paso a paso (la Pasada A no expone eso). Mismo sistema de tokens que el
// resto (tinta/oro), nunca un spinner generico de libreria - ver sistema
// de diseño, seccion "Feedback de carga durante el procesamiento".
export function IndicadorCarga({ texto }) {
  return (
    <p className="indicador-carga" role="status">
      <span className="indicador-carga__spinner" aria-hidden="true" />
      {texto}
    </p>
  )
}
