const rows = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"];
const cols = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export async function renderGameView() {
  const gridContainer = document.getElementById("grid");
  const infoContainer = document.getElementById("game-info");
  if (!gridContainer || !infoContainer) return;

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
    gridContainer.replaceChildren(buildGrid(data));
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

function buildGrid(data) {
  const table = document.createElement("table");
  table.className = "grid";

  const shipCells = new Set(collectShipLocations(data));

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
      if (shipCells.has(cellId)) {
        td.classList.add("ship");
        td.textContent = "S";
      }
      tr.appendChild(td);
    });

    table.appendChild(tr);
  });

  return table;
}

function collectShipLocations(data) {
  if (!data || !Array.isArray(data.ships)) return [];
  return data.ships.flatMap(ship => ship.locations || []);
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
