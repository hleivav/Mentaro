import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export function useDocumento(id) {
  return useQuery({
    queryKey: ['documento', id],
    queryFn: () => api(`/api/documentos/${id}`),
    enabled: Boolean(id),
    refetchInterval: (query) =>
      ['procesando', 'generando'].includes(query.state.data?.estado) ? 3000 : false
  })
}
