# Arquitectura de la app de notas y biblioteca

**Versión:** 1.1, aprobada por Leyni
**Fecha:** 17/11/2026
**Basado en:** `docs/requisitos.md` v1.1

> **Para los agentes:** este documento define **cómo** se construye la app. Los contratos de esta página (entidades, interfaces, formatos) **no se cambian sin una propuesta registrada** en `docs/requisitos.md` §8. Los detalles internos de una implementación sí se pueden ajustar libremente, siempre que respeten el contrato. Si una desviación es dudosa, anótala en `docs/pendientes-a-consultar.md`.
>
> **Para el dueño:** no hace falta entender todo esto. El agente te lo irá explicando a medida que lleguemos a cada parte.

---

## 1. Principios

1. **Los datos primero.** El modelo de datos es lo único difícil de cambiar después. Por eso se define completo desde la v1, aunque la sincronización llegue en la v2 (RNF-11).
2. **Capas con dependencias en un solo sentido:** interfaz → dominio ← datos. El dominio no conoce ni Compose ni SQL.
3. **Código común por defecto.** Solo va a código de plataforma lo que de verdad depende de Android o de Windows (lápiz, PDF, archivos, impresión, red).
4. **Contratos pequeños y probados.** Cada contrato de esta página tiene pruebas automáticas.
5. **Nada de PowerShell** (RNF-22): los scripts van en Git Bash o `cmd`, y los procesos externos se lanzan con `ProcessBuilder`.

---

## 2. Módulos de Gradle

Estructura real del proyecto (plantilla de JetBrains de Kotlin Multiplatform, más los módulos `core`):

```
/                         raíz del repositorio (rootProject "Tablet_app")
├── shared/               interfaz compartida (Compose): pantallas, ViewModels, navegación, recursos
│   ├── commonMain/
│   ├── androidMain/      lienzo con lápiz y otras piezas de interfaz solo de Android
│   └── jvmMain/          piezas de interfaz solo de escritorio
├── androidApp/           punto de entrada de Android (MainActivity), AppContainer de Android
├── desktopApp/           punto de entrada de Windows (main), ventana, AppContainer de escritorio
├── core/
│   ├── model/            entidades, EntityId, HLC, codificación de trazos, normalización de títulos (Kotlin puro)
│   ├── domain/           interfaces de repositorio, casos de uso y contratos de plataforma
│   └── data/             SQLDelight, repositorios, almacenamiento de archivos, respaldo
│       ├── commonMain/
│       ├── jvmCommonMain/  código compartido por Android y Windows que usa APIs de Java (ZIP, SHA-256, flujos)
│       ├── androidMain/    driver de SQLite de Android, PdfRenderer, PrintManager
│       └── jvmMain/        driver JDBC de SQLite, PDFBox, impresión con Java
└── (v2) core/sync/       protocolo, transporte (Ktor), emparejamiento
```

En Gradle, el destino de escritorio se llama **`jvm`** (`jvmMain`, `jvmTest`). En este documento, "escritorio" y "Windows" se refieren a él.

**Reglas de dependencia**

| Módulo | Puede depender de |
|---|---|
| `core:model` | nada (solo la biblioteca estándar de Kotlin y `kotlinx-serialization`) |
| `core:domain` | `core:model` |
| `core:data` | `core:model`, `core:domain` |
| `shared` | `core:domain`, `core:model` |
| `androidApp`, `desktopApp` | `shared`, y `core:data` **solo** para armar dependencias (`AppContainer`) |
| `core:sync` (v2) | `core:model`, `core:domain` |

**Plataforma Android:** `minSdk = 30` (Android 11), `compileSdk` y `targetSdk = 37`. El mínimo de 30 lo exige `VACUUM INTO` en el respaldo (ADR-11).

**Inyección de dependencias:** manual, con un `AppContainer` por plataforma que construye los repositorios y los entrega a los ViewModels. Es más simple de seguir para un principiante que una librería. Si crece demasiado, se evalúa Koin (con propuesta).

**Estado de la interfaz:** flujo unidireccional. Cada pantalla tiene un ViewModel que expone un `StateFlow<UiState>` y recibe eventos. Las pantallas no llaman a repositorios directamente.

---

## 3. Modelo de dominio (`core:model`)

### 3.1 Identificadores

- Todo identificador es un **`EntityId`**: `@JvmInline value class EntityId(val value: String)`, con un UUID v4 en minúsculas y con guiones.
- Se usa en lugar de `String` suelto (para no mezclar el id de una página con el de un cuaderno) y en lugar de `kotlin.uuid.Uuid` (para no depender de una API experimental). En SQL se guarda como `TEXT`.
- Se crea solo mediante `IdGenerator.newId()` o `EntityId.parse(text)`, que valida el formato.

### 3.2 Metadatos comunes (todas las entidades sincronizables)

| Campo | Tipo | Significado |
|---|---|---|
| `id` | `EntityId` | Identificador global. Nunca autoincremental. |
| `createdHlc` | `Hlc` | Momento de creación. |
| `updatedHlc` | `Hlc` | Último cambio. Decide "gana el más reciente". |
| `deviceId` | `EntityId` | Dispositivo que hizo el último cambio. |
| `authorId` | `EntityId` | Persona que hizo el último cambio. En v1 y v2 siempre es el dueño; existe para la v3. |
| `deletedHlc` | `Hlc?` | Si no es nulo, el registro está borrado (borrado suave). |

### 3.3 Entidades

| Entidad | Campos propios | Notas |
|---|---|---|
| `Notebook` (cuaderno) | `title`, `semester?`, `color: Int?` (ARGB), `sortKey` | `sortKey` es texto ordenable (fraccional) para reordenar sin reescribir los demás. |
| `Page` (página) | `notebookId`, `sortKey`, `template` (`BLANK`, `LINED`, `GRID`), `widthMm=210`, `heightMm=297` | A4 fijo (RF-08). |
| `Stroke` (trazo) | `pageId`, `tool` (`PEN`, `HIGHLIGHTER`), `colorArgb: Int`, `baseWidth: Float`, `points` (codificado, §5.1) | **Inmutable.** Borrar con el borrador = borrado suave del trazo. |
| `TextBlock` (bloque de texto) | `pageId`, `x`, `y`, `width`, `content` (§5.4), `contentHlc` | El único elemento editable en sitio. Es la unidad de conflicto real (RF-24). |
| `Document` (documento) | `title`, `fileHash` (SHA-256), `pageCount`, `lastReadPage`, `sizeBytes` | El PDF se guarda aparte, con su hash como nombre. |
| `Annotation` (anotación) | `documentId`, `pageIndex`, `kind` (`INK`, `HIGHLIGHT`, `UNDERLINE`), `colorArgb`, `geometry` (codificado, §5.1) | Inmutable, igual que el trazo. Coordenadas en puntos PDF de esa página. |
| `Device` | `id`, `name`, `isLocal` | El propio dispositivo y, desde la v2, los emparejados. |

### 3.4 Coordenadas

- **Notas:** décimas de milímetro desde la esquina superior izquierda de la página A4 (0–2100 × 0–2970). Son enteros, independientes de la pantalla.
- **Anotaciones en PDF:** décimas de punto PDF (1/720 de pulgada) desde la esquina superior izquierda de la página del PDF.
- La conversión a píxeles de pantalla solo ocurre en la interfaz.

### 3.5 Clave de orden por título (`TitleSort`)

Para que las listas ordenadas por título respeten el español sin salir de SQL (orden y paginación en la base de datos, RNF-17), cada `Notebook` y `Document` guarda una columna derivada **`title_sort`**, calculada con `TitleSort.of(title)` en `core:model`. Es Kotlin puro y da el mismo resultado en Android y en Windows.

**Reglas**
1. Pasar a minúsculas.
2. Quitar las tildes y diéresis de las vocales (á→a, é→e, ü→u, etc.), con una tabla fija de letras latinas acentuadas. No depende de `java.text`.
3. La **ñ** se conserva como letra propia: se codifica como `n` seguida del carácter `\u007F`, para que ordene después de toda palabra que empiece por "n" y antes de "o" (por ejemplo: "nube" < "ñandú" < "oso").
4. Colapsar los espacios repetidos y recortar los de los extremos.

**Ejemplo:** "Álgebra Lineal" → `algebra lineal`, que ordena antes de "Zebra".

`title_sort` se recalcula en cada creación o renombrado, dentro de la misma transacción. Es un dato derivado: no se sincroniza como campo propio, porque cada dispositivo lo recalcula.

---

## 4. Reloj lógico híbrido (HLC)

**Para qué sirve:** ordenar cambios de dos dispositivos aunque sus relojes no coincidan.

**Estructura:** `Hlc(wallMillis: Long, counter: Int, deviceId: EntityId)`.

**Orden:** primero `wallMillis`, luego `counter`, luego `deviceId` (lexicográfico). Así nunca hay empates.

**Reglas**
- **Evento local:** `wall = max(reloj_actual, último.wall)`. Si `wall == último.wall`, `counter = último.counter + 1`; si no, `counter = 0`.
- **Al recibir un HLC remoto (v2):** `wall = max(reloj_actual, último.wall, remoto.wall)`, y el contador según cuál de los tres ganó (algoritmo HLC estándar).
- Si el reloj remoto está más de 24 horas adelantado, se registra una advertencia, pero no se rechaza.

**Forma textual (para guardar y ordenar en SQL):** `wallMillis` en 15 dígitos, `-`, `counter` en hexadecimal de 4 dígitos, `-`, `deviceId`. Ejemplo: `001731772800000-0003-6f1c…`. Comparar estas cadenas como texto da el mismo orden que comparar los HLC.

**El último HLC emitido se persiste** en la base de datos, para no retroceder tras un reinicio.

---

## 5. Formatos estables

### 5.1 Codificación de trazos y anotaciones (`StrokeCodec`)

Un trazo es una lista de puntos `(x, y, presión, tiempo)`. Se guarda como BLOB:

```
byte 0       versión del formato (= 1)
varint       número de puntos N
punto 0      x, y absolutos (varint con zigzag), presión (1 byte, 0–255), t = 0
puntos 1..N  dx, dy (varint con zigzag), presión (1 byte), dt en ms (varint)
```

- Delta + zigzag + varint suelen reducir a unos 4 o 5 bytes por punto, frente a 16 sin comprimir (RNF-13).
- **El codec está versionado:** un formato nuevo usa otro número de versión y el lector soporta los anteriores.
- Pruebas obligatorias: ida y vuelta (codificar y decodificar da lo mismo), trazo vacío, un punto, 10.000 puntos y valores extremos.

### 5.2 Archivos de documentos

- Se guardan en el almacenamiento privado de la app: `documents/<sha256>.pdf`.
- Importar el mismo PDF dos veces no lo duplica (el hash coincide; CS-13).
- El archivo original del usuario nunca se modifica.

### 5.3 Formato de respaldo (RF-17)

Un archivo `.cuaderno-backup` que en realidad es un ZIP:

```
manifest.json      { "formatVersion": 1, "appVersion": "…", "createdAtMillis": 1731772800000,
                     "deviceId": "…", "schemaVersion": N, "documentCount": …, "checksums": {…} }
database.sqlite    copia consistente de la base de datos (VACUUM INTO; requiere API 30)
documents/         los PDF, por su hash
```

- La fecha va en **milisegundos desde 1970 (UTC)**, no como texto ISO, para no depender de `java.time`.

**Reglas**
- Restaurar comprueba `formatVersion` y `schemaVersion`. Si el respaldo es más nuevo que la app, se rechaza con un mensaje claro.
- Restaurar **reemplaza** los datos locales, previa confirmación, y guarda antes un respaldo automático de lo que había.
- Se verifican los checksums y se rechazan las rutas peligrosas dentro del ZIP antes de reemplazar nada.
- **Restauración en dos tiempos:** primero se verifica y se prepara todo en una carpeta aparte; luego se cierra la base, se intercambian los archivos y se reabre. El intercambio final se completa en la etapa 6.
- **Pendiente para la v2 (no bloquea la v1):** cómo convive un respaldo restaurado con la sincronización sin resucitar elementos borrados (CS-12). Por eso los borrados suaves **nunca se purgan** en la v1.

### 5.4 Contenido del texto enriquecido

- Se guarda como **HTML limitado**: `p`, `b`, `i`, `u`, `ul`, `ol`, `li`, `h1`–`h3`, `br`. Es lo que exporta e importa `compose-rich-editor`.
- Cualquier otra etiqueta se descarta al guardar.
- Una prueba debe confirmar que el contenido resiste ida y vuelta: editor → HTML → editor.

---

## 6. Base de datos (SQLDelight)

**Base de datos:** `CuadernoDatabase`, en el paquete `app.cuaderno.data.db`. **Migraciones** en archivos `.sqm` numerados (`1.sqm`, `2.sqm`…). Nunca se edita una migración ya publicada.

**Esquema (versión 1, resumido)**

```sql
CREATE TABLE notebook (
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  title_sort TEXT NOT NULL,              -- §3.5
  semester TEXT,
  color INTEGER,                         -- ARGB
  sort_key TEXT NOT NULL,
  created_hlc TEXT NOT NULL, updated_hlc TEXT NOT NULL,
  device_id TEXT NOT NULL, author_id TEXT NOT NULL,
  deleted_hlc TEXT
);
CREATE INDEX notebook_by_title ON notebook(title_sort);

CREATE TABLE page (
  id TEXT PRIMARY KEY NOT NULL,
  notebook_id TEXT NOT NULL REFERENCES notebook(id),
  sort_key TEXT NOT NULL,
  template TEXT NOT NULL,
  -- metadatos comunes (mismas 5 columnas)
  …
);
CREATE INDEX page_by_notebook ON page(notebook_id, sort_key);

CREATE TABLE stroke (
  id TEXT PRIMARY KEY NOT NULL,
  page_id TEXT NOT NULL REFERENCES page(id),
  tool TEXT NOT NULL, color INTEGER NOT NULL, base_width REAL NOT NULL,
  points BLOB NOT NULL,
  …metadatos comunes
);
CREATE INDEX stroke_by_page ON stroke(page_id);

CREATE TABLE text_block ( … page_id, x, y, width, content TEXT, content_hlc TEXT, …metadatos );
CREATE TABLE document   ( … title, title_sort TEXT NOT NULL, file_hash TEXT UNIQUE, page_count,
                          last_read_page, size_bytes, …metadatos );
CREATE INDEX document_by_title ON document(title_sort);
CREATE TABLE annotation ( … document_id, page_index, kind, color, geometry BLOB, …metadatos );
CREATE INDEX annotation_by_doc_page ON annotation(document_id, page_index);

CREATE TABLE device ( id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, is_local INTEGER NOT NULL );

-- Registro de operaciones locales (base de la sincronización v2)
CREATE TABLE change_log (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,  -- orden local; nunca sale del dispositivo
  entity TEXT NOT NULL,                   -- 'notebook', 'stroke', …
  entity_id TEXT NOT NULL,
  op TEXT NOT NULL,                       -- 'UPSERT' | 'DELETE'
  hlc TEXT NOT NULL
);

CREATE TABLE clock_state ( id INTEGER PRIMARY KEY CHECK (id = 1), last_hlc TEXT NOT NULL );
```

**Reglas**
- Toda escritura de una entidad y su fila en `change_log` ocurre **en la misma transacción**.
- Las consultas normales filtran `deleted_hlc IS NULL`.
- Las listas grandes se consultan paginadas y ordenadas en SQL (`ORDER BY title_sort` o `sort_key`). Los trazos se cargan por página (RNF-17).

**Búsqueda de texto (futuro, v1.x; ver PQ-18).** Cuando lleguen RF-07 (buscar en notas) y RF-15 (buscar en la biblioteca), se usará una tabla virtual **FTS** de SQLite, que es un índice invertido incorporado. Indexará los títulos, el texto de los bloques y el texto reconocido de la escritura a mano. Las anotaciones no se indexan, porque son trazos y no texto. Antes de adoptarla hay que verificar qué versión de FTS (FTS4 o FTS5) trae el SQLite de Android. **No se implementa en la v1.**

---

## 7. Contratos del dominio (`core:domain`)

Los repositorios exponen `Flow` para observar y funciones `suspend` para modificar. Estos son ejemplos de la forma esperada: los nombres exactos pueden afinarse sin propuesta, la semántica no. Todos los identificadores son `EntityId`.

```kotlin
interface NotebookRepository {
    fun observeAll(): Flow<List<Notebook>>
    suspend fun create(title: String): Notebook
    suspend fun rename(id: EntityId, title: String)
    suspend fun move(id: EntityId, after: EntityId?)          // recalcula sortKey
    suspend fun delete(id: EntityId)                          // borrado suave en cascada lógica
}

interface PageRepository {
    fun observeByNotebook(notebookId: EntityId): Flow<List<Page>>
    suspend fun add(notebookId: EntityId, template: PageTemplate, after: EntityId?): Page
    suspend fun duplicate(id: EntityId): Page
    suspend fun move(id: EntityId, after: EntityId?)
    suspend fun delete(id: EntityId)
}

interface InkRepository {
    fun observeStrokes(pageId: EntityId): Flow<List<Stroke>>
    suspend fun add(pageId: EntityId, stroke: NewStroke): Stroke
    suspend fun erase(ids: List<EntityId>)                    // borrado suave
    suspend fun restore(ids: List<EntityId>)                  // para "deshacer" un borrado
}

interface TextBlockRepository {
    fun observeByPage(pageId: EntityId): Flow<List<TextBlock>>
    suspend fun create(pageId: EntityId, x: Int, y: Int, width: Int): TextBlock
    suspend fun updateContent(id: EntityId, html: String)
    suspend fun moveResize(id: EntityId, x: Int, y: Int, width: Int)
    suspend fun delete(id: EntityId)
}

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>                    // ordenado por title_sort
    suspend fun import(source: PlatformFile): ImportResult    // Imported | AlreadyExists | NotPdf | Corrupt
    suspend fun setLastReadPage(id: EntityId, page: Int)
    suspend fun delete(id: EntityId)
}

interface AnnotationRepository { /* igual que InkRepository, por (documentId, pageIndex) */ }

interface HlcClock { fun now(): Hlc; fun receive(remote: Hlc): Hlc }
interface IdGenerator { fun newId(): EntityId }
```

**Respaldo.** La implementación (`BackupService`) vive en `core:data`, en `jvmCommonMain`: está escrita una sola vez para Android y Windows y trabaja con flujos de bytes de Java (exportar, inspeccionar, restaurar). Esta excepción fue aprobada (PQ-14). **Al construir la pantalla de respaldo (etapa 6) se agrega en `core:domain` una interfaz delgada** por encima, para que `shared` no dependa de `core:data`:

```kotlin
interface BackupService {
    suspend fun export(destination: PlatformFile): BackupResult
    suspend fun inspect(source: PlatformFile): BackupInfo     // versión, fecha, tamaño, antes de restaurar
    suspend fun restore(source: PlatformFile): RestoreResult
}
```

**Deshacer y rehacer (RF-02)** es estado de la pantalla, no de la base de datos: una pila de operaciones inversas (`add` ↔ `erase`/`restore`). Así deshacer no rompe la sincronización.

---

## 8. Fronteras de plataforma

Se definen como interfaces en `core:domain` y se implementan en `androidMain` y `jvmMain`. Se prefieren interfaces a `expect/actual`, porque son fáciles de sustituir por dobles en pruebas.

| Contrato | Android | Windows | Notas |
|---|---|---|---|
| `PdfEngine` (abrir, número de páginas, renderizar página a mapa de bits, texto con posiciones, buscar) | `PdfRenderer` | PDFBox | Validar en P-1. |
| `FilePicker` (elegir PDF o respaldo, elegir destino) | Storage Access Framework | `JFileChooser` o diálogo nativo | — |
| `PrintService` (imprimir un PDF generado) | `PrintManager` | PDFBox + `java.awt.print` | RF-27 |
| `PdfExporter` (notas → PDF; documento + capa → PDF) | PdfBox-Android | PDFBox | P-2 |
| `InkSurface` (composable que captura el lápiz) | `androidx.ink` / baja latencia | **No existe**: en la PC solo se renderiza | RF-03 y RF-21 |
| `AppPaths` (carpetas privadas de la app) | `context.filesDir` | `%APPDATA%\CuadernoApp` | — |

**Lápiz (RF-03):** solo `PointerType.Stylus` (y `Eraser`, si el lápiz lo reporta) produce tinta. Los toques de `Touch` van a desplazamiento y zoom. Se prueba siempre en la tablet real.

---

## 9. Convenciones

- **Paquetes:** raíz **`app.cuaderno`** (confirmado). Código por módulo: `app.cuaderno.model`, `app.cuaderno.domain`, `app.cuaderno.data` (base de datos generada en `app.cuaderno.data.db`); la interfaz, en `app.cuaderno` y subpaquetes. Los `namespace` de Android de los módulos son `app.cuaderno.core.<módulo>` y `app.cuaderno.shared`. El `applicationId` es `app.cuaderno`.
- **Idioma:** el código (clases, funciones, variables) en inglés; los comentarios, los textos de la interfaz y la documentación en español. Los textos de la interfaz van en recursos de Compose (`composeResources`), no escritos dentro del código.
- **Formato:** el estilo oficial de Kotlin.
- **Pruebas:**
  - Cada función de `core:model` tiene pruebas en `commonTest`.
  - Las pruebas de la base de datos y del respaldo (`core:data`) están en **`jvmTest`**, porque usan el driver JDBC de SQLite en memoria. La lógica que prueban es común. Excepción aprobada (PQ-10).
  - Lo que depende de Android (por ejemplo, `VACUUM INTO` en el respaldo) lleva además una **prueba instrumentada** que corre en el emulador.
  - La lógica nunca se prueba solo a mano.
- **Lint:** la regla `PropertyEscape` está **desactivada** en `androidApp`. Daba un falso positivo sobre `local.properties`, que es un archivo local que no se sube al repositorio (PQ-05). `./gradlew build` debe pasar completo, sin excluir tareas.
- **Commits:** en español, en imperativo, una idea por commit. Ejemplo: `Agrega codificación de trazos con pruebas`.
- **Versiones de librerías:** solo en `gradle/libs.versions.toml`, con versiones exactas (§11). Añadir una librería nueva requiere anotarla en §11 con su licencia.

---

## 10. Registro de decisiones de arquitectura (ADR)

| ID | Decisión | Motivo | Alternativas descartadas |
|---|---|---|---|
| ADR-01 | Kotlin + Compose Multiplatform | Flutter depende de PowerShell, bloqueado por ESET (RNF-22). Lo nativo además mejora la latencia del lápiz. | Flutter, React Native |
| ADR-02 | SQLDelight | SQL explícito, migraciones verificables y madurez en Android y JVM. | Room KMP (alternativa válida) |
| ADR-03 | Inyección de dependencias manual | Menos magia para un desarrollador que aprende. | Koin, Hilt |
| ADR-04 | Interfaces para las plataformas | Se pueden sustituir en pruebas. | `expect/actual` directo |
| ADR-05 | Trazos inmutables con borrado suave | Fusión sin conflictos y deshacer simple. | Trazos editables |
| ADR-06 | HLC en texto ordenable | Comparación correcta directamente en SQL. | Marca de tiempo simple |
| ADR-07 | Respaldo en ZIP con manifest versionado | Portable, inspeccionable y verificable. | Copia del archivo de la base de datos |
| ADR-08 | PDF original intacto, anotaciones en capa | Sincronización barata, reimportación y sin riesgo de corromper el original. | Escribir las anotaciones dentro del PDF |
| ADR-09 | Estructura `shared` + `androidApp` + `desktopApp` | Es la plantilla actual de JetBrains; reorganizar no aporta nada (PQ-01). | Módulo único `composeApp` |
| ADR-10 | `EntityId` como clase de valor sobre `String` | Seguridad de tipos sin depender de `kotlin.uuid` (experimental) (PQ-02, PQ-12). | `String` suelto, `kotlin.uuid.Uuid` |
| ADR-11 | `minSdk = 30` | `VACUUM INTO` requiere Android 11. Los dispositivos previstos (tablet con Android 16; laptops o tablets de los amigos) lo cumplen (PQ-15). | Respaldo alternativo para Android 7 a 10 |
| ADR-12 | Columna `title_sort` normalizada | Orden correcto en español sin sacar el orden ni la paginación de SQL (PQ-13). | `COLLATE NOCASE`; ordenar en Kotlin con `Collator` |

---

## 11. Librerías y versiones fijadas

Versiones exactas fijadas el **17/11/2026**, tal como están en `gradle/libs.versions.toml`. Toda librería nueva se agrega aquí con su licencia antes de usarla. Para subir una versión hay que actualizar esta tabla.

| Librería o herramienta | Versión | Licencia | Estado |
|---|---|---|---|
| Kotlin (y plugins de Compose y de serialización) | 2.4.21 | Apache 2.0 | Fijada |
| Android Gradle Plugin | 9.1.1 | Apache 2.0 | Fijada |
| Compose Multiplatform | 1.12.2 | Apache 2.0 | Fijada |
| **Compose Material 3** | **1.12.0-alpha03** | Apache 2.0 | **Fijada, pero provisional (versión alfa).** Pendiente PQ-04: pasar a la primera versión estable compatible con Compose Multiplatform 1.12.x cuando exista. Revisar al inicio de cada etapa. |
| AndroidX Lifecycle (ViewModel y runtime de Compose, KMP) | 2.11.0 | Apache 2.0 | Fijada |
| AndroidX Activity Compose | 1.13.0 | Apache 2.0 | Fijada |
| AndroidX Core KTX / AppCompat | 1.19.1 / 1.8.0 | Apache 2.0 | Fijada |
| kotlinx-coroutines | 1.11.0 | Apache 2.0 | Fijada |
| kotlinx-serialization-json | 1.13.0 | Apache 2.0 | Fijada |
| SQLDelight | 2.4.1 | Apache 2.0 | Fijada |
| JUnit / AndroidX Test (runner, ext, espresso) | 4.13.2 / 1.7.0, 1.3.0, 3.7.0 | EPL 1.0 / Apache 2.0 | Fijada (solo pruebas) |
| Navigation Compose (KMP) | — | Apache 2.0 | Aprobada; se fija al adoptarla |
| compose-rich-editor | 1.2.1 | Apache 2.0 | **Adoptada** (18/11/2026): compila en Android y PC, resiste ida y vuelta por HTML y se probó en la tablet. Los títulos se normalizan a `h1`–`h3` con `RichTextHtml` |
| androidx.ink | — | Apache 2.0 | Aprobada. **Adoptada** en la versión estable 1.0.0 tras el prototipo P-0 (17/11/2026). Trae como dependencias transitivas `androidx.input:input-motionprediction` 1.0.0 (predicción del trazo) y `androidx.graphics:graphics-core` 1.0.4 (dibujo en el búfer frontal); `InProgressStrokes` las usa por dentro (PQ-21) |
| Apache PDFBox | 3.0.8 | Apache 2.0 | **En validación (P-1, 18/11/2026):** fijada en `core:data` (`jvmMain`) para el motor de PDF de Windows (`PdfBoxEngine`). Se adopta o se descarta al terminar el P-1 |
| PdfBox-Android | — | Apache 2.0 | A validar en P-2 |
| Ktor, JmDNS, ZXing, CameraX, ML Kit | — | Apache 2.0 | Solo cuando llegue su versión |

**Política de versiones:** se usan versiones estables siempre que existan. Una versión alfa o beta solo se acepta si no hay alternativa estable compatible, y queda marcada como provisional en esta tabla y en `docs/pendientes-a-consultar.md`.

---

## 12. Registro de cambios

| Versión | Fecha | Cambio |
|---|---|---|
| 1.0 | 16/11/2026 | Primera versión aprobada. |
| 1.1 | 17/11/2026 | Decisiones de Leyni sobre `pendientes-a-consultar.md`: estructura real de módulos (§2, ADR-09); `EntityId` (§3.1, ADR-10); `minSdk = 30` (§2, ADR-11); paquete `app.cuaderno` confirmado (§9); versiones fijadas con Material 3 alfa como provisional (§11); tipos de `color` y `baseWidth` confirmados (§3.3); pruebas de base de datos en `jvmTest` más una prueba en el emulador (§9); respaldo en `core:data` con interfaz de dominio en la etapa 6 (§7); `title_sort` (§3.5, §6, ADR-12); fecha del respaldo en milisegundos (§5.3); `PropertyEscape` desactivada (§9); búsqueda futura con FTS (§6). |
