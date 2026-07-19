import { ImagenDocumento } from './ImagenDocumento'

// Los tipos de pregunta pueden traer un imagen_id opcional cuando la
// Pasada B decidio que la pregunta misma -no solo la explicacion previa-
// necesita mostrar una imagen esencial para poder responderse (ej. "¿quien
// tiene el derecho de paso en esta situacion?"). Wrapper compartido para
// no repetir la misma condicion en cada componente de pregunta.
export function ImagenPregunta({ documentoId, imagenId }) {
  if (!imagenId) return null
  return (
    <div className="pregunta-imagen">
      <ImagenDocumento documentoId={documentoId} imagenId={imagenId} alt="Imagen de referencia para esta pregunta" />
    </div>
  )
}
