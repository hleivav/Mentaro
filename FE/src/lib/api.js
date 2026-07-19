import { getAuth } from 'firebase/auth'

export class TextoFuenteExpiradoError extends Error {}

// FormData (subida de archivos) fija su propio Content-Type con boundary -
// si lo forzamos a application/json el multipart llega roto al backend.
function pedir(path, options, token) {
  const esFormData = options.body instanceof FormData
  return fetch(`${import.meta.env.VITE_API_URL}${path}`, {
    ...options,
    headers: {
      ...(esFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })
}

export async function api(path, options = {}) {
  let res = await pedir(path, options, await getAuth().currentUser?.getIdToken())

  if (res.status === 401 && getAuth().currentUser) {
    // El token que el SDK de Firebase tenia en cache puede haber quedado
    // invalido sin que el cliente se entere (refresco fallido, reloj
    // desincronizado) - forzamos un refresh real contra Firebase y
    // reintentamos una vez antes de rendirnos, en vez de obligar al
    // usuario a recargar la pagina a mano cada vez que esto pasa.
    res = await pedir(path, options, await getAuth().currentUser.getIdToken(true))
  }

  if (res.status === 410) {
    // El texto fuente temporal ya expiró (48h de inactividad) — no hay
    // forma de recuperarlo, el backend no reintenta Pasada A solo. La
    // única salida real es pedirle al usuario que suba el documento de
    // nuevo, nunca reintentar silenciosamente.
    throw new TextoFuenteExpiradoError()
  }
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  if (res.status === 204) return null
  return res.json()
}

// Para recursos binarios (imagenes) - un <img src> plano no puede mandar
// el header Authorization, asi que se pide autenticado como blob y se
// arma un object URL del lado del componente (ver ImagenDocumento).
export async function apiBlob(path) {
  let res = await pedir(path, {}, await getAuth().currentUser?.getIdToken())

  if (res.status === 401 && getAuth().currentUser) {
    res = await pedir(path, {}, await getAuth().currentUser.getIdToken(true))
  }

  if (!res.ok) throw new Error(`API error: ${res.status}`)
  return res.blob()
}

// fetch no expone progreso de subida (solo de descarga) - XMLHttpRequest
// es el unico mecanismo del navegador que lo da, de ahi que este caso use
// una implementacion aparte en vez de reusar pedir(). onProgreso recibe
// una fraccion 0..1, real (bytes transferidos / total), nunca inventada.
export async function subirConProgreso(path, formData, onProgreso) {
  const token = await getAuth().currentUser?.getIdToken()
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${import.meta.env.VITE_API_URL}${path}`)
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (evento) => {
      if (evento.lengthComputable && onProgreso) onProgreso(evento.loaded / evento.total)
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.status === 204 ? null : JSON.parse(xhr.responseText))
      } else {
        reject(new Error(`API error: ${xhr.status}`))
      }
    }
    xhr.onerror = () => reject(new Error('Error de red al subir el archivo'))
    xhr.send(formData)
  })
}
