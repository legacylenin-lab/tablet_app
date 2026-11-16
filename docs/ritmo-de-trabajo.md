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

## 4. Bitácora de sesiones

Se conservan las 10 más recientes; las anteriores se resumen en una línea por mes en la sección 5.

| Fecha | Duración aprox. | Etapa | Qué se logró | Qué sigue |
|---|---|---|---|---|
| 16/11/2026 | Sesión larga (varias horas, con una pausa; la duración exacta no se midió) | 1 y 2 | Etapa 1 cerrada (proyecto base en Android y Windows, primer cambio visible). Etapa 2, parte de datos: modelo, HLC, codificación de trazos, claves de orden, base de datos con 6 repositorios y respaldo, con 109 pruebas en verde. 7 commits. | Revisión de `pendientes-a-consultar.md` por Leyni; luego etapa 3 (lienzo con lápiz en la tablet real) |

## 5. Resumen de sesiones anteriores

- —

## 6. Cambios hechos a las skills

| Fecha | Skill | Nota agregada | Motivo |
|---|---|---|---|
| — | — | — | — |
