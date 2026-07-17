package com.mentaro.backend.deepseek;

// Agrupa los parametros de un llamado a DeepSeek para no repetir una lista
// larga de argumentos posicionales del mismo tipo (String, double, boolean,
// String...) en cada call site.
public record DeepSeekOpciones(String modelo, double temperatura, boolean thinkingHabilitado, String reasoningEffort) {

    public static DeepSeekOpciones sinThinking(String modelo, double temperatura) {
        return new DeepSeekOpciones(modelo, temperatura, false, null);
    }

    // DeepSeek ignora en silencio el parametro de temperatura en modo
    // thinking (comportamiento documentado, no un bug) - no tiene sentido
    // exponerlo aca.
    public static DeepSeekOpciones conThinking(String modelo, String reasoningEffort) {
        return new DeepSeekOpciones(modelo, 1.0, true, reasoningEffort);
    }
}
