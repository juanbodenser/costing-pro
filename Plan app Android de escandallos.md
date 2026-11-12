# Plan: app Android de escandallos

Nov 12, 2026 · @Juan

## Objetivo y alcance

App Android personal para calcular el escandallo de cualquier producto gastronómico: un plato de restaurante, un lote de galletas, un fondo o una salsa. El usuario monta la receta con ingredientes de un catálogo, ve el coste al momento, fija el precio de venta y guarda el resultado con foto.

Decisiones ya tomadas:

- **Catálogo de ingredientes.** Cada ingrediente se guarda una vez con su precio de compra. La receta solo indica ingrediente, cantidad y merma. Si cambia un precio, se recalculan todas las recetas que lo usan.
- **Coste por unidad y por lote.** Cada receta declara cuántas raciones o unidades produce. Coste, precio y margen se muestran por unidad y por lote.
- **Subrecetas.** Una receta (fondo, salsa, masa) puede usarse como ingrediente de otra.
- **Precio de venta con tres entradas enlazadas:** precio, % de incremento sobre coste o % de food cost objetivo. Se escribe una y se calculan las otras dos.
- **IVA y costes extra** (envase, mano de obra), **alérgenos** por ingrediente, **exportación a PDF** y **copia de seguridad**.
- **Datos locales** en el móvil, un solo usuario, sin cuenta ni servidor.

## Stack y arquitectura

El mismo stack que Station Control: Kotlin, Jetpack Compose y Room, todo nativo y sin backend.

| Pieza | Elección |
| --- | --- |
| Lenguaje y UI | Kotlin + Jetpack Compose (Material 3) |
| Base de datos | Room (SQLite local) |
| Navegación | Navigation Compose |
| Estado | ViewModel + StateFlow; Room expone Flow |
| Inyección de dependencias | Manual (un AppContainer); sin Hilt |
| Imágenes | Photo Picker y cámara del sistema; Coil para mostrarlas |
| PDF | android.graphics.pdf.PdfDocument (sin librerías externas) |
| Copia de seguridad | Archivo .zip con JSON (kotlinx.serialization) y fotos, vía Storage Access Framework |
| SDK | minSdk 24, target el más reciente estable |

Estructura de paquetes:

- `data`: entidades, DAOs, base de datos y repositorios.
- `domain`: `CostCalculator` y modelos de resultado. Kotlin puro, sin dependencias de Android, con tests unitarios.
- `ui`: una carpeta por pantalla (pantalla + ViewModel) y el tema.
- `export`: generación de PDF y copia de seguridad.

Reglas técnicas:

- Todo el cálculo vive en `CostCalculator`. Las pantallas no hacen cuentas.
- Los cálculos usan `BigDecimal`. Solo se redondea a 2 decimales al mostrar.
- Las fotos se copian al almacenamiento interno de la app y en la base de datos se guarda solo la ruta.
- Los campos numéricos aceptan coma y punto como separador decimal.

## Modelo de datos

Cuatro tablas en Room. El esquema completo se crea desde la fase 2, aunque algunas columnas no se usen hasta fases posteriores, para evitar migraciones.

**Ingredient** (catálogo)

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | Long | Clave primaria |
| name | String | Único, sin distinguir mayúsculas |
| purchasePrice | Double | Precio del formato de compra, sin IVA |
| purchaseQuantity | Double | Cantidad del formato (ej. 25) |
| purchaseUnit | Enum | KG, G, L, ML, UD |
| defaultWastePct | Double | Merma habitual; se propone al añadirlo a una receta |
| allergens | Int | Máscara de bits con los 14 alérgenos de declaración obligatoria en la UE |
| updatedAt | Long | Fecha del último cambio de precio |

**Recipe** (escandallo)

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | Long | Clave primaria |
| name | String | Título |
| yieldQuantity | Double | Cuánto produce el lote (ej. 40) |
| yieldUnit | Enum | RACION, UD, KG, G, L, ML |
| pricingMode | Enum | PRICE, MARKUP o FOOD\_COST: qué dato fijó el usuario |
| pricingValue | Double | El valor de ese dato (precio neto por unidad, o el porcentaje) |
| vatPct | Double | IVA de venta; por defecto el de Ajustes |
| photoPath | String? | Ruta de la foto en almacenamiento interno |
| notes | String? | Notas libres |
| createdAt, updatedAt | Long | Fechas |

**RecipeLine** (fila del escandallo)

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | Long | Clave primaria |
| recipeId | Long | Receta a la que pertenece; borrado en cascada |
| position | Int | Orden de la fila |
| ingredientId | Long? | Ingrediente del catálogo |
| subRecipeId | Long? | Otra receta usada como ingrediente |
| quantity | Double | Cantidad neta que entra en la receta |
| unit | Enum | Misma magnitud que el ingrediente o que el rendimiento de la subreceta |
| wastePct | Double | Merma de esta fila, de 0 a 99 |

**ExtraCost** (coste que no es ingrediente)

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | Long | Clave primaria |
| recipeId | Long | Borrado en cascada |
| name | String | Ej. "Bolsa", "Mano de obra" |
| amount | Double | Importe sin IVA |
| per | Enum | BATCH (una vez por lote) o UNIT (por cada unidad) |

Reglas de integridad:

- Cada RecipeLine tiene exactamente uno de los dos: `ingredientId` o `subRecipeId`.
- Una receta no puede contenerse a sí misma, ni directa ni indirectamente. Se comprueba al añadir una subreceta.
- No se borra un ingrediente o una receta que esté en uso en otra receta: se avisa y se listan las recetas afectadas.
- Solo se convierte dentro de la misma magnitud: g y kg, ml y l, o unidades. No hay conversión de peso a volumen.
- `pricingMode` decide qué pasa cuando suben los costes: con PRICE el precio se mantiene y cambia el margen; con MARKUP o FOOD\_COST el precio se recalcula.

## Fórmulas de cálculo

La cantidad de cada fila es la cantidad neta que entra en la receta, y la merma encarece esa fila dividiendo entre (1 − merma). Multiplicar por (1 + merma) es un error frecuente que deja el coste corto.

Ejemplo de merma: 200 g netos de langostino a 18 €/kg con 40 % de merma cuestan 0,2 × 18 ÷ 0,6 = 6,00 €. Con la fórmula errónea saldrían 5,04 €.

| Dato | Cálculo |
| --- | --- |
| Precio unitario del ingrediente | purchasePrice ÷ purchaseQuantity, pasado a la unidad base (kg, l o ud) |
| Coste de una fila de ingrediente | cantidad × precio unitario ÷ (1 − merma ÷ 100) |
| Coste unitario de una subreceta | coste del lote de la subreceta ÷ su rendimiento |
| Coste de una fila de subreceta | cantidad × coste unitario de la subreceta ÷ (1 − merma ÷ 100) |
| Coste de materia prima del lote | suma de todas las filas |
| Extras del lote | suma de extras BATCH + suma de extras UNIT × rendimiento |
| Coste del lote | materia prima + extras |
| Coste por unidad | coste del lote ÷ rendimiento |
| Precio neto desde incremento | coste por unidad × (1 + incremento ÷ 100) |
| Precio neto desde food cost objetivo | coste por unidad ÷ (food cost ÷ 100) |
| Beneficio por unidad | precio neto − coste por unidad |
| Margen % | beneficio ÷ precio neto × 100 |
| Incremento % | beneficio ÷ coste por unidad × 100 |
| Food cost % | coste por unidad ÷ precio neto × 100 |
| PVP con IVA | precio neto × (1 + IVA ÷ 100) |
| Precio neto desde PVP con IVA | PVP ÷ (1 + IVA ÷ 100) |

Convenciones:

- Los precios de compra y los extras van sin IVA. El margen se calcula siempre sobre el precio neto.
- El coste por unidad incluye los extras. La pantalla muestra aparte cuánto es materia prima y cuánto son extras.
- El IVA por defecto es 10 % y se cambia en Ajustes o en cada receta.
- Si el rendimiento es 0 o falta, no se muestra coste por unidad ni margen.

### Caso de prueba

Galletas de chocolate, lote de 40 unidades. Estos números sirven como test unitario de `CostCalculator`.

| Fila | Cantidad | Precio | Merma | Coste |
| --- | --- | --- | --- | --- |
| Harina | 500 g | 0,90 €/kg | 0 % | 0,45 € |
| Mantequilla | 250 g | 8,00 €/kg | 0 % | 2,00 € |
| Azúcar | 200 g | 1,20 €/kg | 0 % | 0,24 € |
| Chocolate | 200 g | 10,00 €/kg | 0 % | 2,00 € |
| Huevos | 2 ud | 0,25 €/ud | 0 % | 0,50 € |
| Extra: bolsa (por unidad) | 40 ud | 0,05 €/ud |  | 2,00 € |

Resultado esperado: materia prima 5,19 €, coste del lote 7,19 €, coste por unidad 0,17975 € (se muestra 0,18 €). Con precio neto de 1,00 €: beneficio 0,82 €, margen 82,0 %, food cost 18,0 %, incremento 456,3 %, PVP con IVA al 10 % de 1,10 €.

## Pantallas

Cinco pantallas, con una barra inferior de tres pestañas: Recetas, Ingredientes y Ajustes.

### 1. Recetas (inicio)

- Lista de escandallos guardados: miniatura de la foto, nombre, coste por unidad, precio y margen %.
- Buscador por nombre.
- Botón flotante para crear un escandallo nuevo.
- Al pulsar una receta se abre su detalle.

### 2. Editor de escandallo

De arriba abajo, en una sola pantalla con scroll:

1. **Título.**
2. **Rendimiento:** cantidad y unidad (ej. 40 ud, 10 raciones, 3 l).
3. **Filas de ingredientes**, una debajo de otra. Cada fila tiene:
   - Ingrediente: campo con autocompletado sobre el catálogo. Las subrecetas aparecen en la misma búsqueda, marcadas como receta. Si el nombre no existe, se ofrece "Crear ingrediente" sin salir de la pantalla.
   - Cantidad y unidad.
   - Merma %, precargada con la merma habitual del ingrediente.
   - Coste de la fila, calculado y de solo lectura. Debajo, en pequeño, el precio unitario del catálogo.
4. **Botón "Añadir ingrediente".** Las filas se borran deslizando.
5. **Costes extra:** filas con nombre, importe y selector "por lote / por unidad".
6. **Totales en vivo:** coste del lote y coste por unidad, con el reparto entre materia prima y extras.
7. **Precio de venta:** tres campos enlazados (precio neto, incremento %, food cost %). Al escribir en uno se recalculan los otros dos y ese pasa a ser el `pricingMode`. Al lado, el IVA y el PVP con IVA, también editable.
8. **Resultado:** beneficio en euros y margen en %, por unidad y por lote.
9. **Guardar.**

Todo se recalcula mientras se escribe, sin botón de calcular. Guardar exige título y al menos una fila válida.

### 3. Detalle de receta

- Foto arriba. Si no hay, un botón "Añadir foto" con dos opciones: cámara o galería. La foto se puede cambiar o quitar.
- Resumen: coste por unidad, precio neto, PVP con IVA, beneficio y margen %.
- Desglose de filas con el coste de cada una y su peso en % sobre el coste total, ordenable de mayor a menor.
- Alérgenos de la receta: la unión de los de sus ingredientes y subrecetas.
- Acciones: editar, duplicar, exportar a PDF y borrar (con confirmación).

### 4. Ingredientes (catálogo)

- Lista con nombre y precio unitario, con buscador.
- Ficha de ingrediente: nombre, precio de compra, cantidad y unidad del formato, merma habitual y alérgenos (14 casillas).
- En la ficha, "Usado en N recetas" con la lista.

### 5. Ajustes

- IVA por defecto.
- Exportar copia de seguridad e importar copia. Importar sustituye todos los datos y pide confirmación.

## Diseño visual

Costing Pro se usa sentado y con calma, así que el diseño es claro, espacioso y con color: una herramienta de despacho, no de servicio. Tema claro por defecto y tema oscuro siguiendo el sistema.

| Papel | Color | Hex | Dónde se usa |
| --- | --- | --- | --- |
| Primario | Verde petróleo | #1F5F5B | Barra superior, botones principales, pestaña activa |
| Secundario | Ámbar tostado | #C98A2B | Precio de venta y PVP, botón flotante |
| Terciario | Ciruela | #6B4A6E | Subrecetas y etiquetas de categoría |
| Fondo | Marfil cálido | #FAF7F2 | Fondo de todas las pantallas |
| Superficie | Blanco | #FFFFFF | Tarjetas y campos |
| Texto | Tinta | #1E2A2E | Texto y cifras de coste |
| Margen bueno | Verde | #2E7D5B | Food cost hasta 30 % |
| Margen justo | Ocre | #B7791F | Food cost entre 30 % y 35 % |
| Margen bajo | Rojo teja | #B5483A | Food cost por encima de 35 % |

Reglas de uso:

- **Cada color significa una cosa.** El coste va en tinta, el precio en ámbar y el margen en su color de estado. Los mismos significados en el editor, la lista, el detalle y el PDF.
- **Equilibrio.** El marfil y el blanco ocupan la mayor parte de la pantalla; el verde petróleo marca la estructura; ámbar y ciruela son acentos y nunca rellenan áreas grandes.
- **El color no va solo.** El estado del margen lleva también un icono o una palabra ("Bueno", "Justo", "Bajo").
- **Tipografía.** Títulos en una serif elegante (Fraunces) y el resto en una sans limpia (Inter), con cifras tabulares para que los importes se alineen en columna. Las dos fuentes se incluyen como recursos de la app.
- **Espacio y forma.** Rejilla de 8 dp, márgenes amplios, tarjetas con esquinas de 12 dp y sombra suave. Los campos tienen tamaño normal de Material 3, no gigante.
- **Bloque de resultado.** Coste, precio, beneficio y margen van en una tarjeta destacada al final del editor y arriba en el detalle, con el margen como cifra más grande.

Notas técnicas:

- Material 3 con esquema de color propio definido en `ui/theme`; el color dinámico de Android queda desactivado.
- El tema oscuro usa la misma paleta sobre un fondo gris azulado muy oscuro, con los tonos aclarados.
- Todos los pares de texto y fondo deben cumplir contraste AA; si un hex no llega, se ajusta el tono sin cambiar el papel.
- Los umbrales de 30 % y 35 % son constantes en un solo sitio del código.

## Fases de desarrollo

Nueve fases en orden. Al terminar la fase 5 la app ya hace lo básico: crear un escandallo, guardarlo, verlo y ponerle foto. Cada fase acaba con la app compilando y su criterio cumplido.

1. **Proyecto y motor de cálculo.** Proyecto Compose vacío, estructura de paquetes y `CostCalculator` con sus tests.
   - Hecho cuando: los tests reproducen el caso de las galletas y el ejemplo de merma del langostino.
2. **Base de datos.** Entidades, DAOs, base de datos Room y repositorios, con el esquema completo. Datos de ejemplo al primer arranque.
   - Hecho cuando: un test de instrumentación guarda y lee una receta con filas y extras.
3. **Catálogo de ingredientes.** Lista, buscador, alta, edición y borrado, aún sin alérgenos.
   - Hecho cuando: se crea "Harina, 25 kg a 22,50 €" y la lista muestra 0,90 €/kg.
4. **Editor de escandallo.** Título, rendimiento, filas con autocompletado, merma, totales en vivo, los tres campos de precio enlazados y Guardar.
   - Hecho cuando: se monta la receta de galletas sin extras y la pantalla da 5,19 € de lote y 0,13 € por unidad.
5. **Lista, detalle y foto.** Pantalla de inicio, detalle con desglose, añadir foto desde cámara o galería, editar, duplicar y borrar.
   - Hecho cuando: la foto sigue visible tras cerrar y reabrir la app.
6. **Subrecetas.** Las recetas aparecen en el autocompletado, con comprobación de ciclos y recalculado en cascada.
   - Hecho cuando: al subir el precio de un ingrediente de una salsa, cambia el coste del plato que la usa.
7. **Costes extra e IVA.** Sección de extras en el editor, IVA por receta, PVP con IVA editable y pantalla de Ajustes con el IVA por defecto.
   - Hecho cuando: el caso de las galletas completo da 0,18 € por unidad y 1,10 € de PVP.
8. **Alérgenos.** Casillas en la ficha del ingrediente y alérgenos heredados en el detalle de la receta, también a través de subrecetas.
   - Hecho cuando: una receta con harina y huevo muestra gluten y huevos.
9. **PDF y copia de seguridad.** PDF de una página con foto, tabla de filas, totales, precio, margen y alérgenos. Exportar e importar el .zip desde Ajustes.
   - Hecho cuando: se exporta, se borran los datos de la app, se importa y todo vuelve, fotos incluidas.

## Fuera de alcance e ideas futuras

Esta versión no incluye inventario, pedidos a proveedores, lectura de facturas, cuentas de usuario ni sincronización en la nube.

Ideas para más adelante, vistas en otras apps de escandallos:

- Historial de precios por ingrediente, con gráfico ([KitchenCost](https://apps.apple.com/us/app/id6756592070)).
- Escalado: calcular cantidades y coste para N unidades ([Recipeculator](https://mwm.ai/apps/recipeculator/6767253434)).
- Gráfico circular del reparto del coste por ingrediente.
- Categorías de ingredientes y de recetas.
- Conversión entre peso y volumen con densidades.

## Instrucciones para Claude Code

Trabaja una fase cada vez, en el orden de este plan, y no empieces la siguiente hasta cumplir el criterio de la actual.

- Un commit por fase, con los tests pasando.
- No añadas dependencias fuera de las de la tabla de stack sin preguntar.
- No cambies el modelo de datos ni las fórmulas sin preguntar. Si algo del plan resulta ambiguo, pregunta antes de decidir.
- Código y nombres en inglés; todos los textos de la interfaz en español, en `strings.xml`.
- Importes con formato español: coma decimal y símbolo € detrás.
- Interfaz clara y espaciosa, pensada para trabajar con calma; sigue la sección Diseño visual.
- Al acabar cada fase, resume qué se ha hecho y cómo probarlo en el emulador.

Decidido:

- Nombre de la app: Costing Pro. Paquete: com.jboden.costingpro.
- Tema visual: el de la sección Diseño visual, distinto del de Station Control.
