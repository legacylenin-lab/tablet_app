# Entorno de desarrollo

> Referencia para el asistente y el dueño. La PC tiene **ESET Endpoint Security, que bloquea PowerShell**: nada de este proyecto puede depender de él (RNF-22).

## Quién usa qué

| Herramienta | La usa | Para qué |
|---|---|---|
| **Claude Desktop, pestaña Code** | El dueño | Conversar con el asistente, que lee y escribe el código de esta carpeta |
| **Android Studio** (con el plugin Kotlin Multiplatform) | El dueño | Ver el código y ejecutar la app con el botón ▶ |
| **GitHub Desktop** | El dueño | Guardar (commit) y subir (push) el trabajo |
| **Git para Windows** | Solo el asistente, por debajo | Le da a Claude Code una terminal (Git Bash) que no es PowerShell. **Debe estar instalado**, aunque el dueño nunca lo abra. |
| SDK de Android (API 36 o 37) y emulador de tablet | Android Studio | Probar sin la tablet |

El dueño **no usa terminales**. Los comandos los ejecuta el asistente.

## Abrir el proyecto con el asistente

1. Abre **Claude Desktop** y entra en la pestaña **Code**.
2. Elige la carpeta del proyecto: `D:\dev\Tablet_app`.
3. Escribe "hola". El asistente sabe por dónde seguir.

**Configuración del asistente**
- `.claude/settings.json` desactiva la herramienta de PowerShell (`CLAUDE_CODE_USE_POWERSHELL_TOOL=0`) y bloquea los comandos peligrosos de Git.
- Si el asistente dice que no encuentra Git Bash, crea `.claude/settings.local.json` (no se sube al repositorio) con este contenido:
  ```json
  { "env": { "CLAUDE_CODE_GIT_BASH_PATH": "C:\\Program Files\\Git\\bin\\bash.exe" } }
  ```

## Ejecutar la app (el dueño, desde Android Studio)

- **Abrir el proyecto:** *File → Open* → `D:\dev\Tablet_app`. La primera vez, espera a que termine la sincronización de Gradle (la barra de abajo).
- **En el emulador o la tablet:** arriba, en el selector de configuraciones, elige la de Android (`composeApp`) y el dispositivo, y pulsa ▶.
- **En Windows:** elige la configuración de escritorio (`composeApp [desktop]` o similar) y pulsa ▶.

## Comandos (los ejecuta el asistente, en Git Bash)

| Acción | Comando |
|---|---|
| Compilar todo y ejecutar las pruebas | `./gradlew build` |
| Solo las pruebas | `./gradlew allTests` |
| Ejecutar la app de escritorio | `./gradlew :composeApp:run` |
| Instalar en el emulador o la tablet conectada | `./gradlew :composeApp:installDebug` |

## Crear el proyecto base (etapa 1)

El repositorio todavía no tiene el código base. Lo crea el dueño, guiado por el asistente:
1. En **kmp.jetbrains.com**: escribe el nombre del proyecto, marca **Android** y **Desktop** (sin iOS, Web ni Server), con la interfaz compartida, y descarga el ZIP.
2. Descomprime el ZIP. Dentro hay una carpeta con el nombre del proyecto: copia **su contenido** (no la carpeta) a `D:\dev\Tablet_app`, junto a `CLAUDE.md` y `docs/`.
3. Si Windows pregunta por reemplazar `.gitignore` o `README.md`, elige **no reemplazar**. El asistente después fusiona lo que haga falta del `.gitignore` del asistente web de JetBrains.
4. El asistente compila (`./gradlew build`) para comprobar que todo funciona.
5. El dueño abre el proyecto en Android Studio y ejecuta la versión de escritorio y la de Android con ▶.
6. Commit en GitHub Desktop: `Agrega proyecto base Kotlin Multiplatform`.

## Tablet real

- Activa las opciones de desarrollador: en *Ajustes → Acerca de la tablet*, toca 7 veces "Número de compilación".
- En *Ajustes → Sistema → Opciones de desarrollador*, activa la **Depuración por USB**.
- Conéctala por USB y acepta el aviso en la tablet. Aparecerá en el selector de dispositivos de Android Studio.
