# UAT checklist – Dashboard appearance (V25)

For the BRD 25.0.0 acceptance criteria (DAC-01 to DAC-10). This release changes how the Dashboard looks and reads; it
adds one API field (`nextUpTasks`) and no permission. Use a staging server and three accounts:

- **New**: a fresh account with no vision areas.
- **Busy**: at least 4 vision areas; dreams, goals and tasks in every status; one task overdue (any priority), one
  due today, one Blocked with a blocker reason; obstacles of two types; partners in several statuses; daily and weekly
  reviews over the last few weeks; progress logged over more than one week.
- **Coach**: a coach of an active cohort that includes Busy.

Use a desktop browser and a phone (or a 360px-wide window). Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| DAC-01 | Five testers who have not seen the page sign in as Busy. Ask each, within five seconds of the Dashboard loading: "What is your next task, and how are you doing overall?" | 5 of 5 name the first **Next up** task and read the progress ring's percentage | – (moderated session) | ☐ |
| DAC-02 | Busy, desktop: read the page top to bottom. Then a 360px phone | Desktop: **Today** (Next up beside Overall progress, then Due / Overdue / Blocked), then **Plan health** (Vision Area progress, Goals and tasks by status), then **Insights** (attention panel, Progress trend beside the Obstacles / Partners / Reviews tabs, then the energy, gratitude, progress and reminder cards). Phone: Overall progress first, then Next up, then the three figures; in Insights the tabs come before the trend; the trend and the status card open from **Show …** buttons; no sideways scrolling | `DashboardPage.test.tsx` (zone order), `motion.test.tsx` (`MobileCollapse`) | ☐ |
| DAC-03 | Busy: look at the Blocked task in Next up, an Open obstacle (Obstacles page), a Waiting task (Tasks board), and the status chips elsewhere | Blocked is orange with a crossed octagon; Open is berry (pink-purple) with an exclamation circle; Waiting is indigo with an hourglass; every chip has its word. No orange for Open | `StatusChip.test.tsx`, `design/tokens.test.ts` | ☐ |
| DAC-04 | Run the axe check on the Dashboard in light and dark, then with three different accents and with high contrast on (Appearance) | No colour-contrast violations | `e2e/pages.e2e.ts` (axe), `design/tokens.test.ts`, `theme.test.ts` | ☐ |
| DAC-05 | Turn on **Reduce motion** in Appearance (and, separately, the device's reduce-motion setting). Reload the Dashboard; complete a goal elsewhere and come back | Nothing fades, grows or counts up; numbers show their final values at once; no confetti, but the "A goal is complete…" message still appears | `dashboardV25.test.tsx` (chart animation off), `motion.test.tsx` (no confetti) | ☐ |
| DAC-06 | Switch the language to Khmer. View the Dashboard at 360px and 1280px | All new text is in Khmer; no text clips or overlaps; Khmer lines are taller than English; zone labels are not upper-cased | `DashboardPage.test.tsx` (Khmer), `e2e/khmer.e2e.ts` | ☐ |
| DAC-07 | Sign in as New. Add a vision area, a dream and a goal (follow the steps) | The getting-started steps show first, with no empty charts; after each step the next one is offered; once a goal exists the steps disappear and the cards show friendly empty messages, never empty axes | `DashboardPage.test.tsx` (first run, banner, empty messages) | ☐ |
| DAC-08 | Sign in as Coach and open Busy's participant page | Unchanged from V24: the coach sees no dashboard and no new data (D-25-10; no coach dashboard exists) | Coach suites unchanged | ☐ |
| DAC-09 | Product owner opens the design preview and compares it with the built page | Preview approved before phase 4 (record date); the built page matches it, apart from the differences listed in the BRD change log | – | ☐ |
| DAC-10 | CI | Frontend (`npx vitest run --testTimeout=20000`, `npm run build`) and backend (`mvn test`) pass | CI | ☐ |

Feature checks (DA-FR-01 to DA-FR-16):

| ID | Steps | Expected | Result |
| --- | --- | --- | --- |
| DAF-01 | Busy: read **Next up** | Up to five open tasks: overdue first (shown "N days late" in red), then due today ("Today"), then by priority and due date. The Blocked task shows its chip and reason. The footer shows "N Critical · N High open" and **View all tasks** | ☐ |
| DAF-02 | Tick the checkbox on a task that is not Blocked; then on the Blocked task | The first shows "Task completed: …" and the figures refresh; the Blocked one shows the server's message (for example that a change note is needed) and stays | ☐ |
| DAF-03 | Click **Due this month**, **Overdue Tasks** and **Blocked Tasks** | Each opens the Tasks board filtered to exactly the rows it counted; with zero overdue or blocked, the figure reads "All clear" | ☐ |
| DAF-04 | Change the period to **This year**, then choose a vision area | The due figure becomes "Due this year"; the URL keeps both choices; every link carries the area | ☐ |
| DAF-05 | Read **Vision Area progress** | Lowest area first, each with its dream count and colour dot, linked to that area's dreams | ☐ |
| DAF-06 | Read **Goals and tasks by status**; click **View as table** | Two bars, Completed on the right, Paused striped, counts listed under each bar. The table lists each status with linked goal and task counts | ☐ |
| DAF-07 | Open each Insights tab; use the arrow keys between tabs | Obstacles: the blocked-task link and obstacle types, largest first, each linked. Partners: a four-stage pipeline and a link per status. Reviews: the week streak, the 12-week blue heatmap with "Less … More", and the diligence line | ☐ |
| DAF-08 | On **Progress trend**, hover the line; click **View as table** | A dashed grid, a soft blue fill, the last point marked with its value; the tooltip shows the week and value. The table lists every week | ☐ |
| DAF-09 | Simulate a slow network (browser dev tools) and reload | Grey placeholders in the final layout; after 10 seconds "Still loading…" | ☐ |
| DAF-10 | Stop the backend and reload | "Unable to load…" or the server message, with **Try again**; after starting the backend, **Try again** loads the page | ☐ |
| DAF-11 | Complete a goal (Vision Map or Goals page), then open the Dashboard (whole plan, not one area) | A short confetti burst on the progress card and "A goal is complete. One step closer to your dream."; reloading does not play it again | ☐ |

Also check across the app (phase 2 colours):

- Status badges on the Goals, Tasks, Obstacles and Partners pages use the new colours, in light and dark.
- The default accent is the slightly deeper blue; other accents and background tones still apply.
- The High priority chip is a deeper rust and does not look like a Blocked chip.

Khmer wording: review [km-review-v25.csv](km-review-v25.csv).
