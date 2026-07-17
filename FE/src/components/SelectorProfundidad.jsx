const NIVELES = [
  { valor: 'esencial', etiqueta: 'Idea general', descripcion: 'Solo lo esencial' },
  { valor: 'importante', etiqueta: 'Buen dominio', descripcion: 'Esencial + importante' },
  { valor: 'detalle', etiqueta: 'Experto', descripcion: 'Todo, incluyendo detalles' }
]

export function SelectorProfundidad({ valor, onChange }) {
  return (
    <fieldset className="selector-profundidad">
      <legend>Profundidad</legend>
      {NIVELES.map((nivel) => (
        <label key={nivel.valor}>
          <input
            type="radio"
            name="profundidad"
            value={nivel.valor}
            checked={valor === nivel.valor}
            onChange={() => onChange(nivel.valor)}
          />
          {nivel.etiqueta} — {nivel.descripcion}
        </label>
      ))}
    </fieldset>
  )
}
