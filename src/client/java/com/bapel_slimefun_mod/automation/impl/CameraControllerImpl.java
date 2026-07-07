package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.CameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class CameraControllerImpl implements CameraController {

    private static final float LOOK_SPEED      = 5.0f;   // derajat per tick  
    private static final float LOOK_TOLERANCE  = 2.0f;   // derajat toleransi  
    private static final float EYE_HEIGHT      = 1.62f;  

    private BlockPos currentTarget = null;  
    private boolean  controlled    = false;  

    @Override  
    public void lookAt(BlockPos target) {  
        this.currentTarget = target;  
        this.controlled    = true;  
        tickLook(); // mulai rotate sekarang  
    }  

    @Override  
    public boolean isLookingAt(BlockPos target) {  
        Minecraft mc = Minecraft.getInstance();  
        LocalPlayer player = mc.player;  
        if (player == null) return false;  

        float[] desired = calculateAngles(player, target);  
        float   dyaw    = Math.abs(Mth.wrapDegrees(player.getYRot()   - desired[0]));  
        float   dpitch  = Math.abs(Mth.wrapDegrees(player.getXRot() - desired[1]));  

        boolean looking = dyaw < LOOK_TOLERANCE && dpitch < LOOK_TOLERANCE;  

        // Terus rotate setiap tick sampai on-target  
        if (!looking && controlled) tickLook();  

        return looking;  
    }  

    @Override  
    public void release() {  
        this.currentTarget = null;  
        this.controlled    = false;  
    }  

    @Override  
    public boolean isControlled() { return controlled; }  

    // ─────────────────────────────────────────────────────────────  

    private void tickLook() {  
        Minecraft mc = Minecraft.getInstance();  
        LocalPlayer player = mc.player;  
        if (player == null || currentTarget == null) return;  

        float[] desired = calculateAngles(player, currentTarget);  

        float currentYaw   = player.getYRot();  
        float currentPitch = player.getXRot();  

        float newYaw   = lerpAngle(currentYaw,   desired[0], LOOK_SPEED);  
        float newPitch = lerpAngle(currentPitch, desired[1], LOOK_SPEED);  

        player.setYRot(newYaw);  
        player.setXRot(newPitch);  
    }  

    private float[] calculateAngles(LocalPlayer player, BlockPos target) {  
        Vec3 eye    = player.position().add(0, EYE_HEIGHT, 0);  
        Vec3 center = Vec3.atCenterOf(target);  
        Vec3 delta  = center.subtract(eye);  

        double dist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);  
        float  yaw  = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));  
        float  pitch= (float) Math.toDegrees(-Math.atan2(delta.y, dist));  

        return new float[]{ yaw, pitch };  
    }  

    private float lerpAngle(float current, float target, float speed) {  
        float diff = Mth.wrapDegrees(target - current);  
        float step = Mth.clamp(diff, -speed, speed);  
        return current + step;  
    }  
}
