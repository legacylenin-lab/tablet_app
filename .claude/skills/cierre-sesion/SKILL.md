---
name: cierre-sesion
description: Cierre de una sesión de trabajo con el dueño. Úsala cuando diga que termina por hoy o cuando se alcance un buen punto de cierre. Verifica que todo compila, resume lo hecho, guía el commit en GitHub Desktop y actualiza estado.md y ritmo-de-trabajo.md.
---

# Cierre de sesión

## 1. Verificar
- Ejecuta tú `./gradlew build`. Si falla, arréglalo o, si no da tiempo, deja el código en un estado que compile (comentando o revirtiendo lo inacabado) y explícaselo.
- No cierres con pruebas rotas sin decirlo claramente.

## 2. Resumir (para el dueño)
En pocas frases simples: qué se logró hoy, qué aprendió (si hubo un concepto nuevo) y qué sigue.

## 3. Actualizar la documentación
- **`docs/estado.md`:** etapa actual, qué está hecho, qué sigue y bloqueos.
- **`docs/ritmo-de-trabajo.md`:**
  - agrega una fila a la bitácora (fecha, duración aproximada, etapa, logro, siguiente);
  - agrega conceptos nuevos que ya domina;
  - si notaste algo sobre su forma de trabajar (el paso fue muy grande o muy chico, prefirió hacerlo él, se trabó en algo), agrégalo en "Lo que funciona y lo que no", con el ajuste que aplicarás;
  - si hay más de 10 filas en la bitácora, resume las más antiguas en la sección 5.
- **`docs/plan-de-pruebas.md`:** si implementaste pruebas, actualiza la matriz de trazabilidad.
- Si una skill podría adaptarse mejor a su ritmo, agrega la idea en su sección "Notas aprendidas" y regístralo en `ritmo-de-trabajo.md` §6.

## 4. Guardar (commit en GitHub Desktop)
- Propón **uno o pocos** commits, cada uno con su mensaje ya redactado. Si son varios, dile qué archivos marcar en cada uno.
- Guíalo en GitHub Desktop según `docs/guia-git.md`: revisar → *Commit to main* → *Push origin*.
- Recuérdale que el *Push* deja una copia en GitHub y le permite a Leyni revisar.

## 5. Despedida
Una frase sobre qué toca la próxima vez y cómo retomar: abrir Claude Desktop → pestaña Code → la carpeta del proyecto → escribir "hola".

## Notas aprendidas

- —
