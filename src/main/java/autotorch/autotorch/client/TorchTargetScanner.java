package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Volumetric 3D dark-spot scanner that identifies valid floor and wall
 * torch placement candidates while honoring exclusion zones, spacing, and FOV.
 */
public class TorchTargetScanner {
    private static final double RIGHT_WALL_WEIGHT = 3.0;

    public record PlacementCandidate(BlockPos basePos, BlockPos placementPos, Direction supportDir) {}

    public PlacementCandidate findNextCandidate(Minecraft client, ModConfig cdata, ZoneManager zoneManager) {
        if (client.player == null || client.level == null) {
            return null;
        }

        BlockPos playerPos = client.player.blockPosition();
        PlacementCandidate feetCandidate = evaluateCandidate(client, playerPos, cdata, zoneManager, true);
        if (feetCandidate != null) {
            return feetCandidate;
        }

        if (cdata.placementRadius <= 0) {
            return null;
        }

        for (int r = 1; r <= cdata.placementRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -cdata.verticalPlacementRadius; y <= cdata.verticalPlacementRadius; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        PlacementCandidate candidate = evaluateCandidate(client, checkPos, cdata, zoneManager, false);
                        if (candidate != null) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private PlacementCandidate evaluateCandidate(
            Minecraft client,
            BlockPos basePos,
            ModConfig cdata,
            ZoneManager zoneManager,
            boolean isFeetOrigin
    ) {
        if (client.level.getBrightness(LightLayer.BLOCK, basePos) >= cdata.lightLevel) {
            return null;
        }
        if (!isFeetOrigin && !client.level.getBlockState(basePos).canBeReplaced()) {
            return null;
        }

        BlockPos placementPos = basePos;
        Direction bestDir = null;

        if (cdata.preferWallPlacement) {
            BlockPos headPos = basePos.above();
            if (isValidPlacementSpace(client, headPos, zoneManager)) {
                Direction wallDir = getWallDirectionOnly(client, headPos, cdata);
                if (wallDir != null && RaycastUtils.hasLineOfSight(client, headPos, wallDir)) {
                    bestDir = wallDir;
                    placementPos = headPos;
                }
            }
            if (bestDir == null && isValidPlacementSpace(client, basePos, zoneManager)) {
                Direction wallDir = getWallDirectionOnly(client, basePos, cdata);
                if (wallDir != null && RaycastUtils.hasLineOfSight(client, basePos, wallDir)) {
                    bestDir = wallDir;
                    placementPos = basePos;
                }
            }
        }

        if (bestDir == null && isValidPlacementSpace(client, basePos, zoneManager)) {
            Direction floorDir = getFloorDirectionOnly(client, basePos, cdata);
            if (floorDir != null && RaycastUtils.hasLineOfSight(client, basePos, floorDir)) {
                bestDir = floorDir;
                placementPos = basePos;
            }
        }

        if (bestDir == null) {
            return null;
        }

        if (cdata.smartSpacing && hasTorchNearby(client, placementPos, cdata.smartSpacingRadius)) {
            return null;
        }

        if (cdata.requireLineOfSightAngle) {
            if (isFeetOrigin) {
                // Feet floor placement is beneath the player, but head-level wall placement must respect FOV.
                if (bestDir != Direction.DOWN && !RaycastUtils.isLookingAt(client, placementPos.relative(bestDir), cdata)) {
                    return null;
                }
            } else if (!RaycastUtils.isLookingAt(client, placementPos, cdata)) {
                return null;
            }
        }

        return new PlacementCandidate(basePos.immutable(), placementPos.immutable(), bestDir);
    }

    private boolean isValidPlacementSpace(Minecraft client, BlockPos pos, ZoneManager zoneManager) {
        if (zoneManager.isInExcludedZone(client.level, pos)) {
            return false;
        }
        BlockState state = client.level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private Direction getWallDirectionOnly(Minecraft client, BlockPos pos, ModConfig cdata) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getViewVector(1.0F).normalize();
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0));
        if (rightVec.lengthSqr() > 1.0E-6) {
            rightVec = rightVec.normalize();
        }

        Direction bestDir = null;
        double bestScore = -999.0;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = pos.relative(dir);
            if (isSupportBlockAllowed(client, wallPos, cdata)
                    && Block.canSupportCenter(client.level, wallPos, dir.getOpposite())) {
                Vec3 wallDir = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                double rightDot = rightVec.dot(wallDir);

                Vec3 toTarget = Vec3.atCenterOf(wallPos).subtract(eyePos).normalize();
                double forwardDot = lookVec.dot(toTarget);
                double score = (rightDot * RIGHT_WALL_WEIGHT) + forwardDot;

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = dir;
                }
            }
        }
        return bestDir;
    }

    private Direction getFloorDirectionOnly(Minecraft client, BlockPos pos, ModConfig cdata) {
        BlockPos supportPos = pos.below();
        if (isSupportBlockAllowed(client, supportPos, cdata)
                && Block.canSupportCenter(client.level, supportPos, Direction.UP)) {
            return Direction.DOWN;
        }
        return null;
    }

    private boolean isSupportBlockAllowed(Minecraft client, BlockPos supportPos, ModConfig cdata) {
        Block block = client.level.getBlockState(supportPos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        boolean isListed = cdata.blacklistedBlocks.contains(blockId);
        return cdata.blockListIsWhitelist ? isListed : !isListed;
    }

    private boolean hasTorchNearby(Minecraft client, BlockPos pos, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = pos.getX();
        int baseY = pos.getY();
        int baseZ = pos.getZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(baseX + x, baseY + y, baseZ + z);
                    if (client.level.getBlockState(cursor).getBlock() instanceof TorchBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
