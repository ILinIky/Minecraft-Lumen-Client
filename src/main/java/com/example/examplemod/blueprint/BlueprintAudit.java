package com.example.examplemod.blueprint;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Fresh, bounded whole-plan pass. Status ordinals are supplied by the existing comparator. */
public final class BlueprintAudit {
    private final int[] counts;
    public record Issue(int index, int status) {}
    private final List<Issue> examples = new ArrayList<>();
    private int cursor, total;
    private boolean started, running, stale;
    public BlueprintAudit(int statuses) { counts = new int[statuses]; }
    public void start(int total) {
        this.total = total; cursor = 0; started = running = true; stale = false;
        Arrays.fill(counts, 0); examples.clear();
    }
    public void step(int budget, IntUnaryOperator compare, int correct) {
        if (!running) return;
        int end = Math.min(total, cursor + budget);
        while (cursor < end) {
            int status = compare.applyAsInt(cursor);
            counts[status]++;
            if (status != correct && examples.size() < 64) examples.add(new Issue(cursor, status));
            cursor++;
        }
        if (cursor == total) running = false;
    }
    public void invalidate() { if (started) stale = true; }
    public void observedChange(int index) {
        if (started && (!running || index < cursor)) stale = true;
    }
    public boolean running() { return running; }
    public boolean started() { return started; }
    public boolean stale() { return stale; }
    public int percent() { return total == 0 ? 100 : (int)(100L * cursor / total); }
    public int count(int status) { return counts[status]; }
    public List<Issue> examples() { return List.copyOf(examples); }
}
