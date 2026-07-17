package com.mentaro.backend.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Expone un CorsConfigurationSource (no WebMvcConfigurer.addCorsMappings)
// a proposito: Spring Security corre ANTES que el DispatcherServlet, asi
// que un mapping a nivel MVC nunca llega a aplicarse para un preflight
// OPTIONS si la ruta requiere autenticacion - el filtro de seguridad lo
// rechaza con 401 antes de que MVC pueda responder el preflight. Este
// bean lo consume SecurityConfig via .cors(Customizer.withDefaults()),
// que si corre lo bastante temprano en la cadena de filtros.
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
