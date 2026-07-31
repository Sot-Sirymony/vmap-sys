# User Guide

## Start

1. Run the backend.
2. Run the frontend.
3. Register a new account or sign in with the demo account.

Demo account:

```text
demo.vision.mapping@example.com
Password123
```

## Create a Vision Map

1. Open `Vision Areas`.
2. Create a major area such as Career Development.
3. Open `Dreams`.
4. Create a dream under the Vision Area.
5. Open `Goals`.
6. Create goals under the dream.
7. Open `Steps`.
8. Create ordered steps under each goal.
9. Open `Tasks`.
10. Create executable tasks under steps.

## Manage Tasks

The Tasks page shows a board with these columns:
- Not Started
- In Progress
- Waiting
- Blocked
- Completed
- Paused

Use the status select on a task card to move it between columns.

If a task is blocked, enter a blocker reason.

## Add Partners

Open `Partners` to create mentors, experts, advisors, colleagues, or resources. Partners can be linked to a Vision Area, Dream, Goal, Step, or Task.

## Track Obstacles

Open `Obstacles` to record blockers. Link each obstacle to the relevant dream, goal, step, or task. Assign a partner when support is needed.

## Build Communication

Open `Communication` to draft support requests. Select a partner and related work item, fill the purpose/request/outcome fields, then use `Generate message`.

## Review Progress

Open `Reviews` to record daily, weekly, monthly, or quarterly reviews.

Useful review notes:
- what was completed
- what was delayed
- what is blocked
- lessons learned
- next actions

## Appearance

Open `Appearance` in the sidebar, or click the sun/moon icon in the header for
the same controls without leaving the page.

Everything here applies immediately and is saved to your account, so the look you
choose follows you to any browser or device you sign in from.

**Theme** — a preset sets the mode and accent together in one click:

| Preset | What it is |
|---|---|
| Fluent System | Follows your device's light/dark setting (the default) |
| Fluent Light | Always light, brand blue |
| Fluent Dark | Always dark, brand blue |
| Ocean | Light and teal |
| Forest | Light and green |
| Slate | Light and steel blue |
| Midnight | Dark and purple |

Change any individual control afterwards and the theme is shown as **Custom** —
the app won't keep a preset name that no longer describes what you're looking at.
Pick a preset again to go back to one.

**Mode** — Light, Dark, or System. System follows your device and updates live
when your device switches.

**Accent** — ten colours. Every one has been checked for readable contrast in
both light and dark, so no choice can leave text hard to read.

**Background** — changes the page, cards, and sidebar together:

| Tone | What it looks like |
|---|---|
| Neutral | The default |
| Warm | A softer, cream-tinted canvas |
| Cool | A slight blue cast |
| Soft | A greyer canvas, so cards stand out more |
| Tinted | Follows whichever accent you picked |
| Flat | No canvas step — borders separate the cards instead |

Every tone is checked to keep text readable, so none of them can make the app
hard to read. This control is switched off while High contrast is on, because
that mode deliberately uses pure white or black — turn High contrast off and your
chosen tone comes straight back.

**Density** — Comfortable or Compact. Compact fits more rows on screen without
hiding anything.

**Text size** — Small, Medium, or Large. This scales all text proportionally,
which keeps the layout intact — unlike browser zoom.

**High contrast** — stronger text, borders, and focus rings. It works *with*
Light or Dark rather than replacing your choice. Status and priority colours keep
their meaning: Completed is still green, Blocked still orange, Critical still red.

**Reduce motion** — turns off animations. If your device already asks for reduced
motion, that continues to apply whether or not this is switched on.

The **Preview** panel on the settings page shows real badges, buttons, tables,
and progress bars — not mock-ups — so you can judge a choice before keeping it.
Use **Reset to defaults** to go back to the shipped appearance.

If a change can't reach your account (you're offline, for example), it still
applies on the device you're using and a notice explains that it wasn't saved.

## Excel

Open `Import / Export`.

Export downloads the current system data as an `.xlsx` workbook.

Import currently validates workbook structure and reports row counts. It does not yet create database records.
