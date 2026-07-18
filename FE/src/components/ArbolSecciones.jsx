function hijosDe(seccionId, secciones) {
  // El backend omite campos null del JSON (default-property-inclusion:
  // non_null) - una seccion raiz llega sin la clave padre_id, no con
  // padre_id:null, asi que en JS el valor es undefined. Sin el ?? null,
  // "undefined === null" da false y la raiz nunca se encuentra.
  return secciones.filter((seccion) => (seccion.padre_id ?? null) === seccionId)
}

function Nodo({ seccion, secciones, seleccionadas, onToggle, heredado, incluidasPorDependencia }) {
  const hijos = hijosDe(seccion.id, secciones)
  const marcadoDirectamente = seleccionadas.has(seccion.id)
  // Si un ancestro esta marcado, este nodo se ve marcado tambien - refleja
  // visualmente lo que SeleccionSecciones ya hace al calcular unidad_ids
  // (una seccion padre arrastra todo su subarbol). Deshabilitado cuando el
  // marcado es solo heredado: destildarlo aca, sin tocar el padre, no
  // tendria un significado claro todavia (no hay logica de "excluir esta
  // sub-seccion nada mas").
  const marcadoPorSeleccion = marcadoDirectamente || heredado
  // Distinto de "heredado" (viene de un ancestro en el mismo subarbol):
  // esto viene de otra rama del documento, arrastrada porque esta seccion
  // asume un concepto que se explica ahi (depende_de, ver
  // cerrarDependencias en SeleccionSecciones). Mismo motivo para
  // deshabilitar el checkbox: destildar solo el prerrequisito dejaria al
  // usuario en un concepto roto.
  const origenDependencia = !marcadoPorSeleccion ? incluidasPorDependencia.get(seccion.id) : undefined
  const marcado = marcadoPorSeleccion || Boolean(origenDependencia)

  const clases = ['arbol-secciones__nodo']
  if (origenDependencia) clases.push('arbol-secciones__nodo--dependencia')

  return (
    <li className={clases.join(' ')}>
      <label>
        <input
          type="checkbox"
          checked={marcado}
          disabled={(heredado && !marcadoDirectamente) || Boolean(origenDependencia)}
          onChange={() => onToggle(seccion.id)}
        />
        <span className="arbol-secciones__titulo">{seccion.titulo}</span>
      </label>
      {origenDependencia && (
        <p className="etiqueta arbol-secciones__nota">incluida por depender de: {origenDependencia}</p>
      )}
      {hijos.length > 0 && (
        <ul>
          {hijos.map((hijo) => (
            <Nodo
              key={hijo.id}
              seccion={hijo}
              secciones={secciones}
              seleccionadas={seleccionadas}
              onToggle={onToggle}
              heredado={marcado}
              incluidasPorDependencia={incluidasPorDependencia}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

const SIN_DEPENDENCIAS = new Map()

// Tabla de contenidos a partir de la lista plana que devuelve GET
// /documentos/{id}/mapa - no un "arbol de habilidades" de videojuego:
// misma tipografia display del resto del sistema, indentacion por
// anidamiento real de secciones. Marcar una seccion padre marca
// visualmente todo su subarbol (ver "heredado" en Nodo) y marcar
// cualquier seccion arrastra sus prerrequisitos (ver
// "incluidasPorDependencia", calculado en SeleccionSecciones) - en ambos
// casos lo que se ve tildado es lo que realmente se va a generar.
export function ArbolSecciones({ secciones, seleccionadas, onToggle, incluidasPorDependencia = SIN_DEPENDENCIAS }) {
  const raices = hijosDe(null, secciones)
  return (
    <ul className="arbol-secciones">
      {raices.map((raiz) => (
        <Nodo
          key={raiz.id}
          seccion={raiz}
          secciones={secciones}
          seleccionadas={seleccionadas}
          onToggle={onToggle}
          heredado={false}
          incluidasPorDependencia={incluidasPorDependencia}
        />
      ))}
    </ul>
  )
}
