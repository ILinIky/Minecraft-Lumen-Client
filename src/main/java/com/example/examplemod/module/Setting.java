package com.example.examplemod.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Einstellung eines Moduls. Werte werden in {@link ClientConfig} gespeichert. */
public abstract class Setting {
    private final String name;
    private final String description;
    private BooleanSupplier visible = () -> true;
    private Runnable onChange = () -> {};

    protected Setting(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    @SuppressWarnings("unchecked")
    public <S extends Setting> S visibleWhen(BooleanSupplier condition) {
        this.visible = condition;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting> S onChange(Runnable callback) {
        this.onChange = callback;
        return (S) this;
    }

    protected void changed() {
        onChange.run();
        ModuleManager.markDirty();
    }

    public abstract JsonElement save();
    public abstract void load(JsonElement element);

    public static final class Bool extends Setting {
        private boolean value;

        public Bool(String name, String description, boolean value) {
            super(name, description);
            this.value = value;
        }

        public boolean get() { return value; }

        public void set(boolean value) {
            if (this.value == value) return;
            this.value = value;
            changed();
        }

        public void toggle() { set(!value); }

        @Override public JsonElement save() { return new JsonPrimitive(value); }

        @Override public void load(JsonElement element) {
            if (element != null && element.isJsonPrimitive()) value = element.getAsBoolean();
        }
    }

    public static final class Slider extends Setting {
        private final double min;
        private final double max;
        private final double step;
        private final String unit;
        private double value;

        public Slider(String name, String description, double value, double min, double max, double step, String unit) {
            super(name, description);
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
            this.value = clamp(value);
        }

        public double get() { return value; }
        public int getInt() { return (int) Math.round(value); }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getProgress() { return (value - min) / (max - min); }

        public void set(double newValue) {
            double clamped = clamp(newValue);
            if (clamped == value) return;
            value = clamped;
            changed();
        }

        public void setProgress(double progress) { set(min + (max - min) * progress); }

        private double clamp(double v) {
            double stepped = Math.round((v - min) / step) * step + min;
            return Math.max(min, Math.min(max, stepped));
        }

        public String format() {
            String number = step >= 1 ? String.valueOf((long) Math.round(value))
                    : String.format(Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
            return unit.isEmpty() ? number : number + " " + unit;
        }

        @Override public JsonElement save() { return new JsonPrimitive(value); }

        @Override public void load(JsonElement element) {
            if (element != null && element.isJsonPrimitive()) value = clamp(element.getAsDouble());
        }
    }

    public static final class Mode extends Setting {
        private final List<String> modes;
        private int index;

        public Mode(String name, String description, String value, String... modes) {
            super(name, description);
            this.modes = List.of(modes);
            this.index = Math.max(0, this.modes.indexOf(value));
        }

        public String get() { return modes.get(index); }
        public boolean is(String mode) { return get().equals(mode); }

        public void cycle(int direction) {
            index = Math.floorMod(index + direction, modes.size());
            changed();
        }

        @Override public JsonElement save() { return new JsonPrimitive(get()); }

        @Override public void load(JsonElement element) {
            if (element != null && element.isJsonPrimitive()) {
                int found = modes.indexOf(element.getAsString());
                if (found >= 0) index = found;
            }
        }
    }

    /** Schaltfläche in den Einstellungen, z.B. um einen Auswahl-Bildschirm zu öffnen. */
    public static final class Action extends Setting {
        private final Runnable action;

        public Action(String name, String description, Runnable action) {
            super(name, description);
            this.action = action;
        }

        public void run() { action.run(); }

        @Override public JsonElement save() { return JsonNull.INSTANCE; }
        @Override public void load(JsonElement element) {}
    }
}
