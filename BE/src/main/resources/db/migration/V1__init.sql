-- Baseline schema. Add new versioned migrations (V2__*.sql, V3__*.sql, ...)
-- instead of editing this file once it has run against any environment.

CREATE TABLE usuarios (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    TEXT NOT NULL UNIQUE,
    email           TEXT NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documento subido por el usuario. No guarda el texto completo del
-- documento fuente, solo metadata (ver nota de copyright en
-- secuencia-tablero-endpoints.md).
CREATE TABLE documentos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id),
    titulo          TEXT NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    estado          TEXT NOT NULL CHECK (estado IN ('procesando', 'listo', 'error'))
);

CREATE INDEX idx_documentos_usuario_id ON documentos(usuario_id);

-- Unidades generadas por DeepSeek
CREATE TABLE unidades (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id             UUID NOT NULL REFERENCES documentos(id),
    titulo                   TEXT NOT NULL,
    explicacion_corta        TEXT NOT NULL,
    explicacion_alternativa  TEXT NOT NULL,
    pregunta_reconocimiento  JSONB NOT NULL,
    pregunta_refuerzo        JSONB NOT NULL,
    depende_de               UUID[] DEFAULT '{}'
);

CREATE INDEX idx_unidades_documento_id ON unidades(documento_id);

-- Secuencia calculada una vez por documento (orden topológico + intercalado
-- de refuerzos). No se recalcula por partida; "programar refuerzo" solo
-- inserta/reordena posiciones por delante del puntero de avance.
CREATE TABLE secuencia_tablero (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id    UUID NOT NULL REFERENCES documentos(id),
    posicion        INT NOT NULL,
    unidad_id       UUID NOT NULL REFERENCES unidades(id),
    tipo_elemento   TEXT NOT NULL CHECK (tipo_elemento IN ('nueva', 'refuerzo')),
    UNIQUE (documento_id, posicion)
);

CREATE INDEX idx_secuencia_tablero_unidad_id ON secuencia_tablero(unidad_id);

-- Puntero de avance, uno por usuario+documento
CREATE TABLE progreso_usuario (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id),
    documento_id    UUID NOT NULL REFERENCES documentos(id),
    posicion_actual INT NOT NULL DEFAULT 0,
    ultima_sesion   TIMESTAMPTZ,
    UNIQUE (usuario_id, documento_id)
);

-- Estado de dominio por unidad y usuario
CREATE TABLE resultado_unidad (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id),
    unidad_id       UUID NOT NULL REFERENCES unidades(id),
    estado          TEXT NOT NULL CHECK (estado IN ('vista', 'pendiente_refuerzo', 'dominada')),
    intentos        INT NOT NULL DEFAULT 0,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, unidad_id)
);
