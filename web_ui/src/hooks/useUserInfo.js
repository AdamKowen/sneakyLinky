// src/hooks/useUserInfo.js
import { useAuth } from "./useAuth";

/**
 * Centralized user info hook. Extracts email from JWT token.
 * If you want to change the source (e.g. fetch from API), update here.
 */
export function useUserInfo() {
  const { token } = useAuth();
  let email = "";
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1] || ""));
      email = payload.email || "";
    } catch {
      email = "";
    }
  }
  return { email };
}
