import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { subirConProgreso } from '../lib/api'
import { useDocumento } from '../hooks/useDocumento'
import { BarraProgreso } from '../components/BarraProgreso'

export function Subir() {
  const [archivo, setArchivo] = useState(null)
  const [titulo, setTitulo] = useState('')
  const [documentoId, setDocumentoId] = useState(null)
  const [progresoSubida, setProgresoSubida] = useState(0)
  const inputArchivoRef = useRef(null)
  const navigate = useNavigate()

  const subir = useMutation({
    onMutate: () => setProgresoSubida(0),
    mutationFn: () => {
      const formData = new FormData()
      formData.append('archivo', archivo)
      if (titulo) formData.append('titulo', titulo)
      return subirConProgreso('/api/documentos', formData, setProgresoSubida)
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
          <div className="subir__campo-archivo">
            <button
              type="button"
              className="secundario"
              onClick={() => inputArchivoRef.current?.click()}
              disabled={Boolean(documentoId)}
            >
              Elegir documento
            </button>
            {/* Reemplaza el input nativo (texto localizado por el navegador,
                sin relacion con el idioma de la app) - el boton de arriba es
                la unica superficie visible, este queda oculto pero sigue
                siendo un <input type="file"> real. */}
            <input
              ref={inputArchivoRef}
              type="file"
              accept=".txt,.pdf"
              onChange={(evento) => setArchivo(evento.target.files[0])}
              disabled={Boolean(documentoId)}
              hidden
            />
            {archivo && <span className="subir__nombre-archivo">{archivo.name}</span>}
          </div>
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

      {/* Dos etapas encadenadas. "Subiendo": progreso real, medido en bytes
          transferidos (XMLHttpRequest es el unico mecanismo del navegador
          que expone esto, ver subirConProgreso). "Procesando": la Pasada A
          es una sola llamada a DeepSeek sin pasos intermedios que
          reportar - barra indeterminada, nunca un porcentaje inventado. */}
      {subir.isPending && <BarraProgreso texto="Subiendo documento…" fraccion={progresoSubida} />}
      {documentoId && estado === 'procesando' && <BarraProgreso texto="Procesando documento…" />}

      {estado === 'error' && (
        <>
          <p role="alert">Hubo un problema procesando el documento. Intenta subirlo de nuevo.</p>
          <button type="button" className="secundario" onClick={() => setDocumentoId(null)}>
            Volver a intentar
          </button>
        </>
      )}

      {subir.isError && <p role="alert">{subir.error.message}</p>}
    </main>
  )
}
