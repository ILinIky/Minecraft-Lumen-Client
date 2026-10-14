package com.example.examplemod.blueprint;

/**
 * One placement at a time, each confirmed by the server before the next one. A single rejection or timeout
 * only ends that attempt (the caller skips the position for a while); repeated failures within a short window
 * mean the server refuses building here, and the session stops until the player presses the key again.
 */
public final class LazyBuildGate {
    public enum State { IDLE, READY, WAITING, STOPPED }
    /** Failures within {@link #FAILURE_WINDOW} ticks that stop the session. */
    public static final int MAX_FAILURES = 3, FAILURE_WINDOW = 200, TIMEOUT = 60, FAILURE_PAUSE = 10;
    private State state = State.IDLE;
    private long sentAt, nextAt;
    private int confirmed;
    private final long[] failures = new long[MAX_FAILURES];
    private int failureCount;
    public State state() { return state; }
    public int confirmed() { return confirmed; }
    public boolean active() { return state == State.READY || state == State.WAITING; }
    public boolean toggle() {
        if (active()) { release(); return false; }
        release(); return begin(true);
    }
    public boolean begin(boolean requested) {
        if (!requested || state != State.IDLE) return false;
        state = State.READY; nextAt = 0; confirmed = 0; failureCount = 0; return true;
    }
    public boolean maySend(long tick) { return state == State.READY && tick >= nextAt; }
    public void submitted(long tick, int interval) {
        if (!maySend(tick)) throw new IllegalStateException("Placement already pending or cooling down");
        sentAt = tick; nextAt = tick + Math.max(4, interval); state = State.WAITING;
    }
    /** Server answer for the pending position; {@code false} = server kept or set a different block. */
    public void acknowledge(boolean matches, long tick) {
        if (state != State.WAITING) return;
        if (matches) { confirmed++; state = State.READY; } else fail(tick);
    }
    /** {@code true} once the pending attempt has waited too long; it then counts as a failure. */
    public boolean timedOut(long tick) {
        if (state != State.WAITING || tick - sentAt < TIMEOUT) return false;
        fail(tick); return true;
    }
    private void fail(long tick) {
        int recent = 0;
        for (int i = 0; i < failureCount; i++) if (tick - failures[i] < FAILURE_WINDOW) failures[recent++] = failures[i];
        failureCount = recent;
        if (failureCount == MAX_FAILURES) System.arraycopy(failures, 1, failures, 0, --failureCount);
        failures[failureCount++] = tick;
        if (failureCount >= MAX_FAILURES) { state = State.STOPPED; return; }
        state = State.READY; nextAt = Math.max(nextAt, tick + FAILURE_PAUSE);
    }
    public void stop() { state = State.STOPPED; }
    public void release() { state = State.IDLE; }
}
