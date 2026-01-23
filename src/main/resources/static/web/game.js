const rows = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"];
const cols = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export async function renderGameView() {
  const shipGridContainer = document.getElementById("ship-grid");
  const salvoGridContainer = document.getElementById("salvo-grid");
  const infoContainer = document.getElementById("game-info");
  if (!shipGridContainer || !salvoGridContainer || !infoContainer) return;

  const gamePlayerId = getGamePlayerId();
  if (!gamePlayerId) {
    infoContainer.textContent = "Missing game player id in URL (?gp=1).";
    return;
  }

  try {
    const response = await fetch(`/api/game_view/${gamePlayerId}`);
    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }

    const data = await response.json();
    infoContainer.textContent = formatGameInfo(data);
    shipGridContainer.replaceChildren(buildShipGrid(data));
    salvoGridContainer.replaceChildren(buildSalvoGrid(data));
  } catch (err) {
    console.error("Failed to load game view:", err);
    infoContainer.textContent = "Failed to load game data.";
  }
}

function getGamePlayerId() {
  const params = new URLSearchParams(window.location.search);
  const raw = params.get("gp");
  if (!raw) return null;
  const id = Number(raw);
  return Number.isFinite(id) ? id : null;
}

function buildGrid(cellRenderer) {
  const table = document.createElement("table");
  table.className = "grid";

  const headerRow = document.createElement("tr");
  headerRow.appendChild(document.createElement("th"));
  cols.forEach(col => {
    const th = document.createElement("th");
    th.textContent = col;
    headerRow.appendChild(th);
  });
  table.appendChild(headerRow);

  rows.forEach(row => {
    const tr = document.createElement("tr");
    const rowHeader = document.createElement("th");
    rowHeader.textContent = row;
    tr.appendChild(rowHeader);

    cols.forEach(col => {
      const td = document.createElement("td");
      const cellId = `${row}${col}`;
      cellRenderer(cellId, td);
      tr.appendChild(td);
    });

    table.appendChild(tr);
  });

  return table;
}

function buildShipGrid(data) {
  const shipCells = new Set(collectShipLocations(data));
  const hitTurns = collectOpponentHitTurns(data);
  return buildGrid((cellId, td) => {
    const hasShip = shipCells.has(cellId);
    if (hasShip) {
      td.classList.add("ship");
    }
    const hitTurn = hitTurns.get(cellId);
    if (hitTurn !== undefined) {
      td.classList.add("hit");
      td.textContent = String(hitTurn);
      return;
    }
    if (hasShip) {
      td.textContent = "S";
    }
  });
}

function buildSalvoGrid(data) {
  const salvoTurns = collectPlayerSalvoTurns(data);
  return buildGrid((cellId, td) => {
    const turn = salvoTurns.get(cellId);
    if (turn !== undefined) {
      td.classList.add("salvo");
      td.textContent = String(turn);
    }
  });
}

function collectShipLocations(data) {
  if (!data || !Array.isArray(data.ships)) return [];
  return data.ships.flatMap(ship => ship.locations || []);
}

function collectPlayerSalvoTurns(data) {
  const playerId = String(data.gamePlayerId);
  const salvoesByPlayer = getSalvoesByPlayer(data);
  const salvoesByTurn = salvoesByPlayer[playerId] || {};
  return buildLocationTurnMap(salvoesByTurn);
}

function collectOpponentHitTurns(data) {
  const playerId = String(data.gamePlayerId);
  const salvoesByPlayer = getSalvoesByPlayer(data);
  const hitTurns = new Map();
  Object.entries(salvoesByPlayer).forEach(([gpId, salvoesByTurn]) => {
    if (gpId === playerId) return;
    const turnMap = buildLocationTurnMap(salvoesByTurn);
    turnMap.forEach((turn, location) => {
      const existing = hitTurns.get(location);
      if (existing === undefined || turn < existing) {
        hitTurns.set(location, turn);
      }
    });
  });
  return hitTurns;
}

function getSalvoesByPlayer(data) {
  if (!data || typeof data.salvoes !== "object" || data.salvoes === null) {
    return {};
  }
  return data.salvoes;
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

function formatGameInfo(data) {
  const gamePlayers = Array.isArray(data.gamePlayers) ? data.gamePlayers : [];
  const viewer = gamePlayers.find(gp => gp.id === data.gamePlayerId);
  const opponents = gamePlayers.filter(gp => gp.id !== data.gamePlayerId);

  const viewerEmail = viewer && viewer.player ? viewer.player.email : "Unknown";
  const opponentEmails = opponents
    .map(gp => (gp.player ? gp.player.email : "Unknown"))
    .join(" vs ");

  if (!opponentEmails) {
    return `${viewerEmail} (you) - waiting for opponent`;
  }

  return `${viewerEmail} (you) vs ${opponentEmails}`;
}
