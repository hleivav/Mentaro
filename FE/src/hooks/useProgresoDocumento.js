import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

// fraccion_avance (para el Camino de Tinta) y unidades_dominadas por
// seccion (para el indice iluminado) - useSesion.avanzar() invalida esta
// query ademas de la de sesion, asi que se refresca justo despues de
// cada respuesta sin necesidad de polling.
export function useProgresoDocumento(documentoId) {
  return useQuery({
    queryKey: ['progreso', documentoId],
    queryFn: () => api(`/api/documentos/${documentoId}/progreso`),
    enabled: Boolean(documentoId)
  })
}
