// src/hooks/useAuth.js
/**
 * useAuth – Lightweight authentication state management for the UI layer.
 *
 * Responsibilities:
 * - Persist the JWT in localStorage under TOKEN_STORAGE_KEY.
 * - Expose login(email, password) and logout() helpers with busy and error states.
 * - Derive isAuthenticated from the presence/validity of the token.
 * - Keep tabs/windows in sync via the window 'storage' event.
 * - Proactively clear an expired token on page load/refresh (best-effort).
 *
 * Notes:
 * - Token attachment to requests is handled by the centralized axios client via interceptors.
 * - The expiry check is best-effort and purely client-side; the server is the source of truth.
 * - If you don’t want client-side expiry checks, you can treat any non-empty token as authenticated.
 *
 * Usage:
 *   const { token, isAuthenticated, busy, error, login, logout } = useAuth();
 *   await login(email, password); // sets token and resolves on success
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { login as loginRequest } from "../services/auth";
import { TOKEN_STORAGE_KEY } from "../config/constants";

/**
 * Decode a JWT payload and check exp (seconds since epoch).
 *
 * This is a best‑effort check:
 * - JWTs are base64url; most browsers tolerate atob on them, but decoding may fail.
 * - Any parsing/decoding error will be swallowed and treated as "not expired" to avoid false logouts.
 *
 * @param {string} jwt
 * @returns {boolean} true if expired according to payload.exp, else false
 */
function isExpired(jwt) {
  try {
    const payload = JSON.parse(atob(jwt.split(".")[1] || ""));
    if (!payload.exp) return false; // no exp -> assume valid
    const now = Math.floor(Date.now() / 1000);
    return payload.exp <= now;
  } catch {
    // On any decoding/parsing error, treat as not expired (server will still enforce validity)
    return false;
  }
}

export function useAuth() {
  // 1) Bootstrap initial token from storage on first render
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  // 2) Busy flag for in-flight auth requests (e.g., during login)
  const [busy, setBusy]   = useState(false);
  // 3) Human-readable error message for the last auth operation
  const [error, setError] = useState("");

  // 4) Derived authentication state
  //    Return true only when there is a token and it is not (client-estimated) expired.
  //    If you prefer not to validate exp on the client, replace with: return !!token;
  const isAuthenticated = useMemo(() => {
    if (!token) return false;
    return !isExpired(token);
  }, [token]);

  // 5) Log in flow
  //    - Clear prior errors and mark busy
  //    - Call the API (services/auth.login)
  //    - Expect { token } on success; persist to storage and state
  //    - Surface a readable error message on failure and rethrow for callers
  const login = useCallback(async (email, password) => {
    setError("");
    setBusy(true);
    try {
      const data = await loginRequest({ email, password }); // expects { token }
      if (!data?.token) throw new Error("Missing token in response");
      localStorage.setItem(TOKEN_STORAGE_KEY, data.token);
      setToken(data.token);
      return data;
    } catch (e) {
      const message = e?.response?.data?.error || e.message || "Login error";
      setError(message);
      throw e;
    } finally {
      setBusy(false);
    }
  }, []);

  // 6) Log out flow
  //    - Remove token from storage and clear it in state
  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
  }, []);

  // 7) Cross-tab/window synchronization
  //    If another tab modifies the token in localStorage, reflect that change here.
  useEffect(() => {
    function onStorage(e) {
      if (e.key === TOKEN_STORAGE_KEY) setToken(e.newValue);
    }
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  // 8) Proactive cleanup on load/refresh
  //    If a token is already present but (client-estimated) expired, clear it.
  useEffect(() => {
    if (token && isExpired(token)) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      setToken(null);
    }
  }, [token]);

  // 9) Public API of the hook
  return { token, isAuthenticated, busy, error, login, logout, setError };
}
