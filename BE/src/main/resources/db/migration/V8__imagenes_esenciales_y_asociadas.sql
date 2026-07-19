-- es_esencial: la imagen es imprescindible para entender/responder el
-- concepto (ej. el diagrama de una situacion de transito), no solo
-- ilustrativa. Se determina en la misma llamada a Anthropic que ya
-- describe la imagen (ver DescriptorImagenesPdf), no una pasada aparte.
ALTER TABLE documento_imagen_temporal ADD COLUMN es_esencial BOOLEAN NOT NULL DEFAULT false;

-- imagenes_asociadas: que imagenes del documento corresponden a esta
-- unidad especifica (la Pasada A las detecta por los marcadores
-- "[Descripcion de imagen #<uuid>: ...]" en el texto fuente que le
-- toca). Mismo patron que depende_de (array de uuid, no tabla aparte -
-- son pocas por unidad y nunca se consultan al reves).
ALTER TABLE unidades ADD COLUMN imagenes_asociadas UUID[] NOT NULL DEFAULT '{}';
