package com.mentaro.backend.service;

import java.util.List;

// Forma del JSON crudo guardado en unidades.pregunta_reconocimiento y
// unidades.pregunta_refuerzo. Provisional: la estructura definitiva la fija
// prompt-generacion-unidades.md, que todavia no se incorporo a este esquema.
record PreguntaAlmacenada(String enunciado, List<String> alternativas, int correctaIndex) {
}
