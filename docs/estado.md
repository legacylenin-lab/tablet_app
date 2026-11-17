# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 2 completa (estructura y modelo de datos); lista para empezar la etapa 3 (lienzo con lápiz).
**Última actualización:** 17/11/2026

## Hecho
- **Etapa 1:** proyecto base KMP (Android + Desktop) corriendo en el emulador y en Windows.
- **Etapa 2, módulos:** `core:model`, `core:domain`, `core:data`.
- **`core:model`:** `EntityId`, HLC, claves de orden (`SortKey`), `TitleSort` (orden en español), codificación de trazos (`StrokeCodec`) y las entidades con sus metadatos comunes.
- **`core:domain`:** contratos de los repositorios (cuadernos, páginas, tinta, texto, documentos, anotaciones) y el generador de ids.
- **`core:data` (SQLDelight 2.4.1):** esquema v1 con `title_sort`, `change_log` en la misma transacción que cada escritura, reloj HLC guardado, los seis repositorios con borrado suave, y el respaldo (exportar, inspeccionar y verificar).
- **Ajustes aprobados por Leyni (17/11/2026), todos implementados:** `minSdk = 30` (PQ-15), lint sin `-x` (PQ-05), `EntityId` (PQ-02, PQ-12), `title_sort` (PQ-13) y prueba del respaldo en el emulador (PQ-10).
- **Pruebas:** más de 140 pruebas propias en verde, más una prueba instrumentada que corre en el emulador Android (`./gradlew :core:data:connectedAndroidDeviceTest`, con un emulador encendido). `./gradlew build` pasa completo.

## En curso
- Nada abierto. Falta solo guardar el último commit (prueba del emulador y documentos), si no está ya guardado.

## Siguiente
- **Etapa 3:** lienzo con lápiz en la tablet real (prototipo P-0, `plan-de-pruebas.md`). Requiere probar en la tablet con depuración por USB (`docs/entorno.md`). Aquí se verá por primera vez algo nuevo en pantalla.
- Al inicio de cada etapa: revisar si ya hay una versión estable de Compose Material 3 (PQ-04b).
- Para la etapa 6: último paso de la restauración y la interfaz delgada de `BackupService` en `core:domain` (PQ-14, PQ-17).
- Para la v1.x: búsqueda de texto con FTS (PQ-18).

## Bloqueos o pendientes
- Ninguno.
- `sdk.dir` en `local.properties` se completó a mano tras copiar el proyecto base (archivo local, no se sube a Git).
- Lo que solo se puede comprobar en la tablet real: lápiz, latencia y rendimiento (etapa 3 en adelante).
