-- Rastrea el resultado de la Pasada B por unidad, para poder distinguir
-- contenido validado de contenido persistido pese a fallar la validacion
-- (unidades esenciales, para no dejar un hueco en algo indispensable) o
-- descartado (importante/detalle, bajo costo de excluir del juego). Antes
-- esto solo quedaba en un log; ahora es un campo consultable.
--   generada            -> paso la validacion post-generacion (sin IA)
--   fallida_persistida  -> unidad esencial, se guardo igual pese a fallar
--                          la validacion incluso escalada a un modelo mejor
--   fallida_excluida    -> unidad importante/detalle, fallo la validacion
--                          incluso escalada; no se guardo contenido, no
--                          entra al juego
-- NULL = esqueleto de la Pasada A, la Pasada B todavia no corrio para ella.
ALTER TABLE unidades
    ADD COLUMN estado_generacion TEXT
        CHECK (estado_generacion IN ('generada', 'fallida_persistida', 'fallida_excluida'));

CREATE INDEX idx_unidades_estado_generacion ON unidades(estado_generacion)
    WHERE estado_generacion IS NOT NULL;
