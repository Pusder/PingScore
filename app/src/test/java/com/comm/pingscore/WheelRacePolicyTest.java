package com.comm.pingscore;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WheelRacePolicyTest {
    @Test
    public void acceptsTwoDistinctPlayersAsAValidPool() {
        assertTrue(WheelRacePolicy.hasValidPool(Arrays.asList("Alice", "Bob")));
    }

    @Test
    public void rejectsDuplicatePlayersAsAValidPool() {
        assertFalse(WheelRacePolicy.hasValidPool(Arrays.asList("Alice", "Alice")));
    }

    @Test
    public void rejectsSelectingTheSamePlayerTwice() {
        assertFalse(WheelRacePolicy.canStartPair(Arrays.asList("Alice", "Bob"), 0, 0));
    }

    @Test
    public void acceptsAnyTwoDistinctPlayersRegardlessOfOrder() {
        assertTrue(WheelRacePolicy.canStartPair(Arrays.asList("Alice", "Bob"), 1, 0));
    }

    @Test
    public void normalizesNamesWithoutChangingFirstSeenOrder() {
        List<String> normalized = WheelRacePolicy.normalizePool(
                Arrays.asList(" Alice ", "Bob", "Alice", "", "Carol"));
        assertEquals(Arrays.asList("Alice", "Bob", "Carol"), normalized);
    }
}
