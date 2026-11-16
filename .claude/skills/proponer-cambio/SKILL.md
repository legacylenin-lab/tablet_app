---
name: proponer-cambio
description: Registrar una propuesta de cambio cuando el dueño pide algo que contradice o amplía docs/requisitos.md o docs/arquitectura.md (una función nueva, un cambio de versión o prioridad, otra librería u otra tecnología). No implementa nada: lo deja registrado para que Leyni lo apruebe.
---

# Proponer un cambio

## Cuándo aplica
- El dueño pide una función nueva o distinta de lo escrito.
- Quiere adelantar algo de otra versión.
- Hace falta una librería no prevista en `arquitectura.md` §11, o cambiar un contrato de `arquitectura.md`.
- Descubres que algo de la documentación es inviable (un prototipo falló, una librería no soporta Windows…).

**No aplica** a detalles de implementación internos que respetan los contratos. Esos los decides tú y los explicas.

## Pasos
1. **Entender:** pregunta lo justo para formular el pedido con claridad. ¿Qué quiere lograr? ¿Por qué?
2. **Explicar el impacto** en lenguaje simple:
   - qué cambia;
   - qué cuesta (tiempo, complejidad);
   - en qué versión encajaría;
   - si afecta a otra cosa ya decidida.
3. **Registrar** una fila nueva en la tabla "Propuestas de cambio" de `docs/requisitos.md` §8:
   - ID: `PC-<número correlativo>`;
   - fecha;
   - propuesta en una frase;
   - origen ("dueño" o "agente" y por qué);
   - impacto;
   - estado: **"Pendiente de revisión (Leyni)"**.
4. **Avisarle** que queda anotado y que Leyni lo revisará. Mientras tanto, se sigue con la etapa actual.
5. **No implementar** nada de la propuesta hasta que su estado sea "Aprobada".
6. Propón un commit con el mensaje `Registra propuesta PC-<n>: <resumen>`.

## Si quien habla se identificó como Leyni
Puede aprobar o rechazar. Si aprueba:
- aplica el cambio en el documento correspondiente;
- sube su versión (por ejemplo, 1.1 → 1.2);
- agrega una fila al registro de cambios;
- marca la propuesta como "Aprobada (<fecha>)".

## Notas aprendidas

- —
