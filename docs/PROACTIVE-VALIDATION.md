# Proactive prototype validation

Developed from the October 5, 2026 handoff for Danial Tajabadipour. This record separates implementation from verified live behavior.

## Research-informed support update — October 6–7, 2026

This update adds optional mood/energy check-ins, user-chosen meaningful activity/movement/rest, separate outcome and helpfulness feedback, research links, a local agent bridge, and a Google credential setup helper. The desktop journal is unchanged.

The current suite passes **223 checks** using synthetic data:

| Check | Passed | Coverage |
| --- | ---: | --- |
| Planner / persistence / Google fake transport | 132 | Two-hour expiry, time-only edits, no mood inference, availability limits even offline, reserved slots, calendar conflicts, paused work prompts, replay handling, history caps, data clearing and opted-in wellbeing conflict notices with reminder controls. |
| Agent API | 21 | Scoped tools, private-by-default responses, explicit sharing/confirmation, invalid arguments, notifications cannot write, support-only completion, no assumed benefit and accepted replacement priority. |
| MCP stdio bridge | 28 | Initialization, malformed/oversized input, local URL restrictions, bounded replies, split UTF-8 and credential-safe errors. |
| HTTP integration | 30 | Browser boundaries, bearer and Origin checks, real bridge-to-Java requests, check-in expiry, support completion, oversized requests, exports and missing Google configuration. |
| Google setup helper | 12 | Synthetic Desktop file, Web/invalid/missing configuration rejection, environment preservation and no credential output. |

Java compilation, browser JavaScript syntax (node --check) and git diff --check passed. The HTTP run rebuilt the Java sources after the conflict-delivery and replacement-priority fixes; the final wording-only change was then compiled and covered by the 132 planner checks. No actual Google, Dot, Muse or Grok account was used. Published research is described with limits; these short prompts and this app have not been clinically validated.

A source review caught and corrected hidden accepted-work controls, a misleading rescheduling instruction, silently extended energy expiry on time-only edits, and overlong offline support actions. To move a wellbeing action after a calendar conflict, explicitly skip/release it and choose another; there is no direct rescheduling feature yet. A follow-up review fixed agent selection of an older action needing review over its accepted replacement, restored opted-in wellbeing conflict notices while work prompts remain paused, and made the notice explicitly state that no automatic rescheduling occurred.

The isolated sample preview runs at http://127.0.0.1:8471/#today using ignored proactive/target/support-preview.db. The in-app browser failed to attach during this update, so the new interface has **not** had a successful visual or phone-layout walkthrough. Browser controls also timed out after the user signed in to Google Cloud on October 7. The setup guide is saved at [Google Calendar setup](GOOGLE-CALENDAR-SETUP.md); downloading the Desktop credential file and verifying real consent/sync remain pending. The screenshots and browser checks below describe the earlier planner interface, not this support update. Actual phone notifications, hosted accounts/isolation, consumer-agent connections and clinical/user outcomes remain unverified.

## Baseline

- Preserved local desktop commit: `79126aadfe2d65c06cc6ce331e66a4c97a265d14` (`Refresh mood journal and prepare verified Windows release`). Original checkout: `C:/Users/Danial/Desktop/AIMoodCheker`, clean at inspection.
- GitHub `origin/main`, verified by fetch: `39f34965ef6dd851dcb356b9dbda194e443c5109`. It is the direct ancestor of the local desktop release. The local release was one commit ahead, not overwritten.
- Work checkout: `personal website/ai-mood-checker`, branch `codex/proactive-next-action`, cloned locally without hard links. No personal `mood.db`, `.env` or `config.properties` was copied. No `AGENTS.md` applied in the inspected repository/parent locations.
- Java 24.0.2, JavaFX 21.0.2, SQLite 3.44.1.0, Jackson 2.16.1, Node 22.13.0. Maven was absent; the existing dependency cache was reused.
- Fresh `verify-functional.ps1`: **39 checks passed** (including journal CRUD, chronology, concurrency, null scores, injected remote failures and offline behavior).
- Fresh `verify-ui.ps1`: **26 checks passed**, including FXML rendering at 1180 and 920 widths, save feedback, duplicate-submit prevention, draft/delete decisions and recovery from a database write lock. Real native keyboard and screen-reader behavior remain unverified.
- Restricted Java execution could not read dependency jars reliably. Tests were rerun under approved local execution with the installed JDK. The app's earlier Java 17 contribution instructions are stale; the actual build targets 24.

## Initial planner checks

- `verify-planner.ps1`: **70 passed**. Deadline/capacity feasibility, event buffers, dependencies, explicit energy, no-fit/empty/unknown/completed outcomes, stale suggestions, repeated/concurrent responses, atomic rollback, persistence/reopen, superseded sessions, calendar failure retention, recurrence-instance parsing, cancellations, all-day DST handling, PKCE/state/replay, pagination, bounded response consumption, quiet hours, pause/snooze/limit, expired jobs, integer bounds and explicit completion semantics.
- `verify-planner-http.ps1`: **14 passed**. Serves the app, restrictive CSP, runnable sample, Origin/request-token/Host restrictions, stale acceptance, duplicate acceptance and completion, export, sample/personal separation, deletion and missing OAuth configuration. Uses a separate local server and database; no external requests.
- `node --check proactive/web/app.js`: passed.

## Initial planner browser verification

Verified in the Codex in-app browser against the Java server: late-shift update, acceptance, reload persistence, explicit completion (90 to 70 minutes), task creation, confirmed-step creation, preference saving, clock advancement and the opt-in simulated inbox. The synthetic test changes were then reset, leaving the changed-shift sample ready to explore.

Inspected the interface at the default desktop width, 390×844, 375×812 and 812×375. The phone and landscape DOM checks found no horizontal overflow. Visible buttons at 375px were at least 44px high. Final screenshots: [desktop](screenshots/planner-desktop.jpg), [small phone](screenshots/planner-phone.jpg). Actual phone hardware, OS notification permissions, screen-reader behavior and maximum system font scaling have not been verified. The interface uses a fixed light theme and respects reduced-motion preferences for its brief control transitions.

## Initial planner feature status

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
