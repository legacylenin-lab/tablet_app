# Arquitectura de la app de notas y biblioteca

**Versión:** 1.0, aprobada por Leyni
**Fecha:** 16/11/2026
**Basado en:** `docs/requisitos.md` v1.1

> **Para los agentes:** este documento define **cómo** se construye la app. Los contratos de esta página (entidades, interfaces, formatos) **no se cambian sin una propuesta registrada** en `docs/requisitos.md` §8. Los detalles internos de una implementación sí se pueden ajustar libremente, siempre que respeten el contrato.
>
> **Para el dueño:** no hace falta entender todo esto. El agente te lo irá explicando a medida que lleguemos a cada parte.

---

## 1. Principios

1. **Los datos primero.** El modelo de datos es lo único difícil de cambiar después. Por eso se define completo desde la v1, aunque la sincronización llegue en la v2 (RNF-11).
2. **Capas con dependencias en un solo sentido:** interfaz → dominio ← datos. El dominio no conoce ni Compose ni SQL.
3. **Código común por defecto.** Solo va a código de plataforma lo que de verdad depende de Android o de Windows (lápiz, PDF, archivos, impresión, red).
4. **Contratos pequeños y probados.** Cada interfaz de esta página tiene pruebas unitarias en `commonTest`.
5. **Nada de PowerShell** (RNF-22): scripts en `cmd` o Git Bash, procesos externos con `ProcessBuilder`.

---

## 2. Módulos de Gradle

```
/                         raíz del repositorio
├── composeApp/           interfaz (Compose), navegación, ViewModels, puntos de entrada
│   ├── commonMain/       pantallas compartidas
│   ├── androidMain/      MainActivity, lienzo con lápiz, permisos
│   └── desktopMain/      main() de Windows, ventana
├── core/
│   ├── model/            entidades, HLC, ids, codificación de trazos (Kotlin puro, sin dependencias de plataforma)
│   ├── domain/           interfaces de repositorio, casos de uso, contratos de plataforma
│   └── data/             SQLDelight, implementación de repositorios, almacenamiento de archivos, respaldo
│       ├── commonMain/
│       ├── androidMain/  driver de SQLite de Android, PdfRenderer, PrintManager
│       └── desktopMain/  driver JDBC de SQLite, PDFBox, impresión con Java
└── (v2) core/sync/       protocolo, transporte (Ktor), emparejamiento
```

**Reglas de dependencia**

| Módulo | Puede depender de |
|---|---|
| `core:model` | nada (solo la biblioteca estándar de Kotlin y `kotlinx-serialization`) |
| `core:domain` | `core:model` |
| `core:data` | `core:model`, `core:domain` |
| `composeApp` | `core:domain`, `core:model`, y `core:data` **solo** para armar dependencias (`AppContainer`) |
| `core:sync` (v2) | `core:model`, `core:domain` |

**Inyección de dependencias:** manual, con un `AppContainer` por plataforma que construye los repositorios y los entrega a los ViewModels. Es más simple de seguir para un principiante que una librería. Si crece demasiado, se evalúa Koin (con propuesta).

**Estado de la interfaz:** flujo unidireccional. Cada pantalla tiene un ViewModel que expone un `StateFlow<UiState>` y recibe eventos. Las pantallas no llaman a repositorios directamente.

---

## 3. Modelo de dominio (`core:model`)

### 3.1 Metadatos comunes (todas las entidades sincronizables)

| Campo | Tipo | Significado |
|---|---|---|
| `id` | `Uuid` (texto) | Identificador global. Nunca autoincremental. |
| `createdHlc` | `Hlc` | Momento de creación. |
| `updatedHlc` | `Hlc` | Último cambio. Decide "gana el más reciente". |
| `deviceId` | `Uuid` | Dispositivo que hizo el último cambio. |
| `authorId` | `Uuid` | Persona que hizo el último cambio. En v1 y v2 siempre es el dueño; existe para la v3. |
| `deletedHlc` | `Hlc?` | Si no es nulo, el registro está borrado (borrado suave). |

### 3.2 Entidades

| Entidad | Campos propios | Notas |
|---|---|---|
| `Notebook` (cuaderno) | `title`, `semester?`, `color?`, `sortKey` | `sortKey` es texto ordenable (fraccional) para reordenar sin reescribir los demás. |
| `Page` (página) | `notebookId`, `sortKey`, `template` (`BLANK`, `LINED`, `GRID`), `widthMm=210`, `heightMm=297` | A4 fijo (RF-08). |
| `Stroke` (trazo) | `pageId`, `tool` (`PEN`, `HIGHLIGHTER`), `colorArgb`, `baseWidth`, `points` (codificado, §5.1) | **Inmutable.** Borrar con el borrador = borrado suave del trazo. |
| `TextBlock` (bloque de texto) | `pageId`, `x`, `y`, `width`, `content` (§5.4), `contentHlc` | El único elemento editable en sitio. Es la unidad de conflicto real (RF-24). |
| `Document` (documento) | `title`, `fileHash` (SHA-256), `pageCount`, `lastReadPage`, `sizeBytes` | El PDF se guarda aparte, con su hash como nombre. |
| `Annotation` (anotación) | `documentId`, `pageIndex`, `kind` (`INK`, `HIGHLIGHT`, `UNDERLINE`), `colorArgb`, `geometry` (codificado, §5.1) | Inmutable, igual que el trazo. Coordenadas en puntos PDF de esa página. |
| `Device` | `id`, `name`, `isLocal` | El propio dispositivo y, desde la v2, los emparejados. |

### 3.3 Coordenadas

- **Notas:** décimas de milímetro desde la esquina superior izquierda de la página A4 (0–2100 × 0–2970). Son enteros, independientes de la pantalla.
- **Anotaciones en PDF:** décimas de punto PDF (1/720 de pulgada) desde la esquina superior izquierda de la página del PDF.
- La conversión a píxeles de pantalla solo ocurre en la interfaz.

---

## 4. Reloj lógico híbrido (HLC)

**Para qué sirve:** ordenar cambios de dos dispositivos aunque sus relojes no coincidan.

**Estructura:** `Hlc(wallMillis: Long, counter: Int, deviceId: Uuid)`.

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
manifest.json      { "formatVersion": 1, "appVersion": "…", "createdAt": "…",
                     "deviceId": "…", "schemaVersion": N, "documentCount": …, "checksums": {…} }
database.sqlite    copia consistente de la base de datos (VACUUM INTO)
documents/         los PDF, por su hash
```

**Reglas**
- Restaurar comprueba `formatVersion` y `schemaVersion`. Si el respaldo es más nuevo que la app, se rechaza con un mensaje claro.
- Restaurar **reemplaza** los datos locales, previa confirmación, y guarda antes un respaldo automático de lo que había.
- Se verifican los checksums antes de reemplazar nada.
- **Pendiente para la v2 (no bloquea la v1):** cómo convive un respaldo restaurado con la sincronización sin resucitar elementos borrados (CS-12). Por eso los borrados suaves **nunca se purgan** en la v1.

### 5.4 Contenido del texto enriquecido

- Se guarda como **HTML limitado**: `p`, `b`, `i`, `u`, `ul`, `ol`, `li`, `h1`–`h3`, `br`. Es lo que exporta e importa `compose-rich-editor`.
- Cualquier otra etiqueta se descarta al guardar.
- Una prueba debe confirmar que el contenido resiste ida y vuelta: editor → HTML → editor.

---

## 6. Base de datos (SQLDelight)

**Ubicación:** `core/data/src/commonMain/sqldelight/…`. **Migraciones** en archivos `.sqm` numerados (`1.sqm`, `2.sqm`…). Nunca se edita una migración ya publicada.

**Esquema inicial (versión 1, resumido)**

```sql
CREATE TABLE notebook (
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  semester TEXT,
  color INTEGER,
  sort_key TEXT NOT NULL,
  created_hlc TEXT NOT NULL, updated_hlc TEXT NOT NULL,
  device_id TEXT NOT NULL, author_id TEXT NOT NULL,
  deleted_hlc TEXT
);

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
CREATE TABLE document   ( … title, file_hash TEXT UNIQUE, page_count, last_read_page, size_bytes, …metadatos );
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
- Las listas grandes se consultan paginadas, y los trazos se cargan por página (RNF-17).

---

## 7. Contratos del dominio (`core:domain`)

Los repositorios exponen `Flow` para observar y funciones `suspend` para modificar. Ejemplos de la forma esperada; los nombres exactos pueden afinarse sin propuesta, la semántica no:

```kotlin
interface NotebookRepository {
    fun observeAll(): Flow<List<Notebook>>
    suspend fun create(title: String): Notebook
    suspend fun rename(id: Uuid, title: String)
    suspend fun move(id: Uuid, after: Uuid?)          // recalcula sortKey
    suspend fun delete(id: Uuid)                       // borrado suave en cascada lógica
}

interface PageRepository {
    fun observeByNotebook(notebookId: Uuid): Flow<List<Page>>
    suspend fun add(notebookId: Uuid, template: PageTemplate, after: Uuid?): Page
    suspend fun duplicate(id: Uuid): Page
    suspend fun move(id: Uuid, after: Uuid?)
    suspend fun delete(id: Uuid)
}

interface InkRepository {
    fun observeStrokes(pageId: Uuid): Flow<List<Stroke>>
    suspend fun add(pageId: Uuid, stroke: NewStroke): Stroke
    suspend fun erase(ids: List<Uuid>)                 // borrado suave
    suspend fun restore(ids: List<Uuid>)               // para "deshacer" un borrado
}

interface TextBlockRepository {
    fun observeByPage(pageId: Uuid): Flow<List<TextBlock>>
    suspend fun create(pageId: Uuid, x: Int, y: Int, width: Int): TextBlock
    suspend fun updateContent(id: Uuid, html: String)
    suspend fun moveResize(id: Uuid, x: Int, y: Int, width: Int)
    suspend fun delete(id: Uuid)
}

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    suspend fun import(source: PlatformFile): ImportResult   // Imported | AlreadyExists | NotPdf | Corrupt
    suspend fun setLastReadPage(id: Uuid, page: Int)
    suspend fun delete(id: Uuid)
}

interface AnnotationRepository { /* igual que InkRepository, por (documentId, pageIndex) */ }

interface BackupService {
    suspend fun export(destination: PlatformFile): BackupResult
    suspend fun inspect(source: PlatformFile): BackupInfo     // versión, fecha, tamaño, antes de restaurar
    suspend fun restore(source: PlatformFile): RestoreResult
}

interface HlcClock { fun now(): Hlc; fun receive(remote: Hlc): Hlc }
interface IdGenerator { fun newId(): Uuid }
```

**Deshacer y rehacer (RF-02)** es estado de la pantalla, no de la base de datos: una pila de operaciones inversas (`add` ↔ `erase`/`restore`). Así deshacer no rompe la sincronización.

---

## 8. Fronteras de plataforma

Se definen como interfaces en `core:domain` y se implementan en `androidMain` y `desktopMain`. Se prefieren interfaces a `expect/actual`, porque son fáciles de sustituir por dobles en pruebas.

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

- **Paquetes:** `app.cuaderno.<módulo>.<área>`. Ejemplo: `app.cuaderno.data.ink`. El nombre definitivo del paquete se fija en la etapa 2 y se registra aquí.
- **Idioma:** el código (clases, funciones, variables) en inglés; los comentarios, los textos de la interfaz y la documentación en español. Los textos de la interfaz van en recursos de Compose (`composeResources`), no escritos dentro del código.
- **Formato:** el estilo oficial de Kotlin; formatear antes de cada commit con *Code → Reformat Code* en Android Studio.
- **Pruebas:** cada función de `core:model` y cada repositorio tiene pruebas en `commonTest`. La lógica nunca se prueba solo a mano.
- **Commits:** en español, en imperativo, una idea por commit. Ejemplo: `Agrega codificación de trazos con pruebas`.
- **Versiones de librerías:** solo en `gradle/libs.versions.toml`. Añadir una librería nueva requiere anotarla en §11 con su licencia.

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

---

## 11. Librerías aprobadas

Se completa en la etapa 2 con versiones exactas. Toda librería nueva se agrega aquí con su licencia antes de usarla.

| Librería | Uso | Licencia | Estado |
|---|---|---|---|
| Compose Multiplatform | Interfaz | Apache 2.0 | Aprobada |
| kotlinx-coroutines, kotlinx-serialization | Asincronía y JSON | Apache 2.0 | Aprobada. **coroutines adoptada** en la versión 1.11.0 (16/11/2026) |
| SQLDelight | Base de datos | Apache 2.0 | Aprobada. **Adoptada** en la versión 2.4.1 (16/11/2026) |
| AndroidX Lifecycle ViewModel (KMP), Navigation Compose | Estado y navegación | Apache 2.0 | Aprobada |
| compose-rich-editor | Texto enriquecido | Apache 2.0 | A validar en la etapa 4 |
| androidx.ink | Tinta en Android | Apache 2.0 | A validar en la etapa 3 |
| Apache PDFBox | PDF en Windows | Apache 2.0 | A validar en P-1 |
| PdfBox-Android | Exportar en Android | Apache 2.0 | A validar en P-2 |
| Ktor, JmDNS, ZXing, CameraX, ML Kit | v2 y v1.x | Apache 2.0 | Solo cuando llegue su versión |
