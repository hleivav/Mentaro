-- La secuencia inicial de un documento se genera con huecos (paso de 100:
-- 100, 200, 300, ...) en vez de posiciones consecutivas. Asi, "programar un
-- refuerzo" es un INSERT en una posicion intermedia dentro de un hueco
-- existente (ej. 150 entre 100 y 200), sin necesidad de renumerar ninguna
-- fila por delante del puntero. Ver SesionService.programarRefuerzo.
COMMENT ON COLUMN secuencia_tablero.posicion IS
    'Se genera con huecos (paso de 100) para poder insertar refuerzos en posiciones intermedias sin renumerar filas existentes.';
