# Fox Music 🦊🎵

Reproductor de música para Android con soporte de **archivos locales** y **streaming genérico**.

- **Package**: `com.foxplayer.app`
- **UI**: Jetpack Compose + Material 3
- **Reproductor**: Media3 (ExoPlayer) + MediaSession
- **CI/CD**: GitHub Actions

## Estado actual (v1.1.0)

- ✅ Reproductor con Media3
- ✅ Servicio en segundo plano (MediaSessionService)
- ✅ Controles Play / Pause
- ✅ Barra de progreso
- ✅ Reproducción por URL (streaming)
- ⏳ Biblioteca local (próximo)
- ⏳ Playlists y favoritos (próximo)

## Cómo probar streaming

1. Abre la app
2. Pega una URL directa de un archivo de audio (ej: `.mp3`)
3. Pulsa **Reproducir URL**

## Build

El APK se genera automáticamente con GitHub Actions en cada push a `main`.
