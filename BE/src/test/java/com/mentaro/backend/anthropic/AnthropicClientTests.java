package com.mentaro.backend.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

// Prueba el cliente contra un servidor HTTP falso, sin depender de una API
// key real de Anthropic.
class AnthropicClientTests {

    private MockWebServer server;
    private AnthropicClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        client = new AnthropicClient(RestClient.builder(), jsonMapper, server.url("/").toString(), "test-api-key",
                "claude-haiku-4-5-20251001", Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void mandaElRequestCorrectoYDevuelveElTextoDeLaRespuesta() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"content\": [{\"type\": \"text\", \"text\": \"Un molino de viento.\"}]}")
                .addHeader("Content-Type", "application/json"));

        byte[] imagen = {1, 2, 3, 4};
        String resultado = client.describirImagen(imagen, "image/png", "describe esta imagen");

        assertThat(resultado).isEqualTo("Un molino de viento.");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        // Autenticacion de Anthropic: x-api-key + anthropic-version, NO
        // Authorization Bearer (a diferencia de DeepSeek).
        assertThat(request.getHeader("x-api-key")).isEqualTo("test-api-key");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(request.getHeader("Authorization")).isNull();

        String body = request.getBody().readUtf8();
        String base64Esperado = Base64.getEncoder().encodeToString(imagen);
        assertThat(body)
                .contains("\"model\":\"claude-haiku-4-5-20251001\"")
                .contains("\"max_tokens\":300")
                .contains("\"type\":\"image\"")
                .contains("\"media_type\":\"image/png\"")
                .contains("\"data\":\"" + base64Esperado + "\"")
                .contains("\"type\":\"text\"")
                .contains("\"text\":\"describe esta imagen\"");
    }

    @Test
    void lanzaExcepcionSiLaRespuestaNoTraeContenido() {
        server.enqueue(new MockResponse()
                .setBody("{\"content\": []}")
                .addHeader("Content-Type", "application/json"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> client.describirImagen(new byte[] {1}, "image/png", "prompt"));
    }
}
