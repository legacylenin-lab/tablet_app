# Etapa 7: diseño visual de la interfaz (PC-03)

**Versión:** 1.0, aprobada por Leyni
**Fecha:** 22/11/2026
**Basado en:** `requisitos.md` v1.3 (PC-03, §7) y `plan-de-pruebas.md` (CP-17, PA-10)

> **Para el agente:** este documento dice **qué hacer y cómo** en la etapa 7. Léelo completo antes de empezarla y cada vez que trabajes en el diseño. El dueño decide todo lo visual; tú lo implementas, le explicas lo que no es viable y registras cada decisión aquí (§6 y §8). Lo que cambie una función, un requisito o un contrato **no** se resuelve aquí: va como propuesta (`proponer-cambio`).
>
> **Para el dueño:** esta es tu etapa. Aquí está cómo vamos a trabajar el diseño de tu app, desde Figma hasta la app instalada.

---

## 1. Antes de empezar (condiciones de entrada)

La etapa 7 **no empieza** hasta que se cumpla todo esto:
1. Están implementados y probados en la tablet **PC-02** (candado de la vista) y **PQ-25** (`TextBlockRepository.restore`).
2. Se quitaron el código de la plantilla y las pantallas de prototipo que no son parte de la v1 (PQ-06, PQ-22): `Greeting.kt`, `GreetingUtil.kt`, `Platform.*`, `ComposeInkScreen.kt` y la imagen de ejemplo `compose-multiplatform.xml`, si nada las usa. `PdfPrototypeApp.kt` (prototipo de Windows) **se conserva**, porque es de la v2.
3. `./gradlew build` pasa completo.
4. Se marcó la etiqueta **`v1.0-funcional`**. La crea Leyni o el dueño; tú no haces commits ni etiquetas sin que te lo pidan.
5. La app está **instalada en la tablet** y el dueño ya la usa a diario. Lo que le moleste al usarla es material para el diseño: anótalo en §8.

## 2. Alcance

**Entra (solo lo visual):**
- paleta de colores (y si entra el modo oscuro, RF-19: lo decide el dueño en esta etapa);
- tipografías y tamaños de texto;
- íconos;
- formas (esquinas, bordes, sombras) y espaciados;
- distribución de los elementos en cada pantalla y diálogo de la v1;
- la pantalla de inicio de la app (ícono y nombre visibles en la tablet), si el dueño quiere.

**No entra:**
- **funciones nuevas** o cambios de comportamiento. Si al diseñar surge una idea así, va como propuesta en `requisitos.md` §8 y no se implementa en esta etapa;
- las pantallas de Windows (v2). Heredan el tema, pero se diseñan en la v2;
- el lienzo de tinta en sí (cómo se dibuja el trazo) y los gestos: ya están decididos y probados.

**Restricciones que el diseño debe respetar:**

| Regla | Origen | En la práctica |
|---|---|---|
| Interfaz simple, botones grandes | RNF-08 | Todo elemento que se toca mide **al menos 48 × 48 dp** |
| Texto legible | RNF-16 (adelantado como revisión) | Contraste mínimo **4,5:1** en texto normal y **3:1** en texto grande e íconos (criterio WCAG AA) |
| Licencias libres | RNF-14 | Fuentes con licencia OFL o Apache (por ejemplo, Google Fonts); íconos de Material Symbols (Apache) o dibujados por el dueño. Cada fuente o paquete de íconos se anota en `arquitectura.md` §11 con su licencia |
| Ambas orientaciones | RNF-09 | Cada pantalla se diseña en **vertical y horizontal** |
| Interfaz en español | RNF-10 | Los textos del diseño son los textos reales de la app |

## 3. Roles

- **El dueño** decide todo lo visual. Diseña en Figma, elige colores, fuentes e íconos, y aprueba cada pantalla.
- **El agente:**
  - guía al dueño en Figma (§4) cuando lo pida, sin imponer el diseño;
  - obtiene las medidas de la tablet y prepara la información técnica que el dueño necesite;
  - implementa el diseño en un **tema único** y en las pantallas (§5);
  - avisa, con palabras simples, cuando algo del diseño no se puede hacer, cuesta demasiado o incumple una regla de §2, y propone una alternativa;
  - registra decisiones, desviaciones y pendientes (§6, §8).
- **Leyni** resuelve lo que el dueño y el agente no puedan acordar (§7).

## 4. Figma: cómo preparar el diseño

### 4.1 Medidas del lienzo (marcos)

- En Figma se trabaja en **dp**, no en los píxeles físicos de la pantalla. Compose también usa dp, así que el diseño se traslada casi 1 a 1.
- **El agente obtiene las medidas** con la tablet conectada por USB:
  ```
  adb shell wm size
  adb shell wm density
  ```
  Ancho en dp = píxeles de ancho × 160 / densidad (igual para el alto). A eso hay que restar las barras del sistema (hora arriba y botones o gestos abajo), que la app deja libres.
- **Anota aquí las medidas cuando las calcules:**

| Marco | Ancho × alto (dp) | Nota |
|---|---|---|
| Tablet vertical | _por medir_ | sin las barras del sistema |
| Tablet horizontal | _por medir_ | sin las barras del sistema |

### 4.2 Buenas prácticas para que el diseño se traduzca bien

Explícaselas al dueño antes de que empiece a diseñar en serio. No hace falta que las aprenda todas el primer día.
1. **Auto Layout** en todo: equivale a las filas y columnas de Compose. Un diseño sin Auto Layout se traduce peor.
2. **Estilos o variables** de color y de texto, con nombres claros (por ejemplo, `fondo`, `primario`, `titulo`, `cuerpo`). Se convierten en el tema de la app.
3. **Componentes** para lo que se repite: botones, tarjetas de cuaderno y documento, barra de herramientas, diálogos.
4. **Material 3 Design Kit** (gratis en la comunidad de Figma) como punto de partida. Sus piezas corresponden con las de Compose Material 3. El dueño puede cambiar su aspecto todo lo que quiera.
5. **Material Theme Builder** (web o plugin de Figma) para generar una paleta completa a partir de uno o dos colores, incluido el modo oscuro.
6. **Notas sobre el comportamiento** en el propio archivo: qué pasa al tocar, qué se ve al estar activado el candado, etc. Figma solo muestra imágenes quietas.
7. **Nombres claros en los marcos:** el nombre de la pantalla (§6) y la orientación. Por ejemplo, `Mis cuadernos – vertical`.

### 4.3 Entrega del diseño (convención acordada)

**Cuándo:** cuando el dueño considera listo el diseño, ya sea completo o por pantalla. El agente no implementa un diseño a medias sin que el dueño lo pida.

**Qué se entrega:**
1. **El archivo `.fig`**, guardado en `docs/diseno/` y versionado con Git. Es la fuente de verdad del diseño y su respaldo. **Ojo:** `.fig` es el formato interno de Figma y el agente **no lo puede leer directamente**. Por eso la entrega incluye además:
2. **El enlace al archivo en Figma**, para que el agente lo lea con el servidor MCP de Figma (§4.4). Si el dueño solo tiene el `.fig`, primero lo importa en Figma (*Archivo → Importar*).
3. **Una exportación PNG de cada marco** (vertical y horizontal), en `docs/diseno/pantallas/`, con el nombre del marco. Sirve para comparar y como alternativa si el MCP no está disponible.

### 4.4 Cómo lo lee el agente

- **Servidor MCP de Figma** (remoto, `https://mcp.figma.com/mcp`), conectado a Claude Code con la cuenta de Figma del dueño. Lee medidas, estructura, estilos, textos y componentes. El agente guía la conexión según la versión de Claude Desktop (configuración de conectores, o `claude mcp add --transport http figma https://mcp.figma.com/mcp`). **Verificar al llegar a esta etapa.**
- **Límite importante:** en el plan gratuito de Figma (Starter), el MCP permite **unas 20 llamadas al mes**. Úsalo **solo para pantallas ya aprobadas por el dueño**, una o dos llamadas por pantalla. Para iterar, usa las exportaciones PNG. Anota cada uso en §8.
- **Si el MCP no está disponible o se agotó**, trabaja con los PNG y pregúntale al dueño las medidas o colores que no se puedan leer con seguridad. Nunca las inventes.

## 5. Implementación (para el agente)

1. **Primero el tema, después las pantallas.** Un único tema en `shared` (`commonMain`, por ejemplo `app/cuaderno/ui/theme/`) con colores, tipografía, formas y espaciados. Las pantallas no llevan colores ni tamaños escritos a mano: los toman del tema. Así Windows lo hereda en la v2.
2. **Fuentes e íconos** en `composeResources`, anotados en `arquitectura.md` §11 con su licencia antes de usarlos.
3. **Una pantalla a la vez**, en el orden del inventario (§6), con un commit por pantalla propuesto al dueño.
4. **Vistas previas** (`@Preview`) de cada pantalla en vertical y horizontal, para que el dueño vea el resultado en Android Studio sin ejecutar la app.
5. **Fidelidad:** el objetivo es que la pantalla se vea como el diseño. Las diferencias inevitables (por ejemplo, el teclado o las barras del sistema) se explican al dueño y se anotan en §8.
6. **Sin cambios de comportamiento.** Las pruebas existentes deben seguir pasando. Si un cambio visual rompe una prueba de interfaz, se ajusta la prueba solo si el comportamiento sigue siendo el mismo.
7. **Antes y después:** captura de cada pantalla antes de tocarla y al terminarla, en `docs/diseno/capturas/`.
8. **Guía de estilo:** al terminar, `docs/diseno.md` resume la paleta (con sus valores), las tipografías, los íconos, los componentes y los espaciados, con referencias al archivo de Figma.

## 6. Inventario de pantallas de la v1

Estado al 22/11/2026, según el código. Por cada pantalla, el dueño decide una opción:
- **Rediseñar:** en esta etapa.
- **Mantener:** se queda como está, solo con el tema nuevo (colores y tipografía).
- **Diferir:** se rediseña más adelante (§7).

| # | Pantalla | Código | Incluye | Decisión del dueño | Estado |
|---|---|---|---|---|---|
| 1 | **Mis cuadernos** | `notebooks/NotebookListScreen.kt` | Lista; crear, renombrar y eliminar (diálogos); acceso a Biblioteca y Respaldo | _pendiente_ | — |
| 2 | **Cuaderno (hoja)** | `ink/InkScreen.android.kt`, `ink/InkToolbar.kt`, `notebooks/PageNav.kt` | Hoja A4 con tinta; barra de herramientas (lápiz, resaltador, colores, grosores, borrador, deshacer y rehacer); navegación de páginas (nueva, duplicar, eliminar, mover); **candado (PC-02)** | _pendiente_ | — |
| 3 | **Cuadros de texto** | `text/PageTextBlock.kt`, `text/RichTextToolbar.kt` | Cuadro sobre la hoja; mover y cambiar el ancho; barra de formato; modos Lápiz y Teclado | _pendiente_ | — |
| 4 | **Biblioteca** | `library/LibraryScreen.kt` | Lista de documentos; importar PDF; eliminar; aviso de DOCX (RF-11) | _pendiente_ | — |
| 5 | **Lector de PDF** | `library/ReaderScreen.kt` | Página; ◀ ▶; «Ir a…»; barra deslizable; zoom y «Ajustar»; **candado (PC-02)** | _pendiente_ | — |
| 6 | **Respaldo** | `backup/BackupScreen.kt` | Crear respaldo; restaurar con confirmación; volver a como estaba | _pendiente_ | — |
| 7 | **Diálogos y avisos comunes** | varios | Confirmaciones, errores y mensajes breves | _pendiente_ | — |
| 8 | **Ícono y nombre de la app** | `androidApp` | Ícono en la tablet | _pendiente (opcional)_ | — |

**Fuera de este inventario:** `PdfPrototypeApp.kt` y todo lo de Windows (v2). Si aparece una pantalla nueva antes de la etapa 7 (por ejemplo, la del candado), agrégala aquí.

## 7. Reglas de decisión por pantalla

1. **Rediseñar o mantener:** decisión del dueño. Se anota en §6.
2. **Diferir** (no se rediseña ahora, sino después): el dueño y el agente acuerdan a dónde va:
   - **Más tarde dentro de la v1:** queda en §6 como "Diferida (v1)" y como pendiente en `estado.md`. La v1 **no se cierra** hasta resolverla, salvo que se mueva a otra versión.
   - **A otra versión** (v1.x o v2): queda en §6 como "Diferida a vX", y se agrega un recordatorio en `pendientes-a-consultar.md` §3 con su número `PQ-`, para que Leyni lo vea.
3. **Sin acuerdo** entre el dueño y el agente (por ejemplo, el dueño quiere algo que incumple §2, o no está claro si un cambio es visual o funcional): **no se implementa**. Se registra un `PQ-` **Abierto** en `pendientes-a-consultar.md` §1 con las dos posturas, y se sigue con otra pantalla mientras Leyni decide.
4. Cualquier idea que cambie una **función** va como propuesta `PC-` (`proponer-cambio`), no como `PQ-`.

## 8. Registro de la etapa

El agente lo actualiza en cada sesión de diseño.

| Fecha | Pantalla | Qué se hizo o decidió | Desviaciones del diseño y por qué | Uso del MCP de Figma |
|---|---|---|---|---|
| — | — | — | — | — |

**Molestias de uso anotadas antes o durante la etapa** (material para el diseño):
- —

## 9. Criterios de aceptación

**Por pantalla** (prueba CP-17 de `plan-de-pruebas.md`). Una pantalla rediseñada se da por terminada cuando:
1. Se ve como su marco de Figma en la tablet real, en vertical y horizontal. Las diferencias están explicadas en §8.
2. Usa solo valores del tema: no hay colores ni tamaños escritos a mano.
3. Todo lo que se toca mide al menos 48 × 48 dp.
4. El contraste cumple §2 (4,5:1 en texto normal; 3:1 en texto grande e íconos).
5. Su comportamiento no cambió: las pruebas existentes pasan y el dueño confirma que funciona igual.
6. Tiene sus capturas de antes y después y su vista previa en Android Studio.
7. El dueño la aprueba.

**De la etapa** (prueba de aceptación PA-10). La etapa 7, y con ella la v1, se cierra cuando:
1. Todas las pantallas de §6 están **terminadas**, **mantenidas** con el tema nuevo, o **diferidas a otra versión** con su recordatorio. No queda ninguna "Diferida (v1)" ni ningún `PQ-` de diseño abierto.
2. Existen `docs/diseno.md`, el `.fig` y los PNG en `docs/diseno/`, y las capturas de antes y después.
3. Las fuentes e íconos usados están en `arquitectura.md` §11 con su licencia.
4. `./gradlew build` pasa completo y la app está instalada en la tablet.
5. El dueño recorre la app completa y está conforme (PA-10).
6. Se propone la etiqueta **`v1.0`** a Leyni o al dueño.

**Plazo orientativo:** de 3 a 5 sesiones de implementación, contadas desde la entrega del diseño (el tiempo que el dueño dedique a diseñar en Figma no cuenta). Hay margen de unos 4 meses antes de clases. Si al llegar a 5 sesiones quedan ideas, el dueño elige cuáles se difieren según §7.
