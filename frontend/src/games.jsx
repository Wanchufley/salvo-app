import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./components/AppShell.jsx";
import { Favicon } from "./components/Favicon.jsx";
import "./styles.css";

function GamesPage() {
  const [games, setGames] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    fetch("/api/games")
      .then(response => {
        if (!response.ok) {
          throw new Error(`Server error: ${response.status}`);
        }
        return response.json();
      })
      .then(data => {
        if (active) {
          if (Array.isArray(data)) {
            setGames(data);
          } else if (data && Array.isArray(data.games)) {
            setGames(data.games);
          } else {
            setGames([]);
          }
        }
      })
      .catch(err => {
        if (active) {
          setError(err.message);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const leaderboard = useMemo(() => buildLeaderboard(games), [games]);

  return (
    <>
      <Favicon />
      <AppShell title="Salvo" subtitle="Games & leaderboard">
      <section className="card">
        <div className="card-header">
          <h2>Leaderboard</h2>
          <span className="tag">{leaderboard.length} players</span>
        </div>
        {leaderboard.length === 0 ? (
          <p className="notice">No completed games yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Player</th>
                <th>Wins</th>
                <th>Losses</th>
                <th>Ties</th>
                <th>Points</th>
              </tr>
            </thead>
            <tbody>
              {leaderboard.map(row => (
                <tr key={row.playerId}>
                  <td>{row.email}</td>
                  <td>{row.wins}</td>
                  <td>{row.losses}</td>
                  <td>{row.ties}</td>
                  <td>{row.points.toFixed(1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <div className="card-header">
          <h2>Games</h2>
          <span className="tag">{games.length} total</span>
        </div>
        {error ? <p className="notice">{error}</p> : null}
        <div className="game-list">
          {games.map(game => (
            <article key={game.id} className="game-row">
              <strong>Game {game.id}</strong>
              <div className="game-meta">
                <span>{formatDate(game.created)}</span>
                <span>{(game.gamePlayers || []).length} players</span>
              </div>
              <div className="game-players">
                {(game.gamePlayers || []).map(gp => (
                  <a
                    key={gp.id}
                    className="link-button"
                    href={`/web/game.html?gp=${gp.id}`}
                  >
                    {gp.player?.email || "Unknown"}
                  </a>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>
      </AppShell>
    </>
  );
}

function formatDate(value) {
  if (!value) return "Unknown";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Unknown" : date.toLocaleString();
}

function buildLeaderboard(games) {
  const stats = new Map();

  games.forEach(game => {
    (game.gamePlayers || []).forEach(gp => {
      const player = gp.player;
      if (!player) return;
      if (!stats.has(player.id)) {
        stats.set(player.id, {
          playerId: player.id,
          email: player.email,
          wins: 0,
          losses: 0,
          ties: 0
        });
      }
    });

    (game.scores || []).forEach(score => {
      const player = score.player;
      if (!player) return;
      if (!stats.has(player.id)) return;
      const record = stats.get(player.id);
      const value = Number(score.score);
      if (value === 1) record.wins += 1;
      if (value === 0) record.losses += 1;
      if (value === 0.5) record.ties += 1;
    });
  });

  return Array.from(stats.values())
    .map(record => ({
      ...record,
      points: record.wins + record.ties * 0.5
    }))
    .sort((a, b) => b.points - a.points || b.wins - a.wins || a.losses - b.losses);
}

const root = createRoot(document.getElementById("root"));
root.render(<GamesPage />);
