# Proactive prototype validation

Developed from the October 5, 2026 handoff for Danial Tajabadipour. This record separates implementation from verified live behavior.

## Baseline

- Preserved local desktop commit: `79126aadfe2d65c06cc6ce331e66a4c97a265d14` (`Refresh mood journal and prepare verified Windows release`). Original checkout: `C:/Users/Danial/Desktop/AIMoodCheker`, clean at inspection.
- GitHub `origin/main`, verified by fetch: `39f34965ef6dd851dcb356b9dbda194e443c5109`. It is the direct ancestor of the local desktop release. The local release was one commit ahead, not overwritten.
- Work checkout: `personal website/ai-mood-checker`, branch `codex/proactive-next-action`, cloned locally without hard links. No personal `mood.db`, `.env` or `config.properties` was copied. No `AGENTS.md` applied in the inspected repository/parent locations.
- Java 24.0.2, JavaFX 21.0.2, SQLite 3.44.1.0, Jackson 2.16.1, Node 22.13.0. Maven was absent; the existing dependency cache was reused.
- Fresh `verify-functional.ps1`: **39 checks passed** (including journal CRUD, chronology, concurrency, null scores, injected remote failures and offline behavior).
- Fresh `verify-ui.ps1`: **26 checks passed**, including FXML rendering at 1180 and 920 widths, save feedback, duplicate-submit prevention, draft/delete decisions and recovery from a database write lock. Real native keyboard and screen-reader behavior remain unverified.
- Restricted Java execution could not read dependency jars reliably. Tests were rerun under approved local execution with the installed JDK. The app's earlier Java 17 contribution instructions are stale; the actual build targets 24.

## New checks

- `verify-planner.ps1`: **70 passed**. Deadline/capacity feasibility, event buffers, dependencies, explicit energy, no-fit/empty/unknown/completed outcomes, stale suggestions, repeated/concurrent responses, atomic rollback, persistence/reopen, superseded sessions, calendar failure retention, recurrence-instance parsing, cancellations, all-day DST handling, PKCE/state/replay, pagination, bounded response consumption, quiet hours, pause/snooze/limit, expired jobs, integer bounds and explicit completion semantics.
- `verify-planner-http.ps1`: **14 passed**. Serves the app, restrictive CSP, runnable sample, Origin/request-token/Host restrictions, stale acceptance, duplicate acceptance and completion, export, sample/personal separation, deletion and missing OAuth configuration. Uses a separate local server and database; no external requests.
- `node --check proactive/web/app.js`: passed.

## Browser verification

Verified in the Codex in-app browser against the Java server: late-shift update, acceptance, reload persistence, explicit completion (90 to 70 minutes), task creation, confirmed-step creation, preference saving, clock advancement and the opt-in simulated inbox. The synthetic test changes were then reset, leaving the changed-shift sample ready to explore.

Inspected the interface at the default desktop width, 390×844, 375×812 and 812×375. The phone and landscape DOM checks found no horizontal overflow. Visible buttons at 375px were at least 44px high. Final screenshots: [desktop](screenshots/planner-desktop.jpg), [small phone](screenshots/planner-phone.jpg). Actual phone hardware, OS notification permissions, screen-reader behavior and maximum system font scaling have not been verified. The interface uses a fixed light theme and respects reduced-motion preferences for its brief control transitions.

## Feature status

| Capability | Status |
| --- | --- |
| Changed-shift local product loop | Implemented; automated and browser checks |
| Responsive Today, Your plan, Connections, Preferences | Implemented; browser layout checks |
| Persistent task/response state and explicit completion | Implemented; real SQLite tests and browser reload |
| Optional capacity/energy, deadline conflicts, deterministic explanations | Implemented and tested |
| Read-only Google OAuth and primary-calendar snapshot adapter | Implemented; fake transport tests only |
| Real Google account sign-in/sync | Unverified; OAuth client configuration and user consent required |
| Durable local notification jobs/inbox | Implemented and tested; opt-in, quiet hours, pause, snooze, daily cap |
| Real phone notification delivery / hosted access | Not implemented or verified |
| AI task decomposition / model evaluation | Not implemented; baseline does not call an AI model |
| Multi-user authentication/isolation | Not implemented; loopback-only single-person prototype |
| Usage/adoption/performance outcomes | No claims established |

No live Google call, paid model call, public deployment, external message or personal journal import was performed. There are no user recruitment or production impact claims.
