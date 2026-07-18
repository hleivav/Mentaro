function hijosDe(seccionId, secciones) {
  return secciones.filter((seccion) => (seccion.padre_id ?? null) === seccionId)
}

function estadoDeIluminacion(seccionId, progresoPorSeccion) {
  const datos = progresoPorSeccion.get(seccionId)
  if (!datos || datos.unidades_totales === 0) return 'sin-datos'
  if (datos.unidades_dominadas === 0) return 'sin-iluminar'
  if (datos.unidades_dominadas >= datos.unidades_totales) return 'iluminada'
  return 'iluminandose'
}

function Nodo({ seccion, secciones, progresoPorSeccion }) {
  const hijos = hijosDe(seccion.id, secciones)
  const estado = estadoDeIluminacion(seccion.id, progresoPorSeccion)

  return (
    <li className={`indice-iluminado__nodo indice-iluminado__nodo--${estado}`}>
      <span>{seccion.titulo}</span>
      {hijos.length > 0 && (
        <ul>
          {hijos.map((hijo) => (
            <Nodo key={hijo.id} seccion={hijo} secciones={secciones} progresoPorSeccion={progresoPorSeccion} />
          ))}
        </ul>
      )}
    </li>
  )
}

// Segundo elemento de juego (ademas del Camino de Tinta): ArbolSecciones
// reutilizado como objeto coleccionable, en version solo-lectura - cada
// seccion empieza atenuada y se "ilumina" cuando el usuario realmente la
// domina (no solo la vio), como un manuscrito iluminado que se completa
// con el estudio.
export function IndiceIluminado({ secciones, progresoSecciones }) {
  const raices = hijosDe(null, secciones)
  const progresoPorSeccion = new Map(progresoSecciones.map((s) => [s.id, s]))

  return (
    <ul className="indice-iluminado">
      {raices.map((raiz) => (
        <Nodo key={raiz.id} seccion={raiz} secciones={secciones} progresoPorSeccion={progresoPorSeccion} />
      ))}
    </ul>
  )
}
