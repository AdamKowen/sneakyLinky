# SneakyLinky – Web UI (React + Vite)

A minimal, production‑ready React front end with routing and a lightweight authentication layer.

## Tech Stack
- React + Vite
- react-router-dom (routing)
- Axios (HTTP client)

## Quick start
1) Requirements: Node 18+ and npm
2) Install: `npm install`
3) Configure env: create `.env` with `VITE_API_BASE` (see Environment)
4) Run dev server: `npm run dev`
5) Build: `npm run build`, Preview: `npm run preview`

On some Windows setups, run `npm.cmd install` if PowerShell execution policy blocks scripts.

## Environment
- `.env`
  - `VITE_API_BASE` – Base URL of the API, e.g. `http://localhost:3000/v1` or similar. The axios client will prepend this value to all requests.

## Folder structure
```
web_ui/
├─ index.html
├─ vite.config.js
├─ eslint.config.js
├─ package.json
├─ .env
├─ public/
│  └─ vite.svg
└─ src/
   ├─ main.jsx
   ├─ AppRouter.jsx
   ├─ config/
   │  └─ constants.js
   ├─ services/
   │  ├─ apiClient.js
   │  └─ auth.js
   ├─ hooks/
   │  └─ useAuth.js
   ├─ components/
   │  └─ ProtectedRoute.jsx
   └─ pages/
      ├─ Login/
      │  ├─ index.jsx
      │  ├─ login.css
      │  └─ components/
      │     └─ LoginCard.jsx
      └─ AdminHelllo/
         └─ index.jsx
```

## File-by-file roles and key functions

Top-level
- `index.html` – App shell with `<div id="root">`; Vite injects the bundle here.
- `vite.config.js` – Vite/React plugin configuration.
- `eslint.config.js` – Lint rules.
- `package.json` – Scripts and dependencies.

Entry and routing
- `src/main.jsx`
  - Boots the React app and renders `<AppRouter />` into `#root`.
- `src/AppRouter.jsx`
  - Declares all routes using `react-router-dom`:
    - `GET /login` → Public login page.
    - `GET /helloAdmin` → Protected page, only for authenticated users.
    - `*` → Any other path redirects to `/login`.
  - Uses `<ProtectedRoute>` to enforce authentication on protected routes.

Auth layer
- `src/config/constants.js`
  - `TOKEN_STORAGE_KEY` – The localStorage key for the JWT; currently `"auth_token"`.
- `src/services/apiClient.js`
  - Central axios instance.
  - Request interceptor attaches `Authorization: Bearer <token>` if a token exists in localStorage under `TOKEN_STORAGE_KEY`.
- `src/services/auth.js`
  - `login({ email, password })` – POST `/auth/login`. Expects `{ token }` in the response body and returns `data` as-is.
- `src/hooks/useAuth.js`
  - The main authentication hook managing UI auth state.
  - Internal helper: `isExpired(jwt)` – Best-effort client-side check of `exp` in JWT payload; returns `true` if expired.
  - State:
    - `token` – Current JWT from localStorage (or null).
    - `busy` – Whether an auth request is in progress.
    - `error` – Last auth error message.
  - Derived:
    - `isAuthenticated` – `true` if there is a token and it’s not expired (according to `isExpired`).
  - Actions:
    - `login(email, password)` – Calls the API, stores the token in localStorage, updates state; sets `error` on failure and rethrows.
    - `logout()` – Removes the token from localStorage and clears it from state.
    - `setError(message)` – Manually set the error state.
  - Effects:
    - Cross-tab sync via `window.storage` event: updates local state if another tab changes the token.
    - Proactive cleanup: if a present token is detected as expired on load/refresh, it’s removed.
- `src/components/ProtectedRoute.jsx`
  - Guards routes. If `useAuth().isAuthenticated` is false, redirects to `/login`.

Pages
- `src/pages/Login/index.jsx`
  - Page wrapper for the login experience; typically renders `LoginCard` and related UI. The CSS for this page lives in `src/pages/Login/login.css`.
- `src/pages/Login/components/LoginCard.jsx`
  - Self-contained login form component with:
    - Local state: `email`, `pw`, `showPw`.
    - `handleSubmit(e)` – Prevents default, validates inputs, calls `useAuth().login(email, pw)`, then redirects to `/helloAdmin` on success.
    - Password visibility toggle button (eye icon / emoji) that switches `type` between `password` and `text`.
    - Shows `error` from `useAuth()` if present; disables the submit button while `busy` is true.
- `src/pages/AdminHelllo/index.jsx`
  - Simple protected page displaying a header. You can add a logout button here by importing `useAuth` and calling `logout()`.

## How authentication works (end-to-end)
1. User submits the login form in `LoginCard.jsx`.
2. `useAuth().login(email, pw)` calls `services/auth.login`, which POSTs to `/auth/login`.
3. On success, the returned `{ token }` is stored in localStorage under `TOKEN_STORAGE_KEY` and mirrored to React state.
4. The axios request interceptor in `apiClient.js` reads the token from localStorage and sets `Authorization: Bearer <token>` automatically for all requests.
5. `ProtectedRoute` checks `useAuth().isAuthenticated`; if false, it redirects to `/login`.

## Routing map
- `/login` – Public login screen.
- `/helloAdmin` – Protected screen, requires a valid token.
- Any other path → redirects to `/login`.

## Development tips
- If you change `VITE_API_BASE`, restart the dev server.
- If you want to skip client-side JWT expiry checks, modify `useAuth().isAuthenticated` to return `!!token`.
- To add more protected pages, wrap elements with `<ProtectedRoute>` in `AppRouter.jsx`.

## Scripts
- `npm run dev` – Start the dev server.
- `npm run build` – Production build.
- `npm run preview` – Preview the production build locally.
