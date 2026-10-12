package com.example.examplemod.building;

import com.example.examplemod.gui.hud.ListViewport;

public final class MaterialScrollTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var view = new ListViewport();
        view.resize(20, 6);
        check(view.maximum() == 14 && view.offset() == 0, "Six visible rows");
        check(view.scroll(-1) && view.offset() == 1, "Wheel down");
        view.scroll(-1000);
        check(view.offset() == 14, "End clamped");
        check(view.scroll(-1) && view.offset() == 14, "End still consumes input, protecting hotbar");
        view.scroll(1);
        check(view.offset() == 13, "Wheel up");
        view.resize(8,6);
        check(view.offset() == 2, "Completed rows disappear safely");
        view.resize(8,8);
        check(view.offset() == 0 && !view.scroll(-1), "No overflow releases wheel");
        view.resize(0,0);
        check(view.offset() == 0 && view.maximum() == 0, "Empty list");
        view.resize(10,3);
        check(view.scroll(-.1) && view.offset() == 1, "Small trackpad delta");
        check(!view.scroll(Double.NaN) && view.offset() == 1, "Invalid delta ignored");
        System.out.println("MaterialScrollTest: " + assertions + " assertions passed");
    }
}
