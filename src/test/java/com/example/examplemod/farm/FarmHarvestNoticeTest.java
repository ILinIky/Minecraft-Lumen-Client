package com.example.examplemod.farm;

import com.google.gson.Gson;
import java.util.*;

public final class FarmHarvestNoticeTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static FarmRecord farm(int ripe) {
        List<FarmRecord.Plot> plots = new ArrayList<>();
        for (int x = 0; x < 100; x++) plots.add(new FarmRecord.Plot(x, 64, 0));
        return new FarmRecord("id", "Weizen", "overworld", plots, ripe, 100, 1000, -1);
    }
    public static void main(String[] args) {
        var growing = farm(79);
        var ready = growing.observed(80, 100, 2000);
        var first = FarmHarvestNotice.evaluate(growing, ready, 80, true);
        check(first.announce() && first.notified(), "Threshold reached sends first notification");
        var alerted = ready.withHarvestNotified(first.notified());
        check(!FarmHarvestNotice.evaluate(alerted, alerted.observed(100, 100, 3000), 80, true).announce(), "Further growth does not repeat notification");
        for (int i = 0; i < 100; i++) check(!FarmHarvestNotice.evaluate(alerted, alerted, 80, true).announce(), "Repeated loaded observation stays silent");
        var allReady = alerted.observed(100, 100, 3000);
        var partial = allReady.observed(95, 95, 4000);
        check(FarmHarvestNotice.evaluate(allReady, partial, 80, true).notified(), "Small harvest above threshold does not spam");
        var harvested = partial.observed(20, 20, 5000);
        var rearm = FarmHarvestNotice.evaluate(partial, harvested, 80, true);
        check(!rearm.notified() && !rearm.announce(), "Harvest below threshold rearms without immediate notification");
        harvested = harvested.withHarvestNotified(rearm.notified());
        check(FarmHarvestNotice.evaluate(harvested, harvested.observed(80, 100, 6000), 80, true).announce(), "Next crop cycle can notify again");
        check(!FarmHarvestNotice.evaluate(alerted, alerted, 50, true).announce(), "Lower threshold does not repeat an existing notice");
        check(FarmHarvestNotice.evaluate(alerted, alerted, 100, true).notified(), "Raising threshold alone is not harvest");
        check(!FarmHarvestNotice.evaluate(growing, ready, 80, false).announce(), "Disabled setting stays quiet");
        check(!FarmHarvestNotice.evaluate(partial, partial.observed(0, 0, 5000), 80, false).notified(), "Harvest still rearms while notifications are muted");
        check(!FarmHarvestNotice.evaluate(alerted, alerted, 80, true).announce(), "Disable and re-enable does not reset latch");
        var loaded = new Gson().fromJson(new Gson().toJson(alerted), FarmRecord.class);
        check(loaded.harvestNotified() && !FarmHarvestNotice.evaluate(loaded, loaded, 80, true).announce(), "Save/reload preserves notice latch");
        check(loaded.renamed("Karotten").withWaypoint(12).harvestNotified(), "Rename and navigation preserve latch");
        check(new FarmObservation(loaded).finish(loaded, 5000).harvestNotified(), "Measurement construction preserves latch until policy evaluates");
        var legacy = new Gson().toJsonTree(growing).getAsJsonObject(); legacy.remove("harvestNotified");
        check(!new Gson().fromJson(legacy, FarmRecord.class).harvestNotified(), "Old files migrate with unnotified state");
        var more = new ArrayList<>(alerted.plots()); more.add(new FarmRecord.Plot(100, 64, 0));
        var enlarged = new FarmRecord("id", "Weizen", "overworld", more, 79, 101, 3000, -1).withHarvestNotified(true);
        check(FarmHarvestNotice.evaluate(alerted, enlarged, 80, true).notified(), "Area changes never simulate harvest");
        check(FarmAreas.merge(List.of(alerted, enlarged), 0).getFirst().harvestNotified(), "Area union preserves notification latch");
        check(FarmDetection.deduplicate(List.of(alerted, enlarged)).getFirst().harvestNotified(), "Duplicate repair preserves notification latch");
        check(FarmHarvestNotice.evaluate(farm(100), farm(100), 100, true).announce(), "Already mature farm notifies on first valid observation");
        check(!FarmHarvestNotice.evaluate(farm(0), farm(0), 1, true).announce(), "Empty crops never notify");
        System.out.println("FarmHarvestNoticeTest: " + checks + " checks passed");
    }
}
