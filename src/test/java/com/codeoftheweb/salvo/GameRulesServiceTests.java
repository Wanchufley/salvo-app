package com.codeoftheweb.salvo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRulesServiceTests {

	private final GameRulesService gameRulesService = new GameRulesService();

	@Test
	void determineGameStateReturnsPlaceShipsWhenCurrentPlayerHasNoShips() {
		Game game = game(100L);
		GamePlayer current = gamePlayer(1L, game, player(1L, "current@example.com"));
		GamePlayer opponent = gamePlayer(2L, game, player(2L, "opponent@example.com"));
		addShip(opponent, "Destroyer", "A1", "A2", "A3");
		linkGamePlayers(game, current, opponent);

		assertEquals(
			GameRulesService.GAME_STATE_PLACE_SHIPS,
			gameRulesService.determineGameState(current, List.of(current, opponent))
		);
	}

	@Test
	void determineGameStateReturnsWaitingForOpponentSalvoWhenCurrentPlayerIsAhead() {
		Game game = game(100L);
		GamePlayer current = gamePlayer(1L, game, player(1L, "current@example.com"));
		GamePlayer opponent = gamePlayer(2L, game, player(2L, "opponent@example.com"));
		linkGamePlayers(game, current, opponent);
		addShip(current, "Destroyer", "A1", "A2", "A3");
		addShip(opponent, "Destroyer", "B1", "B2", "B3");
		addSalvo(current, 1, "B1");

		assertEquals(
			GameRulesService.GAME_STATE_WAITING_FOR_OPPONENT_SALVO,
			gameRulesService.determineGameState(current, List.of(current, opponent))
		);
		assertEquals(1, gameRulesService.calculateCurrentTurn(current));
		assertEquals(0, gameRulesService.getCompletedTurnCount(current, List.of(current, opponent)));
	}

	@Test
	void getGameResultReturnsTieWhenBothPlayersLoseAllShipsInSameCompletedRound() {
		Game game = game(100L);
		GamePlayer current = gamePlayer(1L, game, player(1L, "current@example.com"));
		GamePlayer opponent = gamePlayer(2L, game, player(2L, "opponent@example.com"));
		linkGamePlayers(game, current, opponent);
		addShip(current, "Patrol Boat", "A1", "A2");
		addShip(opponent, "Patrol Boat", "B1", "B2");
		addSalvo(current, 1, "B1", "B2");
		addSalvo(opponent, 1, "A1", "A2");

		assertEquals(
			GameRulesService.GameResult.TIE,
			gameRulesService.getGameResult(current, List.of(current, opponent))
		);
		assertEquals(
			GameRulesService.GAME_STATE_GAME_OVER_TIE,
			gameRulesService.determineGameState(current, List.of(current, opponent))
		);
	}

	@Test
	void shipValidationRejectsDuplicateTypesAndOverlappingLocations() {
		List<Ship> duplicateTypes = List.of(
			new Ship("Destroyer", List.of("A1", "A2", "A3")),
			new Ship("Destroyer", List.of("B1", "B2", "B3"))
		);
		List<Ship> overlappingLocations = List.of(
			new Ship("Destroyer", List.of("A1", "A2", "A3")),
			new Ship("Submarine", List.of("A3", "B3", "C3"))
		);

		assertTrue(gameRulesService.hasDuplicateShipTypes(duplicateTypes));
		assertTrue(gameRulesService.hasInvalidOrOverlappingShipLocations(overlappingLocations));
	}

	@Test
	void visibleSalvoesHideOpponentsFutureTurnsButKeepCurrentPlayersOwnTurns() {
		Game game = game(100L);
		GamePlayer current = gamePlayer(10L, game, player(1L, "current@example.com"));
		GamePlayer opponent = gamePlayer(9L, game, player(2L, "opponent@example.com"));
		linkGamePlayers(game, current, opponent);
		addSalvo(current, 1, "B5");
		addSalvo(current, 2, "C6");
		addSalvo(current, 3, "H1");
		addSalvo(opponent, 1, "A1");
		addSalvo(opponent, 2, "G6");

		Map<Long, Map<Integer, List<String>>> visibleSalvoes =
			gameRulesService.makeVisibleSalvoesDTO(current, List.of(opponent, current));

		assertEquals(List.of("H1"), visibleSalvoes.get(10L).get(3));
		assertEquals(List.of("G6"), visibleSalvoes.get(9L).get(2));
		assertNull(visibleSalvoes.get(9L).get(3));
	}

	@Test
	void hitLocationsAndShipsAfloatReflectOnlySuccessfulHitsThroughTurn() {
		Game game = game(100L);
		GamePlayer attacker = gamePlayer(1L, game, player(1L, "attacker@example.com"));
		GamePlayer defender = gamePlayer(2L, game, player(2L, "defender@example.com"));
		linkGamePlayers(game, attacker, defender);
		addShip(defender, "Destroyer", "C1", "C2", "C3");
		addShip(defender, "Patrol Boat", "D1", "D2");
		addSalvo(attacker, 1, "C1", "Z9");
		addSalvo(attacker, 2, "C2", "C3");

		Set<String> hitLocations = gameRulesService.getHitLocationsThroughTurn(attacker, defender, 2);

		assertEquals(Set.of("C1", "C2", "C3"), hitLocations);
		assertEquals(1, gameRulesService.countShipsAfloat(defender, hitLocations));
		assertFalse(gameRulesService.isTerminalState(GameRulesService.GAME_STATE_WAITING_FOR_YOUR_SALVO));
		assertTrue(gameRulesService.isTerminalState(GameRulesService.GAME_STATE_GAME_OVER_WIN));
	}

	private Game game(long id) {
		Game game = new Game();
		ReflectionTestUtils.setField(game, "id", id);
		return game;
	}

	private Player player(long id, String userName) {
		Player player = new Player(userName, "encoded");
		ReflectionTestUtils.setField(player, "id", id);
		return player;
	}

	private GamePlayer gamePlayer(long id, Game game, Player player) {
		GamePlayer gamePlayer = new GamePlayer(game, player);
		ReflectionTestUtils.setField(gamePlayer, "id", id);
		return gamePlayer;
	}

	private void linkGamePlayers(Game game, GamePlayer... gamePlayers) {
		ReflectionTestUtils.setField(game, "gamePlayers", Set.of(gamePlayers));
	}

	private void addShip(GamePlayer gamePlayer, String type, String... locations) {
		gamePlayer.addShip(new Ship(type, List.of(locations)));
	}

	private void addSalvo(GamePlayer gamePlayer, int turn, String... locations) {
		gamePlayer.addSalvo(new Salvo(turn, List.of(locations)));
	}
}
