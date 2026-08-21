package com.comm.pingscore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure match rules shared by regular matches and wheel-race pairings. */
public final class MatchEngine {
    public static final int REGULAR_TARGET = 11;

    public static final class GameRecord {
        public final int playerOne;
        public final int playerTwo;
        public final int winner;

        GameRecord(int playerOne, int playerTwo, int winner) {
            this.playerOne = playerOne;
            this.playerTwo = playerTwo;
            this.winner = winner;
        }
    }

    private int bestOf;
    private int targetScore;
    private int serveInterval;
    private int currentOne;
    private int currentTwo;
    private int winsOne;
    private int winsTwo;
    private int startingServer;
    private int pauseOwner = -1;
    private final boolean[] pauseUsed = new boolean[2];
    private boolean started;
    private boolean finished;
    private final List<GameRecord> gameRecords = new ArrayList<>();
    private Snapshot previousSnapshot;

    public MatchEngine(int bestOf, int targetScore) {
        this(bestOf, targetScore, 2);
    }

    public MatchEngine(int bestOf, int targetScore, int serveInterval) {
        configure(bestOf, targetScore, serveInterval);
    }

    public void configure(int bestOf, int targetScore) {
        configure(bestOf, targetScore, 2);
    }

    public void configure(int bestOf, int targetScore, int serveInterval) {
        if (bestOf != 1 && bestOf != 3 && bestOf != 5 && bestOf != 7) {
            throw new IllegalArgumentException("bestOf must be 1, 3, 5 or 7");
        }
        if (targetScore < 1) {
            throw new IllegalArgumentException("targetScore must be positive");
        }
        if (serveInterval != 1 && serveInterval != 2 && serveInterval != 5) {
            throw new IllegalArgumentException("serveInterval must be 1, 2 or 5");
        }
        this.bestOf = bestOf;
        this.targetScore = targetScore;
        this.serveInterval = serveInterval;
        reset();
    }

    public void reset() {
        currentOne = 0;
        currentTwo = 0;
        winsOne = 0;
        winsTwo = 0;
        startingServer = 0;
        pauseOwner = -1;
        pauseUsed[0] = false;
        pauseUsed[1] = false;
        started = false;
        finished = false;
        gameRecords.clear();
        previousSnapshot = null;
    }

    public void start() {
        if (!finished) {
            started = true;
        }
    }

    public void restoreState(int currentOne, int currentTwo, int winsOne, int winsTwo,
                             int startingServer, int pauseOwner, boolean pauseUsedOne,
                             boolean pauseUsedTwo, boolean started, boolean finished,
                             List<GameRecord> records) {
        this.currentOne = currentOne;
        this.currentTwo = currentTwo;
        this.winsOne = winsOne;
        this.winsTwo = winsTwo;
        this.startingServer = startingServer;
        this.pauseOwner = pauseOwner;
        pauseUsed[0] = pauseUsedOne;
        pauseUsed[1] = pauseUsedTwo;
        this.started = started;
        this.finished = finished;
        gameRecords.clear();
        gameRecords.addAll(records);
        previousSnapshot = null;
    }

    public void addPoint(int player) {
        ensureScoringAllowed();
        previousSnapshot = new Snapshot(this);
        if (player == 0) {
            currentOne++;
        } else if (player == 1) {
            currentTwo++;
        } else {
            throw new IllegalArgumentException("player must be 0 or 1");
        }
        settleGameIfNeeded();
    }

    public void subtractPoint(int player) {
        ensureScoringAllowed();
        previousSnapshot = new Snapshot(this);
        if (player == 0 && currentOne > 0) {
            currentOne--;
        } else if (player == 1 && currentTwo > 0) {
            currentTwo--;
        }
    }

    public boolean undoLastAction() {
        if (previousSnapshot == null || !started || pauseOwner >= 0) {
            return false;
        }
        previousSnapshot.restore(this);
        previousSnapshot = null;
        return true;
    }

    public void toggleStartingServer() {
        if (started && !finished && pauseOwner < 0) {
            startingServer = 1 - startingServer;
        }
    }

    public void setCurrentServer(int server) {
        if (server < 0 || server > 1 || !started || finished || pauseOwner >= 0) {
            return;
        }
        int points = currentOne + currentTwo;
        startingServer = (server - points / getCurrentServeInterval() + 2) % 2;
    }

    public void pause(int player) {
        if (!started || finished || pauseOwner >= 0 || player < 0 || player > 1) {
            return;
        }
        if (pauseUsed[player]) {
            return;
        }
        pauseUsed[player] = true;
        pauseOwner = player;
    }

    public void resume() {
        pauseOwner = -1;
    }

    private void ensureScoringAllowed() {
        if (!started || finished || pauseOwner >= 0) {
            throw new IllegalStateException("match is not available for scoring");
        }
    }

    private void settleGameIfNeeded() {
        int high = Math.max(currentOne, currentTwo);
        int low = Math.min(currentOne, currentTwo);
        if (high < targetScore || high - low < 2) {
            return;
        }
        int winner = currentOne > currentTwo ? 0 : 1;
        gameRecords.add(new GameRecord(currentOne, currentTwo, winner));
        if (winner == 0) {
            winsOne++;
        } else {
            winsTwo++;
        }
        if (winsOne > bestOf / 2 || winsTwo > bestOf / 2) {
            finished = true;
            return;
        }
        startingServer = 1 - startingServer;
        currentOne = 0;
        currentTwo = 0;
    }

    public void adjustGameRecord(int index, int playerOne, int playerTwo) {
        if (!started || index < 0 || index >= gameRecords.size()) {
            throw new IllegalArgumentException("game record is not editable");
        }
        int high = Math.max(playerOne, playerTwo);
        int low = Math.min(playerOne, playerTwo);
        if (playerOne < 0 || playerTwo < 0 || high < targetScore || high - low < 2) {
            throw new IllegalArgumentException("invalid game score");
        }
        gameRecords.set(index, new GameRecord(playerOne, playerTwo,
                playerOne > playerTwo ? 0 : 1));
        winsOne = 0;
        winsTwo = 0;
        for (GameRecord record : gameRecords) {
            if (record.winner == 0) winsOne++;
            else winsTwo++;
        }
        finished = winsOne > bestOf / 2 || winsTwo > bestOf / 2;
        previousSnapshot = null;
    }

    public int getBestOf() { return bestOf; }
    public int getTargetScore() { return targetScore; }
    public int getServeInterval() { return serveInterval; }
    public int getCurrentOne() { return currentOne; }
    public int getCurrentTwo() { return currentTwo; }
    public int getWinsOne() { return winsOne; }
    public int getWinsTwo() { return winsTwo; }
    public int getPauseOwner() { return pauseOwner; }
    public int getStartingServer() { return startingServer; }
    public boolean isPauseUsed(int player) { return pauseUsed[player]; }
    public boolean isStarted() { return started; }
    public boolean isFinished() { return finished; }
    public int getGameNumber() { return gameRecords.size() + 1; }
    public int getCurrentServer() {
        int points = currentOne + currentTwo;
        return (startingServer + points / getCurrentServeInterval()) % 2;
    }
    public List<GameRecord> getGameRecords() {
        return Collections.unmodifiableList(gameRecords);
    }

    private int getCurrentServeInterval() {
        int deuceScore = Math.max(0, targetScore - 1);
        return currentOne >= deuceScore && currentTwo >= deuceScore ? 1 : serveInterval;
    }

    private static final class Snapshot {
        private final int currentOne;
        private final int currentTwo;
        private final int winsOne;
        private final int winsTwo;
        private final int startingServer;
        private final boolean finished;
        private final int recordCount;

        Snapshot(MatchEngine engine) {
            currentOne = engine.currentOne;
            currentTwo = engine.currentTwo;
            winsOne = engine.winsOne;
            winsTwo = engine.winsTwo;
            startingServer = engine.startingServer;
            finished = engine.finished;
            recordCount = engine.gameRecords.size();
        }

        void restore(MatchEngine engine) {
            engine.currentOne = currentOne;
            engine.currentTwo = currentTwo;
            engine.winsOne = winsOne;
            engine.winsTwo = winsTwo;
            engine.startingServer = startingServer;
            engine.finished = finished;
            while (engine.gameRecords.size() > recordCount) {
                engine.gameRecords.remove(engine.gameRecords.size() - 1);
            }
        }
    }
}
