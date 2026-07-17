function hijosDe(seccionId, secciones) {
  // El backend omite campos null del JSON (default-property-inclusion:
  // non_null) - una seccion raiz llega sin la clave padre_id, no con
  // padre_id:null, asi que en JS el valor es undefined. Sin el ?? null,
  // "undefined === null" da false y la raiz nunca se encuentra.
  return secciones.filter((seccion) => (seccion.padre_id ?? null) === seccionId)
}

function Nodo({ seccion, secciones, seleccionadas, onToggle }) {
  const hijos = hijosDe(seccion.id, secciones)
  return (
    <li>
      <label>
        <input
          type="checkbox"
          checked={seleccionadas.has(seccion.id)}
          onChange={() => onToggle(seccion.id)}
        />
        {seccion.titulo}
      </label>
      {hijos.length > 0 && (
        <ul>
          {hijos.map((hijo) => (
            <Nodo key={hijo.id} seccion={hijo} secciones={secciones} seleccionadas={seleccionadas} onToggle={onToggle} />
          ))}
        </ul>
      )}
    </li>
  )
}

// Arbol de checkboxes a partir de la lista plana que devuelve GET
// /documentos/{id}/mapa. Marcar una seccion padre no desmarca ni fuerza
// los hijos visualmente - la agregacion real (que una seccion padre
// arrastra su subarbol completo al calcular unidad_ids) vive en
// SeleccionSecciones, no aca; este componente solo refleja que casillas
// estan tildadas.
export function ArbolSecciones({ secciones, seleccionadas, onToggle }) {
  const raices = hijosDe(null, secciones)
  return (
    <ul className="arbol-secciones">
      {raices.map((raiz) => (
        <Nodo key={raiz.id} seccion={raiz} secciones={secciones} seleccionadas={seleccionadas} onToggle={onToggle} />
      ))}
    </ul>
  )
}
