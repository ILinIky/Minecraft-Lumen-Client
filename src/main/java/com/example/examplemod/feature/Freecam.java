package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Löst nur die Kamera vom Spieler. Der Spieler bleibt stehen und sendet keine falschen
 * Positionen an den Server (CameraMixin, EntityTurnMixin, Eingabe in ClientEvents).
 */
public class Freecam extends Module {
    private final Setting.Slider speed;
    private double x, y, z;
    private double prevX, prevY, prevZ;
    private float yaw, pitch;

    public Freecam() {
        super("freecam", "Freecam", "Kamera frei bewegen, der Spieler bleibt stehen", Category.PLAYER, GLFW.GLFW_KEY_F);
        notPersistent();
        speed = add(new Setting.Slider("Geschwindigkeit", "Blöcke pro Tick (Sprinttaste = schneller)", 1.0, 0.1, 5.0, 0.1, ""));
    }

    public static boolean active() {
        return ModuleManager.FREECAM != null && ModuleManager.FREECAM.isEnabled();
    }

    @Override
    protected void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null) {
            setEnabled(false);
            return;
        }
        Vec3 eye = player.getEyePosition();
        x = prevX = eye.x;
        y = prevY = eye.y;
        z = prevZ = eye.z;
        yaw = player.getYRot();
        pitch = player.getXRot();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) {
            setEnabled(false);
            return;
        }
        prevX = x;
        prevY = y;
        prevZ = z;
        if (mc.screen != null) return;

        Options options = mc.options;
        double forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        double strafe = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
        double vertical = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
        double velocity = speed.get() * (options.keySprint.isDown() ? 2.5 : 1.0);

        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 left = Vec3.directionFromRotation(0, yaw - 90);
        Vec3 motion = look.scale(forward).add(left.scale(strafe)).add(0, vertical, 0);
        if (motion.lengthSqr() > 1.0E-6) {
            motion = motion.normalize().scale(velocity);
            x += motion.x;
            y += motion.y;
            z += motion.z;
        }
    }

    /** Mausbewegung dreht die Kamera statt des Spielers (gleicher Faktor wie Vanilla). */
    public void turn(double deltaYaw, double deltaPitch) {
        yaw += (float) deltaYaw * 0.15F;
        pitch = Mth.clamp(pitch + (float) deltaPitch * 0.15F, -90.0F, 90.0F);
    }

    public Vec3 getCameraPos(float partialTick) {
        return new Vec3(Mth.lerp(partialTick, prevX, x), Mth.lerp(partialTick, prevY, y), Mth.lerp(partialTick, prevZ, z));
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    @Override
    public void onLeaveWorld() { setEnabled(false); }
}
