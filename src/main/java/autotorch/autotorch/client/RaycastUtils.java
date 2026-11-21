package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vector raycasting and FOV line-of-sight checks for safe torch placement.
 */
public class RaycastUtils {
    private static final double MIN_DIRECTION_LEN_SQR = 1.0E-4;
    private static final double FACE_INWARD_EPSILON = 0.01;

    public static boolean hasLineOfSight(Minecraft client, BlockPos target) {
        return hasLineOfSight(client, target, Direction.DOWN);
    }

    public static boolean hasLineOfSight(Minecraft client, BlockPos target, Direction supportDir) {
        if (client.player == null || client.level == null) {
            return false;
        }
        Vec3 eyePos = client.player.getEyePosition();
        if (!isEyeInFrontOfSupportFace(eyePos, target, supportDir)) {
            return false;
        }

        Vec3 rayEnd = computeRaycastEndpoint(target, supportDir);
        ClipContext context = new ClipContext(eyePos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player);
        BlockHitResult hit = client.level.clip(context);
        return isValidLineOfSightHit(eyePos, target, supportDir, hit.getType(), hit.getBlockPos(), hit.getDirection());
    }

    public static Vec3 computeTargetHitPos(BlockPos pos, Direction dir) {
        BlockPos targetBlock = pos.relative(dir);
        Direction hitFace = dir.getOpposite();
        return Vec3.atCenterOf(targetBlock).add(
                Vec3.atLowerCornerOf(new Vec3i(hitFace.getStepX(), hitFace.getStepY(), hitFace.getStepZ())).scale(0.5)
        );
    }

    public static Vec3 computeSupportFaceCenter(BlockPos target, Direction supportDir) {
        Vec3 targetCenter = Vec3.atCenterOf(target);
        if (supportDir == null) {
            return targetCenter;
        }
        return targetCenter.add(
                supportDir.getStepX() * 0.5,
                supportDir.getStepY() * 0.5,
                supportDir.getStepZ() * 0.5
        );
    }

    public static Vec3 computeRaycastEndpoint(BlockPos target, Direction supportDir) {
        if (supportDir == null) {
            return Vec3.atCenterOf(target);
        }
        return computeSupportFaceCenter(target, supportDir).add(
                supportDir.getStepX() * FACE_INWARD_EPSILON,
                supportDir.getStepY() * FACE_INWARD_EPSILON,
                supportDir.getStepZ() * FACE_INWARD_EPSILON
        );
    }

    public static boolean isEyeInFrontOfSupportFace(Vec3 eyePos, BlockPos target, Direction supportDir) {
        if (supportDir == null) {
            return true;
        }
        Direction hitFace = supportDir.getOpposite();
        Vec3 faceCenter = computeSupportFaceCenter(target, supportDir);
        double dot = (eyePos.x - faceCenter.x) * hitFace.getStepX()
                + (eyePos.y - faceCenter.y) * hitFace.getStepY()
                + (eyePos.z - faceCenter.z) * hitFace.getStepZ();
        return dot > 0.0;
    }

    public static boolean isValidLineOfSightHit(
            Vec3 eyePos,
            BlockPos target,
            Direction supportDir,
            HitResult.Type hitType,
            BlockPos hitBlockPos,
            Direction hitFace
    ) {
        if (!isEyeInFrontOfSupportFace(eyePos, target, supportDir)) {
            return false;
        }
        if (hitType == HitResult.Type.MISS) {
            return true;
        }
        if (hitType != HitResult.Type.BLOCK || hitBlockPos == null) {
            return false;
        }
        if (hitBlockPos.equals(target)) {
            return true;
        }
        if (supportDir != null) {
            BlockPos expectedSupport = target.relative(supportDir);
            return hitBlockPos.equals(expectedSupport) && hitFace == supportDir.getOpposite();
        }
        return false;
    }

    public static boolean isLookingAt(Minecraft client, BlockPos target, ModConfig cdata) {
        if (client.player == null) {
            return false;
        }
        return isWithinFovCone(
                client.player.getEyePosition(),
                client.player.getViewVector(1.0F),
                target,
                cdata.lineOfSightAngle
        );
    }

    public static boolean isWithinFovCone(Vec3 eyePos, Vec3 lookVec, BlockPos target, int lineOfSightAngleDegrees) {
        double threshold = Math.cos(Math.toRadians(lineOfSightAngleDegrees / 2.0));

        if (Math.abs(lookVec.y) > 0.95) {
            Vec3 toTarget = Vec3.atCenterOf(target).subtract(eyePos);
            if (toTarget.lengthSqr() < MIN_DIRECTION_LEN_SQR) {
                return true;
            }
            return lookVec.normalize().dot(toTarget.normalize()) > threshold;
        }

        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 horizontalDelta = new Vec3(targetCenter.x - eyePos.x, 0, targetCenter.z - eyePos.z);
        if (horizontalDelta.lengthSqr() < MIN_DIRECTION_LEN_SQR) {
            return true;
        }
        Vec3 lookDir = new Vec3(lookVec.x, 0, lookVec.z);
        if (lookDir.lengthSqr() < MIN_DIRECTION_LEN_SQR) {
            return true;
        }

        return lookDir.normalize().dot(horizontalDelta.normalize()) > threshold;
    }
}
