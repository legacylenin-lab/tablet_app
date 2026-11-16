# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 2, estructura y modelo de datos (ver `requisitos.md` §7). La parte de datos está casi completa; falta fijar decisiones con Leyni.
**Última actualización:** 16/11/2026 (cierre de la primera sesión de trabajo)

## Hecho
- **Etapa 1 completa:** proyecto base KMP (Android + Desktop) corriendo en el emulador y en Windows.
- **Módulos creados:** `core:model`, `core:domain`, `core:data`.
- **`core:model`:** HLC, claves de orden (`SortKey`), codificación de trazos (`StrokeCodec`) y las entidades con sus metadatos comunes (CU-01, CU-02, CU-03).
- **`core:data` (SQLDelight 2.4.1):** esquema v1 completo, `change_log` en la misma transacción que cada escritura, reloj HLC guardado. Repositorios de cuadernos, páginas (con duplicado), trazos, bloques de texto, documentos y anotaciones, con borrado suave y "deshacer" donde corresponde (CU-04).
- **Respaldo:** exportar a ZIP con manifiesto y checksums, inspeccionar y verificar antes de restaurar; rechaza archivos dañados, versiones futuras y rutas peligrosas (CU-05, parcial).
- **Pruebas:** 109 pruebas propias (41 en `core:model` y 68 en `core:data`), todas en verde. Commits al día en GitHub.
- **Documentos:** `pendientes-a-consultar.md` creado para Leyni (17 puntos). Versiones de SQLDelight, coroutines y serialization anotadas en `arquitectura.md` §11.

## En curso
- Nada abierto: todo está guardado.

## Siguiente
- **Cerrar la etapa 2:** que Leyni revise `docs/pendientes-a-consultar.md`, sobre todo PC-01 (nombres de módulos), PC-02 (tipo `Uuid` o texto), PC-03 (paquete) y PC-15 (`minSdk` y `VACUUM INTO`). Algunos pueden cambiar código existente.
- Mientras tanto, pasos pequeños posibles: pruebas de la base de datos en Android (PC-10), copia de seguridad automática y el intercambio final de la restauración (queda para la etapa 6).
- **Etapa 3:** lienzo con lápiz en la tablet real (prototipo P-0). Requiere probar en la tablet con depuración por USB (`docs/entorno.md`).

## Bloqueos o pendientes
- **Para Leyni (lint):** `./gradlew build` falla solo en `:androidApp:lintDebug` por la regla `PropertyEscape` sobre `local.properties`, aunque el archivo usa el formato estándar de Android Studio. Con `-x :androidApp:lintDebug` el build termina bien (así se compila hasta ahora). Ver PC-05.
- `sdk.dir` en `local.properties` estaba vacío tras copiar el proyecto base; se completó a mano (archivo local, no se sube a Git).
- Lo que solo se puede comprobar en la tablet real: lápiz, latencia y rendimiento (etapa 3 en adelante).
