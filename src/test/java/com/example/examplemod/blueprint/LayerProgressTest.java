package com.example.examplemod.blueprint;

public final class LayerProgressTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var delay = new LayerProgress.Delay();
        var done = new LayerProgress(12, 12, 0, true);
        check(!new LayerProgress(12, 11, 1, true).complete(), "Unloaded must block completion");
        check(!new LayerProgress(12, 12, 0, false).complete(), "Unfinished pass must block completion");
        check(new LayerProgress(12, 11, 0, true).percent() == 91, "Percentage is floored, never rounds missing blocks to 100");
        check(new LayerProgress(1000, 999, 0, true).percent() == 99, "One missing block remains below 100");
        check(!delay.ready(done, 100), "No immediate advance");
        check(!delay.ready(done, 129), "Delay must finish");
        check(delay.ready(done, 130), "Repeated complete pass advances after 30 ticks");
        delay.reset();
        check(!delay.ready(done, 200), "Manual layer/placement reset cancels previous completion");
        check(!delay.ready(new LayerProgress(12, 11, 1, true), 229), "Unloading cancels timer");
        check(!delay.ready(done, 230), "Reloading starts a new timer");
        check(!delay.ready(done, 259), "Must wait after reload");
        check(delay.ready(done, 260), "Can advance after a stable reload");
        delay.reset();
        check(!delay.ready(new LayerProgress(12, 11, 0, true), 300), "Incorrect state does not advance");
        check(!delay.ready(done, 330), "Accepted correction begins delay");
        check(delay.ready(done, 360), "Accepted corrections count once comparison reports correct");
        check(new LayerProgress(0, 0, 0, true).complete(), "Empty interior layer can be crossed");
        check(!new LayerProgress(0, 0, 0, false).complete(), "Unscanned empty layer cannot advance");
        System.out.println("LayerProgressTest: " + checks + " checks passed");
    }
}
