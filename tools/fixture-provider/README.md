# Fixture provider

Loopback-only fake AniList and MyAnimeList servers for manual emulator checks and README screenshots. They never contact the real providers, and all of their data is fictional.

| Script | Purpose |
| --- | --- |
| `provider_server.py` | AniList GraphQL + MyAnimeList REST/OAuth fixture on `127.0.0.1:18820`, with a control API on `:18821` (`/snapshot`, `/health`, `POST /configure`). |
| `sync_provider_server.py` | Smaller fixture for outbox/sync delivery checks. |
| `showcase.py` | Seeds `provider_server.py` with a fictional collection and generated poster art (needs Pillow). |

## Showcase run

```bash
python3 tools/fixture-provider/showcase.py --output build/showcase/server --art build/showcase/art
python3 tools/fixture-provider/provider_server.py --output build/showcase/server --art build/showcase/art
```

In another shell, build the fixture-aware test runner and launch the app against the loopback server:

```bash
./gradlew -Pmio.testRunner=io.morgan.mio.SyncEmulatorRunner :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:18820 tcp:18820
adb shell am instrument -w -e task20Fixture true -e task21Fixture true -e provider anilist \
  -e class io.morgan.mio.RefreshEmulatorFixtureTest#holdOnboardingForCli \
  io.morgan.mio.test/io.morgan.mio.SyncEmulatorRunner
```

Tap **Continue with AniList**; the fixture authorizes immediately. Release the hold with `adb shell run-as io.morgan.mio touch files/task21-finish`.
