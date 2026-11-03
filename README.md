# NoiseSleep

> [!WARNING]
> **Small personal app, AI-generated, not reviewed.** This was built largely by an AI coding
> assistant for one person's own use. The code has not been reviewed or audited, and it has had
> little testing on real devices. It is shared as-is, with no support and no guarantees.
> Use it at your own risk, and keep the volume at a level that is safe for your hearing.

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
