import { useEffect, useState } from "react";
import { Logo } from "./Logo.jsx";

const THEME_KEY = "salvo-theme";

function getInitialTheme() {
  const stored = localStorage.getItem(THEME_KEY);
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
    return "dark";
  }
  return "light";
}

export function AppShell({ title, subtitle, children }) {
  const [theme, setTheme] = useState(getInitialTheme);
  const [authUser, setAuthUser] = useState(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    let active = true;
    fetch("/api/player")
      .then(response => {
        if (!response.ok) {
          throw new Error(`Server error: ${response.status}`);
        }
        return response.json();
      })
      .then(data => {
        if (!active) return;
        if (data && typeof data.id === "number") {
          setAuthUser({ id: data.id, email: data.email });
        } else {
          setAuthUser(null);
        }
      })
      .catch(() => {
        if (active) {
          setAuthUser(null);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  function toggleTheme() {
    setTheme(current => (current === "dark" ? "light" : "dark"));
  }

  async function handleAuthClick() {
    if (!authUser) {
      window.location.href = "/web/login.html";
      return;
    }

    try {
      const response = await fetch("/api/logout", { method: "POST" });
      if (!response.ok) {
        throw new Error(`Logout failed: ${response.status}`);
      }
      setAuthUser(null);
      window.location.href = "/web/games.html";
    } catch (err) {
      // Keep this simple and visible.
      window.alert(err.message || "Logout failed");
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-row">
          <div className="brand">
            <Logo />
            <h1>{title}</h1>
            {subtitle ? <span>{subtitle}</span> : null}
          </div>
          <div className="header-actions">
            <button type="button" className="toggle" onClick={toggleTheme}>
              {theme === "dark" ? "Light mode" : "Dark mode"}
            </button>
            <button type="button" className="toggle" onClick={handleAuthClick}>
              {authUser ? "Logout" : "Log in / Sign up"}
            </button>
          </div>
        </div>
      </header>
      <main className="main">{children}</main>
    </div>
  );
}
