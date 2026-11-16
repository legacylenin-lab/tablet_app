---
name: revision-tecnica
description: Modo revisor para Leyni. Úsala SOLO cuando la persona se identifique textualmente como Leyni en la conversación. Resume el avance, las propuestas pendientes y los riesgos técnicos, y permite aprobar cambios.
---

# Revisión técnica (solo Leyni)

**Condición de entrada:** la persona escribió explícitamente que es Leyni. Nunca entres a este modo por deducción.

## 1. Informe inicial (técnico y conciso)
- **Avance:** etapa actual según `docs/estado.md` y commits desde la última revisión (`git log --oneline`). Si existe la etiqueta `revision-leyni`, usa los commits posteriores a ella.
- **Propuestas pendientes:** las filas de `docs/requisitos.md` §8 con estado "Pendiente de revisión (Leyni)", con su impacto.
- **Desviaciones:** código que no respeta `docs/arquitectura.md` (capas, contratos, modelo de datos), librerías no listadas en §11, lógica sin pruebas.
- **Riesgos:** prototipos pendientes o fallidos (P-0 a P-3), pruebas rotas, deuda técnica observada.
- **Ritmo:** un resumen de dos líneas de `docs/ritmo-de-trabajo.md` (cómo va el dueño y qué ajustes se aplicaron).

## 2. Atender lo que pida
- Aprobar o rechazar propuestas (ver skill `proponer-cambio`).
- Cambiar documentos con su autorización: subir la versión y registrar el cambio.
- Ajustar skills, `CLAUDE.md` o la ruta.

## 3. Cierre
- Si Leyni lo pide, marca el punto de revisión: `git tag -f revision-leyni` (sin push, salvo que él lo pida).
- Deja en `docs/estado.md` una línea "Revisión de Leyni (<fecha>): <decisiones>", para que el agente lo comunique al dueño en la siguiente sesión.

## Notas aprendidas

- —
