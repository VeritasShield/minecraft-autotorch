package autotorch.autotorch.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RaycastUtils {
    
    public static boolean hasLineOfSight(MinecraftClient client, BlockPos target) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(target);
        RaycastContext context = new RaycastContext(eyePos, targetVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player);
        BlockHitResult hit = client.world.raycast(context);
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target) || hit.getBlockPos().equals(target.down());
    }

    public static boolean isLookingAt(MinecraftClient client, BlockPos target, ModConfig cdata) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d toTarget = Vec3d.ofCenter(target).subtract(eyePos).normalize();
        Vec3d lookVec = client.player.getRotationVec(1.0F).normalize();
        return lookVec.dotProduct(toTarget) > Math.cos(Math.toRadians(cdata.lineOfSightAngle / 2.0));
    }
}