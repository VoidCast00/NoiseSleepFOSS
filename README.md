# NoiseSleep

White, pink and brown noise you can tune for sleep, with a sleep timer. One screen, no clutter.

- **Colour**: blend smoothly from white → pink → brown
- **Warmth**: take the hiss off the top, from bright to deep and muffled
- **Volume**
- **Sleep timer**: Off, 15, 30, 60 or 90 minutes, fading out over the last minute

The noise is generated live on the phone, so there are no loops or seams, and every change is
heard straight away. It keeps playing with the screen off, pauses for phone calls and stops when
headphones are unplugged. Your settings are remembered.

No internet permission, no accounts, no ads, no tracking.

## Build

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # checks no setting clips or goes silent
```

Android 10+ · Kotlin + Jetpack Compose · GPL-3.0

> Built largely with Claude Code under one person's direction.
