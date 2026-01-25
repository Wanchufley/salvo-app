import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./components/AppShell.jsx";
import { Favicon } from "./components/Favicon.jsx";
import "./styles.css";

function RosterPage() {
  const [players, setPlayers] = useState([]);
  const [name, setName] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    refresh();
  }, []);

  function refresh() {
    fetch("/players")
      .then(response => response.json())
      .then(data => {
        const list = data?._embedded?.players || [];
        setPlayers(list);
      })
      .catch(() => setStatus("Failed to load players."));
  }

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setStatus("Enter a player email or name.");
      return;
    }
    setStatus("Saving...");
    fetch("/players", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userName: trimmed })
    })
      .then(response => {
        if (!response.ok) {
          throw new Error("Failed to create player.");
        }
        return response.json();
      })
      .then(() => {
        setName("");
        setStatus("Player added.");
        refresh();
      })
      .catch(err => setStatus(err.message));
  }

  return (
    <>
      <Favicon />
      <AppShell title="Salvo" subtitle="Player roster">
        <section className="card">
          <div className="card-header">
            <h2>Players</h2>
            <span className="tag">{players.length} total</span>
          </div>
          {players.length === 0 ? (
            <p className="notice">No players found yet.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Player</th>
                </tr>
              </thead>
              <tbody>
                {players.map(player => (
                  <tr key={player.id}>
                    <td>{player.id}</td>
                    <td>{player.userName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Add player</h2>
            {status ? <span className="tag">{status}</span> : null}
          </div>
          <form className="form" onSubmit={handleSubmit}>
            <input
              className="input"
              value={name}
              onChange={event => setName(event.target.value)}
              placeholder="Enter email"
              autoComplete="email"
            />
            <button type="submit" className="button">
              Create player
            </button>
          </form>
        </section>
      </AppShell>
    </>
  );
}

const root = createRoot(document.getElementById("root"));
root.render(<RosterPage />);
