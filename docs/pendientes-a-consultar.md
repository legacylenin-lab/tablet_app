# Pendientes a consultar

> **Qué es este documento:** una lista de puntos que surgen al construir y que conviene que Leyni revise o decida. **No modifica** `requisitos.md`, `arquitectura.md` ni `plan-de-pruebas.md`: son avisos y preguntas. Si algo de aquí termina cambiando un requisito o un contrato, se aplica en ese documento con su registro de cambios.
>
> **Prefijo `PQ-`** (pendiente a consultar). Es distinto de `PC-`, que identifica las propuestas de cambio de `requisitos.md` §8. No se mezclan.
>
> **Alcance:** la app se divide en la versión **Android** (tablet: dibujo, notas, biblioteca; v1) y la versión **Windows** (PC: ver, escribir texto, subrayar, sincronizar; v2). Cada punto indica a cuál afecta.
>
> **Quién lo mantiene:** el agente agrega filas con estado **Abierto**; solo Leyni marca **Decidido** o **Descartado**. Cuando una decisión requiere cambiar código, el agente la implementa y lo anota en la columna "Implementado".

Última actualización: 17/11/2026 (revisión de Leyni).

---

## 1. Abiertos

| ID | Afecta a | Punto | Por qué importa | Estado |
|---|---|---|---|---|
| PQ-04b | Ambas | **Compose Material 3 está fijada en una versión alfa (1.12.0-alpha03).** | Leyni pidió fijar las versiones y dejar este punto pendiente hasta que exista una versión estable compatible con Compose Multiplatform 1.12.x. El agente lo revisa al inicio de cada etapa y, cuando haya una estable, propone el cambio. | Abierto (revisar en cada etapa) |
| PQ-19 | Android | **Curva de grosor por presión de `androidx.ink`.** Con la versión estable 1.0.0 la curva del lápiz de presión (`StockBrushes.pressurePen`) es fija: solo se puede cambiar el tamaño total (hoy `size = 3`). Para ajustar el grosor mínimo y el máximo por separado hay que usar las brochas personalizadas, que solo existen en la 1.1.0-alpha, o dibujar con código propio. | Al dueño le gusta el grosor actual. Se decide si hace falta algo más cuando se construya la interfaz real. | Abierto |
| PQ-20 | Android | **Borrador y botón del lápiz.** El Lenovo Tab Pen Plus no tiene extremo borrador y su botón no llega como botón del puntero (`buttonState` siempre 0): su **doble toque** llega como una tecla de un dispositivo aparte (`Lenovo Tab Pen Plus Consumer Control`), que la actividad reenvía a `PenButton`. Un toque o mantenerlo apretado no mandan nada. El prototipo alterna lápiz/borrador con ese doble toque y con un botón en pantalla. | Depende del modelo de lápiz. Otros lápices podrían no mandar esa tecla. El botón en pantalla es el respaldo. Si se quiere depender de este gesto, conviene que Leyni lo confirme. | Abierto |
| PQ-21 | Android | **Latencia sin medir formalmente.** En el P-0 la ruta de baja latencia de `androidx.ink` está activa en la tablet (`CanvasInProgressStrokesRenderHelperV33`), pero a 90 Hz el dueño no notó mucha diferencia con la versión de solo Compose. Falta la medición con cámara lenta que pide `plan-de-pruebas.md` §7.1, con Nebo como referencia. | RNF-03 se valida con esa medición. No bloquea seguir. | Abierto |
| PQ-22 | Ambas | **`ComposeInkScreen` es provisional.** La pantalla de línea base (solo Compose) y `InkPrototypeScreen` son del prototipo y se reemplazan al construir la interfaz real de notas. | Aviso: no es una decisión. | Abierto |
| PQ-23 | Android | **Gesto "atrás" del sistema en el lienzo.** Hoy, dentro de un cuaderno, "atrás" cierra la app; solo el botón "← Cuadernos" vuelve a la lista. Arreglarlo con `BackHandler` requiere agregar `androidx.activity:activity-compose` al módulo `shared` (ya está en el catálogo y en `androidApp`). | Pedimos confirmar que se puede agregar al módulo `shared`, o resolverlo en `MainActivity` sin librería nueva. | Abierto |
| PQ-18 | Ambas | **Búsqueda de texto (RF-07, RF-15, v1.x):** usar una tabla virtual FTS de SQLite (índice invertido) sobre títulos, bloques de texto y texto reconocido de la escritura a mano. Las anotaciones no se indexan, porque son trazos. | Hay que verificar si el SQLite de Android trae FTS5 o solo FTS4. Se decide al llegar a la búsqueda; **no** se implementa en la v1. | Abierto (v1.x) |

## 2. Decididos (17/11/2026)

| ID | Afecta a | Punto | Decisión de Leyni | Implementado |
|---|---|---|---|---|
| PQ-01 | Ambas | El proyecto base trae `shared`, `androidApp` y `desktopApp`, no un único `composeApp`. | **Se ajusta el documento a la estructura real.** `arquitectura.md` §2 y ADR-09. | Documento: sí. Código: no requiere cambios. |
| PQ-02 | Ambas | `deviceId` y los identificadores son `String`, no `Uuid`. | **`EntityId`: clase de valor sobre `String`** con un UUID v4 (`arquitectura.md` §3.1, ADR-10). Se aplica ya, antes de que haya datos reales. | Sí (17/11/2026) |
| PQ-03 | Ambas | Nombre del paquete. | **Confirmado `app.cuaderno`** (`arquitectura.md` §9). | Sí |
| PQ-04 | Ambas | Versiones oficiales de las librerías. | **Fijadas** las del `libs.versions.toml` actual (`arquitectura.md` §11). Material 3 alfa queda provisional → PQ-04b. | Sí |
| PQ-05 | Android | `./gradlew build` fallaba en `:androidApp:lintDebug` por `PropertyEscape` sobre `local.properties`. | **Desactivar solo la regla `PropertyEscape`** en la configuración de lint de `androidApp`. El build debe pasar completo, sin `-x`. | Sí (17/11/2026) |
| PQ-06 | Ambas | Pantalla y archivos de ejemplo del proyecto base (`Greeting`, `Platform`…). | Aviso: se reemplazan al construir la interfaz real (etapa 3 en adelante). | — |
| PQ-10 | Ambas | Las pruebas de `core:data` están en `jvmTest`, no en `commonTest`. | **Aprobado.** Además, agregar **una prueba instrumentada en el emulador** del respaldo (exportar e inspeccionar con `VACUUM INTO`), que es lo específico de Android. `arquitectura.md` §9, `plan-de-pruebas.md` CU-05b. | Sí (17/11/2026), ejecutada en el emulador |
| PQ-11 | Ambas | `Notebook.color` pasa a entero (ARGB) y `Stroke.baseWidth` a decimal (`REAL`). | **Aprobado** (`arquitectura.md` §3.3). | Sí |
| PQ-12 | Ambas | Los repositorios usan `String` en vez de un tipo de id. | **Se resuelve con PQ-02:** todas las firmas pasan a `EntityId`. | Sí (17/11/2026, junto con PQ-02) |
| PQ-13 | Ambas | `COLLATE NOCASE` ordena mal las palabras con tilde ("Álgebra" después de "Zebra"). | **Columna `title_sort` normalizada** en `notebook` y `document`, con índice, calculada por `TitleSort.of()` en `core:model` (`arquitectura.md` §3.5, §6, ADR-12). Requiere migración `2.sqm` si el esquema 1 ya se considera publicado; si no, se agrega al esquema 1 (no hay datos reales todavía). | Sí (17/11/2026), en el esquema 1. Se agregó `NotebookRepository.observeAllByTitle()` para listar por título. |
| PQ-14 | Ambas | `BackupService` vive en `core:data` (`jvmCommonMain`) con flujos de Java, no como interfaz en `core:domain`. | **Aprobado y documentado** (`arquitectura.md` §7). En la etapa 6, al construir la pantalla, se agrega encima una interfaz delgada en `core:domain`. | Documento: sí. Interfaz: etapa 6. |
| PQ-15 | Android | `VACUUM INTO` requiere API 30, pero `minSdk` era 24. | **Subir `minSdk` a 30** (`android-minSdk` en `libs.versions.toml`). `arquitectura.md` §2, ADR-11. | Sí (17/11/2026) |
| PQ-16 | Ambas | La fecha del manifiesto va en milisegundos (`createdAtMillis`), no en texto ISO. | **Aprobado** (`arquitectura.md` §5.3). | Sí |
| PQ-17 | Ambas | Falta el último paso de la restauración (cerrar la base, intercambiar archivos, reabrir, respaldo automático previo). | Trabajo de la etapa 6, ya documentado en `arquitectura.md` §5.3. | Etapa 6 |

## 3. Recordatorios (ya previstos en los documentos)

| ID | Afecta a | Punto | Dónde está |
|---|---|---|---|
| PQ-07 | Windows (v2) | Cómo convive un respaldo restaurado con la sincronización sin resucitar elementos borrados (CS-12). Por eso los borrados suaves no se purgan en la v1. | `arquitectura.md` §5.3 |
| PQ-08 | Ambas | Los prototipos P-0 (tinta), P-1 (PDF), P-2 (exportación) y P-3 (cifrado) validan librerías antes de adoptarlas. | `plan-de-pruebas.md` |
| PQ-09 | Android | El lápiz y la latencia solo se pueden comprobar en la tablet real, no en el emulador. | `arquitectura.md` §8 |
