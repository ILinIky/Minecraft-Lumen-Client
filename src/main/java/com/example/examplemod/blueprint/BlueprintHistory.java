package com.example.examplemod.blueprint;

import java.util.*;

/** Session-local undo, bounded to twenty compact edit snapshots; never stores world blocks. */
public final class BlueprintHistory {
    public record Entry(String label, BitSet accepted, BitSet ignored, Map<String, String> replacements) {}
    private final Deque<Entry> entries = new ArrayDeque<>();
    public void remember(String label, int[] accepted, int[] ignored, Map<String, String> replacements) {
        entries.addFirst(new Entry(label, bits(accepted), bits(ignored), Map.copyOf(replacements)));
        while (entries.size() > 20) entries.removeLast();
    }
    private static BitSet bits(int[] values) {
        BitSet bits = new BitSet();
        for (int value : values) bits.set(value);
        return bits;
    }
    public Entry latest() { return entries.peekFirst(); }
    public void removeLatest() { entries.pollFirst(); }
    public List<String> labels() { return entries.stream().map(Entry::label).toList(); }
    public void clear() { entries.clear(); }
}
