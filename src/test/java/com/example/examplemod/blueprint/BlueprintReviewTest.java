package com.example.examplemod.blueprint;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BlueprintReviewTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var history = new BlueprintHistory();
        int[] accepted = {1, 55}, ignored = {3};
        var swaps = new TreeMap<>(Map.of("oak", "stone"));
        history.remember("Materialtausch", accepted, ignored, swaps);
        accepted[0] = 9; swaps.clear();
        check(history.latest().accepted().get(1) && !history.latest().accepted().get(9), "Snapshot independent of input arrays");
        check(history.latest().replacements().get("oak").equals("stone"), "Replacements preserved");
        check(history.latest().ignored().get(3), "Hidden cells preserved alongside accepted cells");
        history.remember("Ausblenden", new int[]{1}, new int[]{3, 4}, Map.of());
        check(history.labels().getFirst().equals("Ausblenden"), "Undo label names latest edit");
        history.removeLatest();
        check(history.latest().label().equals("Materialtausch"), "Undo runs newest first");
        for (int i = 0; i < 30; i++) history.remember("Edit " + i, new int[0], new int[0], Map.of());
        check(history.labels().size() == 20 && history.labels().getLast().equals("Edit 10"), "Memory bounded to last twenty edits");
        history.clear();
        check(history.latest() == null && history.labels().isEmpty(), "No history from another plan");

        // 0 = correct, 1 = missing, 2 = wrong state, 3 = unloaded.
        var audit = new BlueprintAudit(4);
        audit.start(10000);
        AtomicInteger calls = new AtomicInteger();
        audit.step(1488, i -> { calls.incrementAndGet(); return i % 4; }, 0);
        check(calls.get() == 1488 && audit.running(), "Strict per-tick comparison budget");
        check(audit.percent() == 14, "Progress uses whole plan, not the selected layer");
        check(audit.examples().size() == 64, "Rest list bounded");
        check(audit.examples().getFirst().index() == 1 && audit.examples().getFirst().status() == 1, "Rest entries preserve observed reason");
        audit.observedChange(9000);
        check(!audit.stale(), "Future cells will still be checked fresh");
        audit.observedChange(0);
        check(audit.stale(), "Change behind scan cursor invalidates result");
        while (audit.running()) audit.step(1488, i -> { calls.incrementAndGet(); return i % 4; }, 0);
        check(calls.get() == 10000, "Every cell checked once without overscan");
        for (int status = 0; status < 4; status++) check(audit.count(status) == 2500, "Counts include all categories, including unloaded");
        check(audit.percent() == 100 && audit.stale(), "Stale completion never becomes a fresh success");
        audit.start(3);
        check(!audit.stale() && audit.examples().isEmpty() && audit.count(1) == 0, "Recheck discards old result");
        audit.step(1488, i -> 0, 0);
        check(!audit.running() && audit.count(0) == 3, "All confirmed positions finish");
        audit.observedChange(2);
        check(audit.stale(), "Later removal invalidates completed result");
        audit.start(0); audit.step(1488, i -> { throw new AssertionError("Empty scan"); }, 0);
        check(!audit.running() && audit.percent() == 100, "Empty plan finishes safely");
        audit.invalidate(); check(audit.stale(), "Chunk unload and edits invalidate results");
        System.out.println("BlueprintReviewTest: " + checks + " checks passed");
    }
}
