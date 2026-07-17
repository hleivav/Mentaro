import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

// Arbol de secciones + unidades declarativas (id + nivel_importancia) de
// un documento ya mapeado. Sin polling: se pide una sola vez cuando la
// pantalla de seleccion se monta (el mapa no cambia mientras el usuario
// elige que jugar).
export function useMapaDocumento(documentoId) {
  return useQuery({
    queryKey: ['mapa', documentoId],
    queryFn: () => api(`/api/documentos/${documentoId}/mapa`),
    enabled: Boolean(documentoId)
  })
}
