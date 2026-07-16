package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class RaycastUtils {
    
    public static boolean hasLineOfSight(Minecraft client, BlockPos target) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 targetVec = Vec3.atCenterOf(target);
        ClipContext context = new ClipContext(eyePos, targetVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player);
        BlockHitResult hit = client.level.clip(context);
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target) || hit.getBlockPos().equals(target.below());
    }

    public static boolean isLookingAt(Minecraft client, BlockPos target, ModConfig cdata) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getViewVector(1.0F);
        
        if (Math.abs(lookVec.y) > 0.95) {
            Vec3 toTarget = Vec3.atCenterOf(target).subtract(eyePos).normalize();
            return lookVec.normalize().dot(toTarget) > Math.cos(Math.toRadians(cdata.lineOfSightAngle / 2.0));
        }
        
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 toTarget = new Vec3(targetCenter.x - eyePos.x, 0, targetCenter.z - eyePos.z).normalize();
        Vec3 lookDir = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        
        return lookDir.dot(toTarget) > Math.cos(Math.toRadians(cdata.lineOfSightAngle / 2.0));
    }
}