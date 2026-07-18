// Tonos sintetizados via Web Audio API - evita depender de archivos de
// audio externos para un efecto opcional y liviano (ver sistema de
// diseño: apagado por defecto, nunca un audio inesperado en una app de
// estudio que puede usarse en transporte publico o espacios silenciosos).
let contextoAudio = null

function obtenerContexto() {
  const AudioContextCtor = window.AudioContext || window.webkitAudioContext
  if (!AudioContextCtor) return null
  if (!contextoAudio) contextoAudio = new AudioContextCtor()
  if (contextoAudio.state === 'suspended') contextoAudio.resume().catch(() => {})
  return contextoAudio
}

function tono(frecuencias, duracionSegundos) {
  try {
    const ctx = obtenerContexto()
    if (!ctx) return
    const inicio = ctx.currentTime
    frecuencias.forEach((frecuencia, indice) => {
      const oscilador = ctx.createOscillator()
      const ganancia = ctx.createGain()
      oscilador.type = 'sine'
      oscilador.frequency.value = frecuencia
      const desde = inicio + indice * duracionSegundos * 0.8
      ganancia.gain.setValueAtTime(0, desde)
      ganancia.gain.linearRampToValueAtTime(0.15, desde + 0.01)
      ganancia.gain.exponentialRampToValueAtTime(0.001, desde + duracionSegundos)
      oscilador.connect(ganancia)
      ganancia.connect(ctx.destination)
      oscilador.start(desde)
      oscilador.stop(desde + duracionSegundos)
    })
  } catch {
    // El sonido es un extra opcional, nunca critico para jugar.
  }
}

// "Pling": concepto nuevo - dos notas ascendentes, brillante.
export function reproducirPling() {
  tono([660, 880], 0.16)
}

// "Plong": repaso - una sola nota mas grave, mas apagada.
export function reproducirPlong() {
  tono([392], 0.22)
}

// Acierto: arpeggio corto y alegre, ascendente - distinto de "pling"
// (concepto nuevo) para no confundir los dos momentos.
export function reproducirAcierto() {
  tono([523, 659, 784], 0.12)
}

// Error: dos notas descendentes y suaves - mismo criterio que el resto
// del sistema de diseño para retroalimentacion visual (nunca un efecto
// agresivo), aplicado tambien al sonido.
export function reproducirError() {
  tono([349, 262], 0.18)
}
