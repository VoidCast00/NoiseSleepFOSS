# NoiseSleep

> [!WARNING]
> **Small personal app, AI-generated, not reviewed.** This was built largely by an AI coding
> assistant for one person's own use. The code has not been reviewed or audited, and it has had
> little testing on real devices. It is shared as-is, with no support and no guarantees.
> Use it at your own risk, and keep the volume at a level that is safe for your hearing.

White, pink and brown noise you can tune for sleep, with ocean waves and a sleep timer.
One screen, no clutter.

- **Colour**: blend smoothly from white → pink → brown
- **Warmth**: take the hiss off the top, from bright to deep and muffled. Colour and warmth
  change the character of the sound, not its loudness
- **Volume**
- **Ocean waves** (optional): slow swells every 4–20 s, from 1.5 to 9 dB deep
- **Sleep timer**: 5 min to 3 h with an adjustable fade-out, or off to play all night
- **Quick Settings tile** to start and stop it from the notification shade

The noise is generated live on the phone, so there are no loops or seams, and every change is
heard straight away. It keeps playing with the screen off, pauses for phone calls and stops when
headphones are unplugged. Your settings are remembered.

No internet permission, no accounts, no ads, no tracking.

## Use it gently

It may help mask disturbances. It is **not** a treatment for insomnia and does not boost deep
sleep. Research suggests louder is not better: continuous noise at 50 dB(A) has been found to
reduce REM sleep, and the WHO recommends below 30 dB(A) in bedrooms. Start at the lowest level
you can hear and only turn it up if you need to. The phone's volume percentage does not tell you
the sound level at your ears. Not intended for babies.

See [docs/RESEARCH.md](docs/RESEARCH.md) for what the studies say.

## Build

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # loudness model, no clipping, waves
```

Android 10+ · Kotlin + Jetpack Compose · GPL-3.0
