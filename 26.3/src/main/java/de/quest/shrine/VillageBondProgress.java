package de.quest.shrine;

/** Read-only personal progress; trades never become completed requests. */
public record VillageBondProgress(int actualCompletions, int qualifiedTrades, VillageBondLevel level) {
    public int remainingRequestsToTrusted() { return Math.max(0, VillageBondProgression.TRUSTED_REQUESTS-actualCompletions); }
    public int remainingTradesToTrusted() { return Math.max(0, VillageBondProgression.MAX_TRADES-qualifiedTrades); }
    public boolean tradePathAvailable() { return actualCompletions >= 1; }
}
