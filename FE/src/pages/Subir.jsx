import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useDocumento } from '../hooks/useDocumento'

export function Subir() {
  const [archivo, setArchivo] = useState(null)
  const [titulo, setTitulo] = useState('')
  const [documentoId, setDocumentoId] = useState(null)
  const navigate = useNavigate()

  const subir = useMutation({
    mutationFn: () => {
      const formData = new FormData()
      formData.append('archivo', archivo)
      if (titulo) formData.append('titulo', titulo)
      return api('/api/documentos', { method: 'POST', body: formData })
    },
    onSuccess: (documento) => setDocumentoId(documento.id)
  })

  const documento = useDocumento(documentoId)
  const estado = documento.data?.estado

  useEffect(() => {
    if (documentoId && (estado === 'mapeado' || estado === 'listo')) {
      navigate(`/documentos/${documentoId}/seleccion`)
    }
  }, [documentoId, estado, navigate])

  function manejarEnvio(evento) {
    evento.preventDefault()
    if (archivo) subir.mutate()
  }

  return (
    <main className="subir">
      <p className="etiqueta">Nuevo documento</p>
      <h1>Sube un documento</h1>
      <div className="tarjeta">
        <form onSubmit={manejarEnvio}>
          <input
            type="file"
            accept=".txt,.pdf"
            onChange={(evento) => setArchivo(evento.target.files[0])}
            disabled={Boolean(documentoId)}
            required
          />
          <input
            type="text"
            placeholder="Título (opcional)"
            value={titulo}
            onChange={(evento) => setTitulo(evento.target.value)}
            disabled={Boolean(documentoId)}
          />
          <button type="submit" disabled={!archivo || subir.isPending || Boolean(documentoId)}>
            Subir
          </button>
        </form>
      </div>

      {documentoId && estado === 'procesando' && <p>Procesando documento…</p>}

      {estado === 'error' && (
        <>
          <p role="alert">Hubo un problema procesando el documento. Intenta subirlo de nuevo.</p>
          <button type="button" className="secundario" onClick={() => setDocumentoId(null)}>
            Volver a intentar
          </button>
        </>
      )}

      {subir.isError && <p role="alert">No se pudo subir el documento.</p>}
    </main>
  )
}
