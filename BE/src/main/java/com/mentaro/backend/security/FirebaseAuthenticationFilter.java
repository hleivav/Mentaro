package com.mentaro.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<FirebaseAuth> firebaseAuth;
    private final UsuarioService usuarioService;

    public FirebaseAuthenticationFilter(ObjectProvider<FirebaseAuth> firebaseAuth, UsuarioService usuarioService) {
        this.firebaseAuth = firebaseAuth;
        this.usuarioService = usuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String idToken = header.substring(BEARER_PREFIX.length());
            try {
                FirebaseToken decoded = firebaseAuth.getObject().verifyIdToken(idToken);
                Usuario usuario = usuarioService.resolverOCrear(decoded.getUid(), decoded.getEmail());
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
            } catch (FirebaseAuthException e) {
                log.warn("Token de Firebase rechazado ({}): {}", e.getAuthErrorCode(), e.getMessage());
                SecurityContextHolder.clearContext();
            } catch (BeanCreationException e) {
                // FirebaseAuth es un bean @Lazy: si FIREBASE_SERVICE_ACCOUNT_BASE64
                // no esta configurado, la excepcion recien aparece aca, en la
                // primera request con un Authorization header.
                log.error("No se pudo inicializar Firebase Auth (revisar FIREBASE_SERVICE_ACCOUNT_BASE64): {}",
                        e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
