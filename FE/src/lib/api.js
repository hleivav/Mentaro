import { getAuth } from 'firebase/auth'

export class TextoFuenteExpiradoError extends Error {}

export async function api(path, options = {}) {
  const token = await getAuth().currentUser?.getIdToken()
  // FormData (subida de archivos) fija su propio Content-Type con boundary -
  // si lo forzamos a application/json el multipart llega roto al backend.
  const esFormData = options.body instanceof FormData
  const res = await fetch(`${import.meta.env.VITE_API_URL}${path}`, {
    ...options,
    headers: {
      ...(esFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })
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
