package com.comm.pingscore;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MatchUiStylerTest {
    @Test
    public void historySubtitleIncludesModeBestOfAndSetCount() {
        assertEquals("正规赛 · BO3 · 3 局",
                MatchUiStyler.historySubtitle("regular", 3, 3));
    }

    @Test
    public void resultLabelKeepsSetScoreAndWinnerReadable() {
        assertEquals("第 2 局   11:9   玩家 1 胜",
                MatchUiStyler.resultLabel(2, 11, 9, "玩家 1"));
    }

    @Test
    public void teamHistoryUsesItsOwnSubtitleWhenThereIsNoBestOf() {
        assertEquals("团队赛 · 对局记录",
                MatchUiStyler.historySubtitle("team", 0, 0));
    }
}
