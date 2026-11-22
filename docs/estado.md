# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 6 terminada (respaldo y restauración, probado con desinstalación). Siguen los ajustes que aprobó Leyni (PC-02 y PQ-25) y luego la etapa 7, diseño visual, que cierra la v1.
**Última actualización:** 22/11/2026

## Hecho
- **Etapa 1:** proyecto base KMP (Android + Desktop) corriendo en el emulador y en Windows.
- **Etapa 2, módulos:** `core:model`, `core:domain`, `core:data`.
- **`core:model`:** `EntityId`, HLC, claves de orden (`SortKey`), `TitleSort` (orden en español), codificación de trazos (`StrokeCodec`) y las entidades con sus metadatos comunes.
- **`core:domain`:** contratos de los repositorios (cuadernos, páginas, tinta, texto, documentos, anotaciones) y el generador de ids.
- **`core:data` (SQLDelight 2.4.1):** esquema v1 con `title_sort`, `change_log` en la misma transacción que cada escritura, reloj HLC guardado, los seis repositorios con borrado suave, y el respaldo (exportar, inspeccionar y verificar).
- **Ajustes aprobados por Leyni (17/11/2026), todos implementados:** `minSdk = 30` (PQ-15), lint sin `-x` (PQ-05), `EntityId` (PQ-02, PQ-12), `title_sort` (PQ-13) y prueba del respaldo en el emulador (PQ-10).
- **Pruebas:** más de 140 pruebas propias en verde, más una prueba instrumentada que corre en el emulador Android (`./gradlew :core:data:connectedAndroidDeviceTest`, con un emulador encendido). `./gradlew build` pasa completo.

- **Etapa 3, hasta ahora:** prototipo P-0 con `androidx.ink` 1.0.0 (adoptada); hoja A4 donde solo escribe el lápiz; el doble toque del botón del lápiz alterna lápiz/borrador; los trazos se guardan en la base de datos al soltar el lápiz y se cargan al abrir la app; borrado suave con deshacer y rehacer. Probado a mano en la tablet real (Lenovo TB336ZU, Android 16) y con 7 pruebas instrumentadas.
- **Barra de herramientas (RF-02), probada en la tablet:** lápiz y resaltador con 6 y 4 colores y varios grosores; cada trazo guarda herramienta, color y grosor y se recarga igual. La librería de tinta pide la brocha vigente en cada trazo (`nextBrush`).
- **Dedo (RF-03), probado en la tablet:** un dedo desplaza, dos hacen zoom (100% a 500%), el dedo nunca escribe y con el lápiz apoyado se ignora la palma; botón "Ajustar". La capa de tinta mide lo que la pantalla (si midiera lo que la hoja ampliada, la app se quedaba sin memoria).
- **Etapa 4, cuadernos (RF-01), probado en la tablet:** pantalla "Mis cuadernos" al abrir la app (crear, renombrar, eliminar con borrado suave, abrir); cada cuaderno abre su primera página en el lienzo y conserva sus trazos. Pendiente en la etapa 4: poner el texto en la página (depende de PC-01: texto y lápiz juntos, o páginas de texto aparte), y reordenar páginas.
- **Varias páginas por cuaderno, probado en la tablet:** «Página nueva» (después de la actual), flechas ◀ ▶ y «Página X de N»; cada página guarda sus trazos. Los guardados corren en un alcance que vive con la app (`AppServices.saveScope`), para que no se cancelen al cambiar de página.
- **Texto enriquecido (RF-04), paso 1 probado en la tablet:** `compose-rich-editor` 1.2.1 adoptada (negrita, cursiva, subrayado, título, listas); `RichTextHtml` deja el HTML en el formato acordado, con prueba de ida y vuelta. Pantalla de prueba provisional («Probar texto»).
- **Duplicar y eliminar páginas (parte de RF-08, v1.x), probado en la tablet.** Reordenar páginas y los fondos rayado/cuadriculado siguen pendientes.
- **Propuesta PC-01** (texto y lápiz en la misma página): **aprobada por Leyni**. RF-05 pasa a la v1 (requisitos v1.2).
- **Gesto «atrás» (PQ-23) y botón del lápiz con un solo toque (PQ-20)**, probados en la tablet.
- **ViewModels en código común (PQ-24), probado en la tablet:** `NotebookListViewModel`, `NotebookPagesViewModel` e `InkPageViewModel` (`StateFlow` y eventos). Los de cada cuaderno y cada página se descartan al salir (`ScopedViewModels`), hasta que llegue Navigation Compose. En `androidMain` queda solo lo propio de la tinta.
- **Textos sobre la hoja (RF-05), probado en la tablet:** «Agregar texto» crea un cuadro (`TextBlocksViewModel`, `PageTextBlock`) que no se encima con otros; se guarda solo al dejar de teclear; un cuadro vacío se borra al terminar. Se edita tocándolo con el dedo; el lápiz escribe tinta encima de los textos que no se editan. Al editar: barra «✥ Mover», franja ↔ para el ancho, barra de formato deslizable y modo **Lápiz** (inicial: a mano, Android lo convierte en texto) o **Teclado**, que se cambia con dedo o lápiz. Deshacer/Rehacer siguen el orden de trazos y textos (`UndoableAction`); «Borrar trazos» y «Borrar textos» son botones aparte.
- **Etapa 4 completa (18/11/2026):** reordenar páginas con «Mover ◀ / ▶» (RF-08) y se quitó la pantalla provisional «Probar texto».
- **Etapa 5, Biblioteca (RF-10, RF-12), probada en la tablet con PDF de 48 y de 1702 páginas:** botón «Biblioteca» en «Mis cuadernos». «Importar PDF» (selector del sistema, solo PDF) copia el archivo a `documents/<sha256>.pdf` (`FileDocumentStore`), comprueba que sea PDF, cuenta páginas y lo registra (`DocumentRepository.import`, `ImportResult` como en `arquitectura.md` §7); el mismo PDF no se duplica. Eliminar con borrado suave; al reimportar vuelve con su página. Lector (`ReaderViewModel`): página a lo ancho, ◀ ▶, «Ir a…» con número, barra deslizable (salta al soltar), zoom con dos dedos (`PageViewport`, 100%–500%) que redibuja nítido solo el rectángulo visible (`PdfEngine.renderRegion`), «Ajustar», y recuerda la última página (se guarda tras 1 s quieto).
- **Motor de PDF:** `AndroidPdfEngine` (`PdfRenderer`). Medido (P-1): abrir 0,06 s, primera página 0,3 s, cambio de página 0,22 s con 1702 páginas (`adb logcat -s PdfTiming`).
- **Toda la app deja libre el espacio de la hora y de la barra de íconos de Android** (`App.kt`): antes la barra deslizable quedaba escondida.
- **Windows, prototipo P-1:** `PdfBoxEngine` (PDFBox 3.0.8, en validación) y `desktopApp` muestra la Biblioteca y el lector **sin guardar nada** (base en memoria, PDF en carpeta temporal). Los PDF de texto se ven bien; los escaneados con JPEG 2000 salen en blanco (PQ-27). Faltan zoom con el mouse, selección de texto y búsqueda.
- **Etapa 6, respaldo y restauración (RF-17), probada en la tablet (22/11/2026):** pantalla «Respaldo» desde «Mis cuadernos». «Crear respaldo» guarda todo en un `.cuaderno-backup` donde elija el usuario (selector de Android). «Restaurar desde un archivo…» revisa el respaldo (fecha, número de PDF), pide confirmación, lo verifica entero, guarda una copia de lo actual (`files/backups/antes-de-restaurar.cuaderno-backup`) y reinicia la app; al arrancar, `RestoreInstaller` cambia la base y los PDF con la base todavía cerrada. «Volver a como estaba» deshace la última restauración. Interfaz delgada `BackupService` en `core:domain` (PQ-14) e intercambio final (PQ-17), los dos hechos. `AppContainer` es uno solo por proceso (antes se rearmaba al girar la tablet).
- **Pruebas de cierre de la v1 (22/11/2026), en la tablet:** CP-01 a CP-05, CP-08 y CP-09 (respaldo → desinstalar → reinstalar → restaurar, con datos idénticos tabla por tabla; respaldos falso y dañado rechazados sin tocar nada). Latencia comparada con Nebo y aceptada por Leyni (PQ-21).
- **Tercera revisión de Leyni (22/11/2026):** aprobó PC-02 (candado), PC-03 (diseño visual como etapa 7), PQ-25 (`restore` en los textos), PQ-26 (RNF-04: 2 s o menos) y PQ-27 opción (a) (probar `jai-imageio-jpeg2000` en Windows); aceptó PQ-21.
- **Pruebas:** 293 pruebas propias en verde en la PC (`./gradlew build`), más las instrumentadas de la tinta en la tablet (`./gradlew :shared:connectedAndroidDeviceTest`).

## En curso
- Nada abierto en el código: todo está guardado. Falta guardar el cierre de esta sesión (documentos).

## Siguiente
- **Ajustes aprobados por Leyni el 22/11/2026**, en este orden y con un commit por idea (el dueño tiene tiempo: empezar por aquí en la próxima sesión):
  1. **PC-02, candado de la vista:** un estado «bloqueado» en `InkPageViewModel`/`NotebookPagesViewModel` y en `ReaderViewModel`. Con el candado activado, el dedo no desplaza, no hace zoom ni cambia de página, y se bloquean ◀ ▶, «Ir a…», la barra deslizable y «Ajustar»; el lápiz sigue escribiendo. Con pruebas en `commonTest` y probado en la tablet.
  2. **PQ-25:** `TextBlockRepository.restore(id)` (`arquitectura.md` §7), usado al deshacer el borrado de un texto, con su prueba.
  3. **PQ-27 (no bloquea la v1):** probar `jai-imageio-jpeg2000` en `jvmMain`. Antes, revisar el texto de su licencia y anotarlo en PQ-27; después, medir con el libro de 1702 páginas.
- **Diseño visual (PC-03, aprobada el 22/11/2026):** es la **etapa 7** y el cierre de la v1. **Leer `docs/etapa-7-diseno.md` antes de empezarla.** Condiciones de entrada (§1): PC-02 y PQ-25 hechos; código de plantilla y prototipos quitados (PQ-06, PQ-22); `./gradlew build` en verde; etiqueta `v1.0-funcional` (la propone el agente, la crea Leyni o el dueño); app instalada en la tablet y en uso diario. El dueño diseña en Figma y entrega el `.fig`, el enlace y los PNG (§4.3); se cubren todas las pantallas del inventario (§6). Mientras tanto, anotar en §8 de esa guía lo que le moleste al usar la app.
- **Windows (P-1), cuando se decida PQ-27:** zoom con Ctrl + rueda del mouse, selección de texto con el mouse y búsqueda.
- **Textos, detalles conocidos:** la conversión a mano tarda cerca de 1 s en mostrar la línea roja (la dibuja el teclado de Android, igual en los dos modos; no depende de la app). Con el teclado flotante funciona bien; si el teclado acoplado tapara un texto de la parte baja, habrá que desplazar la hoja (aún no visto). Deshacer un texto eliminado lo recrea con un id nuevo: se corrige con PQ-25 (aprobado).
- **Latencia (PQ-21):** aceptada por Leyni el 22/11/2026 por comparación con Nebo; no hace falta más para cerrar la v1.
- Al inicio de cada etapa: revisar si ya hay una versión estable de Compose Material 3 (PQ-04b).
- Para la v1.x: búsqueda de texto con FTS (PQ-18).

## Bloqueos o pendientes
- **Revisión de Leyni (17/11/2026):** aprobó PC-01 (texto y lápiz en la misma página, en la v1), PQ-19 (se queda `androidx.ink` 1.0.0) y PQ-23 (`BackHandler`). PQ-20: se desactivó en los ajustes de la tablet el panel de Lenovo que capturaba el botón del lápiz; probar que un solo toque alterne lápiz/borrador. PQ-21: `androidx.ink` ya usa búfer frontal, predicción y entrada sin agrupar; Nebo (MyScript iink, comercial) no se adopta; medir contra Nebo. PQ-24 aprobado: ViewModels en código común antes de RF-05 (implementado).
- **Abierto para Leyni:** nada nuevo; todo lo pendiente quedó decidido el 22/11/2026.
- `sdk.dir` en `local.properties` se completó a mano tras copiar el proyecto base (archivo local, no se sube a Git).
- Lo que solo se puede comprobar en la tablet real: lápiz, latencia y rendimiento (etapa 3 en adelante).
