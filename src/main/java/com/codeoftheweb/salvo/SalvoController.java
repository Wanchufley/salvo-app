package com.codeoftheweb.salvo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@RequestMapping("/games")
	public Map<String, Object> getGames(Authentication authentication) {
		Map<String, Object> dto = new LinkedHashMap<>();

		if (authentication != null && authentication.isAuthenticated()) {
			Player player = playerRepository.findByUserName(authentication.getName());
			if (player != null) {
				Map<String, Object> playerDto = new LinkedHashMap<>();
				playerDto.put("id", player.getId());
				playerDto.put("username", player.getUserName());
				dto.put("player", playerDto);
			}
		}

		List<Map<String, Object>> games = gameRepository.findAll().stream()
			.map(this::makeGameDTO)
			.toList();
		dto.put("games", games);

		return dto;
	}

	@GetMapping("/player")
	public ResponseEntity<Map<String, Object>> getCurrentPlayer(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.ok(Map.of());
		}
		Player player = playerRepository.findByUserName(authentication.getName());
		if (player == null) {
			return ResponseEntity.ok(Map.of());
		}
		return ResponseEntity.ok(makePlayerDTO(player));
	}

	@PostMapping("/players")
	public ResponseEntity<Map<String, Object>> register(
		@RequestBody(required = false) String body,
		@RequestParam(required = false) String username,
		@RequestParam(required = false) String password
	) {
		String bodyUserName = null;
		String bodyPassword = null;

		if (body != null && !body.isBlank()) {
			try {
				Map<String, String> json = objectMapper.readValue(body, Map.class);
				bodyUserName = json.get("username");
				bodyPassword = json.get("password");
			} catch (IOException ignored) {
				// Fall back to request parameters if JSON cannot be parsed
			}
		}

		String effectiveUserName = bodyUserName != null ? bodyUserName : username;
		String effectivePassword = bodyPassword != null ? bodyPassword : password;

		String normalizedUserName = normalizeUserName(effectiveUserName);
		String normalizedPassword = normalizePassword(effectivePassword);

		if (normalizedUserName == null || normalizedPassword == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Invalid username or password"));
		}

		Player existing = playerRepository.findByUserName(normalizedUserName);
		if (existing != null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Name in use"));
		}

		Player player = new Player(normalizedUserName, passwordEncoder.encode(normalizedPassword));
		playerRepository.save(player);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", player.getId());
		body.put("name", player.getUserName());

		return ResponseEntity.status(HttpStatus.CREATED).body(body);
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
