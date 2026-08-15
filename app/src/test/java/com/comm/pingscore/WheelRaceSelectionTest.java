package com.comm.pingscore;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WheelRaceSelectionTest {
    @Test
    public void selectsTwoPlayersInTapOrder() {
        WheelRaceSelection selection = new WheelRaceSelection(2);

        assertTrue(selection.toggle(1, 4));
        assertTrue(selection.toggle(3, 4));

        assertEquals(Arrays.asList(1, 3), selection.getSelectedIndexes());
        assertEquals(2, selection.size());
        assertTrue(selection.isComplete());
    }

    @Test
    public void tappingSelectedPlayerCancelsSelection() {
        WheelRaceSelection selection = new WheelRaceSelection(2);
        selection.toggle(1, 4);
        selection.toggle(3, 4);

        assertTrue(selection.toggle(1, 4));

        assertEquals(Arrays.asList(3), selection.getSelectedIndexes());
        assertFalse(selection.isComplete());
    }

    @Test
    public void refusesThirdPlayerWithoutChangingTheFirstTwo() {
        WheelRaceSelection selection = new WheelRaceSelection(2);
        selection.toggle(0, 4);
        selection.toggle(1, 4);

        assertFalse(selection.toggle(2, 4));

        assertEquals(Arrays.asList(0, 1), selection.getSelectedIndexes());
        assertFalse(selection.isSelected(2));
    }

    @Test
    public void rejectsIndexesOutsideThePlayerList() {
        WheelRaceSelection selection = new WheelRaceSelection(2);

        assertFalse(selection.toggle(-1, 2));
        assertFalse(selection.toggle(2, 2));
        assertEquals(0, selection.size());
    }
}
