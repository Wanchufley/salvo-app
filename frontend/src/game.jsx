import React, { useCallback, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { AppShell } from "./components/AppShell.jsx";
import { Favicon } from "./components/Favicon.jsx";
import "./styles.css";

const rows = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"];
const cols = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const MAX_SALVO_SHOTS = 5;
const SHIP_SPECS = [
  { type: "Aircraft Carrier", length: 5 },
  { type: "Battleship", length: 4 },
  { type: "Submarine", length: 3 },
  { type: "Destroyer", length: 3 },
  { type: "Patrol Boat", length: 2 }
];

function GameViewPage() {
  const gamePlayerId = getGamePlayerId();
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [selectedShipType, setSelectedShipType] = useState(null);
  const [startCell, setStartCell] = useState(null);
  const [placementShips, setPlacementShips] = useState([]);
  const [selectedSalvoCells, setSelectedSalvoCells] = useState([]);

  const loadGameView = useCallback(async () => {
    if (!gamePlayerId) return;
    const response = await fetch(`/api/game_view/${gamePlayerId}`);
    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }
    const view = await response.json();
    setData(view);
  }, [gamePlayerId]);

  useEffect(() => {
    if (!gamePlayerId) return;
    let active = true;
    loadGameView()
      .then(() => {
        if (active) setError("");
      })
      .catch(err => {
        if (active) {
          setError(err.message);
        }
      });
    return () => {
      active = false;
    };
  }, [gamePlayerId, loadGameView]);

  useEffect(() => {
    setSelectedSalvoCells([]);
  }, [data?.gameId, data?.gamePlayerId]);

  const postShips = useCallback(
    async ships => {
      if (!gamePlayerId) {
        throw new Error("Missing game player id.");
      }

      const response = await fetch(`/api/games/players/${gamePlayerId}/ships`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(ships)
      });

      if (response.status !== 201) {
        const payload = await response.json().catch(() => ({}));
        const message = payload.error || `Request failed: ${response.status}`;
        throw new Error(message);
      }

      await loadGameView();
      return true;
    },
    [gamePlayerId, loadGameView]
  );

  const postSalvo = useCallback(
    async locations => {
      if (!gamePlayerId) {
        throw new Error("Missing game player id.");
      }

      const turn = getCurrentTurn(data, gamePlayerId);
      const response = await fetch(`/api/games/players/${gamePlayerId}/salvos`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ turn, locations })
      });

      if (response.status !== 201) {
        const payload = await response.json().catch(() => ({}));
        const message = payload.error || `Request failed: ${response.status}`;
        throw new Error(message);
      }

      await loadGameView();
      return true;
    },
    [data, gamePlayerId, loadGameView]
  );

  useEffect(() => {
    window.salvo = {
      postShips,
      postSalvo
    };
    return () => {
      delete window.salvo;
    };
  }, [postShips, postSalvo]);

  const placedShipTypes = useMemo(
    () => new Set(placementShips.map(ship => ship.type)),
    [placementShips]
  );
  const selectedShipSpec = useMemo(
    () => SHIP_SPECS.find(spec => spec.type === selectedShipType) || null,
    [selectedShipType]
  );
  const occupiedPlacementCells = useMemo(() => {
    const set = new Set();
    placementShips.forEach(ship => {
      (ship.locations || []).forEach(location => set.add(location));
    });
    return set;
  }, [placementShips]);
  const possibleEndPlacements = useMemo(() => {
    if (!selectedShipSpec || !startCell) {
      return new Map();
    }
    return computePossiblePlacements(startCell, selectedShipSpec.length, occupiedPlacementCells);
  }, [selectedShipSpec, startCell, occupiedPlacementCells]);

  const hasPlacedShipsOnServer = Array.isArray(data?.ships) && data.ships.length > 0;
  const inPlacementMode = Boolean(data) && !hasPlacedShipsOnServer;
  const allShipsPlaced = placementShips.length === SHIP_SPECS.length;

  function selectShip(type) {
    if (placedShipTypes.has(type)) {
      return;
    }
    setSelectedShipType(type);
    setStartCell(null);
    setStatus("Pick a starting cell, then pick one highlighted ending cell.");
    setError("");
  }

  function handlePlacementCellClick(cellId) {
    if (!selectedShipSpec) {
      setStatus("Select a ship to place first.");
      return;
    }

    if (!startCell) {
      if (occupiedPlacementCells.has(cellId)) {
        setStatus("That cell is already occupied.");
        return;
      }
      setStartCell(cellId);
      setStatus("Now pick a highlighted ending cell.");
      return;
    }

    if (cellId === startCell) {
      setStartCell(null);
      setStatus("Start cell cleared. Pick a new starting cell.");
      return;
    }

    const locations = possibleEndPlacements.get(cellId);
    if (!locations) {
      if (!occupiedPlacementCells.has(cellId)) {
        setStartCell(cellId);
        setStatus("Start moved. Pick a highlighted ending cell.");
      } else {
        setStatus("Invalid ending cell.");
      }
      return;
    }

    setPlacementShips(current => [...current, { type: selectedShipSpec.type, locations }]);
    setSelectedShipType(null);
    setStartCell(null);
    setStatus(`${selectedShipSpec.type} placed.`);
    setError("");
  }

  async function handleSubmitPlacedShips() {
    if (!allShipsPlaced) {
      setStatus("Place all five ships before submitting.");
      return;
    }

    setStatus("Submitting ships...");
    try {
      await postShips(placementShips);
      setStatus("Ships submitted. Game view refreshed.");
      setError("");
    } catch (err) {
      setStatus("");
      setError(err.message || "Failed to post ships.");
    }
  }

  const shipLocations = useMemo(() => new Set(getShipLocations(data)), [data]);
  const { playerTurns, opponentTurns } = useMemo(() => getSalvoMaps(data), [data]);
  const currentTurn = useMemo(() => getCurrentTurn(data, gamePlayerId), [data, gamePlayerId]);
  const firedCells = useMemo(() => new Set(playerTurns.keys()), [playerTurns]);
  const selectedSalvoSet = useMemo(() => new Set(selectedSalvoCells), [selectedSalvoCells]);

  function toggleSalvoCell(cellId) {
    if (firedCells.has(cellId)) {
      return;
    }

    setSelectedSalvoCells(current => {
      if (current.includes(cellId)) {
        return current.filter(cell => cell !== cellId);
      }
      if (current.length >= MAX_SALVO_SHOTS) {
        return current;
      }
      return [...current, cellId];
    });
  }

  async function handleSubmitSalvo() {
    if (selectedSalvoCells.length === 0) {
      return;
    }

    setStatus("Submitting salvo...");
    try {
      await postSalvo(selectedSalvoCells);
      setSelectedSalvoCells([]);
      setStatus("Salvo submitted. Game view refreshed.");
      setError("");
    } catch (err) {
      const message = err.message || "Failed to post salvo.";
      setStatus("");
      setError(message);
      window.alert(message);
    }
  }

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
              {inPlacementMode ? (
                <>
                  <section className="card">
                    <div className="card-header">
                      <h2>Available ships</h2>
                    </div>
                    <div className="ship-picker">
                      {SHIP_SPECS.map(spec => {
                        const isPlaced = placedShipTypes.has(spec.type);
                        const isSelected = selectedShipType === spec.type;
                        const className = [
                          "ship-chip",
                          isSelected ? "selected" : "",
                          isPlaced ? "placed" : ""
                        ]
                          .filter(Boolean)
                          .join(" ");
                        return (
                          <button
                            key={spec.type}
                            type="button"
                            className={className}
                            disabled={isPlaced}
                            onClick={() => selectShip(spec.type)}
                          >
                            {spec.type} ({spec.length})
                          </button>
                        );
                      })}
                    </div>
                    {status ? <p className="notice">{status}</p> : null}
                    <button
                      type="button"
                      className="button"
                      disabled={!allShipsPlaced}
                      onClick={handleSubmitPlacedShips}
                    >
                      Submit ships
                    </button>
                  </section>

                  <div className="placement-grid-section">
                    <GridCard
                      title="Place your fleet"
                      legend={
                        <div className="legend">
                          <span>
                            <i style={{ background: "var(--ship)" }} /> Placed ship
                          </span>
                          <span>
                            <i style={{ background: "var(--accent)" }} /> Start cell
                          </span>
                          <span>
                            <i style={{ background: "var(--salvo)" }} /> Possible end
                          </span>
                        </div>
                      }
                    >
                      <PlacementGrid
                        occupiedCells={occupiedPlacementCells}
                        startCell={startCell}
                        possibleEnds={possibleEndPlacements}
                        onCellClick={handlePlacementCellClick}
                      />
                    </GridCard>
                  </div>
                </>
              ) : (
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
                        <span>
                          <i style={{ background: "var(--accent)" }} /> Selected for next salvo
                        </span>
                      </div>
                    }
                  >
                    <p className="notice">
                      Current turn: <strong>{currentTurn}</strong>
                    </p>
                    <p className="notice">
                      Selected shots: {selectedSalvoCells.length}/{MAX_SALVO_SHOTS}
                    </p>
                    {selectedSalvoCells.length >= MAX_SALVO_SHOTS ? (
                      <p className="notice">Maximum shots selected for this turn.</p>
                    ) : null}
                    <SalvoTargetGrid
                      playerTurns={playerTurns}
                      selectedSalvoSet={selectedSalvoSet}
                      onCellClick={toggleSalvoCell}
                    />
                    <div className="salvo-actions">
                      <button
                        type="button"
                        className="button"
                        disabled={selectedSalvoCells.length === 0}
                        onClick={handleSubmitSalvo}
                      >
                        Done
                      </button>
                    </div>
                  </GridCard>
                </div>
              )}
            </>
          ) : (
            <p className="notice">Loading game data...</p>
          )}
        </section>
      </AppShell>
    </>
  );
}

function GridCard({ title, legend, renderCell, children }) {
  return (
    <div className="card">
      <div className="card-header">
        <h2>{title}</h2>
      </div>
      {legend}
      {children || <Grid renderCell={renderCell} />}
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

function PlacementGrid({ occupiedCells, startCell, possibleEnds, onCellClick }) {
  return (
    <table className="grid placement-grid">
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
              const classNames = ["cell"];

              if (occupiedCells.has(cellId)) {
                classNames.push("ship");
              }
              if (startCell === cellId) {
                classNames.push("placement-start");
              } else if (possibleEnds.has(cellId)) {
                classNames.push("placement-option");
              }

              return (
                <td
                  key={cellId}
                  className={classNames.join(" ")}
                  onClick={() => onCellClick(cellId)}
                >
                  {startCell === cellId ? "S" : possibleEnds.has(cellId) ? "E" : ""}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function SalvoTargetGrid({ playerTurns, selectedSalvoSet, onCellClick }) {
  return (
    <table className="grid salvo-target-grid">
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
              const turn = playerTurns.get(cellId);
              const classNames = ["cell"];
              let label = "";
              let isLocked = false;

              if (turn !== undefined) {
                classNames.push("salvo", "locked");
                label = String(turn);
                isLocked = true;
              } else if (selectedSalvoSet.has(cellId)) {
                classNames.push("salvo-selected");
                label = "X";
              } else {
                classNames.push("targetable");
              }

              return (
                <td
                  key={cellId}
                  className={classNames.join(" ")}
                  onClick={isLocked ? undefined : () => onCellClick(cellId)}
                >
                  {label}
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

function getCurrentTurn(data, gamePlayerId) {
  if (!data || typeof data.salvoes !== "object" || data.salvoes === null) {
    return 1;
  }

  const ownTurns = Object.keys(data.salvoes[String(gamePlayerId)] || {}).filter(
    key => Number.isFinite(Number(key))
  );
  const opponentTurnCounts = Object.entries(data.salvoes)
    .filter(([gpId]) => Number(gpId) !== gamePlayerId)
    .map(([, salvoesByTurn]) =>
      Object.keys(salvoesByTurn || {}).filter(key => Number.isFinite(Number(key))).length
    );

  const ownCount = ownTurns.length;
  const opponentCount = opponentTurnCounts.length > 0 ? Math.max(...opponentTurnCounts) : 0;
  return Math.min(ownCount, opponentCount) + 1;
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

function computePossiblePlacements(startCell, shipLength, occupiedCells) {
  const result = new Map();
  const start = cellToCoord(startCell);
  if (!start || occupiedCells.has(startCell)) {
    return result;
  }

  const directions = [
    { dr: -1, dc: 0 },
    { dr: 1, dc: 0 },
    { dr: 0, dc: -1 },
    { dr: 0, dc: 1 }
  ];

  directions.forEach(({ dr, dc }) => {
    const locations = [];
    for (let step = 0; step < shipLength; step += 1) {
      const rowIndex = start.row + dr * step;
      const colIndex = start.col + dc * step;
      if (!isInsideGrid(rowIndex, colIndex)) {
        return;
      }
      const location = coordToCell(rowIndex, colIndex);
      if (occupiedCells.has(location)) {
        return;
      }
      locations.push(location);
    }

    const endCell = locations[locations.length - 1];
    result.set(endCell, locations);
  });

  return result;
}

function isInsideGrid(rowIndex, colIndex) {
  return rowIndex >= 0 && rowIndex < rows.length && colIndex >= 0 && colIndex < cols.length;
}

function cellToCoord(cell) {
  if (!cell || cell.length < 2) return null;
  const rowLetter = cell[0];
  const rowIndex = rows.indexOf(rowLetter);
  const colNumber = Number(cell.slice(1));
  const colIndex = cols.indexOf(colNumber);
  if (rowIndex === -1 || colIndex === -1) return null;
  return { row: rowIndex, col: colIndex };
}

function coordToCell(rowIndex, colIndex) {
  return `${rows[rowIndex]}${cols[colIndex]}`;
}

const root = createRoot(document.getElementById("root"));
root.render(<GameViewPage />);
