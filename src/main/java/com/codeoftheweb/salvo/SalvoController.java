package com.codeoftheweb.salvo;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SalvoController {

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private GamePlayerRepository gamePlayerRepository;

	@RequestMapping("/games")
	public List<Map<String, Object>> getGames() {
		return gameRepository.findAll().stream().map(this::makeGameDTO).toList();
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
}
