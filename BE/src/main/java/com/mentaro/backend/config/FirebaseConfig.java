package com.mentaro.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class FirebaseConfig {

    // @Lazy evita que Spring cree este bean durante el arranque (fallaria
    // sin credenciales reales de Firebase). FirebaseAuth es ademas una clase
    // final, asi que Spring no puede generarle un proxy perezoso para
    // inyeccion directa por tipo; por eso quien la consume (el filtro) lo
    // hace via ObjectProvider<FirebaseAuth>, resuelto recien en la primera
    // request autenticada.
    @Bean
    @Lazy
    public FirebaseAuth firebaseAuth(@Value("${app.firebase.credentials-base64}") String credentialsBase64)
            throws IOException {
        if (credentialsBase64.isBlank()) {
            throw new IllegalStateException(
                    "FIREBASE_SERVICE_ACCOUNT_BASE64 no esta configurado. Ver BE/.env.example.");
        }

        byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(decoded)))
                .build();

        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        return FirebaseAuth.getInstance(app);
    }
}
