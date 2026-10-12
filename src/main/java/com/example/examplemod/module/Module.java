package com.example.examplemod.module;

import com.example.examplemod.gui.Notifications;
import com.example.examplemod.render.WorldRenderContext;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;
    private int key;
    /** Ob der An/Aus-Zustand beim nächsten Start wiederhergestellt wird. */
    private boolean persistent = true;

    /** Animationswerte für Menü und HUD (0..1). */
    public float toggleAnimation;
    public float hudAnimation;
    public float expandAnimation;

    protected Module(String id, String name, String description, Category category, int defaultKey) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = defaultKey;
    }

    protected Module(String id, String name, String description, Category category) {
        this(id, name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    protected <S extends Setting> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    private boolean defaultEnabled;

    protected void notPersistent() { persistent = false; }
    protected void enabledByDefault() { defaultEnabled = true; }
    boolean isDefaultEnabled() { return defaultEnabled; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }
    public boolean isPersistent() { return persistent; }
    public int getKey() { return key; }

    public void setKey(int key) {
        this.key = key;
        ModuleManager.markDirty();
    }

    public void toggle() { setEnabled(!enabled); }

    /** Eigene Taste aus dem Client-Menü gedrückt; Standard: ein- und ausschalten. */
    public void onKeyPressed() { toggle(); }

    /** Beschriftung der Tastenzeile im Client-Menü. */
    public String keyLabel() { return "Taste"; }

    public void setEnabled(boolean enabled) { setEnabled(enabled, true); }

    public void setEnabled(boolean enabled, boolean notify) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        if (notify) Notifications.moduleToggled(this);
        ModuleManager.markDirty();
    }

    /** Setzt den Zustand beim Laden der Konfiguration ohne Benachrichtigung. */
    void restoreEnabled(boolean enabled) {
        this.enabled = enabled;
        this.toggleAnimation = enabled ? 1 : 0;
        this.hudAnimation = enabled ? 1 : 0;
        if (enabled) onEnable();
    }

    /** Text neben dem Namen in der HUD-Modulliste, z.B. der aktive Modus. */
    public String getHudInfo() { return null; }

    public boolean showInModuleList() { return true; }

    /** Module ohne An/Aus-Schalter (z.B. Design) zeigen im Menü nur ihre Einstellungen. */
    public boolean hasToggle() { return true; }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    public void onWorldRender(WorldRenderContext context) {}
    public void onLeaveWorld() {}
    public void saveExtra(JsonObject json) {}
    public void loadExtra(JsonObject json) {}
}
