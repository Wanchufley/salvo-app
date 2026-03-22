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

}
