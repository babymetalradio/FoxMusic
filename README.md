# Fox Music 🦊🎵

Reproductor de música para Android con **archivos locales** y **streaming genérico**.

- **Package**: `com.foxplayer.app`
- **UI**: Jetpack Compose + Material 3
- **Reproductor**: Media3 (ExoPlayer) + MediaSession
- **CI/CD**: GitHub Actions

## Estado actual (v1.3.0)

- ✅ Reproductor con Media3
- ✅ Servicio en segundo plano
- ✅ Controles Play / Pause / Next / Previous
- ✅ Barra de progreso
- ✅ Streaming por URL (diálogo)
- ✅ **Biblioteca local** (MediaStore)
- ✅ Lista de canciones + cola de reproducción
- ✅ Permisos de audio
- ⏳ Playlists y favoritos (próximo)
- ⏳ Shuffle / Repeat (próximo)

## Cómo usar

1. Al abrir, concede el permiso de audio
2. Verás tu biblioteca local
3. Toca una canción para reproducirla (se carga la cola completa)
4. Icono 🔗 para streaming por URL
5. Botón flotante para volver al reproductor

## Build

APK automático con GitHub Actions en cada push a `main`.
