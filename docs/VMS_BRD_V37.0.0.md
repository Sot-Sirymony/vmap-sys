# BRD – A shorter sidebar

Nov 28, 2026

## Document control

This is draft 37.0.0. It keeps every requirement in BRD 36.0.0 and the earlier BRDs. It regroups the sidebar so a
participant sees fewer, clearer choices. **Only navigation changes**: every page, route, permission and piece of data
stays as it is.

The product owner reviewed a clickable mock of this change before this BRD was written (28 Nov 2026).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Sidebar regrouping addendum |
| Version | 37.0.0 – Draft |
| Parent documents | FR-23.2 (three labelled sidebar sections Plan / Execute / Support), BRD 23 (D-23-1 permissions decide which admin pages show), BRD 26 (interface styles; the collapsed icon sidebar), BRD 35 (Plan checklist), BRD 34 and 36 (My inner blockers on the Assessment page), CLAUDE.md frontend rules (responsive layout, simple English) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 37.0.0 | First draft, from the product owner's request of 28 Nov 2026 to find complicated parts of the system, and their review of the sidebar mock | BA |
| 37.0.0 (all phases built) | All three phases implemented, 28 Nov 2026 (no migration, no backend change). SB-FR-01 to SB-FR-08. Clarified during build: (1) **BR-37-3:** the current page's group heading can't fold (it is marked `aria-disabled`); only the other groups' open state is stored, under `vms.sidebar.openGroups` in that browser. (2) A group with nothing the person may see is left out (so a coach with only `VIEW_USERS` sees Admin with Users only). (3) The Map tab is the Vision Map link itself, so the full destination list (header titles, command palette) has each page once. (4) The dream page (`/dreams/{id}`) and a saved report (`/vision-map/reports/{id}`) show the tab bar with Map and Saved reports marked. (5) The pages' own shortcut buttons (for example "Back to the Vision Map" on the Timeline, "Management report" on the map, which keeps the selected dream) are kept. (6) The tab bar is hidden when printing. (7) "Map" and "Admin" already had Khmer text; 5 new drafts | Dev |

---

## Executive summary

The sidebar grew one BRD at a time. A participant now sees **29 links in 3 groups** (Plan 11, Execute 7, Support 11),
and an admin up to 8 more. Related pages sit in different groups (Assessment under Plan, Work Style under Support),
and settings, reports and admin pages sit next to daily work.

V37 regroups the same pages by what the person is doing:

| Group | Pages |
| --- | --- |
| **Home** | Today, Dashboard |
| **Plan** | Vision Areas, Dreams, Vision Map, Plan checklist |
| **Do** | Goals, Steps, Tasks, Obstacles, Partners, Communication |
| **Review** | Reviews, Streamline, Insights |
| **Know yourself** | Assessment, Strengths and Gaps, Work Style, Modules, Journal |
| **Admin** (only with permission) | Coaching, Users, Roles, Security access, Cohorts, AI settings, Security log, Two-step sign-in |

And moves three kinds of pages out of the list:

- **Vision Map tabs:** Timeline, Management Report and Saved reports become tabs on the Vision Map.
- **Account menu:** Account, Security, Appearance, Import / Export and Issue Reports move to the account menu.
- **Ask the coach:** Dream Coach becomes a button at the top of the sidebar.

A participant goes from **29 links to 20**, in **5 groups**, and only the group they are in is unfolded.

### Expected benefits

| # | Change | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | Five groups by activity | Easier to find a page; related pages together | New pages have an obvious home |
| 2 | Folding groups | A short list at a glance | Room for future pages without a longer list |
| 3 | Reports as Vision Map tabs | Map, timeline and reports read as one place | Four sidebar links become one |
| 4 | Account menu | Settings out of the way of daily work | The sidebar holds work, not configuration |
| 5 | Admin group | Admins see admin pages together; participants never see them | Clear split between using and running the program |

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-37-1 | **Five groups and Admin.** The participant groups are Home, Plan, Do, Review and Know yourself, with the pages in the table above, in that order. The Admin group holds the eight admin pages and shows only the pages the person's permissions allow (D-23-1). A group with no page the person may see is not shown |
| D-37-2 | **Folding groups.** Each group heading can be folded or unfolded. The group of the page being viewed always unfolds when that page opens. Which other groups are open is remembered on that device only (browser storage, with a safe default when storage is unavailable). Default: only the current page's group is open. In the collapsed icon sidebar (BRD 26), all icons show with a divider between groups, as today, and there is no folding |
| D-37-3 | **Vision Map tabs.** The four Vision Map pages (`/vision-map`, `/vision-map/timeline`, `/vision-map/report`, `/vision-map/reports` and a saved report) share one tab bar: **Map**, **Timeline**, **Management Report**, **Saved reports**. Each tab is a link to its existing route. The sidebar's **Vision Map** link is marked current on all of them. The dream page (`/dreams/{id}`) keeps marking Vision Map current, as today |
| D-37-4 | **Account menu.** The account button at the foot of the sidebar (today it only offers Sign out) opens a menu with **Account**, **Security**, **Appearance**, **Import / Export** and **Issue Reports**, a divider, then **Sign out**. The mock showed it top right. In the app the existing account button is used, so there is one account menu, not two |
| D-37-5 | **Ask the coach.** A button at the top of the sidebar opens the Dream Coach page (`/dream-coach`). The floating Dream Coach launcher is not changed in V37: bringing all AI help into one entry is a separate, later change (Q-37-4) |
| D-37-6 | **Nothing else changes.** No route, page, permission, API or data changes. Old links keep working. The command palette and the page title in the header still find every page, including the ones that moved to tabs, the account menu or the coach button. The mobile bottom bar (Dashboard, Map, Tasks, Today) is unchanged |
| D-37-7 | **Names.** Group names in English: Home, Plan, Do, Review, Know yourself, Admin. Khmer drafts: ទំព័រដើម, ផែនការ, អនុវត្ត, ពិនិត្យឡើងវិញ, ស្គាល់ខ្លួនឯង, អ្នកគ្រប់គ្រង (to be checked by the Khmer reviewer). "Ask the coach": សួរគ្រូបង្វឹក. Page names do not change |
| D-37-8 | **Landing page.** After sign-in the app still opens the Dashboard (`/`). Today is listed first in Home because it is the daily page; whether Today should become the landing page is Q-37-3 |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| Find a page quickly | 29 links, 3 groups | Long list; related pages apart |
| Keep daily work in view | Settings and admin pages mixed in | Noise for participants |
| See map, timeline and reports together | Four separate links | No shared navigation |
| Reach settings | Five sidebar links | No account menu beyond Sign out |
| Grow the app | Every new page lengthens the list | No folding |

---

## 2. Where each page goes

| Page | Today | V37 |
| --- | --- | --- |
| Dashboard | Plan | Home |
| Vision Areas, Dreams, Vision Map, Plan checklist | Plan | Plan |
| Timeline, Management Report, Saved reports | Plan | Vision Map tabs |
| Modules, Assessment, Strengths and Gaps | Plan | Know yourself |
| Today | Execute | Home |
| Goals, Steps, Tasks, Obstacles | Execute | Do |
| Streamline | Execute | Review |
| Journal | Execute | Know yourself |
| Dream Coach | Support | Ask the coach button |
| Partners, Communication | Support | Do |
| Work Style | Support | Know yourself |
| Reviews, Insights | Support | Review |
| Issue Reports, Import / Export, Appearance, Security, Account | Support | Account menu |
| Coaching, Users, Roles, Security access, Cohorts, AI settings, Security log, Two-step sign-in | Support (with permission) | Admin (with permission) |

---

## 3. Data model and API

No migration and no API change.

## 4. Business rules

| ID | Rule |
| --- | --- |
| BR-37-1 | Every page reachable before V37 is reachable after it: from the sidebar, a Vision Map tab, the account menu or the coach button, and from the command palette (D-37-6) |
| BR-37-2 | Admin pages show only with their permission, exactly as before (D-23-1); a participant never sees the Admin group |
| BR-37-3 | The group of the current page is always unfolded (D-37-2) |
| BR-37-4 | Remembered fold state lives only in that browser; losing it never hides the current page (D-37-2) |

## 5. Pages and components

- **`nav-items.ts`**: the new groups; a separate list of every destination (sidebar, tabs, account menu, coach) for
  the header title and the command palette.
- **`Sidebar.tsx`**: folding group headings, the **Ask the coach** button, the account menu items.
- **`VisionMapTabs`** (new, `components/vision-map`): the shared tab bar on the four Vision Map pages.
- **`Header.tsx`**, **`CommandPalette.tsx`**: read the full destination list.
- Khmer drafts for the new names (`km-review-v37.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| SB-FR-01 | Five participant groups and an Admin group with the pages and order of D-37-1 | Must |
| SB-FR-02 | Folding groups; the current page's group always open; other groups remembered per device (D-37-2) | Must |
| SB-FR-03 | Collapsed icon sidebar shows all icons with group dividers, no folding (D-37-2) | Must |
| SB-FR-04 | Vision Map tab bar on the four Vision Map pages; Vision Map marked current on all of them (D-37-3) | Must |
| SB-FR-05 | Account menu with Account, Security, Appearance, Import / Export, Issue Reports and Sign out (D-37-4) | Must |
| SB-FR-06 | Ask the coach button at the top of the sidebar (D-37-5) | Must |
| SB-FR-07 | Command palette and header titles still cover every page (D-37-6, BR-37-1) | Must |
| SB-FR-08 | Admin group only with permission (BR-37-2) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SB-NFR-01 | No route, API, permission or data change |
| SB-NFR-02 | WCAG 2.2 AA: group headings are buttons with `aria-expanded`; the tab bar is a list of links with the current one marked `aria-current="page"`; the account menu is keyboard operable |
| SB-NFR-03 | Works at 360px: the mobile drawer shows the same groups; the tab bar wraps or scrolls |
| SB-NFR-04 | Khmer drafts for all new text in `km-review-v37.csv` |
| SB-NFR-05 | Frontend tests: groups and order for a participant and for an admin; folding and the current group; Vision Map tabs on all four routes; account menu items; Ask the coach; command palette still lists moved pages; header titles for moved pages |
| SB-NFR-06 | Frontend build and tests (`--testTimeout=20000`) pass; backend unchanged |

## Implementation phases

Each phase is built in full.

| Phase | Content |
| --- | --- |
| 1 | New groups, folding, Admin group, account menu, Ask the coach; command palette and header titles (SB-FR-01 to 03, 05 to 08); tests |
| 2 | Vision Map tab bar (SB-FR-04); tests |
| 3 | `km-review-v37.csv`, CHANGELOG, `UAT_V37.md`, user guide section "Finding your way (V37)" |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SBC-01 | A participant sees Home, Plan, Do, Review and Know yourself with 20 links, and no Admin group |
| SBC-02 | Opening Goals unfolds Do; folding Plan and reloading keeps Plan folded on that browser; the current group can't be hidden by a stored setting |
| SBC-03 | An admin with only VIEW_USERS sees an Admin group with Users only |
| SBC-04 | Timeline, Management Report and Saved reports are tabs on the Vision Map; each tab opens its page; Vision Map stays marked in the sidebar |
| SBC-05 | The account menu opens Account, Security, Appearance, Import / Export and Issue Reports, and Sign out still works |
| SBC-06 | Ask the coach opens the Dream Coach |
| SBC-07 | The command palette finds Appearance, Timeline and Dream Coach; their pages show the right title in the header |
| SBC-08 | The collapsed icon sidebar and the mobile drawer show the new groups |
| SBC-09 | Builds and tests pass |

## Measuring the benefits

| ID | Measure | Target |
| --- | --- | --- |
| SBM-01 | "I can't find …" issue reports and coach questions about where a page is | Down |
| SBM-02 | Use of the command palette to reach pages | Watched; a big rise means a page is hard to find in the groups |
| SBM-03 | Visits to the Vision Map tabs compared with the old separate links | Same or up |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-37-1 | Are the group names right (Home, Plan, Do, Review, Know yourself)? | Yes, as in the reviewed mock; the Khmer reviewer checks the drafts |
| Q-37-2 | Should Partners and Communication sit under Do, or in their own People group? | Under Do for V37, to keep five groups; revisit if people look for them elsewhere |
| Q-37-3 | Should Today become the page that opens after sign-in? | Not in V37; decide after SBM-01, since the Dashboard is what people know now |
| Q-37-4 | Should the floating Dream Coach launcher go, leaving only Ask the coach? | In the later "one AI entry" change, together with the page help and plan assistant buttons |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
