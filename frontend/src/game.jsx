import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./components/AppShell.jsx";
import { Favicon } from "./components/Favicon.jsx";
import "./styles.css";

const rows = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"];
const cols = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

function GameViewPage() {
  const gamePlayerId = getGamePlayerId();
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!gamePlayerId) return;
    let active = true;
    fetch(`/api/game_view/${gamePlayerId}`)
      .then(response => {
        if (!response.ok) {
          throw new Error(`Server error: ${response.status}`);
        }
        return response.json();
      })
      .then(view => {
        if (active) setData(view);
      })
      .catch(err => {
        if (active) setError(err.message);
      });
    return () => {
      active = false;
    };
  }, [gamePlayerId]);

  const shipLocations = useMemo(() => new Set(getShipLocations(data)), [data]);
  const { playerTurns, opponentTurns } = useMemo(() => getSalvoMaps(data), [data]);

  return (
    <>
      <Favicon />
      <AppShell title="Salvo" subtitle="Game view">
        <section className="card">
          <div className="card-header">
            <h2>{formatGameTitle(data, gamePlayerId)}</h2>
            {data ? <span className="tag">Game {data.gameId}</span> : null}
          </div>
          {!gamePlayerId ? (
            <p className="notice">Missing game player id. Use ?gp=1 in the URL.</p>
          ) : error ? (
            <p className="notice">{error}</p>
          ) : data ? (
            <>
              <div className="grid-wrap">
                <GridCard
                  title="Your fleet"
                  legend={
                    <div className="legend">
                      <span>
                        <i style={{ background: "var(--ship)" }} /> Ship
                      </span>
                      <span>
                        <i style={{ background: "var(--hit)" }} /> Hit
                      </span>
                    </div>
                  }
                  renderCell={cellId => {
                    const hasShip = shipLocations.has(cellId);
                    const hitTurn = opponentTurns.get(cellId);
                    const classNames = ["cell"];
                    if (hasShip && hitTurn !== undefined) {
                      classNames.push("hit");
                      return { className: classNames.join(" "), label: String(hitTurn) };
                    }
                    if (hasShip) {
                      classNames.push("ship");
                      return { className: classNames.join(" "), label: "S" };
                    }
                    return { className: classNames.join(" "), label: "" };
                  }}
                />
                <GridCard
                  title="Your salvoes"
                  legend={
                    <div className="legend">
                      <span>
                        <i style={{ background: "var(--salvo)" }} /> Fired
                      </span>
                    </div>
                  }
                  renderCell={cellId => {
                    const turn = playerTurns.get(cellId);
                    if (turn === undefined) return { className: "cell", label: "" };
                    return { className: "cell salvo", label: String(turn) };
                  }}
                />
              </div>
            </>
          ) : (
            <p className="notice">Loading game data...</p>
          )}
        </section>
      </AppShell>
    </>
  );
}

function GridCard({ title, legend, renderCell }) {
  return (
    <div className="card">
      <div className="card-header">
        <h2>{title}</h2>
      </div>
      {legend}
      <Grid renderCell={renderCell} />
    </div>
  );
}

function Grid({ renderCell }) {
  return (
    <table className="grid">
      <thead>
        <tr>
          <th></th>
          {cols.map(col => (
            <th key={col}>{col}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map(row => (
          <tr key={row}>
            <th>{row}</th>
            {cols.map(col => {
              const cellId = `${row}${col}`;
              const cell = renderCell(cellId);
              return (
                <td key={cellId} className={cell.className}>
                  {cell.label}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function getGamePlayerId() {
  const params = new URLSearchParams(window.location.search);
  const raw = params.get("gp");
  const id = Number(raw);
  return Number.isFinite(id) ? id : null;
}

function formatGameTitle(data, gamePlayerId) {
  if (!data || !Array.isArray(data.gamePlayers)) {
    return "Awaiting game data";
  }
  const viewer = data.gamePlayers.find(gp => gp.id === gamePlayerId);
  const opponents = data.gamePlayers.filter(gp => gp.id !== gamePlayerId);
  const viewerEmail = viewer?.player?.email || "Unknown";
  const opponentEmails = opponents.map(gp => gp.player?.email || "Unknown").join(" vs ");
  if (!opponentEmails) {
    return `${viewerEmail} (you) - waiting for opponent`;
  }
  return `${viewerEmail} (you) vs ${opponentEmails}`;
}

function getShipLocations(data) {
  if (!data || !Array.isArray(data.ships)) return [];
  return data.ships.flatMap(ship => ship.locations || []);
}

function getSalvoMaps(data) {
  const playerTurns = new Map();
  const opponentTurns = new Map();
  if (!data || !data.salvoes) {
    return { playerTurns, opponentTurns };
  }
  const playerId = String(data.gamePlayerId);
  Object.entries(data.salvoes).forEach(([gpId, salvoesByTurn]) => {
    const turnMap = buildLocationTurnMap(salvoesByTurn);
    turnMap.forEach((turn, location) => {
      if (gpId === playerId) {
        playerTurns.set(location, turn);
      } else {
        const existing = opponentTurns.get(location);
        if (existing === undefined || turn < existing) {
          opponentTurns.set(location, turn);
        }
      }
    });
  });
  return { playerTurns, opponentTurns };
}

function buildLocationTurnMap(salvoesByTurn) {
  const map = new Map();
  Object.entries(salvoesByTurn || {}).forEach(([turn, locations]) => {
    const turnNumber = Number(turn);
    if (!Array.isArray(locations) || !Number.isFinite(turnNumber)) return;
    locations.forEach(location => {
      const existing = map.get(location);
      if (existing === undefined || turnNumber < existing) {
        map.set(location, turnNumber);
      }
    });
  });
  return map;
}

const root = createRoot(document.getElementById("root"));
root.render(<GameViewPage />);
