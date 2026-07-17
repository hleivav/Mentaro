import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export function useDocumentos() {
  return useQuery({
    queryKey: ['documentos'],
    queryFn: () => api('/api/documentos')
  })
}
