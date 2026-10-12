package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import net.minecraft.client.player.LocalPlayer;

/** Sprinten bleibt an, solange du vorwärts läufst – ohne die Sprinttaste zu halten. */
public class AutoSprint extends Module {
    public AutoSprint() {
        super("auto_sprint", "Auto-Sprint", "Automatisch sprinten beim Vorwärtslaufen", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || player.isSprinting()) return;
        boolean canSprint = mc.options.keyUp.isDown() && !player.isShiftKeyDown() && !player.isUsingItem()
                && !player.horizontalCollision && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly)
                && !player.isInWater();
        if (canSprint) player.setSprinting(true);
    }
}
