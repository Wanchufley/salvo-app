package com.codeoftheweb.salvo;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.Collections;

@Entity
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private Date creationDate = new Date();

	@OneToMany(mappedBy = "game", fetch = FetchType.EAGER)
	private Set<GamePlayer> gamePlayers = new HashSet<>();

	@OneToMany(mappedBy = "game", fetch = FetchType.EAGER)
	private Set<Score> scores = new HashSet<>();

    public Game() {
    }

    public long getId() {
        return id;
    }

    public Date getCreationDate() {
        return creationDate;
    }

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Set<GamePlayer> getGamePlayers() {
		return gamePlayers;
	}

	public Set<Score> getScores() {
		return Collections.unmodifiableSet(scores);
	}

	public List<Player> getPlayers() {
		return gamePlayers.stream().map(GamePlayer::getPlayer).toList();
	}
}
