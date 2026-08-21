package com.comm.pingscore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MatchEngineTest {
    @Test
    public void gameRequiresTwoPointLeadAndResetsForNextGame() {
        MatchEngine engine = new MatchEngine(3, 11);
        engine.start();
        for (int i = 0; i < 10; i++) engine.addPoint(0);
        for (int i = 0; i < 10; i++) engine.addPoint(1);
        engine.addPoint(0);
        assertEquals(11, engine.getCurrentOne());
        assertEquals(10, engine.getCurrentTwo());
        engine.addPoint(0);
        assertEquals(1, engine.getWinsOne());
        assertEquals(0, engine.getCurrentOne());
        assertEquals(2, engine.getGameNumber());
    }

    @Test
    public void deuceChangesServerEveryPoint() {
        MatchEngine engine = new MatchEngine(3, 11);
        engine.start();
        for (int i = 0; i < 10; i++) engine.addPoint(0);
        for (int i = 0; i < 10; i++) engine.addPoint(1);
        assertEquals(0, engine.getCurrentServer());
        engine.addPoint(0);
        assertEquals(1, engine.getCurrentServer());
    }

    @Test
    public void bestOfSevenEndsAfterFourGameWins() {
        MatchEngine engine = new MatchEngine(7, 1);
        engine.start();
        for (int game = 0; game < 4; game++) {
            engine.addPoint(0);
            engine.addPoint(0);
        }
        assertEquals(4, engine.getWinsOne());
        assertTrue(engine.isFinished());
    }

    @Test
    public void pauseLocksScoringAndCanOnlyBeUsedOnce() {
        MatchEngine engine = new MatchEngine(3, 11);
        engine.start();
        engine.pause(0);
        assertEquals(0, engine.getPauseOwner());
        boolean locked = false;
        try {
            engine.addPoint(1);
        } catch (IllegalStateException expected) {
            locked = true;
        }
        assertTrue(locked);
        engine.resume();
        engine.pause(0);
        assertEquals(-1, engine.getPauseOwner());
        engine.resume();
        engine.pause(1);
        assertTrue(engine.isPauseUsed(1));
        assertEquals(1, engine.getPauseOwner());
    }

    @Test
    public void entertainmentRulesUseConfiguredTargetAndServeInterval() {
        MatchEngine engine = new MatchEngine(3, 21, 5);
        engine.start();
        for (int i = 0; i < 5; i++) engine.addPoint(0);
        assertEquals(1, engine.getCurrentServer());
        for (int i = 0; i < 20; i++) engine.addPoint(1);
        assertEquals(20, engine.getCurrentTwo());
        assertEquals(21, engine.getTargetScore());
        assertEquals(5, engine.getServeInterval());
    }

    @Test
    public void lastScoreActionCanBeUndone() {
        MatchEngine engine = new MatchEngine(1, 11);
        engine.start();
        engine.addPoint(0);
        assertTrue(engine.undoLastAction());
        assertEquals(0, engine.getCurrentOne());
        assertTrue(!engine.undoLastAction());
    }
}
