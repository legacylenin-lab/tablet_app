---
name: primera-sesion
description: Primera sesión con el dueño de la app. Úsala cuando docs/ritmo-de-trabajo.md diga "Primera sesión: PENDIENTE". Presenta el proyecto, traza la ruta, conoce su forma de trabajar, deja el proyecto base funcionando y logra un primer resultado visible con su primer commit.
---

# Primera sesión: marcar la ruta

**Objetivo:** que el dueño termine la sesión sabiendo **qué vamos a construir, en qué orden y cómo vamos a trabajar juntos**, con un primer logro visible guardado en Git. **No** es una sesión para avanzar mucho código.

**Recuerda:** el dueño te usa desde Claude Desktop (pestaña Code) y **no usa terminal**. Los comandos los ejecutas tú; él usa Android Studio (botón ▶) y GitHub Desktop.

Duración orientativa: entre 45 y 90 minutos. Si se cansa o tiene poco tiempo, corta al terminar el paso 4 y deja el resto para la próxima.

## Paso 1: Bienvenida y contexto (5 min)

- Preséntate brevemente: vas a ayudarle a construir **su** app, paso a paso, y él decide.
- Resume la app en 3 o 4 frases simples, a partir de `docs/requisitos.md` §1: cuaderno con lápiz, biblioteca de PDF, después la PC y la sincronización, y al final la colaboración con amigos.
- Explícale cómo van a trabajar: tú escribes y compruebas el código; él lo prueba en Android Studio y lo guarda con GitHub Desktop. Todo lo acordado está escrito en `docs/`.

## Paso 2: Conocer su forma de trabajar (10 min)

Haz **pocas** preguntas, de a una o dos por mensaje, en tono de conversación:
1. ¿Cuánto tiempo sueles tener por sesión? ¿Qué días u horarios?
2. ¿Has programado algo antes, aunque sea poco?
3. ¿Prefieres que yo escriba el código y te explique, intentarlo tú con mi ayuda, o una mezcla?
4. ¿Prefieres explicaciones cortas o con detalle?

Anota las respuestas tal como las dice; las vas a guardar en el paso 6.

## Paso 3: La ruta (10 min)

Explica las versiones y etapas de `docs/requisitos.md` §2.0 y §7 **en lenguaje simple**, por ejemplo:
- **v1:** la app en la tablet: cuadernos, lápiz, texto, PDF y respaldo. Es lo primero y lo más importante.
- **v1.x:** mejoras: anotar en PDF, imprimir, buscar…
- **v2:** la app en la PC y la sincronización por Wi-Fi.
- **v3:** colaborar con amigos.

Dentro de la v1, cuéntale las etapas 1 a 6 en una frase cada una, y explica por qué empezamos por "cómo se guardan los datos" (etapa 2) aunque no se vea nada en pantalla: es el cimiento, y cambiarlo después cuesta mucho.

Pregúntale si tiene dudas o algo que quiera cambiar. Si pide cambios en la ruta, usa la skill `proponer-cambio`.

## Paso 4: Proyecto base y entorno (20 min)

1. **Proyecto base.** Revisa si el repositorio ya tiene `composeApp/`, `settings.gradle.kts` y `gradlew.bat`. Si no, guíalo paso a paso con la sección "Crear el proyecto base" de `docs/entorno.md`: descargar el ZIP en el navegador y copiar su contenido a la carpeta del proyecto. Después:
   - fusiona tú el `.gitignore` del asistente web de JetBrains con el del repositorio, si lo copió aparte;
   - comprueba que `CLAUDE.md`, `docs/` y `.claude/` siguen intactos.
2. **Compilar.** Ejecuta tú `./gradlew build` y dile el resultado en una frase. La primera vez tarda varios minutos porque descarga dependencias.
3. **Ver la app.** Guíalo en Android Studio: *File → Open* → la carpeta del proyecto, esperar la sincronización, y ejecutar con ▶:
   - la configuración de escritorio (ventana de Windows);
   - la de Android, en el emulador o la tablet.
4. **GitHub Desktop:** que confirme que ve el repositorio y la lista de archivos nuevos.

Si algo falla, resuélvelo con calma y explica qué pasó. Si no se puede resolver, anótalo en `docs/estado.md` como bloqueo para Leyni.

## Paso 5: Primer logro y primer commit (10 min)

1. Si el proyecto base es nuevo, primero guíalo en GitHub Desktop para guardarlo con el mensaje `Agrega proyecto base Kotlin Multiplatform` (siguiendo `docs/guia-git.md`).
2. Propón un cambio mínimo y visible: por ejemplo, cambiar el texto del botón de ejemplo o el título de la ventana por el nombre de su app.
3. Si quiere, que lo haga él siguiendo tus instrucciones en Android Studio (archivo y línea exactos); si no, hazlo tú explicando qué cambias.
4. Que lo vea funcionar con ▶.
5. Guíalo para guardarlo en GitHub Desktop: revisar los cambios, pegar el mensaje que le das, *Commit to main* y luego *Push origin*.
6. Celebra el logro en una frase. Es su primer punto de guardado.

## Paso 6: Registrar y cerrar (5 min)

1. En `docs/ritmo-de-trabajo.md`:
   - cambia "Primera sesión: PENDIENTE" por "Primera sesión: completada (<fecha>)";
   - llena el **Perfil** con lo que dijo en el paso 2;
   - anota en "Conceptos que ya domina" lo que quedó claro (por ejemplo: commit, compilar, ejecutar con ▶);
   - agrega la primera fila de la bitácora.
2. En `docs/estado.md`: marca la etapa 1 como hecha (o lo que falte) y deja la etapa 2 como siguiente.
3. Propón el commit de estos cambios de documentación, con su mensaje, para hacerlo en GitHub Desktop.
4. Dile qué toca la próxima vez, en una frase, y cómo retomar: abrir Claude Desktop → pestaña Code → la carpeta del proyecto → escribir "hola".

## Notas aprendidas

Ideas que el agente agrega con el tiempo para adaptar esta skill. Se registran también en `docs/ritmo-de-trabajo.md` §6.

- —
