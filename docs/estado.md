# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 4, cuadernos, páginas y texto. Hechos: cuadernos, páginas, ViewModels (PQ-24) y textos sobre la hoja (RF-05). Falta reordenar páginas.
**Última actualización:** 17/11/2026

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
- **Pruebas:** 228 pruebas propias en verde en la PC (`./gradlew build`), más las instrumentadas de la tinta en la tablet (`./gradlew :shared:connectedAndroidDeviceTest`).

## En curso
- Nada abierto: todo está guardado y subido a GitHub.

## Siguiente
- **Etapa 4:** reordenar páginas (parte de RF-08). Después, quitar la pantalla provisional «Probar texto» de la lista de cuadernos, que ya no hace falta.
- **Textos, detalles conocidos:** la conversión a mano tarda cerca de 1 s en mostrar la línea roja (la dibuja el teclado de Android, igual en los dos modos; no depende de la app). Con el teclado flotante funciona bien; si el teclado acoplado tapara un texto de la parte baja, habrá que desplazar la hoja (aún no visto). Deshacer un texto eliminado lo recrea con un id nuevo (PQ-25, para Leyni).
- **Antes de cerrar la v1:** medir la latencia con cámara lenta contra Nebo (PQ-21, `plan-de-pruebas.md` §7.1).
- Al inicio de cada etapa: revisar si ya hay una versión estable de Compose Material 3 (PQ-04b).
- Para la etapa 6: último paso de la restauración y la interfaz delgada de `BackupService` en `core:domain` (PQ-14, PQ-17).
- Para la v1.x: búsqueda de texto con FTS (PQ-18).

## Bloqueos o pendientes
- **Revisión de Leyni (17/11/2026):** aprobó PC-01 (texto y lápiz en la misma página, en la v1), PQ-19 (se queda `androidx.ink` 1.0.0) y PQ-23 (`BackHandler`). PQ-20: se desactivó en los ajustes de la tablet el panel de Lenovo que capturaba el botón del lápiz; probar que un solo toque alterne lápiz/borrador. PQ-21: `androidx.ink` ya usa búfer frontal, predicción y entrada sin agrupar; Nebo (MyScript iink, comercial) no se adopta; medir contra Nebo. PQ-24 aprobado: ViewModels en código común antes de RF-05 (implementado).
- **Abierto para Leyni:** PQ-25 (agregar `restore` al contrato de los textos).
- `sdk.dir` en `local.properties` se completó a mano tras copiar el proyecto base (archivo local, no se sube a Git).
- Lo que solo se puede comprobar en la tablet real: lápiz, latencia y rendimiento (etapa 3 en adelante).
