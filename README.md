# AI Mood Checker

## New: a next-action planner

The repository now also contains a local web prototype for students whose schedules change. Try a late shift, choose how much time you have, and accept one confirmed step that fits around your calendar and before its deadline. Mood logging is optional; the planner does not read the desktop journal.

```powershell
.\run-planner.ps1
```

Open **http://127.0.0.1:8471/**. The sample day works without an account or API key. See [planner setup and walkthrough](proactive/README.md) and [validation and feature status](docs/PROACTIVE-VALIDATION.md).

The Google Calendar adapter includes read-only Desktop OAuth and bounded sync, tested with synthetic responses. Real sign-in needs your Google OAuth configuration and has not yet been verified. Reminders currently reach a local inbox; phone push delivery, hosted access and multi-user authentication are not implemented.

## Existing desktop journal

A Windows JavaFX desktop journal with three mood choices, saved reflections, history, and mood/tone trends. Check-ins and the keyword-based tone estimate work locally. AI reflections are optional and require your own API configuration.

## Use the packaged application

Extract the entire `AIMoodChecker-Windows-x64.zip` archive, then open `AIMoodChecker/AIMoodChecker.exe`. Keep the `app` and `runtime` folders beside the executable. Java and JavaFX are bundled; a separate SDK installation is not required.

The packaged application stores `mood.db` in `%LOCALAPPDATA%\AIMoodChecker`. It does not overwrite or automatically import the development repository's journal. To use an existing journal, close the app, back up that journal, and launch the executable with `--data-dir "C:\path\to\the\existing\journal-folder"`. The folder must be writable. `--offline` disables remote AI reflections explicitly.

This is an unsigned local app image, not a signed installer. Native Windows keyboard/screen-reader and double-click launcher checks still need to be completed on an interactive desktop before public distribution.

## Optional AI reflections

Without an API key, saving a check-in still works and the app explains that AI reflections are not configured. With a key, saving sends the current mood, current reflection, and aggregate mood counts to the configured OpenAI chat-completions model. API usage can incur charges. Historical reflection text is not included in that request.

Provide `OPENAI_API_KEY` in the process environment, or create `config.properties` in the journal data folder containing `openai.api.key=YOUR_KEY`. The same folder may contain a `.env` file with `OPENAI_API_KEY=YOUR_KEY`. Configuration files and database files must remain outside source control and release archives. Optional properties are `openai.model`, `openai.max.tokens`, and `openai.temperature`; the existing defaults remain `gpt-3.5-turbo`, `1200`, and `0.8`. Availability of that remote model was not tested during offline release verification.

The text-tone line is a small local keyword heuristic. It can misunderstand context; it is not an AI or clinical assessment. Missing legacy scores remain unknown rather than appearing negative.

## Build from source on Windows

Requirements: JDK 24 or newer, PowerShell, and the Maven dependencies declared in `pom.xml`. Set `JAVA_HOME` to the JDK, or pass `-JdkHome` to the scripts. The scripts also detect the standard local `C:\Program Files\Java\jdk-24` installation.

If dependencies are absent, use Maven to populate the local cache:

```powershell
mvn dependency:go-offline
```

Builds use the exact JavaFX 21.0.2, SQLite 3.44.1.0, and Jackson 2.16.1 dependencies declared in `pom.xml`, plus SQLite's SLF4J API dependency. A separately downloaded JavaFX SDK is unnecessary.

```powershell
.\build.ps1
.\run-app.ps1 -Offline
.\verify-functional.ps1
.\verify-ui.ps1
.\package-windows.ps1
```

`run-app.bat` calls the PowerShell launcher. The source launcher always uses the repository directory for its journal, preserving the existing development `mood.db` location. The packaged launcher defaults to Local AppData as described above.

Each build uses a fresh `target/build-<id>` directory. `package-windows.ps1` produces both an application folder and a ZIP there, with a printed SHA-256 digest. It includes compiled application classes, resources, dependency jars, and a Java runtime. Test classes, sample journals, API keys, `.env`, and config files are excluded.

## Verification

`verify-functional.ps1` uses a fresh synthetic journal and injected fake HTTP responses. It checks local scoring, JSON escaping/parsing, missing credentials, HTTP/network failures, interruption, CRUD persistence, chronology, missing scores, date ranges, and concurrent saves. It makes no network request.

`verify-ui.ps1` loads and snapshots the actual JavaFX views, checks validation and duplicate-save prevention, confirms/cancels draft and delete dialogs, and tests recovery from a database write failure. Its coaching service is stubbed and offline mode is enabled. Screenshots are written under the printed build path. Add `-LaunchPreview` to inspect the sample journal interactively; it never opens the personal journal.

See `UI-VALIDATION.md` for the approved design's rendering coverage and `RELEASE-VALIDATION.md` for the latest functional and packaging evidence.
