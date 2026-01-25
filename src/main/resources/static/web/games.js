document.addEventListener("DOMContentLoaded", async () => {
  const list = document.getElementById("games-list");
  const leaderboardBody = document.querySelector("#leaderboard tbody");
  if (!list || !leaderboardBody) return;

  try {
    const response = await fetch("/api/games");
    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }

    const games = await response.json();
    list.replaceChildren(...games.map(renderGame));
    leaderboardBody.replaceChildren(...renderLeaderboard(games));
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

function renderLeaderboard(games) {
  const statsByPlayer = new Map();
  games.forEach(game => {
    (game.scores || []).forEach(score => {
      if (!score || !score.player) return;
      const playerId = score.player.id;
      if (!statsByPlayer.has(playerId)) {
        statsByPlayer.set(playerId, {
          email: score.player.email,
          wins: 0,
          losses: 0,
          ties: 0
        });
      }
      const stats = statsByPlayer.get(playerId);
      const value = Number(score.score);
      if (value === 1) {
        stats.wins += 1;
      } else if (value === 0.5) {
        stats.ties += 1;
      } else if (value === 0) {
        stats.losses += 1;
      }
    });
  });

  return Array.from(statsByPlayer.values())
    .sort((a, b) => {
      const pointsA = a.wins + a.ties * 0.5;
      const pointsB = b.wins + b.ties * 0.5;
      if (pointsB !== pointsA) return pointsB - pointsA;
      if (b.wins !== a.wins) return b.wins - a.wins;
      return a.losses - b.losses;
    })
    .map(stats => {
      const tr = document.createElement("tr");
      const points = stats.wins + stats.ties * 0.5;
      tr.innerHTML = `
        <td>${stats.email}</td>
        <td>${stats.wins}</td>
        <td>${stats.losses}</td>
        <td>${stats.ties}</td>
        <td>${points.toFixed(1)}</td>
      `;
      return tr;
    });
}
