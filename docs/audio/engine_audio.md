# Engine Audio: How It Works and How to Replace the Placeholders

Tank engine and track sounds are driven by a simulated engine speed (RPM) per tank, not by which key is pressed. Each tank type maps to one of two engine families:

| TankType (in-game name) | Engine family | Character |
|---|---|---|
| `STANDARD` (M1A2 Abrams MBT) | Gas turbine (Abrams AGT1500 class) | Compressor whine plus roar, with a slow spool-up |
| `STEALTH` (Shadow Stalker) | Gas turbine, quiet variant | Same recordings, lower gain and low-passed ("whispering death") |
| `HEAVY` (M1A2 TUSK II Heavy) | Diesel V12 | Deep rumble, pitched down, slow to rev |
| `LIGHT` (LAV-25 Stryker) | Diesel V12, light variant | Same recordings, pitched up, quick to rev; **no track layer** (wheeled) |

Only `STANDARD` and `STEALTH` use the turbine. Any new vehicle gets the diesel. A tracked one, such as a Bradley, should keep the track layer on (`trackGain` > 0). A wheeled one, like the LAV-25, gets `trackGain` 0. `forType` is an exhaustive switch, so adding a `TankType` won't compile until you pick its profile.

The sounds shipped today are **synthesized placeholders** made by `EngineSoundSynth`. They are original work and legally clean, but they are not real recordings. Replace them with sourced recordings as you find them. Each file can be swapped on its own.

## How it works

| Piece | Role |
|---|---|
| `EngineSoundProfiles` | The TankType → engine family table, the loop list with the RPM each was recorded at, and per-variant tuning: spool rates, pitch, gain, brightness, track level |
| `EngineAudioModel` | Pure logic, unit-tested. It turns throttle and measured speed into a smoothed RPM, then into an **equal-power crossfade between the two loops recorded nearest that RPM**, each pitched by `rpm / recordedRpm`. It also runs start/stop phases, track loops and random squeaks. |
| `TankAudioEmitter` | The OpenAL side, one per tank. Every loop plays continuously at the tank's position and only gains change, so crossfades never click. With the EFX extension it adds a low-pass (duller when coasting, very dull for STEALTH) and air absorption with distance. |
| `ClientTank.getAudioSpeed()` / `getAudioTurnRate()` | Speed and turn rate measured from position updates. Remote tanks work with no protocol change. |
| `TankBattleGame.updateTankAudio()` | Feeds every tank each frame. The local throttle comes from W/S/A/D. Enemies are audible only while they are drawn, so engines never reveal a cloaked or out-of-sight tank. |

## License rules: what may go in `sounds/`

The repo is public and CI publishes the client JAR to GitHub Releases, so **every file in `sounds/` is publicly redistributed**. Only use licenses that allow that:

| ✅ Allowed (license id for `credits.json`) | ❌ Not allowed |
|---|---|
| Your own recordings / sound design (`nettank-original`) | Any **NC** (NonCommercial) or **ND** (NoDerivatives) license |
| CC0 (`CC0-1.0`) | Sonniss GDC bundles: free for games, but no public redistribution of the files |
| US federal government works, e.g. DVIDS (`PD-USGov`) | Pixabay, ZapSplat, Mixkit, any paid pack: their EULAs ban standalone redistribution |
| CC-BY 3.0 / 4.0 (`CC-BY-3.0`, `CC-BY-4.0`), OGA-BY (`OGA-BY-3.0`) | Freesound "Sampling+", or anything with no stated license |
| CC-BY-SA (`CC-BY-SA-3.0`, `CC-BY-SA-4.0`): the edited sound becomes BY-SA too | Clips from YouTube, films, other games or game mods; AI generators |

- Check the license **on the download page when you download**, and record the URL and date.
- If the page's license badge and its description disagree, follow the badge.

`AudioCreditsTest` enforces this. The build fails if any `.ogg`/`.wav` under `sounds/` lacks an entry in `sounds/credits.json`, or uses a license id outside the allowed list.

## Where to find free sources

1. **[DVIDS](https://www.dvidshub.net)**: US military video, public domain.
   - Search "Abrams B-roll", "tank gunnery", and NATO exercises such as "Combined Resolve" or "Defender Europe". Footage shot by US personnel often includes Leopard 2 and Challenger 2 diesels, and it is still a US government work.
   - Prefer B-roll: natural sound, no music or narration.
   - For each clip:
     - confirm the page says Public Domain ([terms](https://api.dvidshub.net/docs/copyright))
     - use a stretch with no music, voices or third-party footage
     - credit the videographer
     - don't imply DoD endorsement
   - Extract the audio with `ffmpeg -i clip.mp4 -vn -ac 1 -ar 48000 -c:a pcm_s16le raw.wav`.
2. **[Freesound](https://freesound.org)**, with the license filter set to *Creative Commons 0* and *Attribution*.
   - Good starting points: [Tank Engine Loop](https://freesound.org/people/qubodup/sounds/200303/) (CC-BY 4.0) and [Truck Engine Idle Loops](https://freesound.org/people/qubodup/sounds/187564/) (CC0, construction diesels).
   - Search terms: `tank`, `bulldozer`, `excavator`, `diesel idle`, `jet idle`, `APU`, `turbine`, `chain rattle`, `metal clank`.
3. **[OpenGameArt](https://opengameart.org)** (CC0 / CC-BY / OGA-BY): clanks and squeaks for the track one-shots.
4. **Record your own.**
   - Tracked excavators and dozers are the best stand-in, giving you a big diesel and steel tracks together. Airshows and airport fences give you turbines.
   - A phone works: set iPhone Voice Memos to *Lossless*, use a foam windscreen, stand 5–15 m away, and capture 30 s or more of steady held RPM.

## Replacing a placeholder with a recording

Each sound is looked up as `<name>.ogg` first, then `<name>.wav`, so a recording replaces a placeholder by dropping it in.

1. **Prepare the clip**:
   - mono (OpenAL only positions mono)
   - 44.1 or 48 kHz
   - peaks around −1 dBFS
   - for loops: a steady stretch of 2–5 s, cut at zero crossings and crossfaded into itself so it repeats without a click
2. **Find the RPM it was recorded at.** A V12 diesel fires 6 times per crank revolution, so **RPM = firing frequency × 10**. In Audacity, select the loop, open *Analyze → Plot Spectrum*, find the lowest strong peak, and multiply by 10. For turbines, order the loops by whine pitch and spread them across 60–100 (% N1).
3. **Encode it** with `oggenc -q 5 loop.wav -o loop_1500.ogg` (install with `brew install vorbis-tools`). The game's decoder reads Ogg Vorbis, not Opus.
4. **Swap the files.** Put the `.ogg` next to the placeholder with the same base name, for example `sounds/engines/diesel_v12/loop_1500.ogg`, and **delete the matching `.wav`**. The test fails if both exist.
5. **Fix the RPM.** If the recording's RPM differs from the file name, rename the file and update `recordedRpm` (and the path) in `EngineSoundProfiles`. You can use more or fewer loops than the placeholders; each loop only needs to sit within about ±20% pitch of its neighbours.
6. **Add a `credits.json` entry**: title, author, url, license id, licenseUrl, retrieved date, and what you changed. List every source layered into the file.
7. **Run `mvn -pl nettank-client -am test`**, then play: idle, full throttle, coast, reverse, pivot.

To regenerate the placeholders, run this from the repo root after a build:

```shell
java -cp nettank-client/target/classes:nettank-common/target/classes \
    org.chrisgruber.nettank.client.util.EngineSoundSynth          # add --force to overwrite existing .wav
```

The generator never touches a sound that has an `.ogg`, so sourced recordings are safe. `AssetPipelineRunner` no longer writes engine audio.

## Tuning knobs

| What | Where |
|---|---|
| Which engine family each tank uses, pitch, loudness, brightness, spool speed, track level | `EngineSoundProfiles` |
| Cruise RPM under full throttle, crossfade width, pitch limits | `EngineAudioModel` constants |
| How loud engines and tracks sit against shots and explosions, air absorption | `TankAudioEmitter` constants |
| Local throttle per key (forward 1.0, reverse 0.8, pivot 0.7) | `TankBattleGame.handleGameInput` |
| Start/stop timing: when loops fade in under the start one-shot | `EngineSoundSet` start/stop fields in `EngineSoundProfiles` |
