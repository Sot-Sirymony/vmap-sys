# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V6.0.0 |
| **Version** | 6.0.0 (Complete) |
| **Date** | 2026-09-14 |
| **Status** | ✅ Complete. Written from a Session-7-focused gap analysis (dream-listing method) against V1.0.0 → V5.0.0. ✅ **FR-56** (Dream Visual Anchor), ✅ **FR-57** (Intra-Area Letter Priority), and ✅ **FR-58** (Starter Vision Area Suggestions) all shipped 2026-09-14. Every requirement in this document is now built. |
| **Baseline** | Builds on VMS_BRD_V5.0.0 (all FR-1…FR-55 remain in force) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott), used as conceptual reference only, as in V1–V5. **No copyrighted text, named proprietary frameworks, or scripture is reproduced anywhere in this document or in the product** — see *Originality note* below. |

New requirements start at **FR-56**. Business rules continue from **BR-44**
(→ new rules start at **BR-45**). Migrations continue from **V28** (→ new
migrations start at **V29**).

---

## Originality note

The source gap analysis quoted the book's exact instruction line for the
visual-anchor gap and listed the book's own seven-arena life list (naming
"Relationship with God," "Marriage," etc. in that order). Per this
project's standing rule (`CLAUDE.md`), this document:

- Describes the visual-anchor feature by its underlying mechanism (a
  picture that represents the dream's fulfilled state) without quoting the
  book's instruction text.
- Reuses this product's **own, already-established** Vision Area vocabulary
  (`CLAUDE.md`'s own example list: Career, Health, Family, Finance,
  Education, Business, Spiritual, Relationship, Research, Leadership) as
  the starter-template set for FR-58, instead of the book's specific
  seven-item ordered list. This list was written for this project already,
  independent of the book, so reusing it introduces no new originality risk.

---

## Origin note

| Gap area | Current VMS state | Finding |
|---|---|---|
| Visual anchor for a dream | Missing entirely — `Dream` has no image/symbol field, and every version's Non-Functional Notes confirms no file-storage infrastructure exists (file uploads/attachments have been explicitly out of scope since V1.0.0). | **Genuinely new**, scoped to fit the existing no-file-storage constraint. Scoped as **FR-56**. |
| Intra-area letter priority | Partial — `Dream.priority` is a four-value categorical enum (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) shared across every dream in every Vision Area; nothing distinguishes rank *within* an area, so five dreams under one area can all be `HIGH` with no way to tell which one is the actual current focus. | **Genuinely new**, additive alongside the existing `priority` field (not a replacement). Scoped as **FR-57**. |
| Starter Vision Area suggestions | `VisionAreaWizard` (FR-33) is entirely free-text — nothing suggests a starting set of areas, so a new user can build an entirely one-sided plan (e.g. four career-related areas, nothing for health or relationships) without the system ever offering an alternative. | **Genuinely new**, UI-only. Scoped as **FR-58**. |

---

## Business Objective

Close three small gaps in how a dream first gets captured and organized:
give a dream an optional visual reminder of what achieving it looks like,
let the user mark which dreams in a life area actually come first when
several share the same categorical priority, and offer a new user a
starting set of life-area suggestions so their first plan isn't
accidentally one-sided.

---

## FR-56 Dream Visual Anchor — ✅ Done 2026-09-14 *(Effort: S)*

**Shipped (2026-09-14):** `Dream.imageUrl`, validated by a `@Pattern` DTO
constraint (`^https?://.+`) rather than Hibernate Validator's `@URL` — the
latter's default allowed-scheme list includes `ftp`, which is looser than
BR-45's "http or https only" wording, so a plain regex kept the rule exact
without depending on `@URL`'s scheme-list behavior. Rendered as a small
circular thumbnail in the Dreams table's title cell and Kanban cards, and as
a 36px square thumbnail on the Dream's own Vision Map tree node — both wrap
the `<img>` in an `onError` handler that hides the element on a broken link,
so a dead URL degrades to today's no-image look instead of a broken-image
icon. Live-smoke-tested: a `javascript:` URI and a plain non-URL string are
both rejected with BR-45's message; a well-formed link round-trips.

A dream can optionally carry a link to an image that represents what
achieving it looks or feels like — shown wherever the dream itself is
shown, without requiring any file-storage infrastructure.

- FR-56.1 `Dream` gains an optional `imageUrl` field: a link to an image
  hosted elsewhere (the user's own photo host, a stock-photo page, etc.).
  Never required, never validated for reachability — only for looking like
  a URL.
- FR-56.2 When set, the image renders as a small header visual on the
  Dream's own page/tree node (Vision Map) and in the Dreams list/board
  view; when unset, the surface looks exactly as it does today (no broken-
  image placeholder, no layout shift).
- FR-56.3 Clearing the field (saving it blank) removes the visual on the
  next render — there is no separate "remove image" action.

**Acceptance criteria**

1. Creating or editing a Dream with a well-formed `http(s)://` URL in the
   image field succeeds and the value round-trips on the next load.
2. Saving a non-URL string (e.g. plain text, a `javascript:` URI) is
   rejected with a clear validation message; the record is not partially
   saved.
3. Leaving the field blank is always valid — a Dream never requires an
   image.
4. A Dream with no image renders identically to how every existing Dream
   renders today; nothing new appears when the field is empty.

**Business rules**

| # | Rule |
|---|---|
| BR-45 | `Dream.imageUrl`, when present, must be a well-formed `http://` or `https://` URL; blank/null is always valid and never required. |

**Data model / migration**

- `V29__dream_visual_anchor.sql`: additive on `dreams` — one nullable
  `VARCHAR(2048)` column, `image_url`.

**Design decisions**

- **Link, not upload.** File uploads/attachments have been out of scope
  since V1.0.0 specifically because they require file-storage
  infrastructure this system deliberately doesn't have. A URL field
  delivers the same "picture of the fulfilled dream" idea with zero new
  infrastructure — the browser fetches the image directly from wherever
  the user's link points.
- **No reachability check.** Validating that a URL actually resolves to a
  live image would require the backend to make outbound HTTP calls on
  every save — a new capability and a new failure mode (timeouts, SSRF
  exposure) for a cosmetic feature. Format validation only; a dead link
  just fails to render, the same way a mistyped link fails anywhere else
  on the web.

---

## FR-57 Intra-Area Letter Priority — ✅ Done 2026-09-14 *(Effort: S)*

**Shipped (2026-09-14):** `Dream.letterRank`, validated by
`@Pattern(regexp = "^[A-Z]$")` (null always passes; ties allowed since
there is no uniqueness constraint at all, matching BR-46). The frontend
input in both `DreamsPage.tsx` and `VisionMapTree.tsx` normalizes on every
keystroke (`.slice(-1).toUpperCase().replace(/[^A-Z]/, '')`) so a lowercase
or multi-character value never reaches the backend in the first place — the
server-side validation is the backstop, not the primary UX. Added a "Rank"
column (sortable) and filter to the Dreams table, populated only with
letters actually in use (never a static A–Z dropdown). Live-smoke-tested:
two dreams in the same Vision Area both saved as `A` with no conflict, and
a lowercase `a` was rejected with BR-46's message.

Alongside the existing categorical priority, a dream can carry an optional
single-letter rank (`A`, `B`, `C`, …) that distinguishes it from its
siblings inside the same Vision Area — assigned after the fact, not while
first capturing the dream, so listing isn't slowed down by ranking.

- FR-57.1 `Dream` gains an optional `letterRank` field: a single uppercase
  letter. Never required.
- FR-57.2 The letter is a **label, not a strict order** — two dreams in the
  same Vision Area can share the same letter (matching the source method's
  own tie-allowing nature); the system never renumbers or reassigns a
  letter automatically.
- FR-57.3 The letter is scoped to nothing structurally — it is just a
  field on the Dream, sortable/filterable within a Vision Area view, but
  not compared across different Vision Areas (an `A` in "Career" carries
  no relationship to a `B` in "Health").
- FR-57.4 The Dreams list can sort and filter by letter rank the same way
  it already sorts/filters by categorical priority.

**Acceptance criteria**

1. Setting `letterRank` to a single uppercase letter (`A`–`Z`) succeeds.
2. Setting it to anything else (blank stays valid; a number, a word, or
   more than one character does not) is rejected with a clear message.
3. Two dreams in the same Vision Area can both be saved as `A` — no
   uniqueness error.
4. The Dreams table can be sorted by letter rank and filtered to one
   letter, the same way it already sorts/filters by categorical priority.

**Business rules**

| # | Rule |
|---|---|
| BR-46 | `Dream.letterRank`, when present, must be exactly one uppercase letter (`A`–`Z`); blank/null is always valid. Ties are allowed — the system never enforces or auto-assigns uniqueness within a Vision Area. |

**Data model / migration**

- `V30__dream_letter_rank.sql`: additive on `dreams` — one nullable
  `VARCHAR(1)` column, `letter_rank`.

**Design decisions**

- **Additive, not a replacement for `priority`.** `priority` already drives
  existing sort/filter/badge behavior across the whole app (Goals, Steps,
  Tasks all share the same enum); replacing it here would ripple into
  unrelated screens. A second, narrower field avoids that blast radius.
- **Letter, not a numeric drag-rank.** The source method explicitly
  brainstorms first, ranks after, and allows ties within a grade — a
  strict unique numeric order would force the user to break ties the
  method doesn't ask them to break, and would need reorder-on-drag
  bookkeeping this feature doesn't otherwise need.

---

## FR-58 Starter Vision Area Suggestions — ✅ Done 2026-09-14 *(Effort: S)*

**Shipped (2026-09-14):** `VisionAreaWizard` gained an `existingNames`
prop (passed from `VisionAreasPage.tsx` as the current non-archived area
names) and a row of `STARTER_AREA_SUGGESTIONS` chips above the name field
in step 0 — the exact ten-item list `CLAUDE.md` already uses as its own
Vision Area examples. Clicking a chip sets the name field (case-insensitive
match against `existingNames` marks it "✓" but never disables the click,
per FR-58.3). No backend or migration changes — purely a frontend addition
to an existing free-text step.

The Vision Area setup wizard (FR-33) offers a short list of common life-area
starting points as one-click suggestions, so a new plan doesn't accidentally
end up entirely work-focused just because that's what came to mind first.

- FR-58.1 Step 1 of `VisionAreaWizard` shows a small set of suggested area
  names as clickable chips, drawn from this product's own existing
  category vocabulary (already used as examples in this project's own
  guidance: Career, Health, Family, Finance, Education, Business,
  Spiritual, Relationship, Research, Leadership).
- FR-58.2 Clicking a suggestion fills the name field with that suggestion;
  the user can still edit it before saving, and can ignore every
  suggestion and type a fully custom name.
- FR-58.3 A suggestion already used by one of the user's existing
  (non-archived) Vision Areas is visually marked as already added, but
  remains clickable (creating a second area with the same name is allowed,
  matching how the flat create form already allows duplicate names).

**Acceptance criteria**

1. The wizard's first step shows the suggestion chips alongside the
   existing free-text name field.
2. Clicking a chip fills the name field with that text; the user can still
   type over it.
3. Typing a custom name without clicking any chip works exactly as it does
   today — the feature is purely additive to the existing flow.
4. An existing area name is marked as already-added among the suggestions
   but does not block clicking it again.

**Business rules**

| # | Rule |
|---|---|
| BR-47 | Vision Area starter suggestions are advisory only: they pre-fill the wizard's name field but never restrict what a user names an area, how many areas they create, or whether an area name repeats. |

**Data model / migration**

- None. The suggestion list is a static, frontend-only constant; nothing
  about a Vision Area's shape changes.

**Design decisions**

- **Reuse this project's own category vocabulary, not the book's list.**
  `CLAUDE.md` already names a ten-item example set for "Vision Area." That
  set already spans work and personal-life categories (Health, Family,
  Spiritual, Relationship alongside Career, Business, Finance), so it
  serves the same "don't build an entirely one-sided plan" purpose without
  introducing a second, book-derived list that would need its own
  originality review.
- **Suggestions, not a forced checklist.** A required "cover every arena"
  gate would contradict this system's existing archive-not-delete,
  advisory-not-blocking posture (see BR-38's identical "advisory only"
  framing for FR-49). The goal is visibility into common starting points,
  not a new mandatory step.

---

## Business Rules (new in V6.0.0)

| # | Rule | Status |
|---|---|---|
| BR-45 | `Dream.imageUrl`, when present, must be a well-formed `http://` or `https://` URL; blank/null is always valid and never required. | ✅ Done 2026-09-14 |
| BR-46 | `Dream.letterRank`, when present, must be exactly one uppercase letter (`A`–`Z`); blank/null is always valid. Ties are allowed — the system never enforces or auto-assigns uniqueness within a Vision Area. | ✅ Done 2026-09-14 |
| BR-47 | Vision Area starter suggestions are advisory only: they pre-fill the wizard's name field but never restrict what a user names an area, how many areas they create, or whether an area name repeats. | ✅ Done 2026-09-14 |

## Migrations (V6.0.0)

| Migration | Purpose | Type | Status |
|---|---|---|---|
| `V29__dream_visual_anchor.sql` | `image_url` on `dreams` (FR-56) | Additive, nullable | ✅ Done 2026-09-14 |
| `V30__dream_letter_rank.sql` | `letter_rank` on `dreams` (FR-57) | Additive, nullable | ✅ Done 2026-09-14 |

## Build Order

| Order | Item | Why this order | Effort | Status |
|---|---|---|---|---|
| 1 | FR-56 Dream Visual Anchor | Independent, purely additive field + read-only render | S | ✅ Done 2026-09-14 |
| 2 | FR-57 Intra-Area Letter Priority | Independent of FR-56; both touch `Dream` but not the same fields | S | ✅ Done 2026-09-14 |
| 3 | FR-58 Starter Vision Area Suggestions | Frontend-only, no dependency on the other two | S | ✅ Done 2026-09-14 |

## Non-Functional Notes

- Every migration in this document is additive — no destructive schema
  change, matching V1.0.0 through V5.0.0's unbroken practice.
- Both new business rules (BR-45, BR-46) are format-only validations, not
  save-blocking business logic in the FR-32/FR-50/FR-55 sense — confirmed
  in the shipped code: no new `BusinessRuleException` paths, only DTO-level
  `@Pattern` validation annotations.
- No new infrastructure is required: FR-56 deliberately avoids file
  storage by using a URL field instead of an upload, consistent with every
  prior version's "no file storage" note.
- New backend coverage: `DreamVisualAndRankFlowTests` (9 cases — valid/invalid
  image URL, valid/invalid/tied letter rank, blank-is-always-valid for both)
  runs against the real DTO validation pipeline through MockMvc, the same
  pattern `DreamDecisionGateFlowTests` established for FR-55. New frontend
  coverage: `decisionChecklist.test.ts` was already in place from FR-55; no
  new pure-function logic was extracted for FR-56/57/58 since both are
  thin field pass-throughs with client-side input normalization only —
  backend 196/196, frontend 345/345, both green.
- This document is a **living plan, now fully built**: FR-56/BR-45,
  FR-57/BR-46, and FR-58/BR-47 are all shipped. Every requirement, business
  rule, and migration proposed in this document has landed.
