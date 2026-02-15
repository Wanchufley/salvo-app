package com.codeoftheweb.salvo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
	private ShipRepository shipRepository;

	@Autowired
	private SalvoRepository salvoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private ObjectMapper objectMapper;

	private final HttpSessionSecurityContextRepository securityContextRepository =
		new HttpSessionSecurityContextRepository();

	@GetMapping("/games")
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

	@PostMapping("/games")
	public ResponseEntity<Map<String, Object>> createGame(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Player player = playerRepository.findByUserName(authentication.getName());
		if (player == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Game game = new Game();
		gameRepository.save(game);

		GamePlayer gamePlayer = new GamePlayer(game, player);
		gamePlayerRepository.save(gamePlayer);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("gpid", gamePlayer.getId());

		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@PostMapping({"/games/{gameId}/players", "/game/{gameId}/players"})
	public ResponseEntity<Map<String, Object>> joinGame(
		@PathVariable long gameId,
		Authentication authentication
	) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Player player = playerRepository.findByUserName(authentication.getName());
		if (player == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Optional<Game> gameOptional = gameRepository.findById(gameId);
		if (gameOptional.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "No such game"));
		}

		Game game = gameOptional.get();

		boolean alreadyMember = game.getGamePlayers().stream()
			.anyMatch(gp -> gp.getPlayer().getId() == player.getId());
		if (alreadyMember) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Already in game"));
		}

		if (game.getGamePlayers().size() >= 2) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Game is full"));
		}

		GamePlayer gamePlayer = new GamePlayer(game, player);
		gamePlayerRepository.save(gamePlayer);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("gpid", gamePlayer.getId());

		return ResponseEntity.status(HttpStatus.CREATED).body(body);
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
		@RequestParam(required = false) String password,
		HttpServletRequest request,
		HttpServletResponse response
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

		UsernamePasswordAuthenticationToken authRequest =
			new UsernamePasswordAuthenticationToken(normalizedUserName, normalizedPassword);
		Authentication authentication = authenticationManager.authenticate(authRequest);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		Map<String, Object> responseBody = new LinkedHashMap<>();
		responseBody.put("id", player.getId());
		responseBody.put("name", player.getUserName());

		return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
	}

	@RequestMapping("/game_view/{gamePlayerId}")
	public ResponseEntity<Map<String, Object>> getGameView(
		@PathVariable long gamePlayerId,
		Authentication authentication
	) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "No such game player"));
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		Player currentPlayer = playerRepository.findByUserName(authentication.getName());
		if (currentPlayer == null || gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		return ResponseEntity.ok(makeGameViewDTO(gamePlayer));
	}

	@PostMapping("/games/players/{gamePlayerId}/ships")
	public ResponseEntity<Map<String, Object>> placeShips(
		@PathVariable long gamePlayerId,
		@RequestBody List<Ship> ships,
		Authentication authentication
	) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		Player currentPlayer = playerRepository.findByUserName(authentication.getName());
		if (currentPlayer == null || gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		if (!gamePlayer.getShips().isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Ships already placed"));
		}

		for (Ship ship : ships) {
			gamePlayer.addShip(ship);
		}
		shipRepository.saveAll(gamePlayer.getShips());

		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/games/players/{gamePlayerId}/salvos")
	public ResponseEntity<Map<String, Object>> placeSalvo(
		@PathVariable long gamePlayerId,
		@RequestBody Salvo salvoRequest,
		Authentication authentication
	) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		Player currentPlayer = playerRepository.findByUserName(authentication.getName());
		if (currentPlayer == null || gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "Unauthorized"));
		}

		int turn = calculateCurrentTurn(gamePlayer);
		List<String> locations = salvoRequest.getLocations() == null
			? List.of()
			: new ArrayList<>(salvoRequest.getLocations());
		Optional<Salvo> existingTurnSalvo = gamePlayer.getSalvoes().stream()
			.filter(existingSalvo -> existingSalvo.getTurn() == turn)
			.findFirst();

		if (existingTurnSalvo.isPresent()) {
			Salvo existingSalvo = existingTurnSalvo.get();
			existingSalvo.setLocations(locations);
			salvoRepository.save(existingSalvo);
		} else {
			Salvo salvo = new Salvo();
			salvo.setTurn(turn);
			salvo.setLocations(locations);
			gamePlayer.addSalvo(salvo);
			salvoRepository.save(salvo);
		}

		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	private int calculateCurrentTurn(GamePlayer gamePlayer) {
		int ownTurnCount = gamePlayer.getSalvoes().size();
		int opponentTurnCount = gamePlayer.getGame().getGamePlayers().stream()
			.filter(otherGamePlayer -> otherGamePlayer.getId() != gamePlayer.getId())
			.mapToInt(otherGamePlayer -> otherGamePlayer.getSalvoes().size())
			.max()
			.orElse(0);
		return Math.min(ownTurnCount, opponentTurnCount) + 1;
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
