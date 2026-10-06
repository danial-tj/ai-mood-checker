# The next-action prototype

A phone-friendly browser interface that answers one question: **what can I realistically do next, now that my day has changed?** Working students are the first audience hypothesis. Demand, retention and health benefits have not been established.

This is a local, single-person development prototype. It is intentionally bound to `127.0.0.1`; a phone cannot access it over Wi-Fi yet. Responsive layout is different from a hosted mobile product.

## Run it

Use JDK 24 or newer and PowerShell. The original project already targets Java 24; both builds retain that version. The planner uses the existing SQLite 3.44.1.0 and Jackson 2.16.1 dependencies, without JavaFX at runtime.

If the Maven cache is empty, install the dependencies from the repository root with `mvn dependency:go-offline`. Maven is needed only to obtain missing dependencies. The build script copies the five required jars into ignored `proactive/target/lib`.

```powershell
.\run-planner.ps1
# Optional isolated storage or another loopback port:
.\run-planner.ps1 -Port 8472 -DataFile .\proactive\data\another-plan.db
```

Open [the local app](http://127.0.0.1:8471/). Keep the launcher running. Stop with Ctrl+C. State persists in `proactive/data/planner.db`, entirely separate from `mood.db`. The app never reads `.env`, desktop API configuration or journal history.

## Two-minute walkthrough

1. The initial sample clock is October 5, 2026, 5:45 p.m., America/Vancouver. A café shift ended at 5:30. A 90-minute report is due at 8 p.m.; dinner is fixed at 6:35–7:05.
2. Choose **Try a late shift**. The shift now ends at 6:05 and the sample clock becomes 6:10. Availability becomes 20 minutes. Energy is not inferred from the shift.
3. The planner suggests the confirmed 20-minute outline, fitting before dinner with a five-minute buffer. It also reports a deadline conflict: only 70 calendar minutes remain before the deadline, compared with 90 minutes of estimated work. It does not change the deadline.
4. Choose **Make this my next step**, then reload. The accepted action remains; effort is still 90 minutes.
5. Choose **Mark done**. Remaining report effort becomes 70 minutes. Repeating the request cannot subtract it again. **Skip for now** would preserve the effort.
6. Try 10 minutes, another suggestion, a light-energy choice, and the task editor. With insufficient capacity or unconfirmed steps, the app explains why it cannot choose an action.
7. In Preferences, opt into local planning prompts before replaying the late shift. The worker processes persisted jobs into an in-app inbox, respecting quiet hours, pause, snooze and a daily limit. The sample clock stays fixed unless the user advances it.

**Replay sample** replaces only planner data after confirmation. Export first if you want to keep custom changes. **Delete planner data** clears tasks, events, responses and preferences, disconnects the in-memory Google session and starts an empty personal plan. It is logical deletion, not a forensic secure erase or deletion of earlier exports.

## Google Calendar setup

The adapter is implemented and exercised with fake responses. It has not been connected to a real account in this development session.

1. In your Google Cloud project, enable the Google Calendar API and configure the OAuth consent screen for your app. If the app is in testing, add your own Google account as a test user.
2. Create an OAuth client of type **Desktop app**. This local application uses a loopback redirect, `http://127.0.0.1:8471/oauth/callback` (or the chosen port), a random state value, and PKCE with S256.
3. Provide `GOOGLE_CLIENT_ID` and, if the Desktop client includes one, `GOOGLE_CLIENT_SECRET` in the launcher's process environment. Keep these out of the repository, screenshots and chat. Restart the local server after configuring them.
4. In Connections, choose **Start my own plan**. This clears sample data after confirmation. Add your own task and estimate in Your plan.
5. Choose **Connect Google Calendar**, review Google's consent screen and grant read-only event access. The requested scope is `https://www.googleapis.com/auth/calendar.events.readonly`.
6. Verify the last successful sync and event times. Move a disposable test event in Google and press **Sync now**. Confirm that an overlapping accepted action needs review. Cancel the event and sync again.

Only the primary calendar is read. Google expands recurring instances. All-day dates use the calendar timezone and an exclusive end. Cancelled, transparent and self-declined events are excluded from busy time. Imported titles are escaped in the browser and never interpreted as instructions. Calendar data does not provide task effort, assignment instructions or confirmation of completion.

Sync uses a complete bounded snapshot from yesterday through the next 14 days, with at most ten 250-event pages. It deliberately does not combine incremental sync tokens with a moving date window. Each HTTP request has a timeout, a one-MiB body limit, and no redirect following. The worker attempts another sync every five minutes while the server runs; it does not hammer the provider with immediate retries. Partial, oversized and failed responses leave the previous calendar intact. Planning pauses when disconnected, after sync failure, or once the last successful sync is over 15 minutes old.

Access and refresh tokens exist only in process memory. Reconnect after a restart. **Disconnect** forgets the local tokens; it does not revoke the OAuth grant at Google. Revoke that grant separately in your Google Account if desired. The last imported calendar remains visible until planner data is cleared.

Official references used: [Desktop OAuth and loopback redirects](https://developers.google.com/identity/protocols/oauth2/native-app), [Calendar events.list](https://developers.google.com/workspace/calendar/api/v3/reference/events/list), and [recurring event instances](https://developers.google.com/workspace/calendar/api/guides/recurringevents).

## Architecture and decisions

`web/` contains the responsive HTML, CSS and browser JavaScript. The Java backend owns all planning and persistence. The UI does not implement a second scheduler. Native browser components keep this small prototype reproducible without adding a Node build pipeline; React/TypeScript remains an option if the interface grows. Node is used only by the optional HTTP verification script.

`Plan` defines tasks, confirmed steps, events, optional capacity, sessions, responses, preferences and notification jobs. `Planner` is a pure rule engine with injected time. Candidates are ordered by earliest deadline, stable task ID, then declared step order. A candidate must satisfy dependencies, explicit energy preference, remaining effort, deadline, buffers and one continuous free interval inside the user's availability. No task is split into invented work. Every new custom step is explicitly confirmed by the user.

`PlanStore` saves one versioned aggregate atomically in a separate SQLite database. This is an intentional local prototype tradeoff: transactions and replayable state with no premature shared-service schema. It is **not** a normalized multi-user persistence layer. Response keys make repeated acceptance/completion idempotent. Mutations roll back on failure. A changed calendar or capacity can invalidate an accepted session without calling it skipped or completed.

`PlanService` coordinates mutations and explanations. `GoogleCalendar` has an injectable transport for fixtures, full-snapshot semantics and PKCE sign-in. `PlannerServer` serves the UI and API on loopback with Host/Origin checks, an app request token, bounded request bodies and a restrictive content security policy. These controls do not substitute for authentication on a hosted service.

The new planner reuses the existing project's Java/JDBC/Jackson toolchain and persistence practices. `MoodEntry` is a useful standalone journal model; the singleton `EntryRepository`, default-timezone dates and local `DBConnection` are not suitable for shared user data. The planner does not reuse their personal journal database or keyword sentiment heuristic to infer capacity. The existing JavaFX controllers and optional ChatGPT coaching remain untouched.

Calendar-space warnings are an upper bound based on known events and task estimates. Unrecorded commitments, sleep, fatigue and uncertainty can make the real situation tighter. The app never claims that an absence of a warning guarantees the entire task will fit.

## Verify

From the repository root:

```powershell
.\verify-planner.ps1        # Java rules, SQLite, clock, adapter and local inbox
.\verify-planner-http.ps1   # Node 22+: isolated server on port 18471, HTTP boundaries
.\verify-functional.ps1     # Existing desktop journal, fresh synthetic database
.\verify-ui.ps1             # Existing JavaFX render and entry workflow checks
```

All tests use synthetic data and fake Google responses. No account, paid API or personal journal is needed. Test databases and logs remain under ignored `target` directories. Browser walkthrough and layout results are recorded in [validation](../docs/PROACTIVE-VALIDATION.md).

## Before a student pilot

Finish and verify a real Google connection with the intended account; choose a hosting environment; add authentication and tested user isolation; store connection secrets securely; and implement real notification delivery on a supported phone. Verify permission denial, revocation, quiet hours, restart recovery and expired links on that device. A local inbox and a running browser are not durable phone push delivery.

User-selected daily planning times, server-side push delivery, AI-generated proposed steps with validation/evaluation, account-level export/deletion, accessibility testing with a real screen reader and a real phone remain future work. No AI request is needed for this version. Do not recruit or contact people automatically.

For an eventual five-adult-student, two-week experiment, record denominators for first useful suggestion, return days, accept/adjust/dismiss, explicit completion, pauses and reported planning effort. Ask: “Did it fit?”, “Did it arrive at the right time?”, and “Was maintaining the task list worth it?” Do not collect journal text for usage analytics or describe this sample as evidence of adoption.
