package com.codeoftheweb.salvo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
                                      GamePlayerRepository gamePlayerRepository,
                                      ShipRepository shipRepository,
                                      SalvoRepository salvoRepository) {
        return (args) -> {
            Player player1 = playerRepository.save(new Player("j.bauer@ctu.gov"));
            Player player2 = playerRepository.save(new Player("c.obrian@ctu.gov"));
            Player player3 = playerRepository.save(new Player("kim_bauer@gmail.com"));
            Player player4 = playerRepository.save(new Player("t.almeida@ctu.gov"));

            Game game1 = new Game();
            Game game2 = new Game();
            Game game3 = new Game();
            Game game4 = new Game();
            Game game5 = new Game();
            Game game6 = new Game();
            Game game7 = new Game();
            Game game8 = new Game();

            Date baseDate = game1.getCreationDate();
            game2.setCreationDate(new Date(baseDate.getTime() + 3_600_000L));
            game3.setCreationDate(new Date(baseDate.getTime() + 7_200_000L));
            game4.setCreationDate(new Date(baseDate.getTime() + 10_800_000L));
            game5.setCreationDate(new Date(baseDate.getTime() + 14_400_000L));
            game6.setCreationDate(new Date(baseDate.getTime() + 18_000_000L));
            game7.setCreationDate(new Date(baseDate.getTime() + 21_600_000L));
            game8.setCreationDate(new Date(baseDate.getTime() + 25_200_000L));

            gameRepository.save(game1);
            gameRepository.save(game2);
            gameRepository.save(game3);
            gameRepository.save(game4);
            gameRepository.save(game5);
            gameRepository.save(game6);
            gameRepository.save(game7);
            gameRepository.save(game8);

            GamePlayer gp1 = gamePlayerRepository.save(new GamePlayer(game1, player1));
            GamePlayer gp2 = gamePlayerRepository.save(new GamePlayer(game1, player2));
            GamePlayer gp3 = gamePlayerRepository.save(new GamePlayer(game2, player1));
            GamePlayer gp4 = gamePlayerRepository.save(new GamePlayer(game2, player2));
            GamePlayer gp5 = gamePlayerRepository.save(new GamePlayer(game3, player2));
            GamePlayer gp6 = gamePlayerRepository.save(new GamePlayer(game3, player4));
            GamePlayer gp7 = gamePlayerRepository.save(new GamePlayer(game4, player2));
            GamePlayer gp8 = gamePlayerRepository.save(new GamePlayer(game4, player1));
            GamePlayer gp9 = gamePlayerRepository.save(new GamePlayer(game5, player4));
            GamePlayer gp10 = gamePlayerRepository.save(new GamePlayer(game5, player1));
            GamePlayer gp11 = gamePlayerRepository.save(new GamePlayer(game6, player3));
            GamePlayer gp12 = gamePlayerRepository.save(new GamePlayer(game7, player4));
            GamePlayer gp13 = gamePlayerRepository.save(new GamePlayer(game8, player3));
            GamePlayer gp14 = gamePlayerRepository.save(new GamePlayer(game8, player4));

            List<Ship> ships = new ArrayList<>();

            addShip(gp1, "Destroyer", List.of("H2", "H3", "H4"), ships);
            addShip(gp1, "Submarine", List.of("E1", "F1", "G1"), ships);
            addShip(gp1, "Patrol Boat", List.of("B4", "B5"), ships);
            addShip(gp2, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp2, "Patrol Boat", List.of("F1", "F2"), ships);

            addShip(gp3, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp3, "Patrol Boat", List.of("C6", "C7"), ships);
            addShip(gp4, "Submarine", List.of("A2", "A3", "A4"), ships);
            addShip(gp4, "Patrol Boat", List.of("G6", "H6"), ships);

            addShip(gp5, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp5, "Patrol Boat", List.of("C6", "C7"), ships);
            addShip(gp6, "Submarine", List.of("A2", "A3", "A4"), ships);
            addShip(gp6, "Patrol Boat", List.of("G6", "H6"), ships);

            addShip(gp7, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp7, "Patrol Boat", List.of("C6", "C7"), ships);
            addShip(gp8, "Submarine", List.of("A2", "A3", "A4"), ships);
            addShip(gp8, "Patrol Boat", List.of("G6", "H6"), ships);

            addShip(gp9, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp9, "Patrol Boat", List.of("C6", "C7"), ships);
            addShip(gp10, "Submarine", List.of("A2", "A3", "A4"), ships);
            addShip(gp10, "Patrol Boat", List.of("G6", "H6"), ships);

            addShip(gp11, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp11, "Patrol Boat", List.of("C6", "C7"), ships);

            addShip(gp13, "Destroyer", List.of("B5", "C5", "D5"), ships);
            addShip(gp13, "Patrol Boat", List.of("C6", "C7"), ships);
            addShip(gp14, "Submarine", List.of("A2", "A3", "A4"), ships);
            addShip(gp14, "Patrol Boat", List.of("G6", "H6"), ships);

            shipRepository.saveAll(ships);

            List<Salvo> salvoes = new ArrayList<>();

            addSalvo(gp1, 1, List.of("B5", "C5", "F1"), salvoes);
            addSalvo(gp2, 1, List.of("B4", "B5", "B6"), salvoes);
            addSalvo(gp1, 2, List.of("F2", "D5"), salvoes);
            addSalvo(gp2, 2, List.of("E1", "H3", "A2"), salvoes);

            addSalvo(gp3, 1, List.of("A2", "A4", "G6"), salvoes);
            addSalvo(gp4, 1, List.of("B5", "D5", "C7"), salvoes);
            addSalvo(gp3, 2, List.of("A3", "H6"), salvoes);
            addSalvo(gp4, 2, List.of("C5", "C6"), salvoes);

            addSalvo(gp5, 1, List.of("G6", "H6", "A4"), salvoes);
            addSalvo(gp6, 1, List.of("H1", "H2", "H3"), salvoes);
            addSalvo(gp5, 2, List.of("A2", "A3", "D8"), salvoes);
            addSalvo(gp6, 2, List.of("E1", "F2", "G3"), salvoes);

            addSalvo(gp7, 1, List.of("A3", "A4", "F7"), salvoes);
            addSalvo(gp8, 1, List.of("B5", "C6", "H1"), salvoes);
            addSalvo(gp7, 2, List.of("A2", "G6", "H6"), salvoes);
            addSalvo(gp8, 2, List.of("C5", "C7", "D5"), salvoes);

            addSalvo(gp9, 1, List.of("A1", "A2", "A3"), salvoes);
            addSalvo(gp10, 1, List.of("B5", "B6", "C7"), salvoes);
            addSalvo(gp9, 2, List.of("G6", "G7", "G8"), salvoes);
            addSalvo(gp10, 2, List.of("C6", "D6", "E6"), salvoes);
            addSalvo(gp10, 3, List.of("H1", "H8"), salvoes);

            salvoRepository.saveAll(salvoes);
        };
    }

    private static void addShip(GamePlayer gamePlayer, String type, List<String> locations, List<Ship> ships) {
        Ship ship = new Ship(type, locations);
        gamePlayer.addShip(ship);
        ships.add(ship);
    }

    private static void addSalvo(GamePlayer gamePlayer, int turn, List<String> locations, List<Salvo> salvoes) {
        Salvo salvo = new Salvo(turn, locations);
        gamePlayer.addSalvo(salvo);
        salvoes.add(salvo);
    }
}
