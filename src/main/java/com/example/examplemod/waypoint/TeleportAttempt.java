package com.example.examplemod.waypoint;

/** Ein Versuch, danach nur beobachten. Terminale Ergebnisse können nicht wieder gestartet werden. */
final class TeleportAttempt {
    enum Result { WAITING, CONFIRMED, UNCONFIRMED, REJECTED, TIMED_OUT, CANCELLED }

    private final boolean integratedServer;
    private boolean started;
    private boolean serverConfirmed;
    private boolean observedArrival;
    private int stableTicks;
    private int elapsed;
    private Result result = Result.WAITING;

    TeleportAttempt(boolean integratedServer) { this.integratedServer = integratedServer; }

    /** Die einzige Freigabe zum Auslösen des Teleports; nie aus einem Tick oder Paket wiederholen. */
    boolean begin() {
        if (started || result != Result.WAITING) return false;
        started = true;
        return true;
    }

    Result serverPosition(boolean atTarget) {
        if (started && result == Result.WAITING) {
            if (atTarget) serverConfirmed = true;
            else result = Result.REJECTED;
        }
        return result;
    }

    Result tick(boolean atTarget, boolean chunksLoaded) {
        if (!started || result != Result.WAITING) return result;
        elapsed++;
        stableTicks = atTarget && chunksLoaded ? stableTicks + 1 : 0;
        if (stableTicks >= 5) observedArrival = true;
        if (serverConfirmed && atTarget && chunksLoaded) result = Result.CONFIRMED;
        else if (!integratedServer && elapsed >= 100 && observedArrival) result = Result.UNCONFIRMED;
        else if (elapsed >= 200) result = Result.TIMED_OUT;
        return result;
    }

    Result cancel() {
        if (result == Result.WAITING) result = Result.CANCELLED;
        return result;
    }

    int elapsedTicks() { return elapsed; }
    Result result() { return result; }
}
