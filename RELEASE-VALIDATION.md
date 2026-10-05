# Release validation — October 4, 2026

The approved JavaFX design is unchanged. Verification used only synthetic reflections and new isolated SQLite journals. No personal journal, configuration file, API credential, or paid AI request was used.

## Functional fixes

- Coaching requests use Jackson JSON encoding, so quotes, newlines, backslashes, and Unicode in reflections remain valid. Responses are decoded structurally instead of stopping at the first escaped quote.
- Missing credentials return immediately without an HTTP request. Explicit offline mode also prevents remote coaching. HTTP errors, invalid/empty responses, transport failures, and thread interruption return an unavailable message while preserving the saved check-in.
- Local tone uses whole words, handles simple short negation and mixed keyword tone, and accepts empty legacy text. `unhappy` no longer matches `happy`; substrings such as `badge` do not match `bad`. It remains a small heuristic, not an AI or clinical model.
- Same-second journal entries sort by descending ID after date and creation time. SQL NULL scores remain unknown and are omitted from tone averages/plots rather than becoming negative zero scores.
- Each database operation owns its connection. Closing one operation cannot close another operation's connection. SQLite has a bounded busy timeout; failed saves preserve the draft and enable retry.
- Configuration values and mood/date/score debug dumps are no longer written to logs. Packaged data uses Local AppData; the development launcher preserves the repository journal location.

## Checks completed

- **39 functional checks passed** with injected HTTP responses and a fresh SQLite database: scoring, JSON round trips, missing keys, offline mode, 401/429/500, malformed and empty responses, transport failure, interrupted requests, CRUD, stable chronology, date ranges, null scores, daily averages, and 16 concurrent saves.
- **26 JavaFX UI checks passed** at 1180×820 and 920×680. Includes FXML/CSS rendering, empty views, validation, save feedback, duplicate-submit prevention, draft keep/discard, delete keep/confirm, and an exclusive database lock that fails the save without losing the draft or starting coaching.
- Fresh overview, check-in and trend PNGs were inspected/produced with synthetic entries. The overview retains the approved design.
- Compilation used **JDK 24.0.2**, **JavaFX 21.0.2**, **SQLite 3.44.1.0**, and **Jackson 2.16.1**. Maven is absent on this host; scripts used the exact dependencies already present in the Maven cache.

## Windows package

`package-windows.ps1` successfully produced an unsigned Windows x64 application image and ZIP using JDK 24's jpackage/jlink. The image includes a Java runtime and the JavaFX modules; no separate JavaFX SDK is required.

- ZIP: **45.98 MiB**.
- Extracted application image: **107.38 MiB**.
- SHA-256: `5BAC4FCED4726A985C2F325E8A93F756E95FECAD6168BDF87AEA655B8B5DF2BA`.
- Application entry point: `com.aimoodchecker.Launcher`.
- The package input is an explicit allowlist: compiled application jar and five dependency jars. Test sources/classes, synthetic journals, personal journals, `.env`, and `config.properties` are excluded.

The package launcher/runtime verification is a separate root integration check and was pending when this report was written. Actual native window interaction and screen-reader behavior have not been verified because the automated host does not expose a controllable JavaFX window. No claim of signed-installer readiness is made.

## Reproduce

Run `verify-functional.ps1`, `verify-ui.ps1`, and `package-windows.ps1` from the source repository. Every run creates a fresh directory below `target`. The packaged executable accepts `--offline --data-dir "C:\path\to\a\fresh\folder"` for safe integration checks.

Existing harmless dependency notices remain: SLF4J has no logging binding, and JavaFX 21 emits a JDK 24 deprecated `sun.misc.Unsafe` warning while rendering. These did not fail the checks. The live OpenAI model/key path remains unverified because all coaching verification was offline.

## Coordinator package smoke check

- The completed Windows archive is 48,210,784 bytes (45.98 MiB), with SHA-256 `5BAC4FCED4726A985C2F325E8A93F756E95FECAD6168BDF87AEA655B8B5DF2BA`.
- The packaged `AIMoodChecker.exe` launched with `--offline --data-dir` pointed at a fresh isolated test directory, created its SQLite journal, and remained running during the bounded check. Only that test process was closed afterward.
- Archive inspection found no `.env`, `config.properties`, mood database, or SQLite journal files. Source launch continues to use the development repository's existing journal location; the packaged launch uses Local AppData by default.
- Native keyboard/screen-reader interaction and real AI requests remain unverified. SLF4J no-provider and JavaFX native-memory deprecation notices were non-fatal.
