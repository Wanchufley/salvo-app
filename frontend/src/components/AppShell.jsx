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

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  function toggleTheme() {
    setTheme(current => (current === "dark" ? "light" : "dark"));
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
          <nav className="nav-links">
            <a href="/web/games.html">Games</a>
            <a href="/roster.html">Roster</a>
          </nav>
          <button type="button" className="toggle" onClick={toggleTheme}>
            {theme === "dark" ? "Light mode" : "Dark mode"}
          </button>
        </div>
      </header>
      <main className="main">{children}</main>
    </div>
  );
}
