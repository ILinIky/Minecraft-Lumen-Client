package com.example.examplemod.feature;

/** Ohne gestarteten Minecraft-Client ausführbare Prüfung des Schleifenschutzes beim Auto-Abwurf. */
public final class DropGuardTest {
    private static int assertions;

    public static void main(String[] args) {
        pausesAfterRepeatedDrops();
        windowForgetsOldDrops();
        sortsAreIndependent();
        weights();
        System.out.println("DropGuardTest: " + assertions + " assertions passed");
    }

    private static void pausesAfterRepeatedDrops() {
        DropGuard<String> guard = new DropGuard<>(3, 100, 50);
        check(!guard.record("erde", 0) && !guard.record("erde", 10), "Unter der Grenze keine Pause");
        check(guard.record("erde", 20), "Dritter Abwurf im Zeitfenster startet die Pause");
        check(!guard.allowed("erde", 21) && guard.remainingPause("erde", 21) == 49, "Während der Pause gesperrt");
        check(!guard.allowed("erde", 69), "Bis zum Ende der Pause gesperrt");
        check(guard.allowed("erde", 70) && guard.remainingPause("erde", 70) == 0, "Nach der Pause wieder erlaubt");
        check(!guard.record("erde", 71), "Nach der Pause beginnt die Zählung neu");
    }

    private static void windowForgetsOldDrops() {
        DropGuard<String> guard = new DropGuard<>(3, 100, 50);
        guard.record("kies", 0);
        guard.record("kies", 50);
        check(!guard.record("kies", 120), "Abwürfe außerhalb des Zeitfensters zählen nicht");
        check(guard.record("kies", 140), "Drei Abwürfe innerhalb von 100 Ticks lösen aus");
    }

    private static void sortsAreIndependent() {
        DropGuard<String> guard = new DropGuard<>(2, 100, 50);
        guard.record("erde", 0);
        guard.record("erde", 1);
        check(!guard.allowed("erde", 2) && guard.allowed("kies", 2), "Pause gilt nur für die eine Sorte");
        guard.reset();
        check(guard.allowed("erde", 2), "Zurücksetzen hebt Pausen auf");
    }

    private static void weights() {
        // Wie im Auto-Abwurf: Stapel wiegt 16, einzelnes Item 1, Grenze 96.
        DropGuard<String> guard = new DropGuard<>(96, 400, 600);
        for (int i = 0; i < 5; i++) guard.record("stein", i, 16);
        check(guard.allowed("stein", 5), "Fünf Stapel bleiben unter der Grenze");
        boolean paused = false;
        for (int i = 0; i < 16 && !paused; i++) paused = guard.record("stein", 10 + i, 1);
        check(paused, "Einzelne Items zählen anteilig mit und lösen die Pause aus");
        DropGuard<String> singles = new DropGuard<>(96, 400, 600);
        boolean early = false;
        for (int i = 0; i < 54; i++) early |= singles.record("stein", i, 1);
        check(!early, "54 einzelne Items (Rest über \"Behalten\") lösen keine Pause aus");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
