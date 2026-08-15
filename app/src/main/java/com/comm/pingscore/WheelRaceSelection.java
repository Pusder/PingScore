package com.comm.pingscore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maintains the ordered player indexes selected for the next wheel-race pairing. */
public final class WheelRaceSelection {
    private final int maxSelections;
    private final List<Integer> selectedIndexes = new ArrayList<>();

    public WheelRaceSelection(int maxSelections) {
        if (maxSelections < 1) {
            throw new IllegalArgumentException("maxSelections must be positive");
        }
        this.maxSelections = maxSelections;
    }

    public boolean toggle(int index, int itemCount) {
        if (index < 0 || index >= itemCount) return false;
        if (selectedIndexes.remove(Integer.valueOf(index))) return true;
        if (selectedIndexes.size() >= maxSelections) return false;
        selectedIndexes.add(index);
        return true;
    }

    public boolean isSelected(int index) {
        return selectedIndexes.contains(index);
    }

    public boolean isComplete() {
        return selectedIndexes.size() == maxSelections;
    }

    public int size() {
        return selectedIndexes.size();
    }

    public List<Integer> getSelectedIndexes() {
        return Collections.unmodifiableList(new ArrayList<>(selectedIndexes));
    }
}
