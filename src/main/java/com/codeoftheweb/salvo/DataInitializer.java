package com.codeoftheweb.salvo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DataInitializer {
    public static void main(String[] args) {
        SpringApplication.run(DataInitializer.class);
    }

    @Bean
    public CommandLineRunner initData(PlayerRepository repository) {
        return (args) -> {
            repository.save(new Player("j.bauer@ctu.gov"));
            repository.save(new Player("c.obrian@ctu.gov"));
            repository.save(new Player("kim_bauer@gmail.com"));
            repository.save(new Player("t.almeida@ctu.gov"));
        };
    }
}
