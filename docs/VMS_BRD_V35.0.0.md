# BRD – Plan checklist and Build my plan, joined up

Nov 28, 2026

## Document control

This is draft 35.0.0. It keeps every requirement in BRD 34.0.0 and the earlier BRDs. It makes the two planning
places clearly different and links them:

- **Build my plan** (BRD 11, BRD 31), opened from the Vision Map or a dream, *builds* the plan.
- The sidebar **Plan** page (FR-73, FR-74), renamed **Plan checklist**, *checks* the plan and keeps its record.

V35 adds no new data. It changes names, buttons and links, and adds one checklist row for inner blockers (BRD 34).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Plan checklist and Build my plan addendum |
| Version | 35.0.0 – Draft |
| Parent documents | FR-73, FR-74 (the Plan page and its stages), FR-75 (complete plans count), V8.1 BR-63 (strengths never hold back planReady), BRD 11 (PB-FR-30 draft kept 14 days, PB-FR-31 add plan), BRD 31 (stage order; DC-FR-18 edit the dream on Clarify; D-31-7 inner blockers before mapping), BRD 34 (D-34-11 inner blockers private, D-34-12 never sent to AI), BRD 33 (controller standard) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 35.0.0 | First draft, from the product owner's question of 28 Nov 2026: is the sidebar Plan page different from the Build my plan button, when should each be used, and does having both complicate things? | BA |
| 35.0.0 (all phases built) | All three phases implemented, 28 Nov 2026 (no migration). PC-FR-01 to PC-FR-09. Clarified during build: (1) The plan status endpoint is `GET /api/plan-status/{dreamId}` (a path, not `?dreamId=` as §4 says); it gains `INNER` after `STRENGTHS`. (2) The INNER row's older page, for the "or open the … page" link, is the Assessment page, where My inner blockers lives (BRD 34). (3) The "Draft saved {date}" line is an info note under the page header, not under the button, so the header buttons wrap cleanly at 360px. (4) The draft is read as a hint only: if it can't be loaded, the checklist still works and shows **Build my plan**. (5) The server's "{n} inner blocker(s) named." is translated with the same number pattern as the other checklist rows. UAT, the Khmer review (`km-review-v35.csv`, 12 strings) and Q-35-1 to Q-35-4 remain | Dev |

---

## Executive summary

VMS has two places to plan one dream:

| | Build my plan | Plan (sidebar) |
| --- | --- | --- |
| Opened from | The Vision Map and the dream page | The sidebar, Plan section |
| Job | A guided builder: Clarify, Inner blockers, Goals, Steps, Tasks, Dates, Schedule, Check and add | A checklist of what one dream's plan has and lacks, milestones, Download PDF |
| Where the work happens | Inside the builder | On other pages: **Do this next** opens Dreams, Steps, Obstacles or Partners |

They do different jobs, but in use they confuse people:

1. **The names are nearly the same.** "Plan" and "Build my plan" read as one thing.
2. **They don't point to each other.** The Plan page never offers Build my plan, and the builder never points back
   to the checklist. **Do this next** sends people to the separate pages, which is the slower route for goals, steps
   and dates.
3. **The lists don't match.** The checklist has "Strengths and gaps" but not inner blockers. The builder has inner
   blockers (BRD 34) but the checklist never shows whether they were named.
4. **An unfinished draft is hidden.** A Build my plan draft is kept for 14 days (PB-FR-30), but the Plan page doesn't
   show it, so people start again or forget it.

V35 fixes these. One sentence explains the split on both pages: **Build my plan builds it. The plan checklist checks
it.**

### Expected benefits

| # | Change | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | Rename to Plan checklist | Clear which page does what | Fewer support questions about two "plan" pages |
| 2 | Build my plan button and draft resume on the checklist | One click from "what's missing" to fixing it; drafts are finished, not lost | More plans saved through the guided builder, with its plan check |
| 3 | Do this next opens the builder at the right stage | Goals, steps and dates are added faster, in one place | The plan rules (R-01 to R-08) cover more new plans |
| 4 | Inner blockers row | Both places agree on what a full plan includes | BRD 34 is used, not just available |

These benefits are estimates. "Measuring the benefits" says how they are measured after release.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-35-1 | **Rename.** The sidebar item and the page title become **Plan checklist**. The subtitle becomes "See what one dream's plan has and what it still needs." The route stays `/plan`, so bookmarks keep working. Every other place that names the page uses the new name: the Strengths page's "Back to the plan" becomes **Back to the plan checklist**, and the work style page link (`workStyleContent`) says **Plan checklist** |
| D-35-2 | **One line on both pages.** The Plan checklist and the first stage of Build my plan both show the same short help line: "Build my plan builds it. The plan checklist checks it." |
| D-35-3 | **Build my plan on the checklist.** The Plan checklist header shows **Build my plan** for the selected dream, which opens `/dreams/{id}/build`. When that dream has an unfinished draft, the button reads **Continue Build my plan** and the line under it says "Draft saved {date}. It is kept for 14 days after its last change." It uses the existing draft endpoint and no AI |
| D-35-4 | **Do this next opens the builder where it can.** For a stage the builder covers, **Do this next** opens Build my plan at the matching stage: Dream statement → Clarify; Inner blockers → Inner blockers; Goals → Goals; Steps → Steps; Owners and dates → Dates; Resources needed and People → Steps (gaps, helpers and risks, which become obstacles and partners when saved). Strengths and gaps keeps going to the Strengths page, which the builder doesn't cover. Each row also keeps a small link to the old page ("or open the Steps page"), so nothing is taken away |
| D-35-5 | **Opening at a stage.** Build my plan accepts `?stage=` (`CLARIFY`, `INNER`, `GOALS`, `STEPS`, `DATES`). It opens the existing draft, or a new one, at that stage. The draft's content is not changed; only the shown stage is. An unknown value opens the stage the draft was on. The stage lock rules of the builder are unchanged: every stage can still be visited in any order |
| D-35-6 | **Inner blockers row.** The checklist gains **Inner blockers** after Strengths and gaps. It is done when at least one active, not archived inner blocker is linked to this dream or to all dreams (BRD 34). Detail: "{n} inner blocker(s) named." or "Name what might hold you back inside, with one short practice." Like strengths, resources and people, it **never** changes `planReady` or the complete plans count (FR-75, BR-63) |
| D-35-7 | **Privacy stays.** The row shows only done or not done and a count, never the blockers' text. The plan PDF and any coach view of plan status do not show inner blockers (D-34-11). Nothing is sent to AI (D-34-12) |
| D-35-8 | **Back from the builder.** After **Save my plan**, the success message adds **See your plan checklist**, which opens `/plan?dreamId={id}`. The dream page and the Vision Map keep **Build my plan** as the main button and add a small **Plan checklist** link beside it |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| Tell the two pages apart | "Plan" and "Build my plan" | Names too close; no explanation |
| Go from "what's missing" to fixing it | Do this next opens separate pages | The faster guided route is never offered |
| Pick up an unfinished draft | Only by opening Build my plan from the dream | The checklist doesn't show the draft |
| See whether inner blockers were named | Only inside the builder | Not on the checklist |
| Go from the builder to the checklist | No link | Users don't see what is still missing after saving |

---

## 2. The changes

### 2.1 Plan checklist page

- Title **Plan checklist**, the new subtitle and the help line (D-35-1, D-35-2).
- Header actions: **Build my plan** or **Continue Build my plan** (D-35-3), then **Download PDF** as today.
- Stage rows in this order: Dream statement, Strengths and gaps, **Inner blockers**, Goals, Steps, Owners and dates,
  Resources needed, People (D-35-6).
- **Do this next** follows D-35-4, with the small "or open the … page" link under it where the builder is used.
- Milestones are unchanged.

### 2.2 Build my plan

- Accepts `?stage=` (D-35-5).
- The help line on the Clarify stage (D-35-2).
- **See your plan checklist** after saving (D-35-8).

### 2.3 Sidebar, dream page and Vision Map

- Sidebar: **Plan checklist**, same icon and place.
- Dream page and Vision Map: **Plan checklist** link beside **Build my plan** (D-35-8).

---

## 3. Data model

No migration. The checklist row reads `inner_blockers` (BRD 34) through a new count query in the inner blocker
repository: active, not archived, for this dream or for all dreams.

---

## 4. API

| Change | Detail |
| --- | --- |
| `GET /api/v1/plan-status?dreamId=` | `stages` gains `{ key: "INNER", label: "Inner blockers", done, detail }` after `STRENGTHS`. `planReady` is unchanged (D-35-6) |
| Build my plan draft | No change; the checklist reads the existing `GET /api/v1/ai/plan/drafts/{dreamId}` (204 when there is none) |

---

## 5. Business rules

| ID | Rule |
| --- | --- |
| BR-35-1 | The INNER row counts only the user's own active, not archived blockers for this dream or for all dreams; another user's data is never counted (CLAUDE.md rule 13) |
| BR-35-2 | The INNER row never changes `planReady`, the complete plans count or anything that reads them (FR-75, BR-63) |
| BR-35-3 | The INNER row never shows blocker text, and inner blockers never appear in the plan PDF (D-35-7) |
| BR-35-4 | `?stage=` only changes which stage is shown; it never changes or saves draft content (D-35-5) |
| BR-35-5 | Every old route still works: `/plan`, the separate pages, and Build my plan without `?stage=` |

---

## 6. Pages and components

- **`nav-items.ts`**: the sidebar label.
- **`PlanPage`**: title, subtitle, help line, Build my plan / Continue button, the INNER row, Do this next routing and
  the "or open the … page" link.
- **`PlanBuilderPage`**: `?stage=`, the help line on Clarify, **See your plan checklist** after saving.
- **`DreamDetailPage`** (dream page and Vision Map): the **Plan checklist** link.
- **`StrengthsPage`**, **`workStyleContent`**: the new name.
- **`PlanStatusService`**: the INNER row. **`InnerBlockerRepository`**: the count query.
- All new and changed text has Khmer drafts (`km-review-v35.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| PC-FR-01 | Rename the sidebar item and page to Plan checklist, with the new subtitle; route unchanged; other mentions renamed (D-35-1) | Must |
| PC-FR-02 | The help line on the Plan checklist and on Build my plan's Clarify stage (D-35-2) | Should |
| PC-FR-03 | Build my plan button on the Plan checklist for the selected dream (D-35-3) | Must |
| PC-FR-04 | Continue Build my plan with the draft's saved date when a draft exists (D-35-3) | Must |
| PC-FR-05 | Do this next opens Build my plan at the matching stage, with a link to the old page (D-35-4) | Must |
| PC-FR-06 | Build my plan opens at `?stage=` without changing the draft (D-35-5) | Must |
| PC-FR-07 | Inner blockers row on the checklist, not part of planReady (D-35-6) | Must |
| PC-FR-08 | No blocker text on the checklist, in the PDF or in any coach view (D-35-7) | Must |
| PC-FR-09 | See your plan checklist after saving a plan; Plan checklist link on the dream page and Vision Map (D-35-8) | Should |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| PC-NFR-01 | No migration and no change to any saved data |
| PC-NFR-02 | WCAG 2.2 AA: the new buttons and links have clear names ("Build my plan for {dream}"); the stage rows stay a list |
| PC-NFR-03 | Works at 360px; the header buttons wrap |
| PC-NFR-04 | Khmer drafts for all new and changed text in `km-review-v35.csv` |
| PC-NFR-05 | Backend tests: INNER row done and not done (dream, all dreams, archived, resolved, another user's), planReady and the complete plans count unchanged. Frontend tests: the new name, Build my plan and Continue, each Do this next target, `?stage=` with and without a draft, See your plan checklist |
| PC-NFR-06 | `mvn verify` (line ≥ 94%, branch ≥ 75%), `ControllerRulesTest`, frontend tests with `--testTimeout=20000`, and the SonarQube gate pass |

## Implementation phases

Each phase builds the backend and the frontend together, in full.

| Phase | Content |
| --- | --- |
| 1 | Rename and help line (PC-FR-01, 02); Build my plan / Continue on the checklist (PC-FR-03, 04); INNER row (PC-FR-07, 08); tests |
| 2 | `?stage=` in the builder (PC-FR-06); Do this next routing (PC-FR-05); See your plan checklist and the Plan checklist links (PC-FR-09); tests |
| 3 | `km-review-v35.csv`, CHANGELOG, `UAT_V35.md`, `docs/API.md`, user guide section "Plan checklist and Build my plan (V35)" |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| PCC-01 | The sidebar shows **Plan checklist**; `/plan` opens it; the Strengths page says **Back to the plan checklist** |
| PCC-02 | On the Plan checklist with a dream selected, **Build my plan** opens that dream's builder |
| PCC-03 | With an unfinished draft, the button reads **Continue Build my plan** and shows the draft's date; it opens the draft with its content |
| PCC-04 | For a dream with no goals, **Do this next** on Goals opens the builder on the Goals stage; the "or open the … page" link opens the old page |
| PCC-05 | Strengths and gaps' **Do this next** still opens the Strengths page |
| PCC-06 | Opening `/dreams/{id}/build?stage=DATES` shows the Dates stage; the draft content is the same as before |
| PCC-07 | With no inner blockers, the row is not done; after adding one for this dream (or for all dreams) it is done with the count; an archived or resolved blocker doesn't count |
| PCC-08 | The row's state never changes whether the plan counts as ready or complete |
| PCC-09 | The checklist, the PDF and the coach view show no blocker text |
| PCC-10 | After **Save my plan**, **See your plan checklist** opens `/plan?dreamId={id}`; the dream page shows a **Plan checklist** link |
| PCC-11 | Builds, tests, the coverage check, `ControllerRulesTest` and the SonarQube gate pass |

## Measuring the benefits

Measured 30 days before and 30 days after release.

| ID | Measure | Target |
| --- | --- | --- |
| PCM-01 | Plans saved through Build my plan, as a share of dreams that gained goals | Up by at least 10 points |
| PCM-02 | Build my plan drafts that expire unfinished | Down by at least 20% |
| PCM-03 | Do this next clicks that open the builder and end in a saved plan | Counted; no target in V35 |
| PCM-04 | Dreams whose checklist shows Inner blockers done | Up |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-35-1 | Should the sidebar item move under the Vision Map, or be removed and folded into the dream page? | Not in V35. Renaming and linking first; review after PCM-01 is measured |
| Q-35-2 | Should Resources needed and People open the builder (Steps stage) or keep their own pages? | Open the builder, with the old page linked under it (D-35-4), because saving helpers and risks there fills both rows |
| Q-35-3 | Should a Strengths stage be added to Build my plan so the lists match fully? | Not in V35. Strengths and gaps covers what the person brings, which is broader than one plan; keep it on its own page |
| Q-35-4 | Should the Inner blockers row ever count toward planReady? | No. Like strengths, resources and people, it is advice, never a gate (BR-63, D-31-7) |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
