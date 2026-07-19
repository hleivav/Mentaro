// fraccion: 0..1 para progreso real y medible (ej. bytes subidos/total) -
// se omite (undefined) cuando no existe una señal real de avance (ej. la
// Pasada A es una sola llamada a DeepSeek sin pasos intermedios que
// reportar), y la barra pasa a una animacion indeterminada en vez de
// inventar un porcentaje falso.
export function BarraProgreso({ texto, fraccion }) {
  const determinado = typeof fraccion === 'number'
  return (
    <div className="barra-progreso" role="status">
      <p className="barra-progreso__texto">{texto}</p>
      <div className="barra-progreso__pista">
        <div
          className={
            determinado
              ? 'barra-progreso__relleno'
              : 'barra-progreso__relleno barra-progreso__relleno--indeterminado'
          }
          style={determinado ? { width: `${Math.round(fraccion * 100)}%` } : undefined}
        />
      </div>
    </div>
  )
}
