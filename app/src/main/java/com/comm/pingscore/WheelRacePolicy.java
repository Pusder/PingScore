package com.comm.pingscore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure validation rules for the player pool and the next wheel-race pairing. */
public final class WheelRacePolicy {
    private WheelRacePolicy() {
    }

    public static List<String> normalizePool(Collection<String> names) {
        List<String> normalized = new ArrayList<>();
        if (names == null) return normalized;
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null) continue;
            String value = name.trim();
            if (!value.isEmpty() && seen.add(value)) normalized.add(value);
        }
        return normalized;
    }

    public static boolean hasValidPool(List<String> players) {
        return normalizePool(players).size() >= 2;
    }

    public static boolean canStartPair(List<String> players, int firstIndex, int secondIndex) {
        List<String> normalized = normalizePool(players);
        if (normalized.size() < 2) return false;
        if (firstIndex < 0 || secondIndex < 0
                || firstIndex >= normalized.size() || secondIndex >= normalized.size()) {
            return false;
        }
        return firstIndex != secondIndex;
    }
}
