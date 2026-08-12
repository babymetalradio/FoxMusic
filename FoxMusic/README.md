# Fox Music 🦊🎵

Reproductor de música para Android con soporte de **archivos locales** y **streaming genérico**.

- **Package**: `com.foxplayer.app`
- **UI**: Jetpack Compose + Material 3
- **Reproductor**: Media3 (ExoPlayer)
- **CI/CD**: GitHub Actions

## Estado actual

Proyecto base listo.  
La primera pantalla muestra "Fox Music" y el pipeline de GitHub Actions construye el APK automáticamente.

## Cómo empezar

### 1. Crear el repositorio en GitHub
Crea un repositorio vacío llamado por ejemplo `FoxMusic`.

### 2. Subir este código

```bash
git init
git add .
git commit -m "Initial commit - Fox Music base project"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/FoxMusic.git
git push -u origin main
```

### 3. GitHub Actions
Después del push, ve a la pestaña **Actions** del repositorio.  
El workflow construirá el APK y lo subirá como artefacto (`FoxMusic-debug`).

### 4. Instalar el APK
Descarga el artefacto desde Actions e instálalo en tu teléfono.

## Próximos pasos

1. Núcleo del reproductor (Media3 + MediaSession)
2. Biblioteca local (MediaStore + Room)
3. Streaming por URL
4. Playlists, favoritos, shuffle/repeat, etc.

## Requisitos de desarrollo

- Android Studio (versión reciente)
- JDK 17
- minSdk 26 / targetSdk 36
