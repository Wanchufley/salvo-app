import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./components/AppShell.jsx";
import { Favicon } from "./components/Favicon.jsx";
import "./styles.css";

function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  async function submit(path) {
    const body = new URLSearchParams({
      username: email,
      password
    }).toString();

    try {
      const response = await fetch(path, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        const message = data.error || `Request failed: ${response.status}`;
        window.alert(message);
        return;
      }

      // On success, go back to games; the header will reflect login state.
      window.location.href = "/web/games.html";
    } catch (err) {
      window.alert(err.message || "Network error");
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    submit("/api/login");
  }

  return (
    <>
      <Favicon />
      <AppShell title="Salvo" subtitle="Log in or sign up">
        <div className="auth-page">
          <div className="auth-layout">
            <div className="auth-hero">
              <h2>Command your fleet from anywhere</h2>
              <p>
                Sign in to create games, join battles, and track your standing on the Salvo
                leaderboard.
              </p>
            </div>
            <section className="card auth-card">
              <div className="card-header">
                <h2>Account</h2>
              </div>
              <form className="form" onSubmit={handleSubmit}>
                <input
                  type="email"
                  className="input"
                  placeholder="Email"
                  value={email}
                  onChange={event => setEmail(event.target.value)}
                  autoComplete="email"
                />
                <input
                  type="password"
                  className="input"
                  placeholder="Password"
                  value={password}
                  onChange={event => setPassword(event.target.value)}
                  autoComplete="current-password"
                />
                <div style={{ display: "flex", gap: "8px", justifyContent: "center" }}>
                  <button type="submit" className="button">
                    Log in
                  </button>
                  <button
                    type="button"
                    className="button"
                    onClick={() => submit("/api/players")}
                  >
                    Sign up
                  </button>
                </div>
              </form>
            </section>
          </div>
        </div>
      </AppShell>
    </>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<LoginPage />);
