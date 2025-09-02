# Canonical URL Parsing Contract — `toCanonUrlOrNull` & `CanonUrl`

## What this does
`toCanonUrlOrNull(raw: String)` converts a browser-style URL string into a normalized, analysis-ready `CanonUrl`. It **returns a `CanonUrl` on success** or **`null` on any failure** (empty input, invalid syntax, unsupported scheme, malformed host). It never crashes; the caller decides how to message the user. Supported schemes: **`http`** and **`https`**. The function is pure (no network/DB), and internal logs are sanitized (no query/fragment).

---

## Public API (caller pattern)

```kotlin
val canon = url.toCanonUrlOrNull()
if (canon == null) {
    // Show friendly error and skip local checks
} else {
    // Safe to run heuristics using fields on 'canon'
}
```

---

## The `CanonUrl` model (fields & nullability)

### Core components

| Field         | Type      | Nullable | Meaning / Notes                                                                 |
|---------------|-----------|:--------:|----------------------------------------------------------------------------------|
| `originalUrl` | `String`  |    No    | Exact input string.                                                              |
| `scheme`      | `String`  |    No    | Lowercased; always `"http"` or `"https"`.                                        |
| `userInfo`    | `String?` |   Yes    | When present, e.g. `"user:pass"`.                                                |
| `hostUnicode` | `String`  |    No    | Lowercase Unicode host (e.g., `пример.com`) or IPv6 **without** brackets.       |
| `hostAscii`   | `String?` |   Yes    | Punycode/ASCII host for Unicode domains (`xn--…`); for IPs, equals the literal. |
| `port`        | `Int?`    |   Yes    | **Only** when the URL explicitly includes `:port`; no defaulting to 80/443.     |
| `path`        | `String`  |    No    | Raw path; percent-encoding preserved (may be `""` or `"/"`).                     |
| `query`       | `String?` |   Yes    | Raw query without the leading `?`.                                               |
| `fragment`    | `String?` |   Yes    | Raw fragment without the leading `#`.                                            |

### Derived metadata

| Field            | Type                         | Nullable | Meaning / Notes                                                                                 |
|------------------|------------------------------|:--------:|--------------------------------------------------------------------------------------------------|
| `domain`         | `String?`                    |   Yes    | eTLD+1 (e.g., `example.co.uk`). `null` for IPs or unrecognized suffix.                          |
| `subdomain`      | `String?`                    |   Yes    | e.g., `shop`.                                                                                   |
| `tld`            | `String?`                    |   Yes    | Public suffix (e.g., `co.uk`).                                                                   |
| `pathSegments`   | `List<String>`               |    No    | Non-empty segments of `path`, preserving any matrix params within a segment.                    |
| `pathsToParams`  | `Map<String, List<String>>`  |    No    | Matrix params per segment (e.g., `/a/b;c=1` → `"a" -> []`, `"b" -> ["c=1"]`).                   |
| `hasEncodedParts`| `Boolean`                    |    No    | `true` if any `%` appears in path/query/fragment.                                               |
| `isMixedScript`  | `Boolean`                    |    No    | **Coarse** flag: `true` if any char in `hostUnicode` is non-Basic-Latin (not full spoof detect).|

---

## Invariants & guarantees
- `scheme` is lowercase and ∈ {`http`,`https`}.
- `hostUnicode` is lowercase; IPv6 is **unbracketed** (`2001:db8::1`).
- `hostAscii` is lowercase; it’s Punycode for Unicode hosts, or an IP literal for IP hosts.
- `port` is **only** set when explicitly present in the URL; absence means **no explicit port** (do not infer 80/443).
- `domain`/`subdomain`/`tld` are derived from the **ASCII** host using public-suffix data; all three are `null` for IP literals.
- No decoding or normalization is performed (e.g., `..`, `%2F` remain as-is).

---

## How to use it (common patterns)

**Boundary null-check**
```kotlin
val canon = url.toCanonUrlOrNull() ?: return showError("Could not verify link")
```

**Explicit port heuristic**
```kotlin
if (canon.port != null) {
    // URL uses an explicit port
}
```

**IP literal checks**
```kotlin
val isIp = canon.domain == null && (
    (canon.hostAscii?.contains(':') == true) ||                         // IPv6
    (canon.hostAscii?.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) == true) // IPv4
)
```

**Keyword scans**
- Use `hostUnicode`, `path`, `pathSegments`, and `query`.

**TLD policy**
```kotlin
when (val tld = canon.tld) {
    null -> { /* IP or unknown suffix */ }
    else -> { /* check allow/deny lists */ }
}
```

**Homograph pre-filter (coarse)**
```kotlin
if (canon.isMixedScript) { /* raise risk or run deeper checks */ }
```

---

## Non-goals (what the parser does **not** do)
- No redirects or network lookups.
- No path normalization (no `"."` / `".."` resolution).
- No decoding of percent-encodings.
- No default port injection.
- Not a full Unicode homograph/spoof detector (`isMixedScript` is intentionally coarse).

---

## Debugging & logging
- Parser logs are **sanitized** (only `scheme://host[:port]`).
- `CanonUrl.toString()` prints a multi-line dump of all fields—handy for local logs/tests.

---

## TL;DR for implementers
- Call `toCanonUrlOrNull()` once; if `null`, show a friendly message and skip checks.
- Treat `domain`, `subdomain`, `tld`, `port`, `query`, `fragment` as **optional** (nullable).
- Rely on `hostUnicode`/`hostAscii`, `path`, and `pathSegments` for heuristics—they’re raw, stable, and lowercase where it matters.
- Don’t assume default ports or auto-normalization; add those explicitly if a heuristic needs them.
