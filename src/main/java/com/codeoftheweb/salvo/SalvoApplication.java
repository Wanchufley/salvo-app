package com.codeoftheweb.salvo;

import java.util.Date;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SalvoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalvoApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(PlayerRepository playerRepository,
                                      GameRepository gameRepository,
                                      GamePlayerRepository gamePlayerRepository) {
        return (args) -> {
            Player player1 = playerRepository.save(new Player("j.bauer@ctu.gov"));
            Player player2 = playerRepository.save(new Player("c.obrian@ctu.gov"));
            Player player3 = playerRepository.save(new Player("kim_bauer@gmail.com"));
            Player player4 = playerRepository.save(new Player("t.almeida@ctu.gov"));

            Game game1 = new Game();
            Game game2 = new Game();
            Game game3 = new Game();

            Date baseDate = game1.getCreationDate();
            game2.setCreationDate(new Date(baseDate.getTime() + 3_600_000L));
            game3.setCreationDate(new Date(baseDate.getTime() + 7_200_000L));

            gameRepository.save(game1);
            gameRepository.save(game2);
            gameRepository.save(game3);

            gamePlayerRepository.save(new GamePlayer(game1, player1));
            gamePlayerRepository.save(new GamePlayer(game1, player2));
            gamePlayerRepository.save(new GamePlayer(game2, player3));
            gamePlayerRepository.save(new GamePlayer(game2, player4));
            gamePlayerRepository.save(new GamePlayer(game3, player1));
            gamePlayerRepository.save(new GamePlayer(game3, player3));
        };
    }
}
