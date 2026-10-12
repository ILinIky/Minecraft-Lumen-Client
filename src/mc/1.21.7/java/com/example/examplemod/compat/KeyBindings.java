package com.example.examplemod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Tasten aus Lumen-Menüs heraus neu belegen (Minecraft 1.21.7 / Forge). Die KeyMapping-API ändert sich
 * zwischen Minecraft-Versionen gelegentlich, daher liegt sie im versionsabhängigen Ordner.
 * Gespeichert wird wie unter Optionen → Steuerung (options.txt).
 */
public final class KeyBindings {
    private KeyBindings() {}

    public static String keyName(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    public static void bindKeyboard(KeyMapping mapping, int keyCode, int scanCode) {
        set(mapping, KeyModifier.NONE, InputConstants.getKey(keyCode, scanCode));
    }

    /** Maustasten ab 3 (Mausrad-Klick, Seitentasten); Links- und Rechtsklick sind fürs Spielen reserviert. */
    public static void bindMouse(KeyMapping mapping, int button) {
        set(mapping, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(button));
    }

    public static void reset(KeyMapping mapping) {
        set(mapping, mapping.getDefaultKeyModifier(), mapping.getDefaultKey());
    }

    public static boolean isDefault(KeyMapping mapping) { return mapping.isDefault(); }

    private static void set(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        mapping.setKeyModifierAndCode(modifier, key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    /** Namen anderer Belegungen mit derselben Taste, z.B. "Springen". */
    public static List<String> conflicts(KeyMapping mapping) {
        List<String> names = new ArrayList<>();
        if (mapping.isUnbound()) return names;
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other == mapping || other.isUnbound()) continue;
            if (other.getKey().equals(mapping.getKey()) && other.getKeyModifier() == mapping.getKeyModifier()) {
                names.add(Component.translatable(other.getName()).getString());
            }
        }
        return names;
    }
}
