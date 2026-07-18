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
