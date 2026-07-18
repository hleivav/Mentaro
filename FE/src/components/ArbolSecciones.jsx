function hijosDe(seccionId, secciones) {
  // El backend omite campos null del JSON (default-property-inclusion:
  // non_null) - una seccion raiz llega sin la clave padre_id, no con
  // padre_id:null, asi que en JS el valor es undefined. Sin el ?? null,
  // "undefined === null" da false y la raiz nunca se encuentra.
  return secciones.filter((seccion) => (seccion.padre_id ?? null) === seccionId)
}

function Nodo({ seccion, secciones, seleccionadas, onToggle, heredado }) {
  const hijos = hijosDe(seccion.id, secciones)
  const marcadoDirectamente = seleccionadas.has(seccion.id)
  // Si un ancestro esta marcado, este nodo se ve marcado tambien - refleja
  // visualmente lo que SeleccionSecciones ya hace al calcular unidad_ids
  // (una seccion padre arrastra todo su subarbol). Deshabilitado cuando el
  // marcado es solo heredado: destildarlo aca, sin tocar el padre, no
  // tendria un significado claro todavia (no hay logica de "excluir esta
  // sub-seccion nada mas").
  const marcado = marcadoDirectamente || heredado

  return (
    <li>
      <label>
        <input
          type="checkbox"
          checked={marcado}
          disabled={heredado && !marcadoDirectamente}
          onChange={() => onToggle(seccion.id)}
        />
        {seccion.titulo}
      </label>
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
            />
          ))}
        </ul>
      )}
    </li>
  )
}

// Arbol de checkboxes a partir de la lista plana que devuelve GET
// /documentos/{id}/mapa. Marcar una seccion padre marca visualmente todo
// su subarbol (ver "heredado" en Nodo) - coincide con la agregacion real
// que ya hace SeleccionSecciones al calcular unidad_ids, asi que lo que
// se ve tildado es lo que realmente se va a generar.
export function ArbolSecciones({ secciones, seleccionadas, onToggle }) {
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
        />
      ))}
    </ul>
  )
}
