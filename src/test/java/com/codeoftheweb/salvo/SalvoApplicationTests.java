package com.codeoftheweb.salvo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SalvoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
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
