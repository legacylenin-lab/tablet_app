# Guía de Git con GitHub Desktop

> Para el dueño de la app. El asistente te dirá **cuándo** guardar y te dará el mensaje ya escrito; esta guía te explica **cómo**.

## La idea en una frase

Un **commit** es un **punto de guardado**, como en un videojuego. Si algo se rompe, puedes volver a un punto anterior. El **push** sube tus puntos de guardado a GitHub: es tu copia de seguridad en internet, y así Leyni puede revisar el proyecto.

## Cuándo guardar

- **Commit:** cada vez que algo funcione (el asistente te avisará).
- **Push:** al terminar la sesión, o cuando quieras tener la copia en internet.

## Cómo hacer un commit

1. Abre **GitHub Desktop**. Arriba a la izquierda debe decir el nombre del repositorio de la app.
2. En la columna izquierda verás los archivos que cambiaron. Haz clic en uno para ver qué cambió: en verde lo nuevo, en rojo lo quitado.
3. Abajo a la izquierda, en **Summary**, pega el mensaje que te dio el asistente. Por ejemplo: `Cambia el título de la ventana`.
4. Pulsa **Commit to main**.

## Cómo subir a GitHub (push)

- Arriba aparecerá **Push origin** con un número: los commits pendientes de subir. Púlsalo.
- Cuando el número desaparezca, ya está en GitHub.

## Cómo deshacer

| Situación | Qué hacer |
|---|---|
| Cambié un archivo y quiero dejarlo como estaba (todavía sin commit) | Clic derecho sobre el archivo en GitHub Desktop → **Discard changes**. |
| Hice un commit y me arrepentí (todavía sin push) | Pestaña **History** → clic derecho en el commit → **Undo commit**. Los cambios vuelven a la lista y puedes descartarlos o corregirlos. |
| Algo se rompió y no sé qué pasó | **No borres nada.** Cuéntaselo al asistente: él revisa el historial y te ayuda a volver al último punto que funcionaba. |

## Lo que NUNCA va al repositorio

- Tus PDF, apuntes o respaldos personales.
- Contraseñas, claves o certificados.

El archivo `.gitignore` ya excluye lo habitual. Si GitHub Desktop te muestra un archivo raro o muy pesado, pregúntale al asistente antes de guardar.

## Si GitHub Desktop pide iniciar sesión

Usa **tu** cuenta de GitHub, nunca la de otra persona. En *File → Options → Accounts* puedes comprobar con qué cuenta estás.
