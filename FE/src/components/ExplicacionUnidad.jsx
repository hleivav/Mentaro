import { ImagenDocumento } from './ImagenDocumento'

// Clip dibujado + esquina doblada visible (ver identidad visual v2,
// ronda de ajuste: la tarjeta de explicacion necesitaba personalidad
// propia, no solo el indice). El SVG es deliberadamente simple - dos
// curvas superpuestas, no un icono de libreria.
//
// imagenesAsociadas: las imagenes del documento que corresponden a ESTA
// unidad (ver Unidad.imagenesAsociadas / ElementoSesionDTO) - se muestran
// aca mismo, no solo en el boton "ver todas las imagenes del documento"
// (problema real detectado probando: la imagen de una situacion de
// transito puntual tenia que verse junto a esa explicacion puntual).
export function ExplicacionUnidad({ titulo, explicacion, documentoId, imagenesAsociadas }) {
  return (
    <div className="explicacion-unidad">
      <svg className="explicacion-unidad__clip" viewBox="0 0 26 46" aria-hidden="true">
        <path
          d="M13,4 C21,4 24,10 24,17 L24,33 C24,40 19,44 13,44 C7,44 3,40 3,34
             L3,14 C3,10 6,7 9,7 C12,7 15,10 15,14 L15,31"
          fill="none"
        />
      </svg>
      <h2>{titulo}</h2>
      <p>{explicacion}</p>
      {imagenesAsociadas?.length > 0 && (
        <div className="explicacion-unidad__imagenes">
          {imagenesAsociadas.map((imagenId) => (
            <ImagenDocumento key={imagenId} documentoId={documentoId} imagenId={imagenId} alt={titulo} />
          ))}
        </div>
      )}
    </div>
  )
}
