package com.codeoftheweb.salvo;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String shipType;

    @ManyToOne
    @JoinColumn(name = "game_player_id")
    private GamePlayer gamePlayer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ship_locations", joinColumns = @JoinColumn(name = "ship_id"))
    @Column(name = "location")
    private List<String> locations = new ArrayList<>();

    public Ship() {
    }

    public Ship(String shipType, List<String> locations) {
        this.shipType = shipType;
        this.locations = locations;
    }

    public long getId() {
        return id;
    }

    public String getShipType() {
        return shipType;
    }

    public void setShipType(String shipType) {
        this.shipType = shipType;
    }

    public GamePlayer getGamePlayer() {
        return gamePlayer;
    }

    public void setGamePlayer(GamePlayer gamePlayer) {
        this.gamePlayer = gamePlayer;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }
}
