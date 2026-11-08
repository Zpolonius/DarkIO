# DarkIO

Android-launcher i Kotlin + Jetpack Compose til Pixel 10. minSdk 35, targetSdk 36 (compileSdk 37.2, fordi AndroidX kræver det).
Krav: `docs/DarkIO – kravspecifikation v1.md` (F1–F21, N1–N11). Design: `docs/design-system.md` og `docs/tokens.json`.

## Kommandoer (Git Bash; brug `gradlew.bat` i PowerShell)
- Byg: `./gradlew -q --console=plain assembleDebug`
- Unit-tests: `./gradlew -q --console=plain testDebugUnitTest` (én klasse: `--tests 'app.darkio.TokensTest'`)
- UI-tests (emulator kørende): `./gradlew -q --console=plain connectedDebugAndroidTest`
- Lint: `./gradlew -q --console=plain lintDebug` (rapport: `app/build/reports/lint-results-debug.html`)
- Release med R8: `./gradlew -q --console=plain assembleRelease`

## Struktur
- `app/src/main/java/app/darkio/` – `MainActivity` og pakker pr. lag: `data/` (LauncherApps, Room, DataStore, Health Connect), `home/`, `drawer/`, `dashboard/`, `settings/`
- `app/src/main/java/app/darkio/ui/theme/DarkIOTheme.kt` – DarkIOColors, DarkIOType, DarkIOShapes, DarkIOSpace, DarkIOSize
- `app/src/main/res/font/` – Space Grotesk og JetBrains Mono (OFL, licenser i `licenses/`)
- `app/src/test/` – JUnit + Turbine; `app/src/androidTest/` – Compose UI-test; `app/schemas/` – Room-skemaer (commit dem)

## Regler
- Minimal kode: ingen abstraktion før den bruges to steder, ingen interfaces med kun én implementering, constructor-injektion i hånden (ingen Hilt/DI-bibliotek).
- Afhængigheder: kun Compose, Room, DataStore, Coroutines, Health Connect-klient (+ JUnit, Turbine, Compose UI-test). Ingen netværksbibliotek, ingen INTERNET-tilladelse, ingen QUERY_ALL_PACKAGES.
- Hver skærm bygges fra `docs/tokens.json` via DarkIOColors/DarkIOType/DarkIOShapes (og DarkIOSpace/DarkIOSize). Ingen hårdkodede farver, størrelser eller fonte i skærmkode. Mangler en værdi: tilføj den til tokens.json først, derefter til DarkIOTheme.kt (TokensTest tjekker farverne).
- Ydelse: intet arbejde på main-tråden ud over UI (I/O, PackageManager, Room, DataStore, Health Connect på `Dispatchers.IO`/`Default`), lister med stabile keys (`key = { it.id }`), intet baggrundsarbejde (ingen WorkManager, services eller alarmer; data hentes når hjemskærmen vises).
- Apps identificeres af pakkenavn + aktivitet + brugerprofil, aldrig af navn. Room-migrationer må aldrig slette brugerdata.
- Tekst i appen er dansk og ligger i `strings.xml`.
- Tests: målrettede tests under udvikling (`--tests`), fuld suite (unit + lint, og UI-tests hvis emulator kører) én gang før rapport.
- Én PR pr. issue; PR-beskrivelsen lister krav-ID → test der beviser det.
