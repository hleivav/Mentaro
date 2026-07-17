package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Construye secuencia_tablero a partir de unidades ya generadas (Pasada B).
// Ordena respetando depende_de (orden topologico) y asigna posiciones con
// huecos (paso 100, ver PasadaBService.programarRefuerzo) para poder
// intercalar refuerzos despues sin renumerar nada.
@Service
public class SecuenciaTableroService {

    private static final Logger log = LoggerFactory.getLogger(SecuenciaTableroService.class);
    private static final int PASO_POSICION = 100;

    private final SecuenciaTableroRepository secuenciaTableroRepository;

    public SecuenciaTableroService(SecuenciaTableroRepository secuenciaTableroRepository) {
        this.secuenciaTableroRepository = secuenciaTableroRepository;
    }

    // Se llama tanto la primera vez que un documento se genera (secuencia
    // vacia, arranca en 100) como al "profundizar" una seccion ya jugable
    // (se agrega despues de la ultima posicion existente) - en ambos casos
    // solo agrega filas nuevas, nunca reordena las que ya estan.
    @Transactional
    public void construir(Documento documento, List<Unidad> unidadesGeneradas) {
        List<Unidad> jugables = unidadesGeneradas.stream().filter(Unidad::tieneContenido).toList();
        if (jugables.isEmpty()) {
            return;
        }

        List<Unidad> ordenadas = ordenarPorDependencias(jugables);

        int posicion = secuenciaTableroRepository.findFirstByDocumento_IdOrderByPosicionDesc(documento.getId())
                .map(SecuenciaTablero::getPosicion)
                .orElse(0);

        List<SecuenciaTablero> nuevos = new ArrayList<>();
        for (Unidad unidad : ordenadas) {
            posicion += PASO_POSICION;
            nuevos.add(new SecuenciaTablero(documento, posicion, unidad, TipoElemento.NUEVA));
        }
        secuenciaTableroRepository.saveAll(nuevos);
    }

    // Kahn's algorithm. Dependencias fuera del conjunto dado se ignoran (una
    // unidad puede depender de algo no seleccionado/generado). Si hay un
    // ciclo (no deberia pasar con contenido bien generado, pero nunca hay
    // que bloquear), las unidades que quedan sin procesar se agregan al
    // final en su orden original en vez de fallar.
    private List<Unidad> ordenarPorDependencias(List<Unidad> unidades) {
        Set<UUID> idsEnConjunto = new HashSet<>();
        unidades.forEach(u -> idsEnConjunto.add(u.getId()));

        Map<UUID, Integer> gradoEntrada = new HashMap<>();
        Map<UUID, List<UUID>> dependientesDe = new HashMap<>();
        unidades.forEach(u -> gradoEntrada.put(u.getId(), 0));

        for (Unidad unidad : unidades) {
            for (UUID dependencia : unidad.getDependeDe()) {
                if (!idsEnConjunto.contains(dependencia)) {
                    continue;
                }
                dependientesDe.computeIfAbsent(dependencia, k -> new ArrayList<>()).add(unidad.getId());
                gradoEntrada.merge(unidad.getId(), 1, Integer::sum);
            }
        }

        Map<UUID, Unidad> unidadesPorId = new HashMap<>();
        unidades.forEach(u -> unidadesPorId.put(u.getId(), u));

        Deque<UUID> listos = new ArrayDeque<>();
        for (Unidad unidad : unidades) {
            if (gradoEntrada.get(unidad.getId()) == 0) {
                listos.add(unidad.getId());
            }
        }

        List<Unidad> resultado = new ArrayList<>();
        while (!listos.isEmpty()) {
            UUID actualId = listos.poll();
            resultado.add(unidadesPorId.get(actualId));
            for (UUID siguienteId : dependientesDe.getOrDefault(actualId, List.of())) {
                int nuevoGrado = gradoEntrada.merge(siguienteId, -1, Integer::sum);
                if (nuevoGrado == 0) {
                    listos.add(siguienteId);
                }
            }
        }

        if (resultado.size() < unidades.size()) {
            Set<UUID> procesados = new HashSet<>();
            resultado.forEach(u -> procesados.add(u.getId()));
            List<Unidad> pendientes = unidades.stream().filter(u -> !procesados.contains(u.getId())).toList();
            log.warn("Ciclo de dependencias detectado entre {} unidad(es), se agregan al final sin ordenar: {}",
                    pendientes.size(), pendientes.stream().map(Unidad::getId).toList());
            resultado.addAll(pendientes);
        }

        return resultado;
    }
}
