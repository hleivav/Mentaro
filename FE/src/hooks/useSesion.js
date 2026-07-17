import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

export function useSesion(documentoId) {
  const queryClient = useQueryClient()
  const queryKey = ['sesion', documentoId]

  const sesion = useQuery({
    queryKey,
    queryFn: () => api(`/api/documentos/${documentoId}/sesion`),
    enabled: Boolean(documentoId)
  })

  const responder = useMutation({
    mutationFn: (payload) =>
      api(`/api/documentos/${documentoId}/sesion/responder`, {
        method: 'POST',
        body: JSON.stringify(payload)
      })
  })

  // Se llama explicitamente, no en cada respuesta: cuando la respuesta
  // trae "reintentar", hay que seguir mostrando el MISMO elemento (con la
  // explicacion alternativa) en vez de traer el siguiente.
  function avanzar() {
    return queryClient.invalidateQueries({ queryKey })
  }

  return { sesion, responder, avanzar }
}
