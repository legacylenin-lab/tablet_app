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

| PC-10 | Ambas | Las pruebas de la base de datos (`core:data`) están en `jvmTest`, no en `commonTest` como pide `arquitectura.md` §9, porque necesitan el driver de SQLite de escritorio. La lógica sigue siendo común. | Si se quiere correrlas también en Android (emulador), hay que montar un driver de pruebas por plataforma. | Abierto |
| PC-11 | Ambas | Al armar el esquema SQL se ajustaron dos tipos del modelo para que coincidan con `arquitectura.md` §6: `Notebook.color` pasó de texto a entero (ARGB) y `Stroke.baseWidth` de entero a decimal (`REAL`). | Son detalles internos, pero conviene que Leyni los confirme. | Abierto |
| PC-12 | Ambas | Los repositorios usan identificadores de tipo texto (`String`) en vez de `Uuid` (ver PC-02). | Cambiarlo después implica tocar todas las firmas. | Abierto |

| PC-13 | Ambas | La biblioteca se ordena por título con `COLLATE NOCASE` de SQLite, que solo ignora mayúsculas en letras sin acento: "Álgebra" queda después de "Zebra". | Para un orden correcto en español hay que ordenar en Kotlin (con `Collator`) o definir un criterio propio. Se decide al construir la pantalla de biblioteca (etapa 5). | Abierto |

| PC-14 | Ambas | El respaldo está escrito una sola vez para Android y Windows, en un conjunto de código compartido (`jvmCommonMain` en `core:data`). Por eso `BackupService` vive en `core:data` y trabaja con flujos de bytes de Java, no como la interfaz `BackupService` con `PlatformFile` que sugiere `arquitectura.md` §7. La semántica es la misma (exportar, inspeccionar, restaurar). | Si se prefiere la interfaz en `core:domain`, se puede añadir encima sin tocar la lógica. | Abierto |
| PC-15 | Android | El respaldo usa `VACUUM INTO` (como pide §5.3). SQLite de Android lo soporta desde Android 11 (API 30); el proyecto declara `minSdk = 24`. La tablet (Android 16) lo soporta; un Android 7 a 10 no. | Hay que decidir si se sube `minSdk` a 30 o se acepta que esas versiones no puedan hacer respaldos. | Abierto |
| PC-16 | Ambas | El manifiesto guarda la fecha como milisegundos (`createdAtMillis`) y no como texto `createdAt` ISO, para no depender de `java.time` en Androids viejos. | Cambio menor respecto al ejemplo de §5.3; conviene confirmarlo. | Abierto |
| PC-17 | Ambas | Restaurar se hace en dos tiempos: ya se verifica y se prepara todo en una carpeta aparte; falta el último paso (cerrar la base, intercambiar archivos, reabrir y guardar antes un respaldo automático). Ese paso necesita la app armada (etapa 6). | Es trabajo pendiente de la etapa 6, no una decisión; se anota para no olvidarlo. | Abierto |

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
