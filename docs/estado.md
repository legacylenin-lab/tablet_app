# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 2, estructura y modelo de datos (ver `requisitos.md` §7). La etapa 1 está cerrada.
**Última actualización:** 16/11/2026 (primera sesión con el dueño)

## Hecho
- Entorno validado en la PC: Android Studio, emulador, cuenta de GitHub y GitHub Desktop.
- Etapa 1 completa: proyecto base KMP (Android + Desktop) en la raíz del repositorio, compilado y corriendo en el emulador y en la ventana de Windows. Primer commit hecho y subido.
- Primer cambio visible: el botón de ejemplo dice "¡Hola, mi cuaderno!" (en `shared/.../App.kt`).

## En curso
- Nada abierto. El cambio del botón está pendiente de commit.

## Siguiente
- Etapa 2: estructura de módulos (`core:model`, `core:domain`, `core:data`), HLC, codificación de trazos, esquema SQLDelight y repositorios, con sus pruebas (CU-01 a CU-05).

## Bloqueos o pendientes
- **Para Leyni (lint):** `./gradlew build` falla solo en `:androidApp:lintDebug` por la regla `PropertyEscape` sobre `local.properties` (sdk.dir con la ruta de Windows), aunque el archivo usa el formato estándar de Android Studio. Con `-x :androidApp:lintDebug` el build termina bien. Hay que decidir cómo tratarlo (por ejemplo, excluir esa regla en la configuración de lint).
- `sdk.dir` en `local.properties` estaba vacío tras copiar el proyecto base; se completó a mano (archivo local, no se sube a Git).
