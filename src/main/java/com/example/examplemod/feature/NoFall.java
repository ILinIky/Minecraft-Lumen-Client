package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

/**
 * Verhindert Fallschaden. Während eines Sturzes meldet der Client "am Boden", damit der
 * Server keine Fallhöhe ansammelt (LocalPlayerMixin). Im Einzelspieler wird der Schaden
 * zusätzlich direkt auf dem integrierten Server abgefangen.
 */
public class NoFall extends Module {
    public NoFall() {
        super("no_fall", "NoFall", "Kein Fallschaden", Category.MOVEMENT);
    }

    /** Ob das nächste Bewegungspaket "am Boden" melden soll. */
    public static boolean shouldSpoofOnGround(LocalPlayer player) {
        if (ModuleManager.NO_FALL == null || !ModuleManager.NO_FALL.isEnabled()) return false;
        if (player.isFallFlying() || player.getAbilities().flying) return false;
        return player.fallDistance > 1.5 && player.getDeltaMovement().y < 0;
    }

    /** Für den integrierten Server: gehört der fallende Spieler zu diesem Client? */
    public static boolean protects(UUID playerId) {
        if (ModuleManager.NO_FALL == null || !ModuleManager.NO_FALL.isEnabled()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getUUID().equals(playerId);
    }
}
