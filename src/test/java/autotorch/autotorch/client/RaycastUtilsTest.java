package autotorch.autotorch.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycastUtilsTest {
    private static final double EPS = 1.0E-6;

    @Test
    void computeRaycastEndpointNudgesPastAttachmentFaceForFloorAndWall() {
        BlockPos target = new BlockPos(10, 64, 10);

        Vec3 floorEnd = RaycastUtils.computeRaycastEndpoint(target, Direction.DOWN);
        assertEquals(10.5, floorEnd.x, EPS);
        assertEquals(63.99, floorEnd.y, EPS);
        assertEquals(10.5, floorEnd.z, EPS);

        Vec3 wallEnd = RaycastUtils.computeRaycastEndpoint(target, Direction.NORTH);
        assertEquals(10.5, wallEnd.x, EPS);
        assertEquals(64.5, wallEnd.y, EPS);
        assertEquals(9.99, wallEnd.z, EPS);
    }

    @Test
    void floorPlacementPassesWhenHittingUpFaceOfSupportBlock() {
        BlockPos target = new BlockPos(10, 64, 10);
        Vec3 eyePos = new Vec3(8.5, 65.62, 10.5);

        assertTrue(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.DOWN,
                HitResult.Type.BLOCK, target.below(), Direction.UP
        ));
    }

    @Test
    void wallPlacementPassesWhenHittingExposedOppositeFaceOfWallBlock() {
        BlockPos target = new BlockPos(10, 65, 10);
        Vec3 eyePos = new Vec3(10.5, 65.62, 12.5);

        assertTrue(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.NORTH,
                HitResult.Type.BLOCK, target.north(), Direction.SOUTH
        ));
    }

    @Test
    void undersideLedgeFailsEvenWhenRayHitsSupportBlockBelowTarget() {
        BlockPos target = new BlockPos(10, 64, 10);
        Vec3 eyePosBelowLedge = new Vec3(8.5, 62.5, 10.5);

        assertFalse(RaycastUtils.isEyeInFrontOfSupportFace(eyePosBelowLedge, target, Direction.DOWN));
        assertFalse(RaycastUtils.isValidLineOfSightHit(
                eyePosBelowLedge, target, Direction.DOWN,
                HitResult.Type.BLOCK, target.below(), Direction.DOWN
        ));
        assertFalse(RaycastUtils.isValidLineOfSightHit(
                eyePosBelowLedge, target, Direction.DOWN,
                HitResult.Type.MISS, target, Direction.UP
        ));
    }

    @Test
    void hittingWrongSideFaceOfFloorSupportBlockFails() {
        BlockPos target = new BlockPos(10, 64, 10);
        Vec3 eyePos = new Vec3(8.5, 65.0, 12.5);

        assertFalse(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.DOWN,
                HitResult.Type.BLOCK, target.below(), Direction.SOUTH
        ));
    }

    @Test
    void intermediateObstacleBlockFails() {
        BlockPos target = new BlockPos(10, 64, 10);
        BlockPos obstacle = new BlockPos(9, 64, 10);
        Vec3 eyePos = new Vec3(7.5, 65.62, 10.5);

        assertFalse(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.DOWN,
                HitResult.Type.BLOCK, obstacle, Direction.WEST
        ));
    }

    @Test
    void replaceableTargetBlockOrClearMissInFrontPasses() {
        BlockPos target = new BlockPos(10, 64, 10);
        Vec3 eyePos = new Vec3(8.5, 65.62, 10.5);

        assertTrue(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.DOWN,
                HitResult.Type.BLOCK, target, Direction.WEST
        ));
        assertTrue(RaycastUtils.isValidLineOfSightHit(
                eyePos, target, Direction.DOWN,
                HitResult.Type.MISS, BlockPos.ZERO, Direction.UP
        ));
    }

    @Test
    void fovConeAcceptsFrontAndFeetAndRejectsBehindPlayer() {
        Vec3 eyePos = new Vec3(0.5, 65.62, 0.5);
        Vec3 lookSouth = new Vec3(0.0, 0.0, 1.0);

        assertTrue(RaycastUtils.isWithinFovCone(eyePos, lookSouth, new BlockPos(0, 64, 3), 120));
        assertFalse(RaycastUtils.isWithinFovCone(eyePos, lookSouth, new BlockPos(0, 64, -3), 120));
        assertTrue(RaycastUtils.isWithinFovCone(eyePos, lookSouth, new BlockPos(0, 64, 0), 120));

        Vec3 lookStraightDown = new Vec3(0.0, -0.98, 0.19).normalize();
        assertTrue(RaycastUtils.isWithinFovCone(eyePos, lookStraightDown, new BlockPos(0, 64, 0), 90));
    }
}
