package com.codeoftheweb.salvo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SalvoController {

	private static final int FLEET_SIZE = 5;
	private static final int MAX_SALVO_SHOTS = 5;

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
	private ScoreRepository scoreRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FirebaseRealtimeSyncService firebaseRealtimeSyncService;

	@Autowired
	private GameRulesService gameRulesService;

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
		Player player = getAuthenticatedPlayer(authentication);
		if (player == null) {
			return unauthorized();
		}

		Game game = new Game();
		gameRepository.save(game);

		GamePlayer gamePlayer = new GamePlayer(game, player);
		gamePlayerRepository.save(gamePlayer);
		firebaseRealtimeSyncService.publishGameChange(game, "GAME_CREATED", gamePlayer.getId());

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("gpid", gamePlayer.getId()));
	}

	@PostMapping({"/games/{gameId}/players", "/game/{gameId}/players"})
	public ResponseEntity<Map<String, Object>> joinGame(
		@PathVariable long gameId,
		Authentication authentication
	) {
		Player player = getAuthenticatedPlayer(authentication);
		if (player == null) {
			return unauthorized();
		}

		Optional<Game> gameOptional = gameRepository.findById(gameId);
		if (gameOptional.isEmpty()) {
			return forbidden("No such game");
		}

		Game game = gameOptional.get();

		boolean alreadyMember = game.getGamePlayers().stream()
			.anyMatch(gp -> gp.getPlayer().getId() == player.getId());
		if (alreadyMember) {
			return forbidden("Already in game");
		}

		if (game.getGamePlayers().size() >= 2) {
			return forbidden("Game is full");
		}

		GamePlayer gamePlayer = new GamePlayer(game, player);
		gamePlayerRepository.save(gamePlayer);
		firebaseRealtimeSyncService.publishGameChange(game, "PLAYER_JOINED", gamePlayer.getId());

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("gpid", gamePlayer.getId()));
	}

	@GetMapping("/player")
	public ResponseEntity<Map<String, Object>> getCurrentPlayer(Authentication authentication) {
		Player player = getAuthenticatedPlayer(authentication);
		return player == null
			? ResponseEntity.ok(Map.of())
			: ResponseEntity.ok(makePlayerDTO(player));
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
				// Fall back to request parameters if JSON cannot be parsed.
			}
		}

		String effectiveUserName = bodyUserName != null ? bodyUserName : username;
		String effectivePassword = bodyPassword != null ? bodyPassword : password;

		String normalizedUserName = normalizeUserName(effectiveUserName);
		String normalizedPassword = normalizePassword(effectivePassword);

		if (normalizedUserName == null || normalizedPassword == null) {
			return forbidden("Invalid username or password");
		}

		Player existing = playerRepository.findByUserName(normalizedUserName);
		if (existing != null) {
			return forbidden("Name in use");
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
		Player currentPlayer = getAuthenticatedPlayer(authentication);
		if (currentPlayer == null) {
			return unauthorized();
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return forbidden("No such game player");
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		if (gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return unauthorized();
		}

		ensureScoresRecorded(gamePlayer.getGame());
		return ResponseEntity.ok(makeGameViewDTO(gamePlayer));
	}

	@PostMapping("/games/players/{gamePlayerId}/ships")
	public ResponseEntity<Map<String, Object>> placeShips(
		@PathVariable long gamePlayerId,
		@RequestBody(required = false) List<Ship> ships,
		Authentication authentication
	) {
		Player currentPlayer = getAuthenticatedPlayer(authentication);
		if (currentPlayer == null) {
			return unauthorized();
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return unauthorized();
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		if (gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return unauthorized();
		}

		if (!gamePlayer.getShips().isEmpty()) {
			return forbidden("Ships already placed");
		}

		if (ships == null || ships.size() != FLEET_SIZE) {
			return forbidden("You must place exactly 5 ships");
		}

		if (hasDuplicateShipTypes(ships)) {
			return forbidden("Ship types must be unique");
		}

		if (hasInvalidOrOverlappingShipLocations(ships)) {
			return forbidden("Ships must occupy unique board locations");
		}

		for (Ship ship : ships) {
			gamePlayer.addShip(ship);
		}
		shipRepository.saveAll(gamePlayer.getShips());
		firebaseRealtimeSyncService.publishGameChange(gamePlayer.getGame(), "SHIPS_PLACED", gamePlayer.getId());

		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/games/players/{gamePlayerId}/salvos")
	public ResponseEntity<Map<String, Object>> placeSalvo(
		@PathVariable long gamePlayerId,
		@RequestBody(required = false) Salvo salvoRequest,
		Authentication authentication
	) {
		Player currentPlayer = getAuthenticatedPlayer(authentication);
		if (currentPlayer == null) {
			return unauthorized();
		}

		Optional<GamePlayer> gamePlayerOptional = gamePlayerRepository.findById(gamePlayerId);
		if (gamePlayerOptional.isEmpty()) {
			return unauthorized();
		}

		GamePlayer gamePlayer = gamePlayerOptional.get();
		if (gamePlayer.getPlayer().getId() != currentPlayer.getId()) {
			return unauthorized();
		}

		Game game = gamePlayer.getGame();
		ensureScoresRecorded(game);
		if (isGameOver(gamePlayer, getGamePlayersForGame(game.getId()))) {
			return forbidden("Game is over");
		}

		GamePlayer opponent = gameRulesService.getOpponent(gamePlayer, getGamePlayersForGame(game.getId()));
		if (opponent == null) {
			return forbidden("Waiting for an opponent");
		}
		if (gamePlayer.getShips().isEmpty()) {
			return forbidden("Place your ships first");
		}
		if (opponent.getShips().isEmpty()) {
			return forbidden("Waiting for opponent to place ships");
		}

		int expectedTurn = calculateCurrentTurn(gamePlayer);
		Integer requestedTurn = salvoRequest == null ? null : salvoRequest.getTurn();
		if (requestedTurn == null || requestedTurn != expectedTurn) {
			return forbidden("Invalid turn");
		}

		if (gamePlayer.getSalvoes().stream().anyMatch(existingSalvo -> existingSalvo.getTurn() == expectedTurn)) {
			return forbidden("Salvo already submitted for this turn");
		}

		List<String> locations = salvoRequest.getLocations() == null
			? List.of()
			: new ArrayList<>(salvoRequest.getLocations());
		if (locations.isEmpty() || locations.size() > MAX_SALVO_SHOTS) {
			return forbidden("A salvo must contain between 1 and 5 shots");
		}

		Set<String> uniqueLocations = new LinkedHashSet<>(locations);
		if (uniqueLocations.size() != locations.size()) {
			return forbidden("Salvo shots must be unique");
		}

		Salvo salvo = new Salvo();
		salvo.setTurn(expectedTurn);
		salvo.setLocations(locations);
		gamePlayer.addSalvo(salvo);
		salvoRepository.save(salvo);

		ensureScoresRecorded(game);
		firebaseRealtimeSyncService.publishGameChange(game, "SALVO_PLACED", gamePlayer.getId());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	private Map<String, Object> makeGameDTO(Game game) {
		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("id", game.getId());
		dto.put("created", game.getCreationDate());
		dto.put("gamePlayers", game.getGamePlayers().stream().map(this::makeGamePlayerDTO).toList());
		dto.put("scores", game.getScores().stream().map(this::makeScoreDTO).toList());
		return dto;
	}

	private Map<String, Object> makeGameViewDTO(GamePlayer currentGamePlayer) {
		Game game = gameRepository.findById(currentGamePlayer.getGame().getId())
			.orElse(currentGamePlayer.getGame());
		List<GamePlayer> gamePlayers = getGamePlayersForGame(game.getId());
		String gameState = determineGameState(currentGamePlayer, gamePlayers);

		Map<String, Object> dto = new LinkedHashMap<>();
		dto.put("gameId", game.getId());
		dto.put("gamePlayerId", currentGamePlayer.getId());
		dto.put("created", game.getCreationDate());
		dto.put("gamePlayers", gamePlayers.stream().map(this::makeGamePlayerDTO).toList());
		dto.put("ships", currentGamePlayer.getShips().stream().map(this::makeShipDTO).toList());
		dto.put("salvoes", gameRulesService.makeVisibleSalvoesDTO(currentGamePlayer, gamePlayers));
		dto.put("salvoCellStates", gameRulesService.makeSalvoCellStatesDTO(currentGamePlayer, gamePlayers));
		dto.put("hits", gameRulesService.makeHitsHistoryDTO(currentGamePlayer, gamePlayers));
		dto.put("scores", game.getScores().stream().map(this::makeScoreDTO).toList());
		dto.put("gameState", gameState);
		dto.put("currentTurn", gameRulesService.calculateCurrentTurn(currentGamePlayer));
		dto.put("liveSyncUrl", firebaseRealtimeSyncService.getGameStreamUrl(game.getId()));
		dto.put("canPlaceShips", GameRulesService.GAME_STATE_PLACE_SHIPS.equals(gameState));
		dto.put(
			"canFireSalvo",
			GameRulesService.GAME_STATE_STARTING_SOON.equals(gameState)
				|| GameRulesService.GAME_STATE_WAITING_FOR_YOUR_SALVO.equals(gameState)
		);
		dto.put("isGameOver", gameRulesService.isTerminalState(gameState));
		dto.put("completedTurnCount", gameRulesService.getCompletedTurnCount(currentGamePlayer, gamePlayers));
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

	private Player getAuthenticatedPlayer(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return playerRepository.findByUserName(authentication.getName());
	}

	private List<GamePlayer> getGamePlayersForGame(long gameId) {
		return gamePlayerRepository.findAll().stream()
			.filter(gamePlayer -> gamePlayer.getGame() != null && gamePlayer.getGame().getId() == gameId)
			.sorted(Comparator.comparingLong(GamePlayer::getId))
			.toList();
	}

	private int calculateCurrentTurn(GamePlayer gamePlayer) {
		return gameRulesService.calculateCurrentTurn(gamePlayer);
	}

	private String determineGameState(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		return gameRulesService.determineGameState(currentGamePlayer, gamePlayers);
	}

	private boolean isGameOver(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		return gameRulesService.isGameOver(currentGamePlayer, gamePlayers);
	}

	private void ensureScoresRecorded(Game game) {
		if (scoreRepository.existsByGame_Id(game.getId())) {
			return;
		}

		List<GamePlayer> gamePlayers = getGamePlayersForGame(game.getId());
		if (gamePlayers.size() != 2) {
			return;
		}

		GamePlayer first = gamePlayers.get(0);
		GamePlayer second = gamePlayers.get(1);
		GameRulesService.GameResult firstResult = gameRulesService.getGameResult(first, gamePlayers);
		GameRulesService.GameResult secondResult = gameRulesService.getGameResult(second, gamePlayers);
		if (firstResult == null || secondResult == null) {
			return;
		}

		Date finishDate = new Date();
		scoreRepository.saveAll(List.of(
			new Score(game, first.getPlayer(), getScoreValue(firstResult), finishDate),
			new Score(game, second.getPlayer(), getScoreValue(secondResult), finishDate)
		));
		firebaseRealtimeSyncService.publishGameChange(game, "GAME_FINISHED", null);
	}

	private double getScoreValue(GameRulesService.GameResult result) {
		return gameRulesService.getScoreValue(result);
	}

	private boolean hasDuplicateShipTypes(List<Ship> ships) {
		return gameRulesService.hasDuplicateShipTypes(ships);
	}

	private boolean hasInvalidOrOverlappingShipLocations(List<Ship> ships) {
		return gameRulesService.hasInvalidOrOverlappingShipLocations(ships);
	}

	private ResponseEntity<Map<String, Object>> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(Map.of("error", "Unauthorized"));
	}

	private ResponseEntity<Map<String, Object>> forbidden(String message) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(Map.of("error", message));
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
