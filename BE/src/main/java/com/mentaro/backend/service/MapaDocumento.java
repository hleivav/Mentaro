package com.mentaro.backend.service;

import java.util.List;

// Forma del JSON que devuelve la Pasada A. Los "id" son strings arbitrarios
// elegidos por el modelo, validos solo dentro de esta respuesta (para
// referenciar padre_id/seccion_id/depende_de) - se descartan despues de
// persistir, quedan mapeados a los UUID reales que genera la base.
record MapaDocumento(List<SeccionEsqueleto> estructura, List<UnidadEsqueleto> unidades) {

    record SeccionEsqueleto(String id, String titulo, String padreId, String resumen) {
    }

    record UnidadEsqueleto(
            String id, String titulo, String seccionId, String tipoContenido,
            String nivelImportancia, List<String> dependeDe, List<String> imagenesAsociadas) {
    }
}
