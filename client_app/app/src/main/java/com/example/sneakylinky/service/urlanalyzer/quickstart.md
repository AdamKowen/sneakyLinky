# SneakyLinky URL Analyzer — Quick Start (Local Flow)

> **Use case:** You receive a raw URL `String` from outside the browser. Decide whether to block immediately or allow locally (with a warning) while you kick off a deeper server check.

## 1) Single entry point
```kotlin
// suspending
val res = evaluateUrl(rawUrl)  // UrlEvaluation
```

## 2) Branch on result
- `source = PARSE_ERROR` → **BLOCK** (show parse error UI).
- `source = BLACKLIST`   → **BLOCK** (hard deny).
- `source = WHITELIST`   → **ALLOW (final)** (you may still optionally queue server check).
- `source = HEURISTICS`
    - `verdict = BLOCK`    → **BLOCK** (critical hit or soft score ≥ threshold).
    - `verdict = SAFE`     → **ALLOW (local-safe)** → **show warning** → **trigger server analysis**.

## 3) Minimal wiring
```kotlin
when (val res = evaluateUrl(rawUrl)) {
    else -> when (res.source) {
        DecisionSource.PARSE_ERROR, DecisionSource.BLACKLIST -> showBlockScreen(res)
        DecisionSource.WHITELIST -> openInBrowser(rawUrl) // final allow
        DecisionSource.HEURISTICS ->
            if (res.verdict == Verdict.BLOCK) showBlockScreen(res)
            else {
                showLocalSafeWarning(res)    // “Deeper analysis running…”
                openInBrowser(rawUrl)        // provisional allow
                enqueueServerCheck(res.canon!!)
            }
    }
}
```

---

# SneakyLinky URL Analyzer — Integration Guide (Detailed)

This directory (`urlanalyzer/`) provides a **local decision engine** for incoming URLs:
- **Parser**: `toCanonUrlOrNull(raw: String): CanonUrl?` (strict, pure, `http/https` only)
- **Heuristics**: `analyzeAndDecide(canon: CanonUrl): BlockResult` (critical + soft aggregation)
- **Lists & Flow**: `checkLocalLists(canon)`, **`evaluateUrl(raw)`** (single entry with Room lists)
- **Seam for tests**: `WhitelistSource.loader` (defaults to Room; override in tests)

---

## End-to-end call order

1) **Parse**
   ```kotlin
   val canon = rawUrl.toCanonUrlOrNull()
   if (canon == null) { /* PARSE_ERROR → BLOCK */ }
   ```

2) **Local lists (Room)**
   ```kotlin
   when (checkLocalLists(canon)) {
       WHITELISTED -> /* final ALLOW */
       BLACKLISTED -> /* BLOCK */
       NOT_PRESENT -> /* run heuristics */
   }
   ```

3) **Heuristics (aggregate)**
   ```kotlin
   val h = analyzeAndDecide(canon)
   val blocked = h.blocked            // includes criticals
   val softScore = h.totalScore       // 0..1 (1.0 if hard-blocked)
   val reasons: List<Reason> = h.reasons
   ```

4) **Decision envelope (preferred)**
   ```kotlin
   val res = evaluateUrl(rawUrl)  // wraps all steps above
   ```

---

## Decision matrix (what to do in UI)

| `res.source`  | `res.verdict` | Action in app                                              |
|---------------|---------------|------------------------------------------------------------|
| `PARSE_ERROR` | `BLOCK`       | Show parse error → **BLOCK**                               |
| `BLACKLIST`   | `BLOCK`       | Show “Known malicious” → **BLOCK**                         |
| `WHITELIST`   | `SAFE`        | **Open in browser (final allow)**                          |
| `HEURISTICS`  | `BLOCK`       | Show reasons (optional) → **BLOCK**                        |
| `HEURISTICS`  | `SAFE`        | **Local-safe allow**: warning banner → open → server check |

**Local-safe flow** (recommended):
- Show non-blocking banner/snack bar: “We’re checking this link on the server.”
- Immediately forward to the browser of choice.
- Fire & forget a job: `enqueueServerCheck(res.canon!!)`.
- When server verdict arrives, show a notification if it **overrides** local-safe.

---

## What the parser guarantees (CanonUrl essentials)

- **Schemes**: only `http`/`https`, lowercase in `canon.scheme`.
- **Host**:
    - `hostUnicode` (lowercase, Unicode or IP literal, IPv6 **unbracketed**).
    - `hostAscii` (punycode/ASCII for Unicode hosts; equals literal for IPs).
- **Port**: `null` unless explicitly present (no defaulting to 80/443).
- **Domain breakdown** (may be `null` for IPs or unknown suffix):
    - `tld` (public suffix), `domain` (eTLD+1), `subdomain`.
- **Path/query/fragment**: **raw** (no decoding/normalization).
- **Flags**:
    - `hasEncodedParts` — any `%` in path/query/fragment.
    - `isMixedScript` — coarse Unicode red flag (not full spoof detect).

**Caller rule:** treat `port`, `domain`, `subdomain`, `tld`, `query`, `fragment` as **optional**.

---

## Heuristics (how the decision is formed)

- **Critical booleans (any = hard block)**  
  `IP_HOST`, `MIXED_SCRIPT`, `USERINFO_PRESENT`, `UNFAMILIAR_TLD`
- **Soft booleans (weighted)**  
  `PORT_SCHEME_MISMATCH`, `ENCODED_PARTS`
- **Soft numeric (0..1, weighted)**  
  `NEAR_WHITELIST_LOOKALIKE`, `LONG_URL`, `TOO_MANY_SUBDOMAINS`, `PHISH_KEYWORDS`

Aggregation is a **weighted soft-OR** over non-critical contributors; threshold = `0.60`. Critical hits set `blocked = true` regardless of soft score.

---

## Logging (compact, safe)
- Per-test logs like:
    - `ip: true`, `mix: true`, `ui: present`, `tld: unfamiliar`
    - `port: mismatch https:8080`, `enc: present`
    - `med: near=paypal.com r=0.09 s=1.00`, `len: 123 ...`, `subd: 4 ...`, `kw: 3 ...`
- Final line:  
  `soft=0.73 crit=1 hard[ip,tld] soft[len,kw] block=true`

> Logs are sanitized: only `scheme://host[:port]` portions ever appear—no full paths or queries.

---

## Minimal integration snippet (UI + server)

```kotlin
lifecycleScope.launch {
    val res = evaluateUrl(rawUrl)
    when (res.source) {
        DecisionSource.PARSE_ERROR, DecisionSource.BLACKLIST -> showBlockScreen(res)
        DecisionSource.WHITELIST -> openInBrowser(rawUrl) // final allow
        DecisionSource.HEURISTICS ->
            if (res.verdict == Verdict.BLOCK) showBlockScreen(res)
            else {
                showLocalSafeWarning(res)
                openInBrowser(rawUrl)
                enqueueServerCheck(res.canon!!) // deeper analysis
            }
    }
}
```

---

## Extension points & tips

- **Server sync:** `canon.originalUrl` is the raw URL; prefer sending a **sanitized envelope** (host + path + minimal context) to respect privacy.
- **List policy:** Treat **whitelist as final allow** and **blacklist as hard block**. Keep them **host-based** (ASCII) to match `CanonUrl.hostAscii`.
- **Testing seam:** Override `WhitelistSource.loader` in unit tests to avoid Room/Android.
- **Room in tests:** For instrumentation, use an **in-memory DB** and seed DAOs; production uses your singleton via `SneakyLinkyApp.appContext()`.

---

## Integration checklist

- [ ] Add a **warning banner** UI for local-safe allows.
- [ ] Implement `enqueueServerCheck(CanonUrl)` and handle async verdicts.
- [ ] Ensure **default browser handoff** works from your entry Activity.
- [ ] Wire **notifications** for server overrides (unsafe → block).
- [ ] Keep lists updated (Room migrations & background refresh).
- [ ] Monitor **heuristics logs** in release with sampling (PII-safe).

---
