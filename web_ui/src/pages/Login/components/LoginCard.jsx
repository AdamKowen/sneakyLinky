import { useState } from "react";
import { useAuth } from "../../../hooks/useAuth";
import { useNavigate } from "react-router-dom";
import { FaEye, FaEyeSlash } from "react-icons/fa";


export default function LoginCard() {
  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [showPw, setShowPw] = useState(false);
  const { login, busy, error, setError } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!email || !pw) {
      setError("Please enter email and password");
      return;
    }
    try {
      await login(email, pw);
      navigate("/dashboard", { replace: true });
    } catch {
    
    }
  }

  return (
    <div className="login-card">
      <h1 className="login-title">SneakyLinky Admin</h1>

      <form className="login-form" onSubmit={handleSubmit} noValidate>
        {/* Email */}
        <div className="field floating-field">
          <input
            id="email"
            type="email"
            placeholder=" "
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
            aria-labelledby="email-label"
          />
          <label id="email-label" htmlFor="email" className="floating-label">
            Email
          </label>
        </div>

        {/* Password + eye toggle */}
        <div className="field floating-field">
          <div className="input-with-toggle">
            <input
              id="password"
              type={showPw ? "text" : "password"}
              placeholder=" "
              value={pw}
              onChange={(e) => setPw(e.target.value)}
              required
              minLength={8}
              autoComplete="current-password"
              aria-labelledby="password-label"
            />
            <label id="password-label" htmlFor="password" className="floating-label">
              Password
            </label>

            <button
              type="button"
              className="toggle-visibility"
              onClick={() => setShowPw((s) => !s)}
              aria-label={showPw ? "Hide password" : "Show password"}
              title={showPw ? "Hide password" : "Show password"}
            >
              {showPw ? <FaEyeSlash /> : <FaEye />}
            </button>
          </div>
        </div>

        {error && <div className="error">{error}</div>}

        <button type="submit" disabled={busy}>
          {busy ? "Signing in..." : "Log in"}
        </button>
      </form>
    </div>
  );
}
