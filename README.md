*A lightweight “background-check” for links & messages on Android.*  
Set SneakyLinky as the device’s default browser once—from then on, every link you tap is quickly vetted. If it looks risky, you’ll see a *clear warning* with *Proceed / Block*. If it’s fine, SneakyLinky instantly hands it off to your real browser (Chrome, etc.) so your flow stays smooth.

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

## Architecture (10,000-ft view)

*Android App (Kotlin)*
- Intercepts links via Android intents
- Local rules engine (e.g., suspicious patterns, homoglyph/mixed-alphabet checks)
- Caution screen UX
- Local database for history + personal lists
- Networking (e.g., OkHttp) to server for deep checks & updates

*Server (Node/Express or similar)*
- Risk evaluation pipeline (heuristics/AI/services)
- Admin web page (review/triage of community reports)
- Central DB for snapshots, allow/deny lists, versioned deltas


---

## Privacy & Data
- *Local-first by design.* Most checks run on-device; no page content is fetched just to “test” a link.
- *Minimal sharing.* Only the URL (and optional surrounding text) is sent for deep checks—if you enable it.
- *User control.* Proceed/Block is always your call; you can manage personal allow/deny lists and clear history.

---

## Credits
*Team:* Omer Blau • Paz Blutman • Adam Kowen  
*Mentor:* Amir Kirsh

---
