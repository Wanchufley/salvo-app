package com.codeoftheweb.salvo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GameRulesService {

	public static final String GAME_STATE_PLACE_SHIPS = "PLACE_SHIPS";
	public static final String GAME_STATE_WAITING_FOR_OPPONENT = "WAITING_FOR_OPPONENT";
	public static final String GAME_STATE_WAITING_FOR_OPPONENT_SHIPS = "WAITING_FOR_OPPONENT_SHIPS";
	public static final String GAME_STATE_STARTING_SOON = "STARTING_SOON";
	public static final String GAME_STATE_WAITING_FOR_YOUR_SALVO = "WAITING_FOR_YOUR_SALVO";
	public static final String GAME_STATE_WAITING_FOR_OPPONENT_SALVO = "WAITING_FOR_OPPONENT_SALVO";
	public static final String GAME_STATE_GAME_OVER_WIN = "GAME_OVER_WIN";
	public static final String GAME_STATE_GAME_OVER_LOSS = "GAME_OVER_LOSS";
	public static final String GAME_STATE_GAME_OVER_TIE = "GAME_OVER_TIE";

	public Map<Long, Map<Integer, List<String>>> makeVisibleSalvoesDTO(
		GamePlayer currentGamePlayer,
		List<GamePlayer> gamePlayers
	) {
		int visibleOpponentTurn = currentGamePlayer.getSalvoes().size();
		Map<Long, Map<Integer, List<String>>> salvoesByPlayer = new LinkedHashMap<>();

		for (GamePlayer gamePlayer : gamePlayers) {
			Map<Integer, List<String>> salvoesByTurn = new LinkedHashMap<>();
			gamePlayer.getSalvoes().stream()
				.filter(salvo -> gamePlayer.getId() == currentGamePlayer.getId() || salvo.getTurn() <= visibleOpponentTurn)
				.sorted(Comparator.comparingInt(Salvo::getTurn))
				.forEach(salvo -> salvoesByTurn.put(salvo.getTurn(), salvo.getLocations()));
			salvoesByPlayer.put(gamePlayer.getId(), salvoesByTurn);
		}

		return salvoesByPlayer;
	}

	public Map<Integer, Map<String, Object>> makeHitsHistoryDTO(
		GamePlayer currentGamePlayer,
		List<GamePlayer> gamePlayers
	) {
		Map<Integer, Map<String, Object>> history = new LinkedHashMap<>();
		GamePlayer opponent = getOpponent(currentGamePlayer, gamePlayers);
		if (opponent == null) {
			return history;
		}

		int completedTurnCount = getCompletedTurnCount(currentGamePlayer, gamePlayers);
		for (int turn = 1; turn <= completedTurnCount; turn++) {
			Map<String, Object> turnHistory = new LinkedHashMap<>();
			turnHistory.put("self", makeTurnDamageDTO(currentGamePlayer, opponent, turn));
			turnHistory.put("opponent", makeTurnDamageDTO(opponent, currentGamePlayer, turn));
			history.put(turn, turnHistory);
		}

		return history;
	}

	public Map<String, Map<String, String>> makeSalvoCellStatesDTO(
		GamePlayer currentGamePlayer,
		List<GamePlayer> gamePlayers
	) {
		GamePlayer opponent = getOpponent(currentGamePlayer, gamePlayers);
		Map<String, String> selfStates = new LinkedHashMap<>();
		Map<String, String> opponentStates = new LinkedHashMap<>();

		if (opponent != null) {
			selfStates.putAll(makeShotOutcomeMap(currentGamePlayer, opponent, currentGamePlayer.getSalvoes().size()));
			opponentStates.putAll(makeShotOutcomeMap(opponent, currentGamePlayer, currentGamePlayer.getSalvoes().size()));
		}

		Map<String, Map<String, String>> dto = new LinkedHashMap<>();
		dto.put("self", selfStates);
		dto.put("opponent", opponentStates);
		return dto;
	}

	public Map<String, Object> makeTurnDamageDTO(
		GamePlayer attacker,
		GamePlayer defender,
		int turn
	) {
		Map<String, Object> dto = new LinkedHashMap<>();
		Set<String> hitLocationsBeforeTurn = getHitLocationsThroughTurn(attacker, defender, turn - 1);
		Set<String> hitLocationsThroughTurn = getHitLocationsThroughTurn(attacker, defender, turn);
		Map<String, Integer> hits = new LinkedHashMap<>();
		List<String> sunk = new ArrayList<>();

		for (Ship ship : defender.getShips().stream()
			.sorted(Comparator.comparing(Ship::getShipType))
			.toList()) {
			int newHits = (int) ship.getLocations().stream()
				.filter(hitLocationsThroughTurn::contains)
				.filter(location -> !hitLocationsBeforeTurn.contains(location))
				.count();
			if (newHits > 0) {
				hits.put(ship.getShipType(), newHits);
			}

			boolean wasSunkBeforeTurn = ship.getLocations().stream().allMatch(hitLocationsBeforeTurn::contains);
			boolean isSunkAfterTurn = ship.getLocations().stream().allMatch(hitLocationsThroughTurn::contains);
			if (!wasSunkBeforeTurn && isSunkAfterTurn) {
				sunk.add(ship.getShipType());
			}
		}

		dto.put("hitCount", hits.values().stream().mapToInt(Integer::intValue).sum());
		dto.put("hits", hits);
		dto.put("sunk", sunk);
		dto.put("shipsAfloat", countShipsAfloat(defender, hitLocationsThroughTurn));
		return dto;
	}

	public Set<String> getHitLocationsThroughTurn(GamePlayer attacker, GamePlayer defender, int maxTurn) {
		if (maxTurn <= 0) {
			return Set.of();
		}

		Set<String> defenderLocations = defender.getShips().stream()
			.flatMap(ship -> ship.getLocations().stream())
			.collect(LinkedHashSet::new, Set::add, Set::addAll);

		return attacker.getSalvoes().stream()
			.filter(salvo -> salvo.getTurn() <= maxTurn)
			.sorted(Comparator.comparingInt(Salvo::getTurn))
			.flatMap(salvo -> salvo.getLocations().stream())
			.filter(defenderLocations::contains)
			.collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	public int countShipsAfloat(GamePlayer defender, Set<String> hitLocations) {
		return (int) defender.getShips().stream()
			.filter(ship -> ship.getLocations().stream().anyMatch(location -> !hitLocations.contains(location)))
			.count();
	}

	public Ship findShipAtLocation(GamePlayer defender, String location) {
		return defender.getShips().stream()
			.filter(ship -> ship.getLocations().contains(location))
			.findFirst()
			.orElse(null);
	}

	public GamePlayer getOpponent(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		return gamePlayers.stream()
			.filter(gamePlayer -> gamePlayer.getId() != currentGamePlayer.getId())
			.findFirst()
			.orElse(null);
	}

	public int getCompletedTurnCount(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		GamePlayer opponent = getOpponent(currentGamePlayer, gamePlayers);
		if (opponent == null) {
			return 0;
		}
		return Math.min(currentGamePlayer.getSalvoes().size(), opponent.getSalvoes().size());
	}

	public int calculateCurrentTurn(GamePlayer gamePlayer) {
		int ownTurnCount = gamePlayer.getSalvoes().size();
		int opponentTurnCount = gamePlayer.getGame().getGamePlayers().stream()
			.filter(otherGamePlayer -> otherGamePlayer.getId() != gamePlayer.getId())
			.mapToInt(otherGamePlayer -> otherGamePlayer.getSalvoes().size())
			.max()
			.orElse(0);
		return Math.min(ownTurnCount, opponentTurnCount) + 1;
	}

	public String determineGameState(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		GamePlayer opponent = getOpponent(currentGamePlayer, gamePlayers);
		if (opponent == null) {
			return currentGamePlayer.getShips().isEmpty()
				? GAME_STATE_PLACE_SHIPS
				: GAME_STATE_WAITING_FOR_OPPONENT;
		}

		GameResult result = getGameResult(currentGamePlayer, gamePlayers);
		if (result != null) {
			return switch (result) {
				case WIN -> GAME_STATE_GAME_OVER_WIN;
				case LOSS -> GAME_STATE_GAME_OVER_LOSS;
				case TIE -> GAME_STATE_GAME_OVER_TIE;
			};
		}

		if (currentGamePlayer.getShips().isEmpty()) {
			return GAME_STATE_PLACE_SHIPS;
		}
		if (opponent.getShips().isEmpty()) {
			return GAME_STATE_WAITING_FOR_OPPONENT_SHIPS;
		}

		int ownTurnCount = currentGamePlayer.getSalvoes().size();
		int opponentTurnCount = opponent.getSalvoes().size();

		if (ownTurnCount == 0 && opponentTurnCount == 0) {
			return GAME_STATE_STARTING_SOON;
		}
		if (ownTurnCount > opponentTurnCount) {
			return GAME_STATE_WAITING_FOR_OPPONENT_SALVO;
		}
		return GAME_STATE_WAITING_FOR_YOUR_SALVO;
	}

	public boolean isGameOver(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		return getGameResult(currentGamePlayer, gamePlayers) != null;
	}

	public GameResult getGameResult(GamePlayer currentGamePlayer, List<GamePlayer> gamePlayers) {
		GamePlayer opponent = getOpponent(currentGamePlayer, gamePlayers);
		if (opponent == null || currentGamePlayer.getShips().isEmpty() || opponent.getShips().isEmpty()) {
			return null;
		}

		int completedTurnCount = getCompletedTurnCount(currentGamePlayer, gamePlayers);
		if (completedTurnCount == 0) {
			return null;
		}

		int currentShipsAfloat = countShipsAfloat(
			currentGamePlayer,
			getHitLocationsThroughTurn(opponent, currentGamePlayer, completedTurnCount)
		);
		int opponentShipsAfloat = countShipsAfloat(
			opponent,
			getHitLocationsThroughTurn(currentGamePlayer, opponent, completedTurnCount)
		);

		if (currentShipsAfloat > 0 && opponentShipsAfloat > 0) {
			return null;
		}
		if (currentShipsAfloat == 0 && opponentShipsAfloat == 0) {
			return GameResult.TIE;
		}
		return opponentShipsAfloat == 0 ? GameResult.WIN : GameResult.LOSS;
	}

	public double getScoreValue(GameResult result) {
		return switch (result) {
			case WIN -> 1.0;
			case LOSS -> 0.0;
			case TIE -> 0.5;
		};
	}

	public boolean hasDuplicateShipTypes(List<Ship> ships) {
		return ships.stream()
			.map(Ship::getShipType)
			.anyMatch(type -> type == null || type.isBlank())
			|| ships.stream()
				.map(Ship::getShipType)
				.map(String::trim)
				.collect(LinkedHashSet::new, Set::add, Set::addAll)
				.size() != ships.size();
	}

	public boolean hasInvalidOrOverlappingShipLocations(List<Ship> ships) {
		Set<String> occupiedLocations = new LinkedHashSet<>();
		for (Ship ship : ships) {
			List<String> locations = ship.getLocations();
			if (locations == null || locations.isEmpty()) {
				return true;
			}
			for (String location : locations) {
				if (location == null || location.isBlank() || !occupiedLocations.add(location)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isTerminalState(String gameState) {
		return GAME_STATE_GAME_OVER_WIN.equals(gameState)
			|| GAME_STATE_GAME_OVER_LOSS.equals(gameState)
			|| GAME_STATE_GAME_OVER_TIE.equals(gameState);
	}

	private Map<String, String> makeShotOutcomeMap(GamePlayer attacker, GamePlayer defender, int visibleTurnCount) {
		Map<String, String> outcomes = new LinkedHashMap<>();

		attacker.getSalvoes().stream()
			.filter(salvo -> salvo.getTurn() <= visibleTurnCount)
			.sorted(Comparator.comparingInt(Salvo::getTurn))
			.forEach(salvo -> {
				Set<String> hitLocationsThroughTurn = getHitLocationsThroughTurn(attacker, defender, salvo.getTurn());
				for (String location : salvo.getLocations()) {
					Ship hitShip = findShipAtLocation(defender, location);
					if (hitShip == null) {
						outcomes.put(location, "miss");
					} else if (hitShip.getLocations().stream().allMatch(hitLocationsThroughTurn::contains)) {
						outcomes.put(location, "sunk");
					} else {
						outcomes.put(location, "hit");
					}
				}
			});

		return outcomes;
	}

	public enum GameResult {
		WIN,
		LOSS,
		TIE
	}
}
