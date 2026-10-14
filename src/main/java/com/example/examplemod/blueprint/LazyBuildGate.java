package com.example.examplemod.blueprint;

/** One placement at a time; failures require an explicit new toggle, never automatic retries. */
public final class LazyBuildGate {
    public enum State { IDLE, READY, WAITING, STOPPED }
    private State state = State.IDLE;
    private long sentAt, nextAt;
    private int confirmed;
    public State state() { return state; }
    public int confirmed() { return confirmed; }
    public boolean active() { return state == State.READY || state == State.WAITING; }
    public boolean toggle() {
        if (active()) { release(); return false; }
        release(); return begin(true);
    }
    public boolean begin(boolean requested) {
        if (!requested || state != State.IDLE) return false;
        state = State.READY; nextAt = 0; confirmed = 0; return true;
    }
    public boolean maySend(long tick) { return state == State.READY && tick >= nextAt; }
    public void submitted(long tick, int interval) {
        if (!maySend(tick)) throw new IllegalStateException("Placement already pending or cooling down");
        sentAt = tick; nextAt = tick + Math.max(4, interval); state = State.WAITING;
    }
    public void acknowledge(boolean matches) {
        if (state != State.WAITING) return;
        if (matches) { confirmed++; state = State.READY; } else state = State.STOPPED;
    }
    public boolean timedOut(long tick) {
        if (state != State.WAITING || tick - sentAt < 60) return false;
        state = State.STOPPED; return true;
    }
    public void stop() { state = State.STOPPED; }
    public void release() { state = State.IDLE; }
}
