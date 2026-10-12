package com.example.examplemod.gui.hud;

/** A bounded row offset, including when completed materials disappear or GUI size changes. */
public final class ListViewport {
    private int offset, maximum;
    public int offset() { return offset; }
    public int maximum() { return maximum; }
    public void resize(int total, int visible) {
        maximum = Math.max(0, total - Math.max(1, visible));
        offset = Math.max(0, Math.min(maximum, offset));
    }
    public boolean scroll(double delta) {
        if (!Double.isFinite(delta) || delta == 0 || maximum == 0) return false;
        int step = (int)Math.max(-maximum, Math.min(maximum, delta > 0 ? Math.ceil(delta) : Math.floor(delta)));
        offset = Math.max(0, Math.min(maximum, offset - step));
        return true; // Consume even at the boundary: do not accidentally change the hotbar.
    }
}
