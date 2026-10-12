package com.example.examplemod.feature;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Schutz gegen Endlosschleifen beim automatischen Abwerfen: Wird dieselbe Sorte innerhalb kurzer
 * Zeit zu oft abgeworfen (weil der Spieler sie gleich wieder aufhebt), pausiert sie eine Weile.
 * Ohne Minecraft-Abhängigkeit, Zeit in Ticks des Aufrufers; daher testbar.
 */
public final class DropGuard<K> {
    private final int limit;
    private final long windowTicks;
    private final long pauseTicks;
    /** Je Sorte: Zeitpunkte und Gewichte der Abwürfe im Zeitfenster. */
    private final Map<K, Deque<long[]>> history = new HashMap<>();
    private final Map<K, Long> pausedUntil = new HashMap<>();

    /**
     * @param limit       so viel Gewicht (z.B. Anzahl Abwürfe) derselben Sorte im Zeitfenster löst die Pause aus
     * @param windowTicks Länge des Zeitfensters
     * @param pauseTicks  Dauer der Pause
     */
    public DropGuard(int limit, long windowTicks, long pauseTicks) {
        this.limit = Math.max(1, limit);
        this.windowTicks = windowTicks;
        this.pauseTicks = pauseTicks;
    }

    /** Darf diese Sorte jetzt abgeworfen werden? */
    public boolean allowed(K key, long now) {
        Long until = pausedUntil.get(key);
        if (until == null) return true;
        if (now < until) return false;
        pausedUntil.remove(key);
        history.remove(key);
        return true;
    }

    /** Merkt einen Abwurf mit Gewicht 1. Liefert {@code true}, wenn dadurch die Pause für diese Sorte beginnt. */
    public boolean record(K key, long now) { return record(key, now, 1); }

    /**
     * Merkt einen Abwurf mit Gewicht, z.B. ganzer Stapel schwer, einzelnes Item leicht.
     * Liefert {@code true}, wenn dadurch die Pause für diese Sorte beginnt.
     */
    public boolean record(K key, long now, int weight) {
        Deque<long[]> drops = history.computeIfAbsent(key, k -> new ArrayDeque<>());
        drops.addLast(new long[]{now, Math.max(1, weight)});
        while (!drops.isEmpty() && drops.peekFirst()[0] <= now - windowTicks) drops.removeFirst();
        long total = 0;
        for (long[] drop : drops) total += drop[1];
        if (total < limit) return false;
        pausedUntil.put(key, now + pauseTicks);
        history.remove(key);
        return true;
    }

    /** Verbleibende Pause in Ticks, 0 wenn nicht pausiert. */
    public long remainingPause(K key, long now) {
        Long until = pausedUntil.get(key);
        return until == null ? 0 : Math.max(0, until - now);
    }

    public void reset() {
        history.clear();
        pausedUntil.clear();
    }
}
