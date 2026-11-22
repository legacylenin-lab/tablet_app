# Plan de pruebas de la app de notas y biblioteca

**Versión:** 1.1
**Basado en:** `docs/requisitos.md` v1.1 y `docs/arquitectura.md` v1.0
**Fecha:** 16/11/2026

> **Para los agentes:** este plan es una **guía conceptual**. A medida que se termine cada funcionalidad:
> 1. convierte sus casos (CP-xx) en pruebas concretas (código en `commonTest`, `androidInstrumentedTest` o `desktopTest`, o un guion manual en `docs/pruebas-manuales/`);
> 2. actualiza la matriz de trazabilidad (sección 9);
> 3. explícale al dueño, en lenguaje simple, qué se prueba y por qué.
>
> No cambies los criterios de aceptación sin registrarlo como propuesta en `docs/requisitos.md` §8.

---

## 1. Objetivo y alcance

**Objetivo:** verificar que cada RF y RNF se cumple y, sobre todo, que **nunca se pierden datos**.

**Dentro del alcance:**
- v1 y v1.x: Android.
- v2: Windows y sincronización.
- v3: colaboración (solo escenarios).

**Fuera del alcance:**
- Pruebas de las librerías de terceros en sí.
- LaTeX.
- Lectura o edición de DOCX dentro de la app.
- Dibujo en la PC.
- macOS.

---

## 2. Estrategia por niveles

| Nivel | Qué se prueba | Herramienta | Cuándo |
|---|---|---|---|
| **Unitarias** | `core:model` (HLC, `StrokeCodec`, `sortKey`); repositorios con SQLite en memoria; **lógica de fusión** (incluida "gana la edición"); `change_log` en la misma transacción; formato del respaldo; generación del PDF de exportación; validación de huellas de certificados. | `kotlin.test` en `commonTest` (y `desktopTest` para el driver JDBC en memoria) | Con cada funcionalidad. Se ejecutan con `gradlew.bat allTests` o `gradlew.bat desktopTest`. |
| **De interfaz** | Lienzo (lápiz dibuja, dedo no); texto enriquecido; visor; diálogos de conflicto, reimportación, impresión y guía de DOCX. | Pruebas de UI de Compose (`runComposeUiTest`) en escritorio; pruebas instrumentadas en Android | Con cada pantalla. |
| **De integración** | Flujos completos en el emulador y en Windows. | Pruebas instrumentadas de Android (`connectedAndroidTest`) y pruebas de escritorio | Al cerrar cada etapa. |
| **De sistema / extremo a extremo** | Tablet real + PC real + Wi-Fi + impresora. | Manual guiado más registros (logs) | Al cerrar cada versión. |
| **De aceptación** | Historias de usuario con el dueño. | Guiones (sección 8) | Al cerrar cada versión. |

**Automatización:** pruebas unitarias y de interfaz de escritorio en cada cambio, con GitHub Actions en un runner Linux o Windows que use `cmd` o bash, nunca PowerShell (RNF-22). Lápiz, red e impresión son pruebas manuales.

**Prototipos de validación:**
- **P-0 (tinta, etapa 3):**
  - `androidx.ink` o entrada de baja latencia en la tablet real;
  - distinguir lápiz y dedo (RF-03);
  - medir la latencia (7.1).
  - **Resultado (17/11/2026, tablet Lenovo TB336ZU, Android 16, pantalla de 90 Hz):** `androidx.ink` 1.0.0 funciona y usa la ruta de baja latencia. El lápiz y el dedo se distinguen bien: el dedo y la palma no escriben. La presión cambia el grosor. A simple vista la latencia es parecida a la de solo Compose; la medición con cámara lenta (7.1) se hizo el 22/11/2026: parecida a Nebo, sin número preciso (ver PQ-21). El lápiz no tiene extremo borrador: se alterna lápiz/borrador con su doble toque o con un botón en pantalla (PQ-20). **Decisión del prototipo: adoptar `androidx.ink` 1.0.0.**
- **P-1 (PDF, etapa 5):**
  - `PdfRenderer` en Android y PDFBox en Windows detrás de `PdfEngine`;
  - PDF de 500 páginas;
  - capa de anotaciones superpuesta;
  - selección de texto con el mouse en Windows;
  - búsqueda;
  - fijar el umbral de RNF-04.
  - **Resultado en Windows (18/11/2026, prototipo en `desktopApp`, solo en memoria):** `PdfBoxEngine` (PDFBox 3.0.8) detrás de `PdfEngine` y el mismo lector de la tablet. Un PDF de texto se ve bien; un libro escaneado con imágenes JPEG 2000 sale en blanco (PQ-27). Falta: zoom con el mouse, selección de texto y búsqueda.
  - **Resultado en Android (18/11/2026, tablet Lenovo TB336ZU, Android 16):** `PdfRenderer` detrás de `PdfEngine` funciona con un PDF real de **1702 páginas y 164 MB**: abrir 0,06 s, primera página en pantalla unos 0,3 s y cada cambio de página unos 0,22 s (página de 1600 px de ancho; `adb logcat -s PdfTiming`). Saltar a una página lejana con «Ir a…» o la barra también es inmediato. El zoom hasta 500% dibuja nítido solo el rectángulo visible (`renderRegion`), sin cierres ni lentitud. Umbral propuesto para RNF-04 en PQ-26. **Falta:** la parte de Windows (PDFBox), la selección de texto con el mouse, la búsqueda y la capa de anotaciones superpuesta (con RF-13).
- **P-2 (exportación, v1.x):** estampar trazos vectoriales sobre un PDF existente con PDFBox y PdfBox-Android. Plan B: páginas convertidas en imagen.
- **P-3 (cifrado, v2):** TLS 1.3 mutuo con Ktor y fijación de huella, entre la tablet y Windows.

---

## 3. Entornos de prueba

| ID | Entorno |
|---|---|
| E1 | PC con Windows 11, solo mouse y teclado, **con ESET y PowerShell bloqueado**. Con y sin LibreOffice instalado (RF-28). |
| E2 | Emulador de tablet Android, API 36 o 37 |
| E3 | Lenovo Idea Tab con Android 16 y Lenovo Tab Pen Plus |
| E4 | Red Wi-Fi doméstica |
| E5 | Red con aislamiento de clientes o sin red |
| E6 | (v3) Hotspot del anfitrión y una laptop o tablet de un amigo |
| E7 | Una impresora accesible desde Android y Windows, o "Microsoft Print to PDF" como sustituto |

---

## 4. Datos de prueba

**Generador sintético** (una tarea de Gradle o una prueba que llena una base de datos):
- **Perfil "5 años":** unos 60 cuadernos, unas 10.000 páginas A4 y unos 300 PDF.
- **Perfil "un día de clases":** 3 o 4 cuadernos con 10 a 20 páginas nuevas.

**PDF de borde:**
- 500 páginas o más;
- escaneado;
- con contraseña;
- dañado;
- de más de 100 MB.

**DOCX (RF-11 y RF-28):**
- uno simple;
- uno con ecuaciones e imágenes;
- uno muy largo (más de 300 páginas);
- uno dañado.

**Reimportación:** un PDF anotado y su versión editada, en dos variantes: con páginas añadidas y con contenido movido.

> Los archivos personales del dueño **nunca** se suben al repositorio. Los datos de prueba se generan o son PDF de dominio público.

---

## 5. Casos de prueba conceptuales (funcionales)

| ID | Requisito | Caso | Resultado esperado |
|---|---|---|---|
| CP-01 | RF-01 | Crear, renombrar y eliminar un cuaderno y una nota | Persiste al reabrir. El borrado pide confirmación o se puede deshacer. |
| CP-02 | RF-02 | Lápiz con presión, grosor y color; borrar; deshacer y rehacer 20 veces | El trazo sigue al lápiz y deshacer y rehacer son coherentes. |
| CP-03 | RF-03 | Palma apoyada; deslizar el dedo; hacer pinza | La palma no deja marcas y el dedo nunca dibuja. |
| CP-04 | RF-04 | Texto enriquecido con teclado físico y en pantalla, con acentos y ñ | El formato se aplica y persiste (ida y vuelta de HTML, `arquitectura.md` §5.4). |
| CP-05 | RF-08 | Páginas A4: agregar, reordenar, duplicar, eliminar y cambiar el fondo | Persiste. |
| CP-06 | RF-10, RF-12 | Importar un PDF, navegar, hacer zoom, ir a la página N y reabrir | Se abre en la última página leída. Importar el mismo PDF otra vez no lo duplica. |
| CP-07 | RF-13 | Anotar con el lápiz en la tablet; subrayar con el mouse en la PC; probar con un PDF escaneado en la PC | Las anotaciones persisten. Con el escaneado, aparece un aviso de que no hay texto para subrayar, sin errores. |
| CP-08 | RF-16 | Escribir y cerrar la app de inmediato | No se pierde nada, salvo como máximo el último instante. |
| CP-09 | RF-17 | Respaldar → desinstalar → reinstalar → restaurar; probar también un respaldo dañado y uno de una versión más nueva | Los datos quedan idénticos. Los respaldos dañados o incompatibles se rechazan sin tocar los datos locales. |
| CP-10 | RF-11 | Intentar importar un DOCX (en la tablet, o en la PC sin LibreOffice) | No se importa y se muestra la guía para convertirlo a PDF. |
| CP-11 | RF-26 | Reimportar una versión editada | Se ofrece reemplazar o importar como nuevo; el aviso aparece si se reemplaza. |
| CP-12 | RF-09 | Escritura a mano en español; búsqueda; corrección | Se encuentra la página y la corrección persiste y llega a la PC. |
| CP-13 | RF-21 | En Windows: ver trazos, editar texto, subrayar, organizar, buscar; intentar dibujar | Todo funciona y la herramienta de dibujo no está disponible. |
| CP-14 | RF-18, RF-27 | Exportar e imprimir una nota A4 y un PDF anotado desde Android y Windows; elegir el rango y las copias | Se abre el diálogo nativo y el resultado coincide con lo que se ve en pantalla. |
| CP-15 | RF-27 | Imprimir un documento de 500 páginas | No se agota la memoria y se muestra el progreso. |
| CP-16 | RF-28, RNF-21 | En la PC con LibreOffice, importar los DOCX de prueba y seguir usando la app mientras convierte; repetir con el dañado y con uno que supere el tiempo máximo | PDF fiel. **La interfaz no se congela** (medir con el profiler de Android Studio o con el contador de cuadros). Los errores se cancelan y muestran la guía. **Si falla la fluidez, RF-28 se descarta.** |
| CP-17 | PC-03, RNF-08, RNF-09 | Por cada pantalla rediseñada en la etapa 7: compararla con su marco de Figma en la tablet real (vertical y horizontal); medir los elementos táctiles; revisar el contraste con una herramienta de contraste sobre los colores del tema; repetir el uso normal de la pantalla | Coincide con el diseño (diferencias explicadas en `docs/etapa-7-diseno.md` §8); solo usa valores del tema; todo lo táctil mide ≥ 48 × 48 dp; contraste ≥ 4,5:1 en texto normal y ≥ 3:1 en texto grande e íconos; el comportamiento no cambió y las pruebas existentes pasan; el dueño la aprueba. |
| CP-17 | RF-05 | En una página con trazos: agregar textos, escribir con el teclado y a mano (modo Lápiz), moverlos y cambiar su ancho; eliminar uno y deshacer; «Borrar trazos» y «Borrar textos»; cambiar de página y reabrir la app | Los textos no se enciman al crearlos, el lápiz escribe tinta encima de los que no se editan, el dedo nunca escribe y todo persiste. Deshacer sigue el orden de trazos y textos. |

**Casos unitarios obligatorios de la etapa 2** (base de todo lo demás):

| ID | Contrato | Caso |
|---|---|---|
| CU-01 | `Hlc` | Orden total; texto ordenable igual que el orden real; monotonía con el reloj del sistema retrocediendo; `receive` con un reloj remoto adelantado. |
| CU-02 | `StrokeCodec` | Ida y vuelta: vacío, 1 punto, 10.000 puntos, valores extremos; lectura de la versión 1. |
| CU-03 | `sortKey` | Insertar entre dos claves siempre da una clave intermedia; mil inserciones seguidas en el mismo hueco. |
| CU-04 | Repositorios | Cada escritura deja su fila en `change_log` en la misma transacción; las consultas ocultan los borrados; borrar un cuaderno oculta sus páginas. |
| CU-05 | Respaldo | Manifest correcto; los checksums detectan corrupción; se rechaza una versión futura. |
| CU-05b | Respaldo en Android | **Prueba instrumentada en el emulador** (PQ-10): exportar un respaldo con `VACUUM INTO` sobre el SQLite real de Android e inspeccionarlo; el manifiesto y los checksums coinciden. |
| CU-06 | `EntityId` | `parse` acepta un UUID válido y rechaza texto inválido; `newId` genera ids únicos (10.000 sin repetir) con el formato correcto (PQ-02). |
| CU-07 | `TitleSort` | "Álgebra" < "Zebra"; "nube" < "ñandú" < "oso"; mayúsculas, tildes y diéresis no alteran el orden; espacios colapsados; mismo resultado en `jvmTest` y en Android. La consulta de biblioteca y cuadernos ordena por `title_sort`, y renombrar lo recalcula (PQ-13). |

**Estado de implementación (16/11/2026):**

| Caso | Estado | Dónde |
|---|---|---|
| CU-01 | Hecho | `core/model/.../HlcTest.kt` (16 pruebas) |
| CU-02 | Hecho | `core/model/.../StrokeCodecTest.kt` (11 pruebas) |
| CU-03 | Hecho | `core/model/.../SortKeyTest.kt` (6 pruebas) |
| CU-04 | Hecho | `core/data/src/jvmTest/...`: repositorios de cuadernos, páginas, trazos, texto, documentos y anotaciones (50 pruebas). Incluye `change_log` en la misma transacción, borrados ocultos y "borrar un cuaderno oculta sus páginas". También cubre la regla de CS-13 (el mismo PDF no se duplica) a nivel de datos. |
| CU-05 | Hecho | `core/data/src/jvmTest/.../backup/BackupServiceTest.kt` (18 pruebas): manifiesto, checksums, versión futura, archivos dañados y rutas peligrosas. Más `RestoreInstallerTest` (5: intercambio de archivos al arrancar, con la app cerrada a mitad) y `AppBackupServiceTest` (6: crear, revisar, preparar con copia previa, respaldo dañado, deshacer). `BackupViewModelTest` (10) cubre la pantalla. |

| CU-05b | Hecho | `core/data/src/androidDeviceTest/.../AndroidBackupTest.kt` (1 prueba, ejecutada el 17/11/2026 en el emulador `Medium_Phone_API_37.0`): `VACUUM INTO` sobre el SQLite real de Android, manifiesto, checksums, copia que se abre y pasa `integrity_check`. Se ejecuta con `./gradlew :core:data:connectedAndroidDeviceTest` con un emulador encendido; no corre en `./gradlew build`. |
| CU-06 | Hecho | `core/model/.../EntityIdTest.kt` (5 pruebas: parse válido e inválido) y `core/domain/.../RandomIdGeneratorTest.kt` (2 pruebas: 10.000 ids únicos con formato correcto). |
| CU-07 | Hecho | `core/model/.../TitleSortTest.kt` (9 pruebas; corren en JVM y en el destino Android) y pruebas de orden y recálculo en `SqlNotebookRepositoryTest` (3) y `SqlLibraryRepositoryTest` (2). |
| CU-08 | Hecho | Persistencia de trazos. `core/data/.../EnsureFirstPageTest.kt` (5 pruebas: primera página al arrancar), `shared/.../InkConversionTest.kt` (8 pruebas: conversión pura de puntos) y `shared/src/androidDeviceTest/.../InkStrokeConverterTest.kt` (7 pruebas instrumentadas, ejecutadas el 17/11/2026 en la tablet Lenovo TB336ZU: trazo de tinta → bytes guardados → trazo reconstruido, con presión, trazo de un punto y datos dañados). Se ejecutan con `./gradlew :shared:connectedAndroidDeviceTest` con un dispositivo conectado. |
| CU-09 | Hecho | ViewModels en código común (PQ-24), con repositorios en memoria: `NotebookListViewModelTest` (6), `NotebookPagesViewModelTest` (11, con reordenar páginas), `InkPageViewModelTest` (11: trazos, borrador, deshacer mezclado con textos, botón del lápiz) y `TextBlocksViewModelTest` (18: crear sin encimar, guardado tras dejar de teclear, HTML acordado, mover y ancho, eliminar y deshacer, modos Lápiz/Teclado). Más `RichTextHtmlTest.reconoceUnTextoVacio`. |
| CP-17 | Probado a mano | 17/11/2026, tablet real: crear, escribir (teclado y a mano), formato, mover, ancho, eliminar y deshacer, borrar trazos y textos por separado, modos Lápiz y Teclado con dedo y lápiz, persistencia al cambiar de página y al reabrir. La conversión a mano tarda cerca de 1 s en mostrar la línea roja en los dos modos: la dibuja el teclado de Android, no la app. |
| CP-01 | Probado a mano | 22/11/2026, tablet real (repaso de cierre de la v1): crear, renombrar y eliminar un cuaderno (con confirmación; borrado suave); persiste al cerrar y reabrir la app. |
| CP-02 | Probado a mano | 22/11/2026, tablet real: la presión cambia el grosor; varios colores y grosores, resaltador, borrador; deshacer 20 veces y rehacer 20 veces en orden; todo persiste al cerrar y reabrir la app. |
| CP-03 | Probado a mano | 22/11/2026, tablet real: la palma apoyada al escribir no deja marcas; un dedo desplaza y dos hacen zoom; el dedo nunca dibuja. |
| CP-04 | Probado a mano | 22/11/2026, tablet real: texto con acentos y ñ con el teclado en pantalla y con un teclado físico; negrita, título y lista; persiste al cerrar y reabrir la app. |
| CP-05 (sin fondos) | Probado a mano | 22/11/2026, tablet real: agregar, duplicar (con trazos y textos), reordenar («Mover ◀ / ▶») y eliminar páginas con confirmación; persiste. Los fondos rayado y cuadriculado son de la v1.x (RF-08 no está en la v1). |
| CP-08 | Probado a mano | 22/11/2026, tablet real: escribir un trazo o un texto y cerrar la app de inmediato, varias veces cada uno. Los textos se guardaron siempre; **una vez se perdió el último trazo**. La app lo guarda en cuanto recibe el trazo terminado, pero `androidx.ink` lo entrega una fracción de segundo después de levantar el lápiz; si la app muere justo ahí, ese trazo no llega. Dentro del criterio («salvo el último instante»); observación para Leyni. |
| CP-06 | Probado a mano | 18/11/2026, tablet real (y prototipo en Windows con `PdfBoxEngineTest`, 7 pruebas), con PDF de 48 y de 1702 páginas: importar (el mismo PDF otra vez no se duplica), pasar páginas, zoom con dos dedos (nítido al soltar), «Ajustar», «Ir a…» y barra deslizable, reabrir en la última página leída (también tras cerrar la app), eliminar con borrado suave y recuperar al reimportar. Pruebas automáticas: `DocumentImportTest` (11), `LibraryViewModelTest` (8) y `ReaderViewModelTest` (15). |
| CP-09 | Probado a mano | 22/11/2026, tablet real: respaldo de 170 MB en Descargas → **desinstalar** la app → reinstalar → restaurar. Base de datos idéntica tabla por tabla (huella SHA-256 de 5 cuadernos, 12 páginas, 543 trazos, 29 textos, 2 documentos y 1825 filas de `change_log`) y los 2 PDF idénticos. Un archivo falso se rechaza al revisarlo y uno cortado a la mitad se rechaza al prepararlo, **sin tocar los datos** (comprobado con la misma huella). También: restaurar y «Volver a como estaba» con un cuaderno de prueba. El respaldo de una versión más nueva se cubre solo en `BackupServiceTest`. |

Además: `EntitiesTest.kt` (8 pruebas) cubre los metadatos comunes y el borrado suave.

---

## 6. Escenarios de sincronización y conflictos (v2)

Cada escenario se prueba **en ambos sentidos**. La lógica de fusión se prueba primero **como función pura**, con dos bases de datos en memoria que simulan los dos dispositivos.

| ID | Escenario | Resultado esperado |
|---|---|---|
| CS-01 | Solo un dispositivo hizo cambios | El otro recibe todo. |
| CS-02 | Cambios en páginas distintas | Fusión automática. |
| CS-03 | Trazos o anotaciones en la misma página | Se unen todos. |
| CS-04 | Mismo bloque de texto editado en ambos lados | La página se duplica, se marca y el usuario elige. |
| CS-05 | Uno borró; el otro editó | **Gana la edición**: se restaura y se avisa. |
| CS-06 | Ambos renombraron el mismo cuaderno | Gana el más reciente según el HLC. |
| CS-07 | Relojes de los dispositivos desfasados | La fusión es correcta. |
| CS-08 | Corte del Wi-Fi a mitad de la sincronización | No hay corrupción y se reanuda sin duplicar. |
| CS-09 | Primera sincronización con el perfil "5 años" | Termina, muestra el progreso y los datos quedan idénticos. |
| CS-10 | Sincronizar dos veces seguidas sin cambios | La segunda no transfiere nada. |
| CS-11 | Un dispositivo no emparejado intenta conectarse; luego se revoca el emparejamiento | Se rechaza en ambos casos. |
| CS-12 | Restaurar un respaldo antiguo y luego sincronizar | No reaparecen elementos borrados ni se pierden los recientes. **Riesgo alto.** |
| CS-13 | Mismo PDF importado en ambos dispositivos | Se detecta por su hash y no se duplica. |
| CS-14 | Mucho tiempo sin sincronizar | RF-25 avisa. |
| CS-15 | Red con aislamiento de clientes | Mensaje claro, sin bloqueos. |
| CS-16 | Abrir la app en la PC con la tablet en la red | Sincroniza sola. |
| CS-17 | Reimportación en un dispositivo y luego sincronización | El otro recibe la versión nueva y la capa. |
| CS-18 | Emparejamiento por QR desde cero; QR caducado o ya usado | Se completa con un QR válido; se rechaza un QR caducado o reutilizado. |
| CS-19 | DOCX convertido en la PC (RF-28) y luego sincronización | La tablet recibe solo el PDF. |

**Verificación de integridad:** comparar el resumen (hash) de cada cuaderno en ambos dispositivos.

---

## 7. Pruebas no funcionales

### 7.1 Latencia del lápiz (RNF-03), prueba manual

1. Grabar en **cámara lenta** (240 fps o más) el lápiz trazando una línea rápida.
2. Contar los cuadros entre la punta y el final de la tinta (cada cuadro ≈ 4,2 ms a 240 fps).
3. Repetir al menos 10 veces y obtener la mediana.
4. Medir lo mismo con **Nebo (MyScript Notes)** como **línea base** (referencia elegida por Leyni y el dueño, 17/11/2026), en la misma tablet. Medir la pantalla real del cuaderno, no la de prueba `ComposeInkScreen`.
5. Complementar con el profiler de Android Studio (o Perfetto): tiempos de cuadro y cuadros perdidos.
6. Repetir con una página de unos 2.000 trazos.

### 7.2 Rendimiento y volumen (RNF-04, RNF-17, RNF-18, RNF-21)

Con el perfil "5 años", medir:
- tiempo de arranque;
- tiempo para abrir un cuaderno grande;
- desplazamiento por la lista de cuadernos;
- tiempo de búsqueda;
- apertura de un PDF de 500 páginas;
- sincronización de un día;
- uso de memoria.

Además, medir la fluidez de la interfaz durante una conversión de DOCX (CP-16).

### 7.3 Fiabilidad (RNF-06, RNF-19)

- Forzar el cierre durante el guardado, la importación, la reimportación, la exportación o impresión, la conversión, el respaldo y la sincronización.
- Llenar el almacenamiento.
- Probar con batería baja y con reinicio del dispositivo.

### 7.4 Compatibilidad (RNF-01, RNF-09)

- Android 16 en vertical y horizontal.
- Windows 11 con la ventana redimensionada y el escalado de pantalla al 125 % y 150 %.

### 7.5 Seguridad y privacidad (RNF-07, RNF-20)

- Verificar que no haya conexiones a internet.
- Comprobar con un analizador de tráfico de red que no se vean datos en claro.
- Una conexión con un certificado no fijado debe ser rechazada.
- La revocación del emparejamiento debe funcionar.

### 7.6 Usabilidad (RNF-08)

Pruebas con el dueño sin ayuda, cronometradas, en estas tareas:
- crear un cuaderno;
- escribir;
- importar un PDF;
- seguir la guía de DOCX;
- reimportar un documento;
- imprimir;
- emparejar los dispositivos;
- sincronizar;
- resolver un conflicto.

### 7.7 Manual de usuario (RF-F2, v3)

Una persona sin ayuda sigue el manual de la app para activar el hotspot y unirse a una sesión, y lo logra.

### 7.8 Entorno (RNF-22)

Toda tarea de construcción y prueba documentada se ejecuta en la PC con ESET usando `cmd` o Git Bash, sin PowerShell.

---

## 8. Pruebas de aceptación (con el dueño)

| ID | Historia o guion | Aceptado si... |
|---|---|---|
| PA-01 | "Tomo apuntes de una clase de 2 horas con el lápiz en la tablet." | Sin quejas de retraso ni de marcas de la palma, y sin pérdidas. |
| PA-02 | "Abro el PDF del curso, subrayo y anoto." | Las anotaciones persisten. |
| PA-03 | "Edito un documento en Word, lo guardo como PDF y lo reimporto." | Ve la versión nueva y decide qué hacer con sus anotaciones. |
| PA-04 | "Hago un respaldo y lo restauro." | No falta nada. |
| PA-05 | "Imprimo mis apuntes de la semana y un PDF anotado." | La impresión coincide con lo que ve en pantalla. |
| PA-06 (v2) | "Llego a casa, abro la app en la PC y tengo los apuntes del día." | Sincroniza sola y los datos están completos. |
| PA-07 (v2) | "Edité lo mismo en la tablet y en la PC sin sincronizar." | Ve ambas versiones y elige sin perder nada. |
| PA-08 (v2) | "Arrastro un DOCX a la app de la PC." | Se convierte solo y aparece en la tablet. Si no hay LibreOffice, recibe instrucciones claras. |
| PA-09 (v3) | "Hacemos una lluvia de ideas con dos amigos usando mi hotspot." | Se conectan siguiendo el manual y cada uno ve los trazos de los demás. |
| PA-10 (v1, etapa 7) | "Recorro todas las pantallas de la app con el diseño nuevo, en vertical y horizontal." | Se cumplen los criterios de cierre de la etapa en `docs/etapa-7-diseno.md` §9: el dueño está conforme con cada pantalla; ninguna queda "Diferida (v1)" ni hay un `PQ-` de diseño abierto; existen `docs/diseno.md`, el `.fig`, los PNG y las capturas de antes y después. |

---

## 9. Matriz de trazabilidad

| Requisito | Casos |
|---|---|
| RF-01 | CP-01, CU-03, CU-04 |
| RF-02 | CP-02, CU-02, P-0, PA-01 |
| RF-03 | CP-03, P-0, PA-01 |
| RF-04 | CP-04, CU-09 |
| RF-05 | CP-17, CU-09 |
| RF-08 | CP-05, CU-03 |
| RF-09 | CP-12 |
| RF-10, RF-12 | CP-06, P-1 |
| RF-11 | CP-10, PA-03 |
| RF-13 | CP-07, PA-02 |
| RF-16 | CP-08 |
| RF-17 | CP-09, CU-05, CU-05b, PA-04 |
| RF-18, RF-27 | CP-14, CP-15, PA-05, P-2 |
| RF-21 | CP-13 |
| RF-22 a RF-25 | CS-01 a CS-18, PA-06, PA-07 |
| RF-26 | CP-11, CS-17, PA-03 |
| RF-28 | CP-16, CS-19, PA-08 |
| RF-F1 | PA-09 |
| RF-F2 | 7.7, PA-09 |
| PC-03 (diseño visual) | CP-17, PA-10 |
| RNF-03 | 7.1, P-0 |
| RNF-04, RNF-17, RNF-18 | 7.2, CS-09, P-1 |
| RNF-06, RNF-19 | 7.3, CS-05, CS-08, CS-12 |
| RNF-01, RNF-09 | 7.4 |
| RNF-07, RNF-20 | 7.5, CS-11, CS-18, P-3 |
| RNF-08 | 7.6 |
| RNF-11 | CU-01, CU-04, CU-06 |
| RNF-17 (orden de listas) | CU-07 |
| RNF-13 | CU-02 |
| RNF-21 | CP-16, 7.2 |
| RNF-22 | 7.8 |

---

## 10. Criterios de entrada, salida y defectos

**Entrada (para probar una funcionalidad):**
- compila para Android y escritorio (`gradlew.bat build`);
- las pruebas unitarias del módulo existen y pasan.

**Salida de una versión:**
- 100 % de los casos de prioridad M pasan;
- ningún defecto crítico abierto;
- aceptación del dueño registrada en `docs/requisitos.md`.

**Severidad de defectos:**
- **Crítico:** pérdida o corrupción de datos, cierre inesperado al escribir, o datos legibles en la red.
- **Alto:** la función no sirve, o la interfaz se congela.
- **Medio:** hay una alternativa para seguir.
- **Bajo:** problema visual.

**Regla:** cualquier pérdida de datos es crítica.

---

## 11. Riesgos de prueba

- El emulador no reproduce bien el lápiz; las pruebas del lápiz se hacen siempre en la tablet real.
- La lógica de fusión debe ser pura y probarse con pruebas unitarias, simulando dos dispositivos en memoria.
- Las redes universitarias aíslan a los dispositivos entre sí; se mitiga con el hotspot y el manual.
- El volumen real puede superar la estimación; repetir 7.2 cada semestre.
- La reimportación puede desalinear las anotaciones.
- `PdfRenderer` de Android puede tener limitaciones con PDF complejos o protegidos (P-1).
- La fidelidad de LibreOffice con DOCX complejos (sobre todo ecuaciones) puede variar.
- Sin impresora disponible, usar "Microsoft Print to PDF" como sustituto.
- Una herramienta nueva podría depender de PowerShell sin avisarlo; verificar en la PC antes de adoptarla (7.8).
