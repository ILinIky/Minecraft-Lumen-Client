package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import net.minecraft.world.effect.MobEffects;

/** Blendet den eigenen Spieler lokal aus (z.B. für Screenshots). Andere Spieler sehen dich weiterhin. */
public class Invisibility extends Module {
    public Invisibility() {
        super("invisibility", "Unsichtbar (lokal)", "Eigenen Körper ausblenden, nur für dich sichtbar", Category.PLAYER);
        notPersistent();
    }

    @Override
    public void onTick() {
        // Der Server synchronisiert die Flags regelmäßig, daher jeden Tick erneut setzen.
        if (mc.player != null && !mc.player.isInvisible()) mc.player.setInvisible(true);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && !mc.player.hasEffect(MobEffects.INVISIBILITY)) mc.player.setInvisible(false);
    }

    @Override
    public void onLeaveWorld() { setEnabled(false); }
}
