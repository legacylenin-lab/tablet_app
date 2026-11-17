# Ritmo de trabajo del dueño (documento vivo)

> **Para el agente:** este archivo te dice cómo trabajar con el dueño. Léelo al empezar cada sesión y actualízalo al cerrarla (skill `cierre-sesion`). Anota solo observaciones de trabajo: nada personal ni juicios. El dueño puede leerlo cuando quiera, así que escribe como si te leyera por encima del hombro.
>
> **Para el dueño:** aquí el asistente va anotando cómo te gusta trabajar, para adaptarse a ti. Si algo no te parece correcto, díselo y lo corrige.

**Primera sesión: completada (16/11/2026)**

---

## 1. Perfil de trabajo

Se completa en la primera sesión con lo que **el dueño diga**, y se ajusta después con lo observado.

| Tema | Valor |
|---|---|
| Tiempo habitual por sesión | Al menos 3 horas por día; tiene mucho tiempo libre antes de que empiece la universidad |
| Días u horarios en que suele trabajar | A diario (sin horario fijo anotado) |
| Experiencia previa con programación | Hizo una app de tablaturas de guitarra para PC, con ayuda de una IA |
| Cómo prefiere aprender (ver hacer / hacer él mismo / mezcla) | Que el asistente escriba el código y lo explique |
| Nivel de detalle que prefiere en las explicaciones | Explicaciones cortas |
| Tamaño de paso que le funciona | Pasos pequeños con una comprobación al final de cada uno; funcionó bien |
| Dispositivo de prueba más a mano (emulador / tablet) | Emulador de celular (la tablet real aún no se ha probado) |

## 2. Conceptos que ya domina

Lista breve, para no volver a explicarlos desde cero. Ejemplos: commit, compilar, emulador, función, prueba unitaria.

- Commit y push con GitHub Desktop (hecho con éxito).
- Ejecutar la app con ▶ en Android Studio (emulador) y ver el resultado.
- Compilar (lo hace el asistente; entiende qué es).
- Abrir el proyecto correcto en Android Studio (aprendido tras confundirse con otro proyecto abierto).
- Rutina de guardado en GitHub Desktop: revisar la lista de archivos, pegar el mensaje, *Commit to main* y *Push origin* (la repitió unas 7 veces).
- Idea de pruebas automáticas: el asistente las escribe y las ejecuta; el dueño no necesita correrlas.
- Idea de "borrado suave" (ocultar sin eliminar) y de que cada cambio se anota en un registro; aún no la ha necesitado usar él mismo.
- Probar en la tablet real: conectada por USB, con la app instalada por el asistente; el dueño la usa con el lápiz y cuenta lo que ve (muy buenas descripciones: dice qué hizo y qué pasó).
- Crear, renombrar, eliminar y abrir cuadernos, y pasar de página, desde la app (probado en la tablet).
- Cerrar la app por completo y reabrirla para comprobar que algo se guardó.

## 3. Lo que funciona y lo que no

Observaciones acumuladas, cada una con su fecha.

| Fecha | Observación | Ajuste que se aplica |
|---|---|---|
| 16/11/2026 | Al copiar el ZIP de JetBrains, se copió la carpeta entera en vez de su contenido | Dar instrucciones de copia con una verificación posterior hecha por el asistente |
| 16/11/2026 | Android Studio tenía abierto otro proyecto (PruebaKMP) y el ▶ ejecutó ese | Al guiar ▶, pedir primero que confirme el nombre del proyecto en el título de la ventana |
| 16/11/2026 | No encontró el selector de configuraciones (abrió el menú de "crear nueva") | Si un menú no aparece, el asistente ejecuta el comando él mismo en vez de insistir |
| 16/11/2026 | Con pasos de unos 15 a 30 minutos, cada uno con su commit, avanzó sin trabarse; pidió continuar en cada paso y dijo que avisaría cuando quisiera parar | Mantener bloques de ese tamaño, con un commit por idea; no cerrar la sesión hasta que él lo diga |
| 16/11/2026 | Una vez respondió "ya lo guardé" sin haber guardado, y otra vez sí lo había hecho cuando el asistente creyó que no | El asistente verifica siempre con `git log` y `git status` antes de dar por guardado un commit, y corrige abiertamente si se equivocó |
| 16/11/2026 | Preguntó si el número de archivos a marcar era el esperado (14 y no 20) | Contar los archivos con `git status` antes de decir cuántos deben aparecer, en vez de estimar |
| 16/11/2026 | Pasó en el chat una nota de Leyni para crear un documento de pendientes | Se atendió como petición del dueño sin cambiar de modo; solo se activa la revisión técnica si dice textualmente "soy Leyni" |
| 17/11/2026 | Las pruebas en la tablet dieron respuestas muy útiles (por ejemplo, que el borrador no respondía y que el botón del lápiz solo funciona con doble toque), lo que permitió medir en vez de adivinar | Pedirle pasos concretos y numerados para probar en la tablet, y grabar lo que manda el lápiz cuando haga falta |
| 17/11/2026 | El contexto de la conversación llegó al 60% tras una sesión muy larga (unos 6 commits y mucha lectura de documentación) | Cerrar la sesión y abrir una nueva cada 4 o 5 commits, o antes si el dueño avisa de que el contexto sube. Todo el estado queda en `docs/` para retomarlo con "hola" |
| 17/11/2026 | Guardó el commit antes de avisar de los errores de la prueba; el arreglo fue un commit nuevo, sin problema | Pedir que cuente el resultado de la prueba antes de guardar; si ya guardó, seguir con un commit de corrección |
| 17/11/2026 | Con 3 horas disponibles aguantó 9 commits en una conversación; cada paso tuvo su prueba en la tablet, que encontró 3 fallos reales que las pruebas automáticas no veían (memoria con zoom, guardado al cambiar de página) | Mantener el paso pequeño con prueba en la tablet; el texto con teclado (más grande) empieza en sesión nueva. Verificar siempre que la tablet esté autorizada antes de decir "instalada" |
| 18/11/2026 | Ante la pregunta de cómo debía funcionar el texto, eligió la opción más completa (texto y lápiz juntos), que los requisitos dejan para después | Cuando una elección del dueño contradiga los requisitos, explicar el costo en una frase, registrar la propuesta y seguir con trabajo que no dependa de ella |

## 4. Bitácora de sesiones

Se conservan las 10 más recientes; las anteriores se resumen en una línea por mes en la sección 5.

| Fecha | Duración aprox. | Etapa | Qué se logró | Qué sigue |
|---|---|---|---|---|
| 16/11/2026 | Sesión larga (varias horas, con una pausa; la duración exacta no se midió) | 1 y 2 | Etapa 1 cerrada (proyecto base en Android y Windows, primer cambio visible). Etapa 2, parte de datos: modelo, HLC, codificación de trazos, claves de orden, base de datos con 6 repositorios y respaldo, con 109 pruebas en verde. 7 commits. | Revisión de `pendientes-a-consultar.md` por Leyni; luego etapa 3 (lienzo con lápiz en la tablet real) |
| 17/11/2026 | Sesión larga (varias horas) | 2 (ajustes de Leyni) y 3 | Ajustes aprobados por Leyni: minSdk 30, lint, EntityId, TitleSort y prueba del respaldo en el emulador. Etapa 3: prototipo de tinta P-0 en la tablet real con androidx.ink (adoptada), modo borrador con el doble toque del lápiz, trazos guardados en la base de datos con deshacer y rehacer. 6 commits. | Terminar la etapa 3: color y grosor del lápiz, resaltador, y dedo que desplaza y hace zoom |
| 17/11/2026 (2.ª sesión) | Sesión larga (unas 3 horas) | 3 y 4 | Etapa 3 completa (color, grosor, resaltador, dedo con zoom y desplazamiento). Etapa 4: lista de cuadernos y varias páginas por cuaderno. Fallos hallados en la tablet y corregidos: la tinta ignoraba color y tipo; la capa de tinta con zoom agotaba la memoria; los trazos se perdían al cambiar de página. 9 commits. | Etapa 4: texto con teclado (RF-04); borrar, duplicar y reordenar páginas |
| 18/11/2026 | Sesión larga (3 horas o más) | 4 | Librería de texto enriquecido validada y adoptada (compose-rich-editor 1.2.1) con pantalla de prueba y limpieza del HTML a `h1`–`h3`; duplicar y eliminar páginas. El dueño pidió texto y lápiz en la misma página: se registró como propuesta PC-01 para Leyni, sin implementarla. 6 commits. | Esperar la decisión de Leyni sobre PC-01; luego poner el texto en la página |

## 5. Resumen de sesiones anteriores

- —

## 6. Cambios hechos a las skills

| Fecha | Skill | Nota agregada | Motivo |
|---|---|---|---|
| — | — | — | — |
