package com.mentaro.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(nullable = false)
    private String email;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected Usuario() {
    }

    public Usuario(String firebaseUid, String email) {
        this.firebaseUid = firebaseUid;
        this.email = email;
    }

    public UUID getId() {
        return id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
