package com.mentaro.backend.dto;

public record ResponderResponse(boolean correcto, Boolean reintentar, Boolean avanzar, String explicacionAlternativa) {

    public static ResponderResponse respuestaCorrecta() {
        return new ResponderResponse(true, null, null, null);
    }

    public static ResponderResponse paraReintentar(String explicacionAlternativa) {
        return new ResponderResponse(false, true, null, explicacionAlternativa);
    }

    public static ResponderResponse paraAvanzarSinBloquear() {
        return new ResponderResponse(false, null, true, null);
    }
}
