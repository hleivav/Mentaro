// retroalimentacion: { indice, correcto } | null - la unica alternativa
// que se marca es la que el usuario efectivamente eligio (ver principios
// de movimiento: un trazo de tinta al acertar, un sello sutil al
// fallar - nunca shake ni rojo saturado).
export function PreguntaOpcionMultiple({ pregunta, onResponder, deshabilitado, retroalimentacion }) {
  return (
    <div className="pregunta-opcion-multiple">
      <p>{pregunta.enunciado}</p>
      <ul>
        {pregunta.alternativas.map((alternativa, indice) => {
          const marcada = retroalimentacion?.indice === indice
          const clase = marcada
            ? retroalimentacion.correcto
              ? 'alternativa--correcta'
              : 'alternativa--incorrecta'
            : ''
          return (
            <li key={indice}>
              <button type="button" className={clase} disabled={deshabilitado} onClick={() => onResponder(indice)}>
                {alternativa}
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
