package com.mentaro.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

// El ObjectMapper autoconfigurado por Spring Boot (usado tanto por SesionService
// como por los HttpMessageConverter de Spring MVC) debe serializar/deserializar
// en snake_case, tal cual los ejemplos JSON de secuencia-tablero-endpoints.md.
@SpringBootTest
class JsonSnakeCaseTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializaCamposEnSnakeCase() {
        ResponderResponse response = ResponderResponse.paraReintentar("explicacion alternativa");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"explicacion_alternativa\"").doesNotContain("explicacionAlternativa");
    }

    @Test
    void deserializaDesdeSnakeCase() {
        UUID unidadId = UUID.randomUUID();
        String json = """
                {
                  "unidad_id": "%s",
                  "tipo_elemento": "nueva",
                  "respuesta_index": 2,
                  "intento_numero": 1
                }
                """.formatted(unidadId);

        ResponderRequest request = objectMapper.readValue(json, ResponderRequest.class);

        assertThat(request.unidadId()).isEqualTo(unidadId);
        assertThat(request.tipoElemento()).isEqualTo("nueva");
        assertThat(request.respuestaIndex()).isEqualTo(2);
        assertThat(request.intentoNumero()).isEqualTo(1);
    }

    @Test
    void omiteCamposNulosEnLaRespuesta() {
        ResponderResponse response = ResponderResponse.respuestaCorrecta();
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("reintentar").doesNotContain("avanzar").doesNotContain("explicacion");
    }
}
