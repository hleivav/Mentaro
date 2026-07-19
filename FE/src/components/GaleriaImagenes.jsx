import { useImagenesDocumento } from '../hooks/useImagenesDocumento'
import { ImagenDocumento } from './ImagenDocumento'

// Galeria simple por documento (no por seccion/pregunta especifica - hoy
// no hay vinculo entre una unidad y la pagina de origen de una imagen,
// ver sistema de diseño). Lista todas las imagenes en orden de aparicion,
// cada una con la descripcion ya generada como pie de foto.
export function GaleriaImagenes({ documentoId }) {
  const imagenes = useImagenesDocumento(documentoId)

  if (imagenes.isLoading) return <p>Cargando imágenes…</p>
  if (imagenes.isError) return <p role="alert">No se pudieron cargar las imágenes.</p>
  if (imagenes.data?.length === 0) return <p>Este documento no tiene imágenes.</p>

  return (
    <ul className="galeria-imagenes">
      {imagenes.data?.map((imagen) => (
        <li key={imagen.id} className="galeria-imagenes__item">
          <ImagenDocumento documentoId={documentoId} imagenId={imagen.id} alt={imagen.descripcion} />
          <p className="galeria-imagenes__pie">{imagen.descripcion}</p>
        </li>
      ))}
    </ul>
  )
}
