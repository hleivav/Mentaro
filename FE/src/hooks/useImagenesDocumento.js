import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export function useImagenesDocumento(documentoId) {
  return useQuery({
    queryKey: ['imagenes', documentoId],
    queryFn: () => api(`/api/documentos/${documentoId}/imagenes`),
    enabled: Boolean(documentoId)
  })
}
