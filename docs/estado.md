# Estado del proyecto

> Lo actualiza el agente al cerrar cada sesión. Es breve: dónde estamos y qué sigue.

**Versión en curso:** v1 (MVP Android)
**Etapa actual:** 1, entorno y repositorio (ver `requisitos.md` §7)
**Última actualización:** 16/11/2026 (preparado por Leyni antes de la primera sesión)

## Hecho
- Entorno validado en la PC: Android Studio corre un proyecto Kotlin + Compose en el emulador (API 37), y un proyecto Kotlin Multiplatform corre en escritorio Windows.
- Cuenta de GitHub del dueño y GitHub Desktop configurados en su usuario de Windows.
- Repositorio creado en `D:\dev\Tablet_app` con `CLAUDE.md`, `docs/` y `.claude/`.

## En curso
- Etapa 1: **falta agregar el proyecto base KMP** (Android + Desktop) siguiendo `docs/entorno.md`, comprobar que compila y corre, y hacer su commit.

## Siguiente
- Etapa 2: estructura de módulos (`core:model`, `core:domain`, `core:data`), HLC, codificación de trazos, esquema SQLDelight y repositorios, con sus pruebas (CU-01 a CU-05).

## Bloqueos o pendientes
- Ninguno.
