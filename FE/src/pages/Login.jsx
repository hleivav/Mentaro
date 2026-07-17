import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export function Login() {
  const { iniciarSesionConEmail, registrarseConEmail, iniciarSesionConGoogle } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  async function manejarEnvio(evento) {
    evento.preventDefault()
    setError(null)
    try {
      await iniciarSesionConEmail(email, password)
      navigate('/documentos')
    } catch {
      setError('No se pudo iniciar sesión. Revisa el email y la contraseña.')
    }
  }

  async function manejarRegistro() {
    setError(null)
    try {
      await registrarseConEmail(email, password)
      navigate('/documentos')
    } catch {
      setError('No se pudo crear la cuenta.')
    }
  }

  async function manejarGoogle() {
    setError(null)
    try {
      await iniciarSesionConGoogle()
      navigate('/documentos')
    } catch {
      setError('No se pudo iniciar sesión con Google.')
    }
  }

  return (
    <main className="login">
      <h1>Mentaro</h1>
      <form onSubmit={manejarEnvio}>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Contraseña"
          value={password}
          onChange={(evento) => setPassword(evento.target.value)}
          required
        />
        <button type="submit">Iniciar sesión</button>
        <button type="button" onClick={manejarRegistro}>
          Crear cuenta
        </button>
      </form>
      <button type="button" onClick={manejarGoogle}>
        Continuar con Google
      </button>
      {error && <p role="alert">{error}</p>}
    </main>
  )
}
