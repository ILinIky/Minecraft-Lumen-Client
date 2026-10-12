package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

/** Fliegen wie im Kreativmodus, mit einstellbarer Geschwindigkeit. */
public class Flight extends Module {
    private static final float VANILLA_FLY_SPEED = 0.05F;
    private final Setting.Slider speed;

    public Flight() {
        super("flight", "Flug", "Fliegen wie im Kreativmodus (doppelt Leertaste)", Category.MOVEMENT);
        notPersistent();
        speed = add(new Setting.Slider("Geschwindigkeit", "Vielfaches der normalen Fluggeschwindigkeit", 1.0, 0.5, 5.0, 0.1, "x"));
    }

    @Override
    public String getHudInfo() { return speed.format(); }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.setFlyingSpeed(VANILLA_FLY_SPEED * (float) speed.get());
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(VANILLA_FLY_SPEED);
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    @Override
    public void onLeaveWorld() { setEnabled(false); }
}
