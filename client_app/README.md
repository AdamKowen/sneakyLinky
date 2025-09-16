# SneakyLinky (Android Client)

*A lightweight “background-check” for links & messages on Android.*  
Set SneakyLinky as the device’s default browser once—after that, every link you tap is quickly vetted. If it looks risky, you’ll get a *clear warning* with *Proceed / Block*. If it’s fine, SneakyLinky instantly hands it off to your preferred browser so your flow stays smooth.

---

## Why
- *Phishing happens at the click.* Protection must appear exactly when a link is about to open.  
- *Keep habits intact.* Don’t replace the browser—add a fast safety layer before it.  
- *Local-first.* Most checks run on-device for speed and privacy; the hotset DB updates weekly.

---

## How It Works (High-Level)
1. **Interception (Default Browser).** Intents for `VIEW`/http(s) route through SneakyLinky first.  
2. **Instant Local Checks.** A lightweight rules engine scores the URL (no page load).  
3. **Caution Screen (if needed).** Clear context + *Proceed / Block* choice.  
4. **Open as Usual.** Safe links are forwarded to the *preferred browser* you picked in-app.  
5. **Async Deep Check (background).** In parallel, the app can run a **background deep-check** (server/AI) using the **URL + optional message context** captured by the Accessibility Service. If risk escalates, you’ll get a notification with the updated verdict.

---

## Key Features (Android app)
- **Minimal, transparent UI at click-time** (acts as *default browser* entry point).  
- **Message capture via Accessibility Service** (surface risky links from messaging apps).  
- **Fast local link checks** using a **local DB** (weekly hotset sync via WorkManager).  
- **Asynchronous background deep-check (link + message)** for second opinions without blocking UX.  
- **Preferred-browser selection** (Chrome, Edge, etc.)—forward safe links automatically.  
- **One-tap reporting** to help tune false positives/negatives.

---

## Where to Look in the Code
> Paths are from `client_app/app/src/main/java/com/example/sneakylinky/...`

- **Default-browser interception & hand-off**  
  - `ui/LinkRelayActivity.kt` – entry point for tapped links; routes to warning or real browser  
  - `ui/DefaultBrowserHelper.kt` – detection/forwarding to the user’s chosen browser  
- **Accessibility capture (messages)**  
  - `service/MyAccessibilityService.kt` – listens for on-screen messages to extract clicked URLs & context  
- **Local heuristics & URL utilities**  
  - `service/urlanalyzer/UrlHeuristics.kt`, `UrlCanon.kt`, `UrlUtils.kt`, `WhitelistSource.kt`  
- **Weekly hotset sync (local DB updates)**  
  - `service/hotsetdatabase/HotsetSyncScheduler.kt`, `HotsetSyncWorker.kt`, `RetrofitProvider.kt`, `HotsetApi.kt`, `HotsetRepository.kt`  
- **Caution screen & minimal UI**  
  - `ui/LinkWarningActivity.kt`, `ui/UiNotices.kt` (+ layouts under `res/layout/…`)  
- **Preferred-browser picker**  
  - `ui/BrowserCarouselAdapter.kt` (+ `res/layout/browser_pick_card.xml`, `item_browser_card.xml`)  
- **History & reporting**  
  - `service/report/HistoryStore.kt`, `HistoryEntities.kt`, `ReportDialogFragment.kt`

---

## Reviewer Guide (Build & Try)
1. **Build:** Android Studio (JDK 17). `Run` on a device/emulator with Play Services.  
2. **Set as default browser:** Android will prompt on first link; choose *SneakyLinky*.  
3. **Enable Accessibility Service:** Settings → Accessibility → *SneakyLinky* → On (for message capture).  
4. **Pick preferred browser:** Open the app → choose your real browser (Chrome, etc.).  
5. **Test:** Tap a few links from apps/messages.  
   - Safe: opens instantly in the preferred browser.  
   - Suspicious: shows caution screen with *Proceed / Block*.  
   - **Background deep-check:** you may receive a follow-up notification if risk changes based on URL + message context.  
   - Reporting: use the report action to send feedback.  

> Note: No page content is fetched during local checks; only the URL string is analyzed. Weekly hotset updates run via WorkManager.

---

## Privacy & Data
- **Local-first by design.** Heuristics and hotset checks run on-device.  
- **Minimal sharing.** Optional deep checks may send the URL (and, if enabled, surrounding text).  
- **User control.** Proceed/Block is always the user’s choice; personal allow/deny lists and history can be cleared.

---

## Credits
*Android App:* Kotlin + WorkManager + Retrofit  
*Team:* Omer Blau • Paz Blutman • Adam Kowen  
*Mentor:* Amir Kirsh
