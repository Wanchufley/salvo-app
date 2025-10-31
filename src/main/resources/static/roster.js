document.addEventListener("DOMContentLoaded", async () => {
  const list = document.getElementById("player-list");
  if (!list) return console.error("UL element not found!");

  try {
    const res = await fetch("/players");
    const data = await res.json();
    const players = data._embedded.players;

    if (!players || !Array.isArray(players)) {
      console.error("No players array found!", data);
      return;
    }

    players.forEach(player => {
      const li = document.createElement("li");
      li.textContent = player.userName;
      list.appendChild(li);
    });

  } catch (err) {
    console.error("Error fetching players:", err);
  }
});

document.getElementById('sendButton').addEventListener('click', async () => {
  const name = document.getElementById('new-player').value.trim();
  if (!name) {
    alert('Please, enter a player name.');
    return;
  }

  try {
    const response = await fetch('/players', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ userName: name })
    });
  
  if (!response.ok) {
    throw new Error(`Server error: ${response.status}`);
  }

  const data = await response.json();
  console.log('Player added:', data);
  alert('Player added succesfully!');
  document.getElementById('new-player').value = '';
  } catch (err) {
    console.error('Error sending player:', err);
    alert('Failed to add player.');
  }
});
