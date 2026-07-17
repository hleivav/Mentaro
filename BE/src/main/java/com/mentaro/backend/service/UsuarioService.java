package com.mentaro.backend.service;

import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Usuario resolverOCrear(String firebaseUid, String email) {
        return usuarioRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> usuarioRepository.save(new Usuario(firebaseUid, email)));
    }
}
