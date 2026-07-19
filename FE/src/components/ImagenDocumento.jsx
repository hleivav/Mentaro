import { useEffect, useState } from 'react'
import { apiBlob } from '../lib/api'

// <img src> plano no puede mandar el header Authorization - se trae la
// imagen autenticada como blob y se arma un object URL local, revocado al
// desmontar para no filtrar memoria.
export function ImagenDocumento({ documentoId, imagenId, alt }) {
  const [url, setUrl] = useState(null)

  useEffect(() => {
    let objectUrl
    let cancelado = false
    apiBlob(`/api/documentos/${documentoId}/imagenes/${imagenId}`).then((blob) => {
      if (cancelado) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    })
    return () => {
      cancelado = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [documentoId, imagenId])

  if (!url) return <div className="imagen-documento imagen-documento--cargando" aria-hidden="true" />
  return <img className="imagen-documento" src={url} alt={alt} />
}
