import { useEffect, useState } from 'react'
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut
} from 'firebase/auth'
import { auth, googleProvider } from '../lib/firebase'

export function useAuth() {
  const [usuario, setUsuario] = useState(auth.currentUser)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    return onAuthStateChanged(auth, (usuarioActual) => {
      setUsuario(usuarioActual)
      setCargando(false)
    })
  }, [])

  return {
    usuario,
    cargando,
    iniciarSesionConEmail: (email, password) => signInWithEmailAndPassword(auth, email, password),
    registrarseConEmail: (email, password) => createUserWithEmailAndPassword(auth, email, password),
    iniciarSesionConGoogle: () => signInWithPopup(auth, googleProvider),
    cerrarSesion: () => signOut(auth)
  }
}
