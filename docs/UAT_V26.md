# UAT checklist – Interface styles (V26)

For the BRD 26.0.0 acceptance criteria (ISC-01 to ISC-09). This release changes how the three interface styles look
and adds one response field (`interfaceStyleSource`) and migration V93. No permission changes. Q-26-1 was answered
"keep CLASSIC": no stored style is changed by the migration.

Use a staging server, a desktop browser (Chrome and Safari), and a real mid-range phone. Accounts:

- **Busy**: vision areas, dreams, goals and tasks in several statuses, one obstacle, one partner, a few reviews.
- **Old Classic**: an account that held CLASSIC before V93 (any account created before this release that never
  changed its style).
- **New**: register a fresh account during the test.

Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| ISC-01 | Busy: Appearance → Interface style. Switch between the three styles and visit Dashboard, Tasks, Vision Map, Partners, Reviews and Appearance, and open a dialog and a menu | Every surface changes together: corners, shadows and spacing. In Liquid Glass only the sidebar, the header once you scroll, menus, tooltips, toasts, dialogs and the Overall progress card are frosted; cards, tables, kanban, charts and forms stay solid | `e2e/interface-styles.e2e.ts` (snapshots), `design/no-hardcoded-style.test.ts`, `styles/interface-style.test.ts` | ☐ |
| ISC-02 | Run axe on each main page in each style, light and dark, with three accents. Then Liquid Glass with a very dark and a very bright background image (Appearance → Background image) | No contrast violations | `e2e/interface-styles.e2e.ts` (axe matrix, background images), `design/style-tokens.test.ts` (glass floors) | ☐ |
| ISC-03 | Liquid Glass, then each of: turn on High contrast; on macOS turn on Reduce transparency; on Windows turn on Contrast themes; open the app in a browser without backdrop-filter if available | Panels turn solid each time; the Liquid Glass card in Appearance says why | `e2e/interface-styles.e2e.ts` (fallbacks), `hooks/useInterfaceStyle.test.ts`, `pages/AppearanceSettingsPage.test.tsx` | ☐ |
| ISC-04 | Developer: add `borderRadius: 2` or `boxShadow: '0 2px 4px rgba(0,0,0,.2)'` to any component and run the unit tests | `no-hardcoded-style.test.ts` fails and names the line | `design/no-hardcoded-style.test.ts` | ☐ |
| ISC-05 | Liquid Glass: Dashboard, Tasks and Vision Map, scrolled; then with a dialog open | At most 4 frosted layers on screen; nothing frosted inside a scrolling list or chart | `e2e/interface-styles.e2e.ts` (blur-layer count) | ☐ |
| ISC-06 | Register **New**: check the style. Sign in as **Old Classic** on two different browsers. As Busy, pick Classic, then sign in on another browser | New starts on Modern. Old Classic shows Classic on both browsers. Busy's Classic choice holds on every browser | `AppearancePreferenceServiceTest`, `ThemeModeContext.test.tsx` | ☐ |
| ISC-07 | Classic, then Modern, then Liquid Glass: look at the header and sidebar | Classic: breadcrumbs, rectangular navigation with a 3px accent bar. Modern and Liquid Glass: greeting header, pill navigation, sidebar search | `e2e/interface-styles.e2e.ts` (header anatomy) | ☐ |
| ISC-08 | Product owner and design reviewer review the snapshot images in `frontend/e2e/interface-styles.e2e.ts-snapshots/` (Dashboard, Tasks, Vision Map, a dialog × 3 styles × light/dark × English/Khmer, and Khmer at 360px) and the preview `docs/design/v26-interface-styles-preview.html` | Approved (record date and names); no Khmer text clips in any style | `e2e/interface-styles.e2e.ts` | ☐ |
| ISC-09 | CI | Frontend (`npx vitest run --testTimeout=20000`, `npm run build`) and backend (`mvn test`) pass | CI | ☐ |
| NFR-04 | Real mid-range phone, Liquid Glass, Dashboard: scroll up and down quickly for 10 seconds | Scrolling stays smooth with no visible stutter. (The automated check covers main-thread work only; the blur runs on the GPU and needs a real device) | `e2e/interface-styles.e2e.ts` (main-thread work per frame) | ☐ |
| KM | Khmer reviewer checks `docs/km-review-v26.csv` | Drafts approved or corrected | – | ☐ |

## Running the automated browser checks

The backend must run on port 8080 with the local database. Then, in `frontend/`:

```bash
npx playwright test interface-styles                       # check against the approved snapshots
npx playwright test interface-styles --update-snapshots    # after a deliberate visual change; review the images before committing
```

Snapshots are named for the operating system they were taken on (`-darwin.png`); a run on another OS needs its own
baseline. The suite reuses one seed account for 30 minutes (cached in `node_modules/.cache/vms-e2e/`), because the
backend allows 10 sign-ups per hour per network.
