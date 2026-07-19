-- Igual espiritu que documento_texto_temporal (ver V6): vida transitoria,
-- se limpia por inactividad (48h), nunca persiste indefinidamente. A
-- diferencia del texto (una fila por documento), hay muchas imagenes por
-- documento, de ahi el id propio en vez de documento_id como PK.
CREATE TABLE documento_imagen_temporal (
    id              UUID PRIMARY KEY,
    documento_id    UUID NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    pagina          INT NOT NULL,
    orden           INT NOT NULL,
    descripcion     TEXT NOT NULL,
    imagen_bytes    BYTEA NOT NULL,
    media_type      TEXT NOT NULL,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_imagen_temporal_documento_id ON documento_imagen_temporal(documento_id);
CREATE INDEX idx_documento_imagen_temporal_actualizado_en ON documento_imagen_temporal(actualizado_en);
