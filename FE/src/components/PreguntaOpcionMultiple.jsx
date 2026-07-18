// retroalimentacion: { indice, correcto } | null - la unica alternativa
// que se marca es la que el usuario efectivamente eligio. El trazo de
// tinta y el sello son decoracion, NUNCA la unica señal (ver sistema de
// diseño): color + icono + texto breve, siempre los tres juntos - una
// sutileza visual sola no le sirve a un jugador nuevo, ni a quien no
// distingue bien rojo/verde.
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
                {marcada && (
                  <span className="pregunta-opcion-multiple__retro-texto">
                    {retroalimentacion.correcto ? '✓ ¡Correcto!' : '✕ No es así'}
                  </span>
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
