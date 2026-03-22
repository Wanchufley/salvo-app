package com.codeoftheweb.salvo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SalvoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

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

	@Test
	void contextLoads() {
	}

	@Test
	void gamesEndpointReturnsSeededGameListForAnonymousUser() throws Exception {
		mockMvc.perform(get("/api/games"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.player").doesNotExist())
			.andExpect(jsonPath("$.games.length()").value(8))
			.andExpect(jsonPath("$.games[0].id").value(1))
			.andExpect(jsonPath("$.games[0].gamePlayers.length()").value(2))
			.andExpect(jsonPath("$.games[0].scores.length()").value(2))
			.andExpect(jsonPath("$.games[5].gamePlayers.length()").value(1))
			.andExpect(jsonPath("$.games[6].gamePlayers.length()").value(1))
			.andExpect(jsonPath("$.games[7].gamePlayers.length()").value(2));
	}

	@Test
	void gamesEndpointIncludesAuthenticatedPlayerSummary() throws Exception {
		mockMvc.perform(get("/api/games").with(user("j.bauer@ctu.gov").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.player.id").value(1))
			.andExpect(jsonPath("$.player.username").value("j.bauer@ctu.gov"))
			.andExpect(jsonPath("$.games.length()").value(8));
	}

	@Test
	void playerEndpointReturnsEmptyObjectForAnonymousUser() throws Exception {
		mockMvc.perform(get("/api/player"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void playerEndpointReturnsAuthenticatedPlayer() throws Exception {
		mockMvc.perform(get("/api/player").with(user("j.bauer@ctu.gov").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.email").value("j.bauer@ctu.gov"));
	}

	@Test
	void registerCreatesPlayerAndAuthenticatesSession() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "new.player@example.com",
					  "password": "secret123"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("new.player@example.com"))
			.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

		mockMvc.perform(get("/api/player").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("new.player@example.com"));
	}

	@Test
	void registerRejectsInvalidCredentials() throws Exception {
		mockMvc.perform(post("/api/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "invalid user",
					  "password": "bad password"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Invalid username or password"));
	}

	@Test
	void registerRejectsDuplicateUserName() throws Exception {
		mockMvc.perform(post("/api/players")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "j.bauer@ctu.gov",
					  "password": "24"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Name in use"));
	}

	@Test
	void createGameRequiresAuthentication() throws Exception {
		long gameCountBefore = gameRepository.count();

		mockMvc.perform(post("/api/games"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));

		org.junit.jupiter.api.Assertions.assertEquals(gameCountBefore, gameRepository.count());
	}

	@Test
	void createGameCreatesGameAndGamePlayerForAuthenticatedUser() throws Exception {
		long gameCountBefore = gameRepository.count();
		long gamePlayerCountBefore = gamePlayerRepository.count();

		mockMvc.perform(post("/api/games").with(user("j.bauer@ctu.gov").roles("USER")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.gpid").isNumber());

		org.junit.jupiter.api.Assertions.assertEquals(gameCountBefore + 1, gameRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(gamePlayerCountBefore + 1, gamePlayerRepository.count());
	}

	@Test
	void joinGameCreatesSecondGamePlayerForOpenGame() throws Exception {
		Game game = createGameWithPlayers("j.bauer@ctu.gov");
		long gamePlayerCountBefore = gamePlayerRepository.count();

		mockMvc.perform(post("/api/games/" + game.getId() + "/players").with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.gpid").isNumber());

		org.junit.jupiter.api.Assertions.assertEquals(gamePlayerCountBefore + 1, gamePlayerRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(2, gamePlayerRepository.findAll().stream()
			.filter(gamePlayer -> gamePlayer.getGame().getId() == game.getId())
			.count());
	}

	@Test
	void joinGameRejectsMissingGame() throws Exception {
		mockMvc.perform(post("/api/games/999999/players").with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("No such game"));
	}

	@Test
	void joinGameRejectsPlayerAlreadyInGame() throws Exception {
		Game game = createGameWithPlayers("j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/" + game.getId() + "/players").with(user("j.bauer@ctu.gov").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Already in game"));
	}

	@Test
	void joinGameRejectsFullGame() throws Exception {
		Game game = createGameWithPlayers("j.bauer@ctu.gov", "c.obrian@ctu.gov");

		mockMvc.perform(post("/api/games/" + game.getId() + "/players").with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Game is full"));
	}

	@Test
	void gameViewRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/game_view/1"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void gameViewRejectsNonOwner() throws Exception {
		mockMvc.perform(get("/api/game_view/1").with(user("c.obrian@ctu.gov").roles("USER")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void gameViewExposesExpectedStateMetadataAcrossSeededGames() throws Exception {
		assertGameViewState(1, "j.bauer@ctu.gov", "GAME_OVER_WIN", false, false, true, 3, 2);
		assertGameViewState(2, "c.obrian@ctu.gov", "GAME_OVER_LOSS", false, false, true, 3, 2);
		assertGameViewState(3, "j.bauer@ctu.gov", "GAME_OVER_TIE", false, false, true, 3, 2);
		assertGameViewState(9, "t.almeida@ctu.gov", "WAITING_FOR_YOUR_SALVO", false, true, false, 3, 2);
		assertGameViewState(10, "j.bauer@ctu.gov", "WAITING_FOR_OPPONENT_SALVO", false, false, false, 3, 2);
		assertGameViewState(11, "kim_bauer@gmail.com", "WAITING_FOR_OPPONENT", false, false, false, 1, 0);
		assertGameViewState(12, "t.almeida@ctu.gov", "PLACE_SHIPS", true, false, false, 1, 0);
		assertGameViewState(13, "kim_bauer@gmail.com", "STARTING_SOON", false, true, false, 1, 0);
	}

	@Test
	void gameViewHidesOpponentFutureSalvosUntilCurrentPlayerCatchesUp() throws Exception {
		mockMvc.perform(get("/api/game_view/9").with(user("t.almeida@ctu.gov").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salvoes['9']['1'][0]").value("A1"))
			.andExpect(jsonPath("$.salvoes['9']['2'][0]").value("G6"))
			.andExpect(jsonPath("$.salvoes['10']['1'][0]").value("B5"))
			.andExpect(jsonPath("$.salvoes['10']['2'][0]").value("C6"))
			.andExpect(jsonPath("$.salvoes['10']['3']").doesNotExist());
	}

	@Test
	void gameViewAlwaysShowsCurrentPlayersOwnFutureSalvos() throws Exception {
		mockMvc.perform(get("/api/game_view/10").with(user("j.bauer@ctu.gov").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salvoes['10']['1'][0]").value("B5"))
			.andExpect(jsonPath("$.salvoes['10']['2'][0]").value("C6"))
			.andExpect(jsonPath("$.salvoes['10']['3'][0]").value("H1"))
			.andExpect(jsonPath("$.salvoes['9']['2'][0]").value("G6"));
	}

	@Test
	void placeShipsCreatesFleetForAuthorizedPlayer() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");
		long shipCountBefore = shipRepository.count();

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validFleetJson()))
			.andExpect(status().isCreated());

		org.junit.jupiter.api.Assertions.assertEquals(shipCountBefore + 5, shipRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(5, shipRepository.findAll().stream()
			.filter(ship -> ship.getGamePlayer().getId() == gamePlayer.getId())
			.count());
	}

	@Test
	void placeShipsRequiresAuthentication() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");
		long shipCountBefore = shipRepository.count();

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validFleetJson()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));

		org.junit.jupiter.api.Assertions.assertEquals(shipCountBefore, shipRepository.count());
	}

	@Test
	void placeShipsRejectsWrongGamePlayerOwner() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("j.bauer@ctu.gov").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validFleetJson()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void placeShipsRejectsSecondPlacement() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");
		addShip(gamePlayer, "Destroyer", "A1", "A2", "A3");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validFleetJson()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ships already placed"));
	}

	@Test
	void placeShipsRejectsWrongFleetSize() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": "Carrier", "locations": ["A1", "A2", "A3", "A4", "A5"] },
					  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("You must place exactly 5 ships"));
	}

	@Test
	void placeShipsRejectsDuplicateShipTypes() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": "Destroyer", "locations": ["A1", "A2", "A3"] },
					  { "type": "Destroyer", "locations": ["B1", "B2", "B3"] },
					  { "type": "Patrol Boat", "locations": ["C1", "C2"] },
					  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
					  { "type": "Carrier", "locations": ["E1", "E2", "E3", "E4", "E5"] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ship types must be unique"));
	}

	@Test
	void placeShipsRejectsBlankShipType() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": " ", "locations": ["A1", "A2", "A3"] },
					  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] },
					  { "type": "Patrol Boat", "locations": ["C1", "C2"] },
					  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
					  { "type": "Carrier", "locations": ["E1", "E2", "E3", "E4", "E5"] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ship types must be unique"));
	}

	@Test
	void placeShipsRejectsOverlappingLocations() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": "Carrier", "locations": ["A1", "A2", "A3", "A4", "A5"] },
					  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] },
					  { "type": "Destroyer", "locations": ["C1", "C2", "C3"] },
					  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
					  { "type": "Patrol Boat", "locations": ["D3", "E3"] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ships must occupy unique board locations"));
	}

	@Test
	void placeShipsRejectsBlankLocation() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": "Carrier", "locations": ["A1", "A2", "A3", "A4", "A5"] },
					  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] },
					  { "type": "Destroyer", "locations": ["C1", "C2", "C3"] },
					  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
					  { "type": "Patrol Boat", "locations": [" ", "E3"] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ships must occupy unique board locations"));
	}

	@Test
	void placeShipsRejectsEmptyLocationList() throws Exception {
		GamePlayer gamePlayer = createGamePlayerFor("kim_bauer@gmail.com");

		mockMvc.perform(post("/api/games/players/" + gamePlayer.getId() + "/ships")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					[
					  { "type": "Carrier", "locations": ["A1", "A2", "A3", "A4", "A5"] },
					  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] },
					  { "type": "Destroyer", "locations": ["C1", "C2", "C3"] },
					  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
					  { "type": "Patrol Boat", "locations": [] }
					]
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Ships must occupy unique board locations"));
	}

	@Test
	void placeSalvoCreatesSalvoForAuthorizedPlayer() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		long salvoCountBefore = salvoRepository.count();

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isCreated());

		org.junit.jupiter.api.Assertions.assertEquals(salvoCountBefore + 1, salvoRepository.count());
	}

	@Test
	void placeSalvoRequiresAuthentication() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void placeSalvoRejectsWrongGamePlayerOwner() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("c.obrian@ctu.gov").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void placeSalvoRejectsWhenWaitingForOpponent() throws Exception {
		GamePlayer current = createGamePlayerFor("kim_bauer@gmail.com");
		addStandardShips(current);

		mockMvc.perform(post("/api/games/players/" + current.getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Waiting for an opponent"));
	}

	@Test
	void placeSalvoRejectsWhenCurrentPlayerHasNotPlacedShips() throws Exception {
		GamePlayer current = createGamePlayerFor("kim_bauer@gmail.com");
		GamePlayer opponent = createOpponentForGame(current.getGame(), "j.bauer@ctu.gov");
		addStandardShips(opponent);

		mockMvc.perform(post("/api/games/players/" + current.getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Place your ships first"));
	}

	@Test
	void placeSalvoRejectsWhenOpponentHasNotPlacedShips() throws Exception {
		GamePlayer current = createGamePlayerFor("kim_bauer@gmail.com");
		createOpponentForGame(current.getGame(), "j.bauer@ctu.gov");
		addStandardShips(current);

		mockMvc.perform(post("/api/games/players/" + current.getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Waiting for opponent to place ships"));
	}

	@Test
	void placeSalvoRejectsInvalidTurn() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 2,
					  "locations": ["A1", "A2", "A3"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Invalid turn"));
	}

	@Test
	void placeSalvoRejectsDuplicateTurnSubmission() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		addSalvo(battle.current(), 1, "A1", "A2", "A3");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["B1", "B2", "B3"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Salvo already submitted for this turn"));
	}

	@Test
	void placeSalvoRejectsZeroShots() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": []
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("A salvo must contain between 1 and 5 shots"));
	}

	@Test
	void placeSalvoRejectsMoreThanFiveShots() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A2", "A3", "A4", "A5", "A6"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("A salvo must contain between 1 and 5 shots"));
	}

	@Test
	void placeSalvoRejectsDuplicateShotLocations() throws Exception {
		BattleFixture battle = createReadyBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 1,
					  "locations": ["A1", "A1", "A2"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Salvo shots must be unique"));
	}

	@Test
	void placeSalvoRejectsWhenGameIsAlreadyOver() throws Exception {
		BattleFixture battle = createPatrolBoatBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		addSalvo(battle.current(), 1, "B1", "B2");
		addSalvo(battle.opponent(), 1, "A1", "A2");

		mockMvc.perform(post("/api/games/players/" + battle.current().getId() + "/salvos")
				.with(user("kim_bauer@gmail.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "turn": 2,
					  "locations": ["C1"]
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Game is over"));
	}

	@Test
	void gameViewRecordsWinStateAndCreatesScoresOnce() throws Exception {
		BattleFixture battle = createPatrolBoatBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		addSalvo(battle.current(), 1, "B1", "B2");
		addSalvo(battle.opponent(), 1, "J10");

		org.junit.jupiter.api.Assertions.assertEquals(0, countScoresForGame(battle.game().getId()));

		mockMvc.perform(get("/api/game_view/" + battle.current().getId()).with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gameState").value("GAME_OVER_WIN"))
			.andExpect(jsonPath("$.isGameOver").value(true))
			.andExpect(jsonPath("$.completedTurnCount").value(1))
			.andExpect(jsonPath("$.scores.length()").value(2))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'kim_bauer@gmail.com')].score").value(1.0))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'j.bauer@ctu.gov')].score").value(0.0));

		org.junit.jupiter.api.Assertions.assertEquals(2, countScoresForGame(battle.game().getId()));

		mockMvc.perform(get("/api/game_view/" + battle.current().getId()).with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scores.length()").value(2));

		org.junit.jupiter.api.Assertions.assertEquals(2, countScoresForGame(battle.game().getId()));
	}

	@Test
	void gameViewReportsLossStateAndRecordedScoresForLosingPlayer() throws Exception {
		BattleFixture battle = createPatrolBoatBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		addSalvo(battle.current(), 1, "J10");
		addSalvo(battle.opponent(), 1, "A1", "A2");

		mockMvc.perform(get("/api/game_view/" + battle.current().getId()).with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gameState").value("GAME_OVER_LOSS"))
			.andExpect(jsonPath("$.isGameOver").value(true))
			.andExpect(jsonPath("$.completedTurnCount").value(1))
			.andExpect(jsonPath("$.scores.length()").value(2))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'kim_bauer@gmail.com')].score").value(0.0))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'j.bauer@ctu.gov')].score").value(1.0));
	}

	@Test
	void gameViewReportsTieStateAndHalfPointScores() throws Exception {
		BattleFixture battle = createPatrolBoatBattle("kim_bauer@gmail.com", "j.bauer@ctu.gov");
		addSalvo(battle.current(), 1, "B1", "B2");
		addSalvo(battle.opponent(), 1, "A1", "A2");

		mockMvc.perform(get("/api/game_view/" + battle.current().getId()).with(user("kim_bauer@gmail.com").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gameState").value("GAME_OVER_TIE"))
			.andExpect(jsonPath("$.isGameOver").value(true))
			.andExpect(jsonPath("$.completedTurnCount").value(1))
			.andExpect(jsonPath("$.scores.length()").value(2))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'kim_bauer@gmail.com')].score").value(0.5))
			.andExpect(jsonPath("$.scores[?(@.player.email == 'j.bauer@ctu.gov')].score").value(0.5));
	}

	@Test
	void gameViewIncludesTurnByTurnHitHistoryAndShipsAfloat() throws Exception {
			mockMvc.perform(get("/api/game_view/1").with(user("j.bauer@ctu.gov").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.salvoCellStates.self.B5").value("hit"))
				.andExpect(jsonPath("$.salvoCellStates.self.C5").value("hit"))
				.andExpect(jsonPath("$.salvoCellStates.self.F1").value("hit"))
				.andExpect(jsonPath("$.salvoCellStates.self.F2").value("sunk"))
				.andExpect(jsonPath("$.salvoCellStates.self.D5").value("sunk"))
				.andExpect(jsonPath("$.salvoCellStates.opponent.B4").value("sunk"))
				.andExpect(jsonPath("$.salvoCellStates.opponent.B5").value("sunk"))
				.andExpect(jsonPath("$.salvoCellStates.opponent.B6").value("miss"))
			.andExpect(jsonPath("$.hits['1'].self.hitCount").value(3))
			.andExpect(jsonPath("$.hits['1'].self.hits.Destroyer").value(2))
			.andExpect(jsonPath("$.hits['1'].self.hits['Patrol Boat']").value(1))
			.andExpect(jsonPath("$.hits['1'].self.sunk").isEmpty())
			.andExpect(jsonPath("$.hits['1'].self.shipsAfloat").value(2))
			.andExpect(jsonPath("$.hits['1'].opponent.hitCount").value(2))
			.andExpect(jsonPath("$.hits['1'].opponent.sunk[0]").value("Patrol Boat"))
			.andExpect(jsonPath("$.hits['1'].opponent.shipsAfloat").value(2))
			.andExpect(jsonPath("$.hits['2'].self.hitCount").value(2))
			.andExpect(jsonPath("$.hits['2'].self.sunk[0]").value("Destroyer"))
			.andExpect(jsonPath("$.hits['2'].self.sunk[1]").value("Patrol Boat"))
			.andExpect(jsonPath("$.hits['2'].self.shipsAfloat").value(0))
			.andExpect(jsonPath("$.hits['2'].opponent.hitCount").value(2))
			.andExpect(jsonPath("$.hits['2'].opponent.sunk").isEmpty())
			.andExpect(jsonPath("$.hits['2'].opponent.shipsAfloat").value(2));
	}

	private Game createGameWithPlayers(String... userNames) {
		Game game = gameRepository.save(new Game());
		for (String userName : userNames) {
			Player player = playerRepository.findByUserName(userName);
			gamePlayerRepository.save(new GamePlayer(game, player));
		}
		return game;
	}

	private GamePlayer createGamePlayerFor(String userName) {
		Game game = gameRepository.save(new Game());
		Player player = playerRepository.findByUserName(userName);
		return gamePlayerRepository.save(new GamePlayer(game, player));
	}

	private GamePlayer createOpponentForGame(Game game, String userName) {
		Player player = playerRepository.findByUserName(userName);
		return gamePlayerRepository.save(new GamePlayer(game, player));
	}

	private BattleFixture createReadyBattle(String currentUserName, String opponentUserName) {
		GamePlayer current = createGamePlayerFor(currentUserName);
		GamePlayer opponent = createOpponentForGame(current.getGame(), opponentUserName);
		addStandardShips(current);
		addStandardShips(opponent);
		return new BattleFixture(current.getGame(), current, opponent);
	}

	private BattleFixture createPatrolBoatBattle(String currentUserName, String opponentUserName) {
		GamePlayer current = createGamePlayerFor(currentUserName);
		GamePlayer opponent = createOpponentForGame(current.getGame(), opponentUserName);
		addShip(current, "Patrol Boat", "A1", "A2");
		addShip(opponent, "Patrol Boat", "B1", "B2");
		return new BattleFixture(current.getGame(), current, opponent);
	}

	private void addShip(GamePlayer gamePlayer, String type, String... locations) {
		Ship ship = shipRepository.save(new Ship(type, java.util.List.of(locations)));
		ship.setGamePlayer(gamePlayer);
		shipRepository.save(ship);
	}

	private void addStandardShips(GamePlayer gamePlayer) {
		addShip(gamePlayer, "Carrier", "A1", "A2", "A3", "A4", "A5");
		addShip(gamePlayer, "Battleship", "B1", "B2", "B3", "B4");
		addShip(gamePlayer, "Destroyer", "C1", "C2", "C3");
		addShip(gamePlayer, "Submarine", "D1", "D2", "D3");
		addShip(gamePlayer, "Patrol Boat", "E1", "E2");
	}

	private void addSalvo(GamePlayer gamePlayer, int turn, String... locations) {
		Salvo salvo = salvoRepository.save(new Salvo(turn, java.util.List.of(locations)));
		salvo.setGamePlayer(gamePlayer);
		salvoRepository.save(salvo);
	}

	private long countScoresForGame(long gameId) {
		return scoreRepository.findAll().stream()
			.filter(score -> score.getGame().getId() == gameId)
			.count();
	}

	private String validFleetJson() {
		return """
			[
			  { "type": "Carrier", "locations": ["A1", "A2", "A3", "A4", "A5"] },
			  { "type": "Battleship", "locations": ["B1", "B2", "B3", "B4"] },
			  { "type": "Destroyer", "locations": ["C1", "C2", "C3"] },
			  { "type": "Submarine", "locations": ["D1", "D2", "D3"] },
			  { "type": "Patrol Boat", "locations": ["E1", "E2"] }
			]
			""";
	}

	private void assertGameViewState(
		long gamePlayerId,
		String userName,
		String expectedState,
		boolean expectedCanPlaceShips,
		boolean expectedCanFireSalvo,
		boolean expectedGameOver,
		int expectedCurrentTurn,
		int expectedCompletedTurnCount
	) throws Exception {
		mockMvc.perform(get("/api/game_view/" + gamePlayerId).with(user(userName).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gameState").value(expectedState))
			.andExpect(jsonPath("$.canPlaceShips").value(expectedCanPlaceShips))
			.andExpect(jsonPath("$.canFireSalvo").value(expectedCanFireSalvo))
			.andExpect(jsonPath("$.isGameOver").value(expectedGameOver))
			.andExpect(jsonPath("$.currentTurn").value(expectedCurrentTurn))
			.andExpect(jsonPath("$.completedTurnCount").value(expectedCompletedTurnCount));
	}

	private record BattleFixture(Game game, GamePlayer current, GamePlayer opponent) {
	}

}
