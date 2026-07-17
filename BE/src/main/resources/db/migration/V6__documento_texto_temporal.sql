-- Tabla separada (no columna en documentos) para el texto fuente extraido,
-- de vida transitoria: se necesita en Pasada A y en cada Pasada B/profundizar
-- posterior, pero nunca debe persistir indefinidamente (ver principio de
-- copyright en proyecto-mentaro-vision). Se limpia via job programado
-- (barrido de filas con actualizado_en > 48h), no al llegar a 'listo',
-- porque profundizar puede volver a necesitar el texto mucho despues de
-- la primera generacion.
CREATE TABLE documento_texto_temporal (
    documento_id    UUID PRIMARY KEY REFERENCES documentos(id) ON DELETE CASCADE,
    texto           TEXT NOT NULL,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_texto_temporal_actualizado_en ON documento_texto_temporal(actualizado_en);
