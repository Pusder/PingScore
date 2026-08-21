package com.comm.pingscore;

import androidx.lifecycle.ViewModel;

/** Holds the live match state across Activity recreation. */
public final class MatchViewModel extends ViewModel {
    private MatchEngine engine;
    private String mode = "regular";
    private String playerOne = "玩家 1";
    private String playerTwo = "玩家 2";

    public boolean hasSession() {
        return engine != null;
    }

    public MatchEngine getEngine() {
        return engine;
    }

    public String getMode() {
        return mode;
    }

    public String getPlayerOne() {
        return playerOne;
    }

    public String getPlayerTwo() {
        return playerTwo;
    }

    public void update(MatchEngine engine, String mode, String playerOne, String playerTwo) {
        this.engine = engine;
        this.mode = mode;
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
    }
}
