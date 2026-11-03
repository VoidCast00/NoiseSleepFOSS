# What the research says (and what NoiseSleep does about it)

Summary of a deep-research pass done in November 2026. It is a summary of studies, not medical
advice. Exact settings (filter cutoffs, wave depth, fade length) have **not** been established by
sleep trials; the defaults below are sensible engineering choices, not proven optima.

## Key findings

- **No noise colour is established as "best for sleep".** White, pink and brown are different
  textures (0, −3 and −6 dB per octave). Pink is a reasonable starting point, not a proven winner.
- **Quiet is the baseline to beat.** Noise is most useful for masking a disturbance you can't
  avoid. Someone sleeping well in a quiet room does not necessarily benefit from adding it.
- **Louder is not better.** In a 2026 lab study, continuous pink noise at 50 dB(A) reduced REM
  sleep by about 19 minutes compared with a quiet room, even though total sleep time looked
  normal. The WHO recommends below 30 dB(A) in bedrooms.
- **Overall evidence is weak and mixed.** A systematic review of continuous broadband noise found
  results ranging from improvement to disruption, with very low certainty.
- **"Pink noise boosts deep sleep and memory" comes from a different intervention:** short
  pulses timed to brain waves measured by EEG. Continuous playback, or slow ocean swells, is not
  the same thing, and a phone cannot do it without EEG.
- **No sleep-optimal wave speed or depth is known.** Waves are a preference, best kept subtle.
- **No evidence shows an ideal fade length,** or that a timer beats playing all night. A timer
  suits winding down; playing all night suits masking ongoing noise.

## What changed in the app

| Area | Change |
|---|---|
| Loudness | Colour and warmth no longer change the volume: output is kept at a steady A-weighted level |
| Rumble | Fixed 30 Hz high-pass (phones can't play it, and it removes brown-noise drift) |
| Start | Fades in over 5 s (quiet first) |
| Waves | Strength now in decibels: 1.5 / 3 / 4.5 / 6 / 9 dB. Mostly volume moves, brightness only slightly. Still off by default |
| Defaults | Pink, warmth ≈ 4 kHz, waves 12 s at 3 dB, timer 60 min with a 10 min fade included |

Already in line with the findings: waves off by default, fade included in the timer, playback
pauses when headphones disconnect, timer off = plays all night for masking.

Deliberately **not** added: "deep sleep" pulses (need EEG), binaural beats, infant or tinnitus
modes, microphone-adaptive volume.

## Main sources

- Riedy et al., 2021, *Sleep Medicine Reviews*: systematic review of continuous noise and sleep
- Capezuti et al., 2022, *Journal of Clinical Sleep Medicine*: review of 34 auditory-stimulation studies
- Messineo et al., 2017, *Frontiers in Neurology*: broadband sound and sleep onset (industry funded)
- Ding et al., 2025, *Sleep Medicine*: meta-analysis of 12 RCTs, questionnaire outcomes
- Basner et al., 2026, *SLEEP* and *Sleep Health*: pink noise and earplugs, polysomnography
- Papalambros et al., 2017, *Frontiers in Human Neuroscience*; Ngo et al., 2013, *Neuron*: EEG-timed pink-noise pulses
- WHO night noise guidelines for Europe (bedroom level below 30 dB(A))
