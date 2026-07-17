-- "generando" se usa solo entre "mapeado" y "listo", mientras corre la
-- Pasada B inicial (nada jugable existe todavia, asi que bloquear GET
-- /sesion durante esta ventana es correcto). Una vez que un documento llega
-- a "listo" el estado ya no vuelve a cambiar: "profundizar" una seccion ya
-- jugable corre en el fondo sin tocar este campo, y el resultado se mergea
-- directo a secuencia_tablero al terminar - el usuario nunca pierde acceso
-- a lo que ya podia jugar.
ALTER TABLE documentos
    DROP CONSTRAINT documentos_estado_check,
    ADD CONSTRAINT documentos_estado_check
        CHECK (estado IN ('procesando', 'mapeado', 'generando', 'listo', 'error'));
