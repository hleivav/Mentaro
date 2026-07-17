package com.mentaro.backend.anthropic;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

// Cliente para el endpoint /v1/messages de Anthropic - usado unicamente
// para describir imagenes embebidas en documentos (DeepSeek no acepta
// imagenes en su API). Separado de DeepSeekClient a proposito: distinta
// API key, distinto esquema de auth (x-api-key + anthropic-version, no
// Bearer), distinto formato de mensaje (bloques de contenido tipados en
// vez de un string plano).
@Component
public class AnthropicClient {

    private static final int MAX_TOKENS = 300;
    private static final String VERSION = "2023-06-01";

    private final RestClient restClient;
    private final String modelo;

    public AnthropicClient(
            RestClient.Builder builder,
            JsonMapper jsonMapper,
            @Value("${app.anthropic.base-url}") String baseUrl,
            @Value("${app.anthropic.api-key}") String apiKey,
            @Value("${app.anthropic.modelo}") String modelo,
            @Value("${app.anthropic.timeout-conexion}") Duration timeoutConexion,
            @Value("${app.anthropic.timeout-lectura}") Duration timeoutLectura) {
        this.modelo = modelo;
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults().withTimeouts(timeoutConexion, timeoutLectura));
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", VERSION)
                .requestFactory(requestFactory)
                .configureMessageConverters(
                        converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
    }

    public String describirImagen(byte[] imagenBytes, String mediaType, String prompt) {
        MessageRequest request = new MessageRequest(
                modelo,
                MAX_TOKENS,
                List.of(new Mensaje("user", List.of(
                        new BloqueImagen(new FuenteImagen(mediaType, Base64.getEncoder().encodeToString(imagenBytes))),
                        new BloqueTexto(prompt)))));

        MessageResponse response = restClient.post()
                .uri("/v1/messages")
                .body(request)
                .retrieve()
                .body(MessageResponse.class);

        if (response == null || response.content().isEmpty()) {
            throw new IllegalStateException("Anthropic no devolvio ninguna respuesta valida");
        }
        return response.content().getFirst().text();
    }

    record MessageRequest(String model, int maxTokens, List<Mensaje> messages) {
    }

    record Mensaje(String role, List<Object> content) {
    }

    record BloqueImagen(String type, FuenteImagen source) {
        BloqueImagen(FuenteImagen source) {
            this("image", source);
        }
    }

    record FuenteImagen(String type, String mediaType, String data) {
        FuenteImagen(String mediaType, String data) {
            this("base64", mediaType, data);
        }
    }

    record BloqueTexto(String type, String text) {
        BloqueTexto(String text) {
            this("text", text);
        }
    }

    record MessageResponse(List<BloqueRespuesta> content) {
    }

    record BloqueRespuesta(String type, String text) {
    }
}
