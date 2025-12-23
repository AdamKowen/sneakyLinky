*A lightweight “background-check” for links & messages on Android.*  
Set SneakyLinky as the device’s default browser once—from then on, every link you tap is quickly vetted. If it looks risky, you’ll see a *clear warning* with *Proceed / Block*. If it’s fine, SneakyLinky instantly hands it off to your real browser (Chrome, etc.) so your flow stays smooth.

<img width="4096" height="4096" alt="IMG_0006" src="https://github.com/user-attachments/assets/e265b56f-4571-4162-91f8-eaf08e87bef1" />
<img width="4096" height="3072" alt="sneakyPic" src="https://github.com/user-attachments/assets/bc2b2360-3474-428c-89e3-23cfd7945189" />



## Repo Map

client_app/   — Android app (Kotlin)

openai_proxy/ — App Server (Node/Express)

web_ui/       — Admin Web (React/Vite)

---

## Why
- *Phishing happens at the click.* Users need help exactly when they’re about to open a link.
- *Keep habits intact.* Don’t replace the browser—add a fast safety layer before it.
- *Local-first.* Most checks run on-device for speed and privacy; deeper checks can be offloaded to the server.

---

## How It Works (High-Level)

1. *Interception (Default Browser).* Tapping any link routes through SneakyLinky first.
2. *Instant Local Checks.* A lightweight rules engine inspects the URL immediately (no page load).
3. *Caution Screen (if needed).* Clear context + *Proceed / Block* choice.
4. *Open as Usual.* Safe links are forwarded to the browser you picked in settings.
5. *(Optional) Deep Check.* In parallel, the app can ask the server/AI for a second opinion and notify if risk escalates.
6. *Report & Learn.* One-tap reporting, admin review, and evolving allow/deny lists.


---

## Key Features
- *Default-browser safety layer* (no habit change).
- *On-device heuristics* (fast, private).
- *Suspicion alerts* with *Proceed / Block*.
- *User reports* (one tap) → *Admin review* to reduce false positives.
- *Personal allow/deny lists* and *history* (local, with server sync).
- *Optional cloud analysis* (AI/risk engines) for second opinion.


---

## Privacy & Data
- *Local-first by design.* Most checks run on-device; no page content is fetched just to “test” a link.
- *Minimal sharing.* Only the URL (and optional surrounding text) is sent for deep checks—if you enable it.
- *User control.* Proceed/Block is always your call; you can manage personal allow/deny lists and clear history.

---


## Architecture (10,000-ft view)

**Android App (`client_app`)**
- Intercepts links via default-browser intent
- Hotset DB via root (weekly sync with WorkManager with smart update by version diff)
- Local heuristics and rules engine (e.g., suspicious patterns, homoglyph/mixed-alphabet checks)
- Minimal caution UI + preferred-browser handoff
- Local database for history + local lists
- Networking for tests and reporting (OkHttp)
- **Async background deep-check** (URL + message context via Accessibility)

**App Server (`openai_proxy`)**
- REST API for deep risk evaluation
- Hotset management + weekly scheduler
- PostgreSQL + Sequelize, JWT admin auth, logging & tests

**Admin Web (`web_ui`)**
- React + Vite, protected routes (JWT)
- Domains & User Reports management + stats
- Deployed via Vercel


---


## Reviewer Guide — Quick Build & Try

**Android (`client_app`)**
1) Android Studio (JDK 17). 2) Run on device/emulator.  
3) Set SneakyLinky as *default browser*. 4) Enable Accessibility Service.  
5) Tap links → safe = opens in preferred browser; suspicious = *Proceed/Block*;  
   background deep-check may notify if verdict changes.



## What to Review (Code Pointers)

**Android**
- URL canon & heuristics: `service/urlanalyzer/UrlCanon.kt`, `UrlHeuristics.kt`, `UrlUtils.kt`
- Default-browser handoff: `ui/LinkRelayActivity.kt`, `ui/DefaultBrowserHelper.kt`
- Accessibility capture & context: `service/MyAccessibilityService.kt`
- Weekly hotset sync: `service/hotsetdatabase/HotsetSyncScheduler.kt`, `HotsetSyncWorker.kt`, `HotsetRepository.kt`
- Preferred browser picker: `ui/BrowserCarouselAdapter.kt` (+ layouts)
- Link Flow screen: `ui/FlowCardBinder.kt`, `res/layout/link_flow_card.xml`
- Reporting UI: `service/report/ReportDialogFragment.kt` (one-tap report dialog),
  data & persistence: `service/report/HistoryStore.kt`, `service/report/HistoryEntities.kt`.
- Transparent UI (manifest + theme): `AndroidManifest.xml` → `ui/LinkRelayActivity` (`@style/Theme.SneakyLinky.Transparent`) 



**Server**
- Risk pipeline & routes: `src/routes/v1/*`, services in `src/services/*`
- Hotset scheduler & repos: `src/HotsetScheduler.js`, `src/repositories/*`
- Models & DB config: `src/models/*`, `src/config/db.js`

**Admin Web**
- Protected routing: `src/components/ProtectedRoute.jsx`, `src/AppRouter.jsx`
- Domains & Reports UIs: `src/pages/Dashboard/*`, `src/pages/UserReports/*`
- API client & auth: `src/services/apiClient.js`, `src/services/*`


---

## Credits
*Team:* Omer Blau • Paz Blutman • Adam Kowen  
*Mentor:* Amir Kirsh

---
