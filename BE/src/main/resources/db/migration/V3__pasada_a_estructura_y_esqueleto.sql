-- Soporte para el flujo de dos pasadas de prompt-generacion-unidades.md:
-- Pasada A mapea la estructura del documento y crea el esqueleto de cada
-- unidad (sin contenido todavia); Pasada B rellena el contenido solo de
-- las unidades que el usuario selecciono en la pantalla de profundidad.

-- Arbol de secciones (libros/capitulos/secciones) que produce la Pasada A.
CREATE TABLE secciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id    UUID NOT NULL REFERENCES documentos(id),
    padre_id        UUID REFERENCES secciones(id),
    titulo          TEXT NOT NULL,
    resumen         TEXT NOT NULL
);

CREATE INDEX idx_secciones_documento_id ON secciones(documento_id);
CREATE INDEX idx_secciones_padre_id ON secciones(padre_id);

-- Esqueleto de la Pasada A: a que seccion pertenece, tipo de contenido y
-- nivel de importancia (para calcular tiempo estimado y armar la pantalla
-- de seleccion, todo aritmetica local, sin IA). El contenido real
-- (explicaciones y preguntas) lo llena la Pasada B despues, solo para lo
-- elegido - por eso esas 4 columnas pasan a ser nullable.
ALTER TABLE unidades
    ADD COLUMN seccion_id UUID NOT NULL REFERENCES secciones(id),
    ADD COLUMN tipo_contenido TEXT NOT NULL
        CHECK (tipo_contenido IN ('declarativo', 'procedimental', 'mixto')),
    ADD COLUMN nivel_importancia TEXT NOT NULL
        CHECK (nivel_importancia IN ('esencial', 'importante', 'detalle')),
    ALTER COLUMN explicacion_corta DROP NOT NULL,
    ALTER COLUMN explicacion_alternativa DROP NOT NULL,
    ALTER COLUMN pregunta_reconocimiento DROP NOT NULL,
    ALTER COLUMN pregunta_refuerzo DROP NOT NULL;

CREATE INDEX idx_unidades_seccion_id ON unidades(seccion_id);

-- Nuevo estado intermedio: la Pasada A termino (hay estructura + esqueleto,
-- se puede mostrar la pantalla de seleccion) pero todavia no hay contenido
-- jugable. "listo" pasa a significar "la Pasada B ya corrio para al menos
-- la seleccion inicial del usuario".
ALTER TABLE documentos
    DROP CONSTRAINT documentos_estado_check,
    ADD CONSTRAINT documentos_estado_check
        CHECK (estado IN ('procesando', 'mapeado', 'listo', 'error'));
