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
| PQ-18 | Ambas | **Búsqueda de texto (RF-07, RF-15, v1.x):** usar una tabla virtual FTS de SQLite (índice invertido) sobre títulos, bloques de texto y texto reconocido de la escritura a mano. Las anotaciones no se indexan, porque son trazos. | Hay que verificar si el SQLite de Android trae FTS5 o solo FTS4. Se decide al llegar a la búsqueda; **no** se implementa en la v1. | Abierto (v1.x) |

## 2. Decididos (17/11/2026)

| ID | Afecta a | Punto | Decisión de Leyni | Implementado |
|---|---|---|---|---|
| PQ-01 | Ambas | El proyecto base trae `shared`, `androidApp` y `desktopApp`, no un único `composeApp`. | **Se ajusta el documento a la estructura real.** `arquitectura.md` §2 y ADR-09. | Documento: sí. Código: no requiere cambios. |
| PQ-02 | Ambas | `deviceId` y los identificadores son `String`, no `Uuid`. | **`EntityId`: clase de valor sobre `String`** con un UUID v4 (`arquitectura.md` §3.1, ADR-10). Se aplica ya, antes de que haya datos reales. | Pendiente (agente) |
| PQ-03 | Ambas | Nombre del paquete. | **Confirmado `app.cuaderno`** (`arquitectura.md` §9). | Sí |
| PQ-04 | Ambas | Versiones oficiales de las librerías. | **Fijadas** las del `libs.versions.toml` actual (`arquitectura.md` §11). Material 3 alfa queda provisional → PQ-04b. | Sí |
| PQ-05 | Android | `./gradlew build` fallaba en `:androidApp:lintDebug` por `PropertyEscape` sobre `local.properties`. | **Desactivar solo la regla `PropertyEscape`** en la configuración de lint de `androidApp`. El build debe pasar completo, sin `-x`. | Pendiente (agente) |
| PQ-06 | Ambas | Pantalla y archivos de ejemplo del proyecto base (`Greeting`, `Platform`…). | Aviso: se reemplazan al construir la interfaz real (etapa 3 en adelante). | — |
| PQ-10 | Ambas | Las pruebas de `core:data` están en `jvmTest`, no en `commonTest`. | **Aprobado.** Además, agregar **una prueba instrumentada en el emulador** del respaldo (exportar e inspeccionar con `VACUUM INTO`), que es lo específico de Android. `arquitectura.md` §9, `plan-de-pruebas.md` CU-05b. | Pendiente (agente) |
| PQ-11 | Ambas | `Notebook.color` pasa a entero (ARGB) y `Stroke.baseWidth` a decimal (`REAL`). | **Aprobado** (`arquitectura.md` §3.3). | Sí |
| PQ-12 | Ambas | Los repositorios usan `String` en vez de un tipo de id. | **Se resuelve con PQ-02:** todas las firmas pasan a `EntityId`. | Pendiente (agente, junto con PQ-02) |
| PQ-13 | Ambas | `COLLATE NOCASE` ordena mal las palabras con tilde ("Álgebra" después de "Zebra"). | **Columna `title_sort` normalizada** en `notebook` y `document`, con índice, calculada por `TitleSort.of()` en `core:model` (`arquitectura.md` §3.5, §6, ADR-12). Requiere migración `2.sqm` si el esquema 1 ya se considera publicado; si no, se agrega al esquema 1 (no hay datos reales todavía). | Pendiente (agente) |
| PQ-14 | Ambas | `BackupService` vive en `core:data` (`jvmCommonMain`) con flujos de Java, no como interfaz en `core:domain`. | **Aprobado y documentado** (`arquitectura.md` §7). En la etapa 6, al construir la pantalla, se agrega encima una interfaz delgada en `core:domain`. | Documento: sí. Interfaz: etapa 6. |
| PQ-15 | Android | `VACUUM INTO` requiere API 30, pero `minSdk` era 24. | **Subir `minSdk` a 30** (`android-minSdk` en `libs.versions.toml`). `arquitectura.md` §2, ADR-11. | Pendiente (agente) |
| PQ-16 | Ambas | La fecha del manifiesto va en milisegundos (`createdAtMillis`), no en texto ISO. | **Aprobado** (`arquitectura.md` §5.3). | Sí |
| PQ-17 | Ambas | Falta el último paso de la restauración (cerrar la base, intercambiar archivos, reabrir, respaldo automático previo). | Trabajo de la etapa 6, ya documentado en `arquitectura.md` §5.3. | Etapa 6 |

## 3. Recordatorios (ya previstos en los documentos)

| ID | Afecta a | Punto | Dónde está |
|---|---|---|---|
| PQ-07 | Windows (v2) | Cómo convive un respaldo restaurado con la sincronización sin resucitar elementos borrados (CS-12). Por eso los borrados suaves no se purgan en la v1. | `arquitectura.md` §5.3 |
| PQ-08 | Ambas | Los prototipos P-0 (tinta), P-1 (PDF), P-2 (exportación) y P-3 (cifrado) validan librerías antes de adoptarlas. | `plan-de-pruebas.md` |
| PQ-09 | Android | El lápiz y la latencia solo se pueden comprobar en la tablet real, no en el emulador. | `arquitectura.md` §8 |
