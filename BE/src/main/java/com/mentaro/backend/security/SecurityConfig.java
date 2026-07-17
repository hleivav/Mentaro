package com.mentaro.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.mentaro.backend.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public FirebaseAuthenticationFilter firebaseAuthenticationFilter(
            ObjectProvider<FirebaseAuth> firebaseAuth, UsuarioService usuarioService) {
        return new FirebaseAuthenticationFilter(firebaseAuth, usuarioService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, FirebaseAuthenticationFilter firebaseAuthenticationFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // .cors(...) por si solo NO exime el preflight OPTIONS de
                        // autenticacion - sin esto, el navegador nunca llega a
                        // mandar la solicitud real (queda bloqueada en el
                        // preflight, que el filtro de auth rechaza con 401 antes
                        // de que MVC/CORS puedan responderlo).
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
