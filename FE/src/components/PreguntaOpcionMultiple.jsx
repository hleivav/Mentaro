export function PreguntaOpcionMultiple({ pregunta, onResponder, deshabilitado }) {
  return (
    <div className="pregunta-opcion-multiple">
      <p>{pregunta.enunciado}</p>
      <ul>
        {pregunta.alternativas.map((alternativa, indice) => (
          <li key={indice}>
            <button type="button" disabled={deshabilitado} onClick={() => onResponder(indice)}>
              {alternativa}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
