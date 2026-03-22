package com.codeoftheweb.salvo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {
    boolean existsByGame_Id(long gameId);
    List<Score> findByGame_IdOrderByIdAsc(long gameId);
}
