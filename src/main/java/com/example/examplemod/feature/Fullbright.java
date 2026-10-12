package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Volle Helligkeit über einen unsichtbaren, nur lokalen Nachtsicht-Effekt.
 * X-Ray nutzt denselben Effekt, damit freigelegte Erze nicht schwarz erscheinen.
 */
public class Fullbright extends Module {
    private static boolean applied;

    public Fullbright() {
        super("fullbright", "Fullbright", "Alles hell sehen, auch in Höhlen", Category.RENDER);
    }

    /** Wird jeden Tick aufgerufen, unabhängig davon, ob das Modul aktiv ist. */
    public static void update() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            applied = false;
            return;
        }
        boolean wanted = ModuleManager.FULLBRIGHT.isEnabled() || ModuleManager.XRAY.isEnabled();
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (wanted) {
            if (current == null) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION,
                        0, false, false, false));
                applied = true;
            }
        } else if (applied) {
            // Nur unseren eigenen Effekt entfernen, nicht einen echten Trank.
            if (current != null && current.isInfiniteDuration() && !current.isVisible()) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
            applied = false;
        }
    }
}
