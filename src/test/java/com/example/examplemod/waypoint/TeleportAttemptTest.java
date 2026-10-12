package com.example.examplemod.waypoint;

import static com.example.examplemod.waypoint.TeleportAttempt.Result.*;

public final class TeleportAttemptTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static TeleportAttempt start(boolean integrated) {
        var attempt = new TeleportAttempt(integrated);
        check(attempt.begin(), "Initial request starts");
        return attempt;
    }

    public static void main(String[] args) {
        // Replay des beobachteten Fehlers: 39 Rücksetzungen dürfen keine 39 Wiederholungen auslösen.
        var attempt = start(false);
        check(attempt.serverPosition(false) == REJECTED, "First rejection ends attempt");
        for (int i = 0; i < 39; i++) {
            check(!attempt.begin(), "No second request after rejection");
            check(attempt.serverPosition(false) == REJECTED, "Repeated correction stays terminal");
            check(attempt.tick(true, true) == REJECTED, "Client target cannot undo rejection");
        }

        attempt = start(false);
        check(!attempt.begin(), "Repeated input while pending cannot restart");
        attempt.serverPosition(true);
        check(attempt.tick(true, false) == WAITING, "Server target still awaits loaded chunks");
        check(attempt.tick(true, true) == CONFIRMED, "Server target and loaded chunks confirm arrival");
        check(!attempt.begin(), "Completed attempt cannot resend");

        attempt = start(false);
        for (int i = 0; i < 99; i++) check(attempt.tick(true, true) == WAITING, "Observe delayed corrections");
        check(attempt.tick(true, true) == UNCONFIRMED, "Silence is not server confirmation");

        attempt = start(false);
        for (int i = 0; i < 99; i++) attempt.tick(true, true);
        check(attempt.serverPosition(false) == REJECTED, "Late correction is still rejection");
        check(attempt.tick(true, true) == REJECTED, "No false success at observation deadline");

        attempt = start(false);
        attempt.serverPosition(true);
        check(attempt.serverPosition(false) == REJECTED, "Correction before arrival overrides early confirmation");

        attempt = start(false);
        for (int i = 0; i < 199; i++) check(attempt.tick(false, false) == WAITING, "Finite wait for distant chunks");
        check(attempt.tick(false, false) == TIMED_OUT, "Missing chunks time out");
        check(!attempt.begin(), "Timeout never retries");

        attempt = start(true);
        for (int i = 0; i < 199; i++) check(attempt.tick(true, true) == WAITING, "Integrated server must really execute");
        check(attempt.tick(true, true) == TIMED_OUT, "No fake singleplayer success");

        attempt = start(true);
        attempt.serverPosition(true);
        check(attempt.tick(false, false) == WAITING, "Dimension transition still pending");
        check(attempt.tick(true, true) == CONFIRMED, "Integrated execution plus arrival confirms");

        attempt = start(false);
        check(attempt.cancel() == CANCELLED, "Disconnect/respawn cancels");
        check(attempt.serverPosition(true) == CANCELLED, "Stale callback cannot revive cancelled attempt");
        check(!attempt.begin(), "Cancelled attempt cannot resend");

        attempt = new TeleportAttempt(false);
        check(attempt.tick(true, true) == WAITING && attempt.elapsedTicks() == 0, "No progress before request");
        attempt.cancel();
        check(!attempt.begin(), "Cancel before request prevents sending");

        System.out.println("TeleportAttempt: " + checks + " checks passed (including 39-correction regression)");
    }
}
