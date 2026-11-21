from pathlib import Path
from PIL import Image, ImageOps

origen = Path('eye.jpeg')
destino = Path('src/main/resources/img')
destino.mkdir(parents=True, exist_ok=True)

# El uso de 'with' garantiza que el archivo se cierre y la memoria se libere automáticamente
with Image.open(origen) as img:
    imagen = img.convert('RGB')

    # El archivo original es cuadrado y deja mucho margen blanco. Estas variantes
    # conservan el diseño, eliminan ese margen y hacen predecible su tamaño en UI.
    imagen.crop((130, 330, 1120, 910)).save(destino / 'eye-logo.png', optimize=True)
    
    # Ícono: sólo el ojo central, sin el texto de marca. Se completa con blanco
    # para obtener un lienzo cuadrado que se puede recortar en círculo sin deformar.
    icono = imagen.crop((350, 300, 900, 750))
    ImageOps.pad(icono, (550, 550), color='white', method=Image.Resampling.LANCZOS).save(destino / 'eye-icono.png', optimize=True)

print(f'Variantes de logo creadas exitosamente en: {destino}')