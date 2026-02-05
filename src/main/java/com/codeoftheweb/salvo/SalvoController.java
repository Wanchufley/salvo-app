package com.codeoftheweb.salvo;

import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SalvoController {

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private GamePlayerRepository gamePlayerRepository;

	@Autowired
	private PlayerRepository playerRepository;

	@RequestMapping("/games")
	public List<Map<String, Object>> getGames() {
		return gameRepository.findAll().stream().map(this::makeGameDTO).toList();
	}

	@GetMapping("/player")
	public ResponseEntity<Map<String, Object>> getCurrentPlayer(HttpSession session) {
		Player player = getLoggedPlayer(session);
		if (player == null) {
			return ResponseEntity.status(HttpStatus.OK).body(Map.of());
		}
		return ResponseEntity.ok(makePlayerDTO(player));
	}

	@PostMapping("/players")
	public ResponseEntity<Map<String, Object>> register(
		@RequestParam String username,
		@RequestParam String password,
		HttpSession session
	) {
		String normalizedUserName = normalizeUserName(username);
		String normalizedPassword = normalizePassword(password);

		if (normalizedUserName == null || normalizedPassword == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Invalid username or password"));
		}

		List<Player> existing = playerRepository.findByUserName(normalizedUserName);
		if (!existing.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Username already in use"));
		}

		Player player = new Player(normalizedUserName, normalizedPassword);
		playerRepository.save(player);
		session.setAttribute("playerId", player.getId());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", player.getId());
		body.put("name", player.getUserName());

		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(
		@RequestParam String username,
		@RequestParam String password,
		HttpSession session
	) {
		String normalizedUserName = normalizeUserName(username);
		String normalizedPassword = normalizePassword(password);

		if (normalizedUserName == null || normalizedPassword == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Invalid username or password"));
		}

		List<Player> players = playerRepository.findByUserName(normalizedUserName);
		if (players.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "No such user"));
		}

		Player player = players.getFirst();
		if (player.getPassword() == null || !player.getPassword().equals(normalizedPassword)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Password does not match"));
		}

		session.setAttribute("playerId", player.getId());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", player.getId());
		body.put("email", player.getUserName());

		return ResponseEntity.ok(body);
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
		session.invalidate();
		return ResponseEntity.ok(Map.of());
	}

	@RequestMapping("/game_view/{gamePlayerId}")
	public Map<String, Object> getGameView(@PathVariable long gamePlayerId) {
		Optional<GamePlayer> gamePlayer = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayer.isEmpty()) {
			return Map.of();
		}

		return makeGameViewDTO(gamePlayer.get());
	}

	private Map<String, Object> makeGameDTO(Game game) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("id", game.getId());
		dto.put("created", game.getCreationDate());
		dto.put("gamePlayers", game.getGamePlayers().stream().map(this::makeGamePlayerDTO).toList());
		dto.put("scores", game.getScores().stream().map(this::makeScoreDTO).toList());
		return dto;
	}

	private Map<String, Object> makeGameViewDTO(GamePlayer gamePlayer) {
		Map<String, Object> dto = new LinkedHashMap<>();
		Game game = gamePlayer.getGame();
		dto.put("gameId", game.getId());
		dto.put("gamePlayerId", gamePlayer.getId());
		dto.put("created", game.getCreationDate());
		dto.put("gamePlayers", game.getGamePlayers().stream().map(this::makeGamePlayerDTO).toList());
		dto.put("ships", gamePlayer.getShips().stream().map(this::makeShipDTO).toList());
		dto.put("salvoes", makeSalvoesDTO(game));
		return dto;
	}

	private Map<String, Object> makeGamePlayerDTO(GamePlayer gamePlayer) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("id", gamePlayer.getId());
		dto.put("player", makePlayerDTO(gamePlayer.getPlayer()));
		return dto;
	}

	private Map<String, Object> makeShipDTO(Ship ship) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("type", ship.getShipType());
		dto.put("locations", ship.getLocations());
		return dto;
	}

	private Map<Long, Map<Integer, List<String>>> makeSalvoesDTO(Game game) {
		Map<Long, Map<Integer, List<String>>> salvoesByPlayer = new LinkedHashMap<>();
		for (GamePlayer gamePlayer : game.getGamePlayers()) {
			Map<Integer, List<String>> salvoesByTurn = new LinkedHashMap<>();
			gamePlayer.getSalvoes().stream()
				.sorted(Comparator.comparingInt(Salvo::getTurn))
				.forEach(salvo -> salvoesByTurn.put(salvo.getTurn(), salvo.getLocations()));
			salvoesByPlayer.put(gamePlayer.getId(), salvoesByTurn);
		}
		return salvoesByPlayer;
	}

	private Map<String, Object> makeScoreDTO(Score score) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("score", score.getScore());
		dto.put("finishDate", score.getFinishDate());
		dto.put("player", makePlayerDTO(score.getPlayer()));
		return dto;
	}

	private Map<String, Object> makePlayerDTO(Player player) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("id", player.getId());
		dto.put("email", player.getUserName());
		return dto;
	}

	private Player getLoggedPlayer(HttpSession session) {
		Object playerId = session.getAttribute("playerId");
		if (!(playerId instanceof Long id)) {
			return null;
		}
		return playerRepository.findById(id).orElse(null);
	}

	private String normalizeUserName(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || trimmed.contains(" ") || !trimmed.contains("@")) {
			return null;
		}
		return trimmed;
	}

	private String normalizePassword(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || trimmed.contains(" ")) {
			return null;
		}
		return trimmed;
	}
}
