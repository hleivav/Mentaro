package com.mentaro.backend.deepseek;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

// Cliente generico para el endpoint chat/completions de DeepSeek (API
// compatible con OpenAI). Usado tanto por la Pasada A como por la Pasada B.
@Component
public class DeepSeekClient {

    private final RestClient restClient;

    public DeepSeekClient(
            RestClient.Builder builder,
            JsonMapper jsonMapper,
            @Value("${app.deepseek.base-url}") String baseUrl,
            @Value("${app.deepseek.api-key}") String apiKey,
            @Value("${app.deepseek.timeout-conexion}") Duration timeoutConexion,
            @Value("${app.deepseek.timeout-lectura}") Duration timeoutLectura) {
        // El request factory por defecto de RestClient.Builder tiene un
        // timeout de lectura demasiado corto para la Pasada B (thinking +
        // reasoning_effort alto sobre un lote de unidades puede tardar
        // varios minutos, no segundos) - sin esto, DeepSeek corta la
        // conexion a mitad de una respuesta valida (Connection reset), no
        // es un error del prompt ni del contenido.
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults().withTimeouts(timeoutConexion, timeoutLectura));

        // El RestClient.Builder autoconfigurado no usa el ObjectMapper de la
        // app (con snake_case ya configurado) para sus converters por
        // defecto - se lo pasamos explicito para que el JSON hacia DeepSeek
        // salga consistente con el resto de la app (response_format, no
        // responseFormat).
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .configureMessageConverters(
                        converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
    }

    // Devuelve el texto de la respuesta del modelo (siempre JSON, ver
    // response_format). Thinking y reasoning_effort se declaran siempre
    // explicitos (thinking viene "enabled" por defecto del lado de DeepSeek
    // si no se manda nada - mejor no depender de ese default).
    public String completar(DeepSeekOpciones opciones, String promptSistema, String promptUsuario) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                opciones.modelo(),
                List.of(new Mensaje("system", promptSistema), new Mensaje("user", promptUsuario)),
                opciones.temperatura(),
                new ResponseFormat("json_object"),
                new ExtraBody(new Thinking(opciones.thinkingHabilitado() ? "enabled" : "disabled")),
                opciones.reasoningEffort());

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices().isEmpty()) {
            throw new IllegalStateException("DeepSeek no devolvio ninguna respuesta valida");
        }
        return response.choices().getFirst().message().content();
    }

    record ChatCompletionRequest(
            String model, List<Mensaje> messages, double temperature,
            ResponseFormat responseFormat, ExtraBody extraBody, String reasoningEffort) {
    }

    record Mensaje(String role, String content) {
    }

    record ResponseFormat(String type) {
    }

    record ExtraBody(Thinking thinking) {
    }

    record Thinking(String type) {
    }

    record ChatCompletionResponse(List<Choice> choices) {
    }

    record Choice(Mensaje message) {
    }
}
