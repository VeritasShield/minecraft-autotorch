package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders bounded particle bounding boxes for Anti-Torch exclusion zones
 * and optional Darkness ESP indicators.
 */
public class SelectionRenderer {
    private static final int PARTICLE_TICK_INTERVAL = 5;
    private static final int MAX_PARTICLES_PER_EDGE = 120;
    private static final double MAX_ZONE_RENDER_DISTANCE_SQR = 96.0 * 96.0;

    public static void spawnSelectionParticles(Minecraft client, ZoneManager zoneManager) {
        if (!zoneManager.isZoneSelectionMode() || client.player == null || client.level == null) {
            return;
        }
        if (client.level.getGameTime() % PARTICLE_TICK_INTERVAL != 0) {
            return;
        }

        Vec3 playerPos = client.player.position();

        if (zoneManager.getPos1() != null) {
            drawParticleLineBox(client, new AABB(zoneManager.getPos1()), ParticleTypes.SOUL_FIRE_FLAME);
        }
        if (zoneManager.getPos2() != null) {
            drawParticleLineBox(client, new AABB(zoneManager.getPos2()), ParticleTypes.FLAME);
        }

        if (zoneManager.getPos1() != null && zoneManager.getPos2() != null) {
            AABB previewBox = new AABB(
                    Math.min(zoneManager.getPos1().getX(), zoneManager.getPos2().getX()),
                    Math.min(zoneManager.getPos1().getY(), zoneManager.getPos2().getY()),
                    Math.min(zoneManager.getPos1().getZ(), zoneManager.getPos2().getZ()),
                    Math.max(zoneManager.getPos1().getX(), zoneManager.getPos2().getX()) + 1.0,
                    Math.max(zoneManager.getPos1().getY(), zoneManager.getPos2().getY()) + 1.0,
                    Math.max(zoneManager.getPos1().getZ(), zoneManager.getPos2().getZ()) + 1.0
            );
            if (distanceSqrToBox(playerPos, previewBox) <= MAX_ZONE_RENDER_DISTANCE_SQR) {
                drawParticleLineBox(client, previewBox, ParticleTypes.END_ROD);
            }
        }

        for (AABB box : zoneManager.getExcludedZones(client.level)) {
            if (distanceSqrToBox(playerPos, box) <= MAX_ZONE_RENDER_DISTANCE_SQR) {
                drawParticleLineBox(client, box, ParticleTypes.HAPPY_VILLAGER);
            }
        }
    }

    public static void renderDarknessESP(Minecraft client, ModConfig cdata) {
        if (!cdata.showDarknessESP || client.player == null || client.level == null) {
            return;
        }
        if (client.level.getGameTime() % PARTICLE_TICK_INTERVAL != 0) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();
        int radius = cdata.placementRadius > 0 ? cdata.placementRadius : 2;
        int verticalRadius = cdata.verticalPlacementRadius > 0 ? cdata.verticalPlacementRadius : 1;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos supportPos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    checkPos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    if (client.level.getBlockState(checkPos).getFluidState().isEmpty()
                            && client.level.getBlockState(checkPos).canBeReplaced()) {
                        supportPos.set(checkPos.getX(), checkPos.getY() - 1, checkPos.getZ());
                        if (Block.canSupportCenter(client.level, supportPos, Direction.UP)
                                && client.level.getBrightness(LightLayer.BLOCK, checkPos) < cdata.lightLevel) {
                            client.particleEngine.createParticle(
                                    ParticleTypes.FLAME,
                                    checkPos.getX() + 0.5,
                                    checkPos.getY() + 0.1,
                                    checkPos.getZ() + 0.5,
                                    0.0, 0.0, 0.0
                            );
                        }
                    }
                }
            }
        }
    }

    private static double distanceSqrToBox(Vec3 point, AABB box) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static void drawParticleLineBox(Minecraft client, AABB box, ParticleOptions particleType) {
        spawnParticleLine(client, particleType, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ);

        spawnParticleLine(client, particleType, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);

        spawnParticleLine(client, particleType, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
    }

    private static void spawnParticleLine(
            Minecraft client, ParticleOptions type,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int dots = Math.min(Math.max(1, (int) (distance * 3.0)), MAX_PARTICLES_PER_EDGE);
        for (int i = 0; i <= dots; i++) {
            double fraction = (double) i / dots;
            double px = x1 + dx * fraction;
            double py = y1 + dy * fraction;
            double pz = z1 + dz * fraction;
            client.particleEngine.createParticle(type, px, py, pz, 0.0, 0.0, 0.0);
        }
    }
}
