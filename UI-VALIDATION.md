# UI refresh validation

Validated October 4, 2026, on Windows with JDK 24 and the exact JavaFX 21.0.2 jars declared in `pom.xml`. Maven is not installed on PATH on this host, so compilation uses `javac` with the existing Maven dependency cache. No dependencies or runtime versions were changed.

## Reproduce

Run `powershell -ExecutionPolicy Bypass -File .\verify-ui.ps1` from the project. It compiles the application and `UiSmokeCheck`, makes a new isolated directory under `target`, seeds only synthetic entries, stubs AI coaching, and writes rendered PNGs. It refuses to run against an existing database or a directory containing credentials. It never reads the user's journal or config.

Add `-LaunchPreview` to open the verified JavaFX app with that sample journal and stubbed coaching. Saving and deleting in this preview affect only its temporary journal. Closing the preview ends the process.

## Automated coverage

- All five FXML views load and render against the shared stylesheet.
- Empty journal and empty trend states.
- Overview counts and journal data agree with five seeded entries.
- Both 1180×820 and 920×680 scene layouts, including long reflection text.
- Mood selection and inline validation for missing mood/description.
- Trend range changes, distinct series, and keyboard-readable numeric values.
- Save/progress state, duplicate-submit prevention, persistence, a stubbed coaching dialog, and return to overview.
- Any uncaught JavaFX exception fails the harness.

The first full smoke pass exposed an attempt to overwrite JavaFX's bound accessible text on chart symbols; it was removed in favor of JavaFX's native semantics and the explicit daily-values view. Final screenshots also guided the simplified overview header, flatter summary section, wider Delete column, and styled disclosure pane.

## Scope and limits

The original SQLite schema, repository behavior, scoring logic, configured API, and journal data are preserved. No paid/live AI request was part of UI verification. The existing local keyword-based tone model is labelled accurately. Existing repository debug logging and API response parsing are outside this UI change.

Rendered screenshots verify the actual FXML/CSS output, not an HTML reconstruction. Native keyboard and dialog checks can additionally be performed in the isolated `-LaunchPreview` window. There is no decorative motion, and chart/disclosure animations are explicitly disabled.

The automated execution host kept the optional preview process without an exposed native window handle, so direct Windows mouse/keyboard interaction was not verified in this session. The JavaFX scene renders and programmatic interaction checks above completed successfully; do not treat those as a screen-reader or real-keyboard audit.
