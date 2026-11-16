# Sonora Visuals Android

Native Android audio-visualizer studio. Version 0.2.0 adds an After Effects-inspired composition preview: circular spectrum, linear spectrum, waveform, particles, neon bars and beat pulse, with sensitivity, glow and band controls.

## Current
- Import audio with Android's Storage Access Framework.
- Playback, pause and seek.
- Live waveform and FFT capture through Android Visualizer.
- AE-inspired switchable visual compositions and live controls.

## Not yet implemented
- Offline PCM analysis and robust BPM/beat-grid/downbeat detection.
- Shared GPU renderer for preview and off-screen export.
- H.264/AAC MP4 muxing at 1080x1920/60 FPS, progress, cancellation and audio synchronization.
- Device performance validation.

## Build
JDK 17, Android SDK 35, Gradle 8.9. Run `gradle :app:assembleDebug` and `gradle :app:testDebugUnitTest`. GitHub Actions uploads `sonora-visuals-debug-apk`.
