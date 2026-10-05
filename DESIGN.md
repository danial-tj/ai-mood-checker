# AI Mood Checker

A quiet desktop journal. The primary job is to make a daily check-in feel easy, with room to read past entries and notice patterns. Preserve the existing JavaFX application and its three mood values: Happy, Neutral, Sad.

## Direction

Ivory paper, soft sage, forest-green controls, and restrained serif headings. Segoe UI supplies the readable native body and control type; Georgia is used only for editorial headings. The interface uses a persistent left sidebar, a focused main workspace, and a quiet status line. A single welcome headline and check-in action lead the overview. Journal counts are supporting information, never a wellbeing score or streak obligation.

The old blue glass cards, emoji navigation, repeated centered headings, and oversized margins have been replaced. The welcome artwork is a small JavaFX vector composition, so it remains crisp without adding an asset dependency.

## Theme contract

All shared color tokens live in `src/main/resources/styles.css` as JavaFX looked-up colors.

| Role | Value |
| --- | --- |
| Paper | `#F7F8F3` |
| Surface | `#FFFFFF` |
| Sidebar | `#EDF0E8` |
| Primary ink | `#203D34` |
| Secondary text | `#5C6B62` |
| Primary action | `#2E5F4B` |
| Selected surface | `#E1E9DC` |
| Boundary | `#D6DFD4` |
| Keyboard focus | `#8A682B` |
| Error / destructive text | `#9C4437` |

Use 14–15px body/input text, 12px supporting text, 21px section headings, 30px page titles, and a 38px welcome headline. Spacing follows 8/12/16/24/28/36px steps. Most surfaces use 7–10px corner radii. Buttons are at least 44px high; mood choices are at least 116px high. Avoid heavy shadows, glass effects, gradients, and decorative motion.

## Interaction

- Persistent navigation exposes Overview, Daily check-in, Your journal, and Mood trends, with selected state and a visible focus border.
- Mood choices use native toggle controls, vector faces, explicit text, and selected borders; color is never the only signal.
- Validation stays beside the form, focuses the missing control, and preserves entered text. A draft requires a deliberate discard when leaving or closing.
- Save shows its current state and disables repeated submission. Persistence and AI coaching run in background tasks. Navigation is temporarily disabled while saving so the result cannot return to the wrong page.
- Journal rows retain date, mood, reflection, text tone, and a labelled Delete action. Double-click or Enter opens the complete reflection. Deletion requires confirmation.
- Trends use solid green for self-reported mood, dashed ochre for keyword-based text tone, distinct point shapes, tooltips, and an expandable keyboard-readable daily-values view. No fabricated data is shown. Empty and one-day ranges have useful states.
- Coaching retains the AI label. The existing sentiment implementation is a local keyword heuristic, so journal/chart labels use **Text tone**, not AI sentiment.

## Motion and resizing

Animate's frequency gate favors immediate feedback for frequent native controls and navigation. There are no ornamental transitions. Chart and disclosure animation are disabled, so the same interface already supports reduced-motion use. Native controls handle keyboard navigation and selection semantics.

The desktop window defaults to 1180×820 with a 920×680 minimum outer window. Check-in and overview scroll vertically at compact sizes. Tables and charts grow with available space. This is a desktop application; a mobile website was not introduced.

## References applied

- Local UI/UX Pro Max: wellness direction search, JavaFX pane selection, focus, keyboard, state and contrast guidance. Its marketing-page/testimonial recommendation was rejected as inapplicable to a desktop journal.
- Local Animate: frequency/purpose gate; no animation was warranted for these repeated interactions.
- Vercel Web Interface Guidelines: labelled native controls, visible focus, inline feedback, empty/long content, and keyboard equivalents, adapted to JavaFX.
- 21st.dev architectural sidebar reference: composition only; no React dependency or component code.
- ImageGen composition reference from the portfolio toolkit: ivory/forest/sage, sidebar and reflective content hierarchy. Only existing functions and the existing three-mood scale were implemented.
- Awesome DESIGN.md: this project-specific record of palette, typography, components, states, and constraints.

Do not turn this into a marketing page, add unsupported Settings navigation, present check-in counts as medical conclusions, or copy the portfolio's pixel-art theme.
