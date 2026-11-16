# Pendientes a consultar

> **Qué es este documento:** una lista de puntos que surgieron al construir y que conviene que Leyni revise o decida. **No modifica** `requisitos.md`, `arquitectura.md` ni `plan-de-pruebas.md`: son avisos y preguntas. Si algo de aquí termina cambiando un requisito o un contrato, se registra aparte como propuesta (`requisitos.md` §8).
>
> **Alcance:** la app se divide en la versión **Android** (tablet: dibujo, notas, biblioteca; v1) y la versión **Windows** (PC: ver, escribir texto, subrayar, sincronizar; v2). Cada punto indica a cuál afecta.
>
> **Quién lo mantiene:** el agente agrega filas; Leyni marca la decisión. Estados: **Abierto**, **Decidido**, **Descartado**.

Última actualización: 16/11/2026.

---

## 1. Para decidir (afectan a la estructura)

| ID | Afecta a | Punto | Por qué importa | Estado |
|---|---|---|---|---|
| PC-01 | Ambas | `arquitectura.md` §2 habla de un módulo `composeApp` con `androidMain` y `desktopMain`. El proyecto base de JetBrains lo trae dividido en `shared` (interfaz común), `androidApp` y `desktopApp`. | Sirve igual, pero los documentos y el código no coinciden. Hay que decidir: ajustar el documento al proyecto real, o reorganizar el proyecto. Por ahora se usa la estructura real. | Abierto |
| PC-02 | Ambas | `arquitectura.md` §3 define los identificadores y el `deviceId` como `Uuid`. En el HLC actual el `deviceId` es texto (`String`), para no depender de `kotlin.uuid`. | Si se quiere el tipo `Uuid`, conviene cambiarlo ahora, antes de que haya datos guardados. | Abierto |
| PC-03 | Ambas | `arquitectura.md` §9 dice que el nombre definitivo del paquete se fija en la etapa 2. Se está usando `app.cuaderno.<módulo>` (por ejemplo `app.cuaderno.model`). | Hay que confirmarlo para registrarlo en el documento. | Abierto |
| PC-04 | Ambas | `arquitectura.md` §11 pide completar con versiones exactas de las librerías en la etapa 2. Las versiones del proyecto base (Kotlin, Compose, AGP…) están en `gradle/libs.versions.toml`. | Falta decidir cuáles se fijan como oficiales (en particular Compose Material 3 está en versión alfa). | Abierto |

## 2. Del entorno (afectan a cómo se compila)

| ID | Afecta a | Punto | Por qué importa | Estado |
|---|---|---|---|---|
| PC-05 | Android | `./gradlew build` falla solo en `:androidApp:lintDebug` por la regla `PropertyEscape` sobre `local.properties` (la ruta de `sdk.dir`), aunque el archivo usa el formato estándar de Android Studio. Con `-x :androidApp:lintDebug` todo termina bien. | `CLAUDE.md` pide ejecutar `./gradlew build` antes de decir "listo". Hay que decidir si se excluye esa regla en la configuración de lint o cómo se trata. | Abierto |
| PC-06 | Ambas | El proyecto base trae una pantalla de ejemplo ("¡Hola, mi cuaderno!") y archivos de ejemplo en `shared` (`Greeting`, `Platform`…). | Se reemplazan al construir la interfaz real (etapa 3 en adelante). No hay riesgo; solo aviso de que son provisionales. | Abierto |

## 3. Ya previstos en los documentos (solo recordatorio)

| ID | Afecta a | Punto | Dónde está |
|---|---|---|---|
| PC-07 | Windows (v2) | Cómo convive un respaldo restaurado con la sincronización sin resucitar elementos borrados (CS-12). Por eso los borrados suaves no se purgan en la v1. | `arquitectura.md` §5.3 |
| PC-08 | Ambas | Los prototipos P-1 (PDF), P-2 (exportación) y P-3 (cifrado) validan librerías antes de adoptarlas. | `plan-de-pruebas.md` |
| PC-09 | Android | El lápiz y la latencia solo se pueden comprobar en la tablet real, no en el emulador. | `arquitectura.md` §8 |
