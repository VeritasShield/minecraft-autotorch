package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;

public class SelectionRenderer {

    public static void spawnSelectionParticles(Minecraft client, ZoneManager zoneManager) {
        if (!zoneManager.isZoneSelectionMode()) return;
        
        if (client.player == null || client.level == null) return;

        // Limitamos la generación a 1 vez cada 5 ticks (4 veces por segundo) para evitar alcanzar el límite de partículas de Minecraft
        if (client.level.getGameTime() % 5 != 0) return; 

        if (zoneManager.getPos1() != null) {
            drawParticleLineBox(client, new AABB(zoneManager.getPos1()), ParticleTypes.SOUL_FIRE_FLAME); // Azul
        }
        if (zoneManager.getPos2() != null) {
            drawParticleLineBox(client, new AABB(zoneManager.getPos2()), ParticleTypes.FLAME); // Rojo/Naranja
        }
        
        // Dibujar previsualización de la zona completa si ambos puntos están establecidos antes de guardarse
        if (zoneManager.getPos1() != null && zoneManager.getPos2() != null && zoneManager.isZoneSelectionMode()) {
            AABB previewBox = new AABB(
                    Math.min(zoneManager.getPos1().getX(), zoneManager.getPos2().getX()),
                    Math.min(zoneManager.getPos1().getY(), zoneManager.getPos2().getY()),
                    Math.min(zoneManager.getPos1().getZ(), zoneManager.getPos2().getZ()),
                    Math.max(zoneManager.getPos1().getX(), zoneManager.getPos2().getX()) + 1,
                    Math.max(zoneManager.getPos1().getY(), zoneManager.getPos2().getY()) + 1,
                    Math.max(zoneManager.getPos1().getZ(), zoneManager.getPos2().getZ()) + 1
            );
            drawParticleLineBox(client, previewBox, ParticleTypes.END_ROD); // Blanco brillante
        }

        for (AABB box : zoneManager.getExcludedZones()) {
            drawParticleLineBox(client, box, ParticleTypes.HAPPY_VILLAGER); // Verde
        }
    }

    public static void renderDarknessESP(Minecraft client, ModConfig cdata) {
        if (!cdata.showDarknessESP || client.player == null || client.level == null) return;
        if (client.level.getGameTime() % 5 != 0) return;

        BlockPos playerPos = client.player.blockPosition();
        int radius = cdata.placementRadius > 0 ? cdata.placementRadius : 2;
        int verticalRadius = cdata.verticalPlacementRadius > 0 ? cdata.verticalPlacementRadius : 1;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    // Check if block can have torch placed on it
                    if (client.level.getBlockState(checkPos).getFluidState().isEmpty() && client.level.getBlockState(checkPos).canBeReplaced()) {
                        BlockPos supportPos = checkPos.below();
                        if (Block.canSupportCenter(client.level, supportPos, net.minecraft.core.Direction.UP)) {
                            if (client.level.getBrightness(LightLayer.BLOCK, checkPos) < cdata.lightLevel) {
                                // Draw a single red particle (dust or flame) in the center of the block
                                client.particleEngine.createParticle(ParticleTypes.FLAME, checkPos.getX() + 0.5, checkPos.getY() + 0.1, checkPos.getZ() + 0.5, 0.0, 0.0, 0.0);
                            }
                        }
                    }
                }
            }
        }
    }


    private static void drawParticleLineBox(Minecraft client, AABB box, ParticleOptions particleType) {
        // En lugar de partículas aleatorias en las caras, dibujamos puntos específicos a lo largo de las 12 aristas de la caja
        
        // Aristas inferiores
        spawnParticleLine(client, particleType, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ);

        // Aristas superiores
        spawnParticleLine(client, particleType, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);

        // Pilares verticales
        spawnParticleLine(client, particleType, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        spawnParticleLine(client, particleType, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        spawnParticleLine(client, particleType, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
    }

    private static void spawnParticleLine(Minecraft client, ParticleOptions type, double x1, double y1, double z1, double x2, double y2, double z2) {
        // Calcula cuántas partículas necesitamos poner en esta línea
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        
        // Ponemos 3 partículas por cada bloque de distancia para formar una línea punteada visible
        int dots = (int) (distance * 3); 
        if (dots < 1) dots = 1;

        for (int i = 0; i <= dots; i++) {
            double fraction = (double) i / dots;
            
            // Calculamos la posición exacta de cada "punto" en la línea
            double px = x1 + (x2 - x1) * fraction;
            double py = y1 + (y2 - y1) * fraction;
            double pz = z1 + (z2 - z1) * fraction;

            // Añadimos la partícula. Al tener velocidad 0.0, se quedan casi estáticas simulando una línea flotante
            client.particleEngine.createParticle(type, px, py, pz, 0.0, 0.0, 0.0);
        }
    }
}