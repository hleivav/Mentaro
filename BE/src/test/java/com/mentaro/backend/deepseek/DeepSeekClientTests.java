package com.mentaro.backend.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.time.Duration;
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
// key real de DeepSeek.
class DeepSeekClientTests {

    private MockWebServer server;
    private DeepSeekClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // Misma config que application.yml (spring.jackson.property-naming-
        // strategy: SNAKE_CASE, default-property-inclusion: non_null),
        // reproducida a mano porque este test no levanta el contexto de
        // Spring.
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        client = new DeepSeekClient(RestClient.builder(), jsonMapper, server.url("/").toString(), "test-api-key",
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void mandaElRequestCorrectoYDevuelveElContenidoDeLaRespuesta() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"{\\\"ok\\\": true}\"}}]}")
                .addHeader("Content-Type", "application/json"));

        String resultado = client.completar(
                DeepSeekOpciones.sinThinking("deepseek-v4-flash", 0.3), "prompt sistema", "prompt usuario");

        assertThat(resultado).isEqualTo("{\"ok\": true}");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

        String body = request.getBody().readUtf8();
        assertThat(body)
                .contains("\"model\":\"deepseek-v4-flash\"")
                .contains("\"temperature\":0.3")
                .contains("\"prompt sistema\"")
                .contains("\"prompt usuario\"")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"extra_body\":{\"thinking\":{\"type\":\"disabled\"}}")
                .doesNotContain("reasoning_effort");
    }

    @Test
    void habilitaThinkingYReasoningEffortCuandoSePide() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"{}\"}}]}")
                .addHeader("Content-Type", "application/json"));

        client.completar(
                DeepSeekOpciones.conThinking("deepseek-v4-pro", "high"), "prompt sistema", "prompt usuario");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .contains("\"extra_body\":{\"thinking\":{\"type\":\"enabled\"}}")
                .contains("\"reasoning_effort\":\"high\"");
    }
}
