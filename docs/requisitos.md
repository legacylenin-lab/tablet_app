# Requisitos de la app de notas y biblioteca (tablet + PC)

**Estado del documento:** **v1.1, LÍNEA BASE APROBADA** por Leyni (programador del proyecto)
**Fecha:** 16/11/2026
**Documentos relacionados (todos en `docs/`):**
- `arquitectura.md`: cómo se construye (módulos, datos, contratos).
- `plan-de-pruebas.md`: cómo se verifica.
- `ritmo-de-trabajo.md`: cómo trabaja el dueño (documento vivo).
- `guia-git.md`: cómo guardar el trabajo con GitHub Desktop.

> **Control de cambios:** todo cambio de requisitos se propone, se evalúa su impacto y se registra en la sección 8 antes de implementarse. Leyni aprueba los cambios.

---

## 0. Aclaración de roles

Las reglas para los agentes viven en `CLAUDE.md`, en la raíz del repositorio. Resumen:
- La idea y la aplicación son del **hermano de Leyni** ("el dueño de la app"). **No es programador.** Trabaja con Claude Code en este repositorio.
- **Leyni** es el programador y revisor técnico. Aprueba los cambios de requisitos, arquitectura y stack.
- Los agentes asumen siempre que hablan con el dueño. Solo tratan a alguien como Leyni si lo dice textualmente en la conversación.
- Este documento, `arquitectura.md` y `plan-de-pruebas.md` son la **fuente de verdad**. No se cambian sin registrar una propuesta en la sección 8.

---

## 1. Resumen del proyecto

Aplicación de **cuaderno de notas y biblioteca personal**, pensada para la universidad (ingenierías). Funciona en dos dispositivos:
- **Tablet Lenovo Idea Tab** con Android 16 y lápiz Lenovo Tab Pen Plus. Es el dispositivo principal y el **único donde se dibuja a mano**.
- **PC con Windows 11**, que es la contraparte en casa.
  - Solo se usan mouse y teclado.
  - Permite ver los trazos, escribir texto y subrayar.

Funciones principales:
- **Notas** en páginas A4 separadas.
- **Biblioteca de PDF** con anotaciones. **Solo se importan PDF.** Los DOCX se convierten a PDF antes de importarlos; en la v2 hay conversión automática opcional en la PC.
- **Impresión nativa** de notas y documentos con sus anotaciones.
- **Sincronización** entre la tablet y la PC por la red Wi-Fi de casa, sin servidores ni nube, cifrada.
- **Futuro:** cuaderno o pizarra compartida con amigos por red local o hotspot.

**Fuera de alcance:**
- LaTeX.
- Edición de documentos dentro de la app: se editan en Office o equivalente y se reimportan como PDF.
- Lectura directa de DOCX.
- Dibujo en la PC.
- macOS.

**Historia de usuario principal (sincronización)**
> Como estudiante, quiero llegar a casa y tener en la PC los apuntes que hice durante el día en la tablet, y viceversa, para trabajar en cualquiera de los dos sin pasar archivos a mano ni perder cambios.

**Volumen estimado:** cientos de cuadernos y documentos, miles de páginas (unas 5.000 a 15.000) y varios GB. Detalle en la sección 5.9.

> **Estado:** **Confirmado** = aprobado. **Backlog** = aceptado como idea; se confirma con el dueño al planificar su versión. **Pendiente** = falta decidir.
> **Prioridad (MoSCoW):** **M** = imprescindible, **S** = deseable, **C** = opcional, **W** = fuera de esta etapa.

---

## 2. Requisitos funcionales (RF)

### 2.0 Versiones

| Versión | Contenido |
|---|---|
| **v1: MVP Android** | RF-01, RF-02, RF-03, RF-04, RF-10, RF-12, RF-16, RF-17. El modelo de datos ya está preparado para sincronizar (RNF-11). |
| **v1.x: mejoras Android** | RF-11, RF-13, RF-18, RF-26 y RF-27, más el backlog que se priorice (RF-05 a RF-09, RF-14, RF-15, RF-19). |
| **v2: PC + sincronización** | RF-21 a RF-25, más RF-28 (conversión automática de DOCX, opcional). |
| **v3: colaboración local** | RF-F1 y RF-F2. |
| **v4 (hipotética): port a macOS** | Fuera de alcance por ahora. |

### 2.1 Notas

| ID | Requisito | Prior. | Estado |
|---|---|---|---|
| RF-01 | Crear, abrir, renombrar y eliminar cuadernos y notas. | M | Confirmado |
| RF-02 | Escribir **a mano con el lápiz** en la tablet: trazo fluido con presión, grosor y color seleccionables, borrador, deshacer y rehacer. | M | Confirmado |
| RF-03 | **Entrada separada por tipo de dispositivo en la tablet.** Lápiz y teclado escriben. El dedo solo desplaza, hace zoom y cambia de página, **nunca escribe**. Incluye rechazo de palma. | M | Confirmado |
| RF-04 | Escribir texto con el teclado en bloques de **texto enriquecido**: negrita, cursiva, listas y títulos. | M | Confirmado |
| RF-05 | Combinar en una misma página trazos a mano y bloques de texto. | S | Backlog |
| RF-06 | Organizar en cuadernos y semestres, con etiquetas opcionales. | S | Backlog |
| RF-07 | Buscar notas por título, por texto escrito con teclado y por el texto reconocido de la escritura a mano. | S | Backlog |
| RF-08 | **Páginas separadas en tamaño A4.** Agregar, reordenar, duplicar y eliminar páginas. Fondo en blanco, rayado o cuadriculado. | S | Confirmado |
| RF-09 | Reconocer la escritura a mano **en la tablet** con Google ML Kit Digital Ink. El texto queda buscable, se puede corregir y se sincroniza a la PC. | C | Confirmado |

### 2.2 Biblioteca

| ID | Requisito | Prior. | Estado |
|---|---|---|---|
| RF-10 | Importar **PDF** a la biblioteca. **Es el único formato importable.** | M | Confirmado |
| RF-11 | **DOCX mediante conversión previa.** Si el usuario intenta importar un DOCX, la app no lo importa y muestra una guía breve para convertirlo a PDF (en Word: Archivo → Guardar como → PDF). En la v2 aplica además RF-28. | S | Confirmado |
| RF-12 | Leer documentos: pasar páginas, zoom, ir a una página y recordar la última página leída. | M | Confirmado |
| RF-13 | Anotar sobre los PDF. En la **tablet**, con el lápiz: subrayar, resaltar y escribir al margen. En la **PC**, solo subrayar o resaltar seleccionando texto con el mouse (no funciona en PDF escaneados). Hay una sola copia por documento; por dentro, las anotaciones son una capa (sección 5.7). | S | Confirmado |
| RF-14 | Catálogo con portada, título y autor; favoritos y colecciones. | S | Backlog |
| RF-15 | Buscar en la biblioteca por título y autor. | S | Backlog |
| RF-26 | **Reimportar una versión nueva** (en PDF) de un documento editado fuera de la app. Opciones: (a) reemplazar conservando las anotaciones, con un aviso de posible desalineación; o (b) importarlo como documento nuevo. | S | Confirmado |

### 2.3 Generales

| ID | Requisito | Prior. | Estado |
|---|---|---|---|
| RF-16 | **Guardado automático** de notas y anotaciones. | M | Confirmado |
| RF-17 | **Respaldo y restauración**: exportar todos los datos a un archivo y restaurarlos. | M | Confirmado |
| RF-18 | **Exportar a PDF** una nota, un cuaderno o un documento con sus anotaciones estampadas sobre él. | S | Confirmado |
| RF-19 | Modo oscuro. | C | Backlog |
| ~~RF-20~~ | ~~Fórmulas LaTeX~~ | — | **Eliminado** |
| RF-27 | **Imprimir de forma nativa** con el diálogo del sistema en Android y en Windows: notas A4 (fondo, trazos y texto) y documentos con su capa de anotaciones. Se elige la impresora, el rango de páginas y el número de copias. Se basa en RF-18 (sección 5.11). | S | Confirmado |

### 2.4 PC y sincronización (v2)

| ID | Requisito | Prior. | Estado |
|---|---|---|---|
| RF-21 | **App para Windows 11**, solo con mouse y teclado. **No permite dibujar a mano.** Permite ver todo, escribir y editar texto, subrayar o resaltar documentos, organizar, buscar, importar, exportar e imprimir. | M (v2) | Confirmado |
| RF-22 | **Sincronización tablet ↔ PC por la red Wi-Fi local.** Fase A: automática al abrir la app si el otro dispositivo está en la red, más un botón "Sincronizar ahora". Fase B: la app de PC queda en la bandeja del sistema y la tablet sincroniza en segundo plano. Siempre incremental. | M (v2) | Confirmado |
| RF-23 | **Emparejamiento único** con un QR que muestra la PC y escanea la tablet, más un código de un solo uso. El emparejamiento se puede revocar (sección 5.12). | M (v2) | Confirmado |
| RF-24 | **Resolución de conflictos.** Fusión automática por elemento. En un conflicto real, la página se duplica, se marca y el usuario elige. Si se borró en un lado y se editó en el otro, **gana la edición**. | M (v2) | Confirmado |
| RF-25 | **Indicador de estado de sincronización**: última sincronización, cambios pendientes y aviso si hace mucho que no sincroniza. | S (v2) | Confirmado |
| RF-28 | **Conversión automática de DOCX a PDF en la PC (opcional).** Al importar un DOCX en la PC, si LibreOffice está instalado, la app lo convierte a PDF en un proceso externo y en segundo plano. El PDF llega a la tablet por sincronización. Si LibreOffice no está instalado o la conversión falla, se muestra la guía de RF-11. **Condición:** no debe degradar el rendimiento de la app (sección 5.2). | C (v2) | Confirmado con condición |

### 2.5 Colaboración (v3)

| ID | Requisito | Prior. | Estado |
|---|---|---|---|
| RF-F1 | **Cuaderno o pizarra compartida por red local o hotspot**: trabajos en equipo, lluvia de ideas, apuntes grupales y de reuniones. Un dispositivo hace de anfitrión. Cada autor se identifica con nombre y color, y todos conservan una copia al terminar. | W | Confirmado |
| RF-F2 | **Manual de usuario dentro de la app** que explique, paso a paso y con capturas, cómo colaborar y cómo **activar el hotspot**. | W (v3) | Confirmado |

---

## 3. Requisitos no funcionales (RNF)

| ID | Categoría | Requisito | Estado |
|---|---|---|---|
| RNF-01 | Plataforma | **Android 16** (Lenovo Idea Tab) y **Windows 11**. Los dispositivos de los amigos (laptops o tablets) se definen al iniciar la v3. macOS queda fuera. | Confirmado |
| RNF-02 | Entorno | Se desarrolla y prueba en la PC. Se prueba en la tablet real durante el desarrollo y se instala allí la versión final. | Confirmado |
| RNF-03 | Rendimiento | Trazo del lápiz sin retraso perceptible e interfaz fluida (objetivo: ≥ 60 cuadros por segundo). Se valida con prueba manual en la tablet. | Confirmado |
| RNF-04 | Rendimiento | Un PDF de 500 páginas o más muestra la primera página en pocos segundos. El umbral se fija tras el prototipo P-1. | Confirmado |
| RNF-05 | Disponibilidad | Funciona **sin internet**. La sincronización y la colaboración usan solo la red local o el hotspot. | Confirmado |
| RNF-06 | Fiabilidad | No se pierden datos si la app se cierra, falla o se queda sin batería. El guardado es incremental. | Confirmado |
| RNF-07 | Privacidad | Los datos solo viven en los dispositivos del dueño (y en los de los participantes de la v3). Solo viajan por la red local, entre dispositivos emparejados y cifrados. Sin nube, sin terceros y sin analítica. | Confirmado |
| RNF-08 | Usabilidad | Interfaz simple para un usuario no técnico, con botones grandes en la tablet y atajos de teclado en la PC. | Confirmado |
| RNF-09 | Usabilidad | Funciona en orientación vertical y horizontal (tablet) y en ventana redimensionable (PC). | Confirmado |
| RNF-10 | Idioma | Interfaz en español. | Confirmado |
| RNF-11 | Evolución | **Modelo de datos listo para sincronizar desde la v1** (sección 5.1 y `arquitectura.md`). | Confirmado |
| RNF-12 | Mantenibilidad | Código en capas: interfaz, dominio, datos y sincronización. El módulo de sincronización es independiente del medio de transporte. Pruebas automáticas de lo esencial. | Confirmado |
| RNF-13 | Almacenamiento | Trazos guardados de forma compacta. La app muestra el espacio usado. | Confirmado |
| RNF-14 | Legal | Solo librerías con licencia compatible con uso gratuito y personal. Se prefieren las de código abierto. | Confirmado |
| ~~RNF-15~~ | ~~Seguridad~~ | **Eliminado.** Se usa el bloqueo del dispositivo. | Confirmado |
| RNF-16 | Accesibilidad | Tamaño de texto ajustable y contraste suficiente. | Backlog (C) |
| RNF-17 | Escalabilidad | Soporta el volumen de 5 años sin degradarse: carga perezosa de páginas y miniaturas, y búsqueda indexada. | Confirmado |
| RNF-18 | Rendimiento de sincronización | La sincronización de un día normal de apuntes es rápida en el Wi-Fi de casa. La primera sincronización completa muestra su progreso. El umbral se fija tras medir. | Confirmado |
| RNF-19 | Integridad | Ninguna sincronización pierde datos. En un conflicto se conservan ambas versiones; frente a un borrado, gana la edición. Una sincronización interrumpida no deja datos corruptos y se puede reanudar. | Confirmado |
| RNF-20 | Seguridad de red | **TLS 1.3 mutuo con certificados fijados** en el emparejamiento (sección 5.12). Un dispositivo no emparejado no puede leer ni escribir datos. El emparejamiento se puede revocar. | Confirmado |
| RNF-21 | Rendimiento (aislamiento de procesos externos) | Las tareas pesadas externas (conversión de DOCX en RF-28) corren **fuera del proceso de la app y en segundo plano**. Nunca bloquean la interfaz, tienen un tiempo máximo con cancelación y la app sigue usable mientras corren. Si no se cumple, RF-28 se descarta. | Confirmado |
| RNF-22 | Entorno de desarrollo | **PowerShell está bloqueado** en la PC de desarrollo por ESET Endpoint Security, y no se va a desbloquear. Toda herramienta, script o comando se ejecuta con `cmd` o Git Bash (`gradlew.bat`, nunca scripts `.ps1`). No se adopta ninguna herramienta que dependa de PowerShell. | Confirmado |

---

## 4. Stack tecnológico definido (aprobado, v1.1)

**Cambio respecto a la v1.0:** Flutter queda descartado porque su herramienta de línea de comandos depende de PowerShell, que ESET bloquea (RNF-22). Se validó en esta PC que **Kotlin + Compose Multiplatform** compila y ejecuta tanto en Android (emulador API 37) como en escritorio Windows.

- **Lenguaje y plataforma:** Kotlin, con **Kotlin Multiplatform (KMP)** y **Compose Multiplatform** para la interfaz compartida entre Android y escritorio Windows (JVM).
- **Herramientas:** Android Studio, con el plugin Kotlin Multiplatform; Gradle mediante `gradlew.bat`; JDK incluido en Android Studio. **No se usan** Visual Studio, Flutter ni el Modo de desarrollador de Windows.
- **Asistente de desarrollo:** Claude Code, configurado para usar Git Bash y nunca PowerShell (`.claude/settings.json`).
- **Detalle técnico** (librerías concretas, módulos, contratos): en `arquitectura.md`. Ese documento manda en las decisiones de implementación.

| Área | Elección | Alternativa, si su prototipo falla |
|---|---|---|
| Interfaz | Compose Multiplatform | — |
| Estado | ViewModel de AndroidX (multiplataforma) + `StateFlow` | — |
| Navegación | Navigation Compose multiplataforma | — |
| Base de datos | SQLDelight (SQLite) | Room KMP |
| Texto enriquecido | `compose-rich-editor` | Editor propio mínimo |
| Tinta (solo tablet) | `androidx.ink` + entrada de baja latencia | `pointerInput` de Compose con renderizado propio |
| Visor de PDF | Android: `PdfRenderer` del sistema; Windows: Apache PDFBox | `androidx.pdf` en Android |
| Exportar / estampar PDF | Apache PDFBox (Windows) y PdfBox-Android (tablet) | Páginas convertidas en imagen |
| Impresión | Android: `PrintManager`; Windows: PDFBox con Java Print | — |
| Red y cifrado (v2) | Ktor (HTTP y WebSocket) con TLS 1.3 mutuo y certificados fijados | — |
| Descubrimiento (v2) | Android: `NsdManager`; Windows: JmDNS | — |
| QR (v2) | Windows: ZXing (genera); tablet: CameraX + ML Kit Barcode (escanea) | — |
| Reconocimiento de escritura | ML Kit Digital Ink (solo Android) | — |
| Pruebas | `kotlin.test`, pruebas de UI de Compose, pruebas instrumentadas de Android | — |

> Antes de adoptar cada librería, verificar en un prototipo que soporta las plataformas donde se usa, que está mantenida y su licencia. Verificar también que ML Kit reconozca español y el estado de `androidx.ink`.

---

## 5. Decisiones y fundamentos

### 5.1 Sincronización y conflictos

Se sincronizan **elementos pequeños** (trazos, bloques de texto, páginas), no archivos completos. El detalle técnico está en `arquitectura.md`.

**Modelo de datos (obligatorio desde la v1)**
- **Estructura:** cuaderno → página → elementos (trazo, bloque de texto, anotación).
- **Identificadores únicos (UUID)** para todo.
- **Metadatos en cada registro:** `device_id`, `author_id` y `updated_hlc` con **reloj lógico híbrido (HLC)**.
- **Borrados suaves:** se marca el registro como eliminado en lugar de borrarlo.
- **Los trazos son inmutables:** se crean o se borran, no se editan.
- **Registro de cambios (operaciones)** por dispositivo.

**Reglas de fusión**
- Trazos agregados en ambos lados: se unen todos.
- Borrado en un lado: se propaga.
- Metadatos editados en ambos lados: gana el más reciente según el HLC.
- Mismo bloque de texto editado en ambos lados: la página se duplica, se marca y el usuario elige.
- **Borrado contra edición: gana la edición**, que se restaura con un aviso.

**Sincronización automática** por fases (RF-22), con el indicador RF-25. Los documentos se transfieren una sola vez, identificados por su hash; después solo viajan las anotaciones.

### 5.2 Formatos

- **Solo se importa PDF.** Es el único formato con páginas fijas, y por eso las anotaciones quedan estables.
- **DOCX:** el dueño lo convierte a PDF desde Word antes de importarlo (RF-11).
- **LaTeX:** fuera de alcance.

**Conversión automática en la PC (RF-28) y deuda de rendimiento**
- LibreOffice se ejecuta como un **proceso externo e independiente** (`soffice --headless --convert-to pdf`, lanzado con `ProcessBuilder`, nunca vía PowerShell), solo al importar un DOCX.
- No comparte memoria con la app, así que **no afecta al dibujo, la navegación ni la sincronización**.
- El coste es puntual: unos segundos por documento, en segundo plano.
- La deuda principal es de mantenimiento (detectar la instalación, errores, tiempos máximos). Se mitiga porque es **opcional**, está aislada en un módulo pequeño y se cancela si tarda demasiado.
- **Verificación:** CP-16. **Si no cumple RNF-21, RF-28 se descarta sin afectar al resto.**

### 5.3 Respaldo

RF-17 es imprescindible. La PC no reemplaza al respaldo, porque un borrado se sincroniza a ambos dispositivos.

### 5.4 Reconocimiento de escritura

ML Kit Digital Ink, **solo en la tablet**. El texto se puede corregir y se sincroniza a la PC.

### 5.5 Latencia del lápiz

Prueba manual en la tablet real: cámara lenta y comparación con la app de notas de la tablet.

### 5.6 Colaboración por red local (v3)

- Un dispositivo hace de anfitrión; los demás se unen por la red local o por su hotspot, con el manual en la app (RF-F2).
- **No requiere refactor** si la v1 respeta la sección 5.1.
- **Pendientes al iniciar la v3:** los dispositivos de los amigos y si la lluvia de ideas usa página o lienzo infinito.

### 5.7 Anotaciones

- El usuario ve una sola copia de cada documento, con sus anotaciones.
- Por dentro, el PDF se guarda intacto y las anotaciones son una capa por página. Esto permite sincronizar poco, fusionar sin conflictos, editar, deshacer y conservar las anotaciones al reimportar.

### 5.8 Criterios de aceptación

Se definen en `plan-de-pruebas.md`.

### 5.9 Volumen estimado

- **Cursos y páginas:** unos 60 cursos y **6.000 a 15.000 páginas**.
- **Trazos:** alrededor de 1 a 1,5 GB comprimidos.
- **Biblioteca:** varios GB.
- **Consecuencias:** carga perezosa, búsqueda indexada, sincronización incremental y control del espacio disponible.

### 5.10 Resumen de decisiones

| Tema | Decisión |
|---|---|
| Stack | Kotlin + Compose Multiplatform (Flutter descartado por RNF-22) |
| Formatos | Solo se importa PDF. DOCX se convierte antes (y de forma automática y opcional en la PC en la v2). Sin LaTeX. |
| Edición de documentos | Fuera de la app; reimportar como PDF (RF-26) |
| Página | A4, páginas separadas |
| Entrada en la tablet | Lápiz y teclado escriben; el dedo desplaza |
| Entrada en la PC | Mouse y teclado: ver, escribir texto, subrayar; sin dibujar |
| Conflictos | Fusión por elemento, página duplicada y elección; gana la edición |
| Texto enriquecido | Sí (`compose-rich-editor`) |
| Visor de PDF | `PdfRenderer` (Android) y PDFBox (Windows) |
| Cifrado | TLS 1.3 mutuo con certificados fijados; emparejamiento por QR |
| Reconocimiento de escritura | ML Kit, solo en la tablet |
| Impresión | Nativa, en Android y Windows |
| Colaboración | Red local o hotspot, con manual en la app |
| Plataformas | Android 16 y Windows 11; macOS fuera |
| Asistente | Claude Code, sin PowerShell |

**Pendientes:** solo los de la v3 (dispositivos de los amigos; página o lienzo infinito).

### 5.11 Impresión nativa

- Se imprime el PDF que genera RF-18. En Android se entrega a `PrintManager` (diálogo del sistema); en Windows, PDFBox lo envía al diálogo de impresión de Java.
- Las notas se dibujan en A4 como vectores.
- Los documentos combinan el PDF original con la capa de anotaciones. PDFBox permite estampar vectores sobre un PDF existente, así que el riesgo P-2 baja respecto a la v1.0. Plan B: convertir las páginas en imagen.
- Los documentos grandes se generan por rangos.

### 5.12 Cifrado y emparejamiento

**TLS 1.3 mutuo con certificados fijados**, implementado con Ktor.
- Cada dispositivo genera una vez su par de claves y su certificado.
- **Emparejamiento:**
  1. La PC muestra un QR con la huella de su certificado y un código de un solo uso.
  2. La tablet lo escanea, se conecta y envía su propio certificado.
  3. Ambas guardan la huella de la otra.
- Solo se aceptan conexiones de huellas guardadas. Revocar significa borrar la huella.
- En la v3, el anfitrión muestra un QR por sesión.

### 5.13 Visor de PDF

- **Android:** `android.graphics.pdf.PdfRenderer`, del sistema, sin dependencias. En Android 15 y posteriores también ofrece extracción de texto y búsqueda (a verificar en P-1).
- **Windows:** Apache PDFBox, para renderizar páginas, obtener las posiciones del texto (selección para subrayar) y buscar.
- Ambos quedan detrás de una interfaz común (`PdfEngine`, ver `arquitectura.md`), así que la interfaz de usuario no sabe cuál usa.
- Se valida con el prototipo P-1 en la etapa 5: PDF de 500 páginas, capa superpuesta, selección de texto con el mouse en Windows y búsqueda.

---

## 6. Glosario breve (para explicar al dueño)

- **Sincronizar:** hacer que la tablet y la PC tengan los mismos datos.
- **Conflicto:** cuando lo mismo se cambió de forma distinta en los dos dispositivos.
- **Emparejar:** autorizar una vez a la PC y la tablet (escaneando un QR) para que confíen entre sí.
- **Cifrado:** convertir los datos en algo ilegible para quien no tenga la clave.
- **Hotspot:** compartir la conexión de un dispositivo para que los demás se conecten a él directamente.
- **MVP:** primera versión mínima que ya sirve.
- **Texto enriquecido:** texto con formato (negrita, listas, títulos).
- **PDF:** formato de documento con páginas fijas; es el único que la app importa.
- **Commit:** un "punto de guardado" del código, como en un videojuego.
- **Kotlin:** el lenguaje de programación de la app.

---

## 7. Plan por etapas

1. **Entorno y repositorio** (✅ entorno validado): proyecto KMP base (Android + Desktop) creado con kmp.jetbrains.com, subido a GitHub, con `CLAUDE.md`, `docs/` y `.claude/` en la raíz. Primer commit.
2. **Estructura y modelo de datos:** módulos de `arquitectura.md`, entidades, HLC, codificación de trazos, esquema SQLDelight y repositorios, con sus pruebas unitarias.
3. **Lienzo con lápiz** en la tablet real (prototipo de tinta, RF-02 y RF-03).
4. **Notas:** cuadernos, páginas A4, texto enriquecido y guardado automático.
5. **Biblioteca:** importar y leer PDF (con la guía para DOCX) y recordar la página, más el **prototipo P-1**.
6. **Respaldo y restauración.** → **Cierre de la v1.**
7. **v1.x:** anotaciones, exportar e imprimir (con el prototipo P-2), reimportación y el backlog priorizado.
8. **v2:** app de Windows completa, emparejamiento por QR con TLS (prototipo P-3), sincronización, conflictos y conversión de DOCX opcional.
9. **v3:** colaboración en red local y manual del hotspot.

---

## 8. Registro de cambios

| Versión | Fecha | Cambio |
|---|---|---|
| 0.1 | 28/10/2026 | Borrador inicial de RF y RNF. |
| 0.2 | 28/10/2026 | Contraparte Windows y sincronización Wi-Fi. Formatos, volumen, colaboración. Plan de pruebas. |
| 0.3 | 28/10/2026 | Conflictos y texto enriquecido aprobados. LaTeX eliminado. ML Kit solo en la tablet. Hotspot y manual. |
| 0.4 | 28/10/2026 | RF-26 confirmado. A4. Impresión nativa (RF-27). PC sin dibujo. Gana la edición. Propuestas de DOCX, cifrado y visor. |
| 1.0 | 31/10/2026 | Línea base aprobada. Solo PDF; RF-28 condicionado a RNF-21. TLS 1.3 mutuo aprobado. |
| **1.1** | **16/11/2026** | **Cambio de stack:** Flutter → Kotlin + Compose Multiplatform, porque ESET bloquea PowerShell y Flutter depende de él (nuevo RNF-22). Validado en la PC: Android y escritorio compilan y corren. Se actualizan las secciones 4, 5.10, 5.11, 5.12, 5.13 y 7. Los documentos pasan al repositorio (`docs/`) y se agrega `arquitectura.md`. Los requisitos funcionales no cambian. |

**Propuestas de cambio posteriores a la v1.1** (las registran los agentes o Leyni; solo Leyni las aprueba):

| ID | Fecha | Propuesta | Origen | Impacto | Estado |
|---|---|---|---|---|---|
| PC-01 | 18/11/2026 | **Adelantar RF-05 a la v1:** poder combinar en una misma página los trazos a mano y los bloques de texto enriquecido (cuadros de texto sobre la hoja donde también se dibuja). Hoy RF-05 está en el backlog de la v1.x y la v1 solo pide RF-04 (texto en bloques). | Dueño: quiere escribir texto y dibujar en la misma página. | El modelo de datos ya lo soporta (`TextBlock` con página, posición y ancho; repositorio hecho). Cuesta más que páginas de texto aparte: mover y redimensionar bloques, convivir con los gestos del lápiz y del dedo, y el orden en que se dibujan. Estimado: 2 o 3 sesiones más. Encaja en la etapa 4 de la v1. No contradice otras decisiones, salvo mover RF-05 de «Backlog (S)» a la v1. | Pendiente de revisión (Leyni) |
