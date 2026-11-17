# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 2, estructura y modelo de datos (ver `requisitos.md` §7). La parte de datos está casi completa; faltan los ajustes que decidió Leyni.
**Última actualización:** 17/11/2026 (revisión de Leyni)

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

## Revisión de Leyni (17/11/2026)
Leyni revisó `docs/pendientes-a-consultar.md` (ahora con prefijo `PQ-`) y decidió todos los puntos. `docs/arquitectura.md` pasó a la versión 1.1. **Cuéntaselo al dueño en una o dos frases al iniciar la próxima sesión**: se aprobó casi todo, y hay unos ajustes que hacer antes de seguir.

## Siguiente
- **Cerrar la etapa 2 con los ajustes aprobados**, en este orden y con un commit por idea:
  1. **PQ-15:** subir `android-minSdk` a 30 en `gradle/libs.versions.toml`.
  2. **PQ-05:** desactivar solo la regla `PropertyEscape` en la configuración de lint de `androidApp`. Desde aquí, `./gradlew build` debe pasar completo, sin `-x`.
  3. **PQ-02 y PQ-12:** crear `EntityId` en `core:model` (`arquitectura.md` §3.1) y cambiar todas las firmas de entidades, HLC y repositorios. Pruebas CU-06.
  4. **PQ-13:** `TitleSort` en `core:model` y la columna `title_sort` con índice en `notebook` y `document` (`arquitectura.md` §3.5 y §6). Como no hay datos reales, puede ir en el esquema 1. Pruebas CU-07.
  5. **PQ-10:** prueba instrumentada del respaldo en el emulador (CU-05b).
  6. Marcar cada punto como implementado en `pendientes-a-consultar.md` y actualizar el estado de las pruebas en `plan-de-pruebas.md`.
- **Etapa 3:** lienzo con lápiz en la tablet real (prototipo P-0). Requiere probar en la tablet con depuración por USB (`docs/entorno.md`).
- Al inicio de cada etapa: revisar si ya hay una versión estable de Compose Material 3 (PQ-04b).

## Bloqueos o pendientes
- Ninguno bloqueante. El problema de lint (PQ-05) ya está decidido: ver el punto 2 de "Siguiente".
- `sdk.dir` en `local.properties` estaba vacío tras copiar el proyecto base; se completó a mano (archivo local, no se sube a Git).
- Lo que solo se puede comprobar en la tablet real: lápiz, latencia y rendimiento (etapa 3 en adelante).
