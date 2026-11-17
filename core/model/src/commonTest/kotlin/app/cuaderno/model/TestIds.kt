package app.cuaderno.model

/** Identificador válido y predecible para pruebas: `testId(1)`, `testId(2)`… */
fun testId(n: Int): EntityId = EntityId.parse("00000000-0000-4000-8000-" + n.toString(16).padStart(12, '0'))
