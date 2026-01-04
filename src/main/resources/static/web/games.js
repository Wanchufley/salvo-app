document.addEventListener("DOMContentLoaded", async () => {
  const list = document.getElementById("games-list");
  if (!list) return;

  try {
    const response = await fetch("/api/games");
    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }

    const games = await response.json();
    list.replaceChildren(...games.map(renderGame));
  } catch (err) {
    console.error("Failed to load games:", err);
  }
});

function renderGame(game) {
  const li = document.createElement("li");
  const created = new Date(game.created).toLocaleString();
  const players = (game.gamePlayers || [])
    .map(gp => gp.player && gp.player.email)
    .filter(Boolean)
    .join(", ");

  const playersText = players || "No players yet";
  li.textContent = `Game ${game.id} - ${created} - ${playersText}`;
  return li;
}
