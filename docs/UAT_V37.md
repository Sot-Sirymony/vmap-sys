# UAT checklist – A shorter sidebar (V37)

For the BRD 37.0.0 acceptance criteria (SBC-01 to SBC-09). Navigation only: no page, route, permission or data
changes. Use a staging server and three accounts: **Participant**, **Admin**, and **Viewer** (a role with only "View
users").

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| SBC-01 | Participant: open Goals; open every group | Home, Plan, Do, Review and Know yourself; 20 links; no Admin group | `AppLayout.test.tsx` | ☐ |
| SBC-02 | Participant on Goals: open Review; reload; open Tasks; fold Review; try to fold Do | Do is open on arrival; Review stays open after the reload, then folds; Do can't be folded while you are on Tasks | `AppLayout.test.tsx` | ☐ |
| SBC-03 | Viewer: look at the sidebar | An Admin group with Users only | `nav-items.test.ts` | ☐ |
| SBC-04 | Open the Vision Map; click each tab; open a dream; open a saved report | Each tab opens its page; the right tab is marked; Vision Map stays marked in the sidebar | `VisionMapTabs.test.tsx`, `AppLayout.test.tsx` | ☐ |
| SBC-05 | Click your name at the foot of the sidebar; open each item; Log out | Account, Security, Appearance, Import / Export, Issue Reports, Log out; each opens its page; Log out signs you out | `AppLayout.test.tsx` | ☐ |
| SBC-06 | **Ask the coach** | The Dream Coach opens | `AppLayout.test.tsx` | ☐ |
| SBC-07 | Ctrl+K, type "Appearance", "Timeline", "Dream Coach"; open each | Each is found and opens; the header shows the page's name | `CommandPalette.test.tsx`, `AppLayout.test.tsx` | ☐ |
| SBC-08 | Collapse the sidebar to icons; open the app at 360px | The icon rail shows every icon with dividers; the phone drawer shows the groups | `AppLayout.test.tsx` | ☐ |
| SBC-09 | CI | Builds and tests pass | CI | ☐ |

Khmer: review the 5 drafts in `docs/km-review-v37.csv` (ទំព័រដើម, អនុវត្ត, ពិនិត្យឡើងវិញ, ស្គាល់ខ្លួនឯង, សួរគ្រូបង្វឹក).
