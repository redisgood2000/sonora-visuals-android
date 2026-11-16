# Sonora Visuals Android

Native Android audio-visualizer project for importing local audio and previewing reactive visuals.

## Current milestone — 0.1.0

- Kotlin + Jetpack Compose Android application (min SDK 26).
- Imports local audio through Android's document picker.
- Plays selected audio with MediaPlayer.
- Captures playback waveform and FFT data through Android's Visualizer effect. Android requires audio-capture permission for this API.
- Six live visualizer styles: Spectrum, Wave, Circular, Mirror, Pulse, and Neon.
- Basic live tempo estimate from low-frequency energy peaks. This is approximate, not offline beat-grid analysis.

## Not implemented yet

The MP4 export control is intentionally disabled in this milestone. These are future engineering milestones and are not claimed as complete:
1. Offline PCM decoding and beat-map generation (BPM, beat timestamps, bars/downbeats, onset strength).
2. Stronger instrument-band/onset analysis and improved beat alignment.
3. Native 60 FPS portrait 1080x1920 H.264/AAC MP4 renderer with progress, cancellation, and audio/video synchronization.
4. Export preview, file destination selection, and device-level validation.

## Build

- JDK 17
- Android SDK platform 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3

Open in Android Studio or run gradle :app:assembleDebug and gradle :app:testDebugUnitTest. GitHub Actions is configured to build a debug APK on pushes to main and upload it as the sonora-visuals-debug-apk artifact.
