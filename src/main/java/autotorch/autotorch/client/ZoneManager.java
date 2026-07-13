package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ZoneManager {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("autotorch");

    private boolean zoneSelectionMode = false;
    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private final List<AABB> excludedZones = new ArrayList<>();

    public boolean isZoneSelectionMode() {
        return zoneSelectionMode;
    }

    public void toggleZoneSelectionMode(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        zoneSelectionMode = !zoneSelectionMode;
        if (zoneSelectionMode) {
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.selection_mode_on"));
        } else {
            if (pos1 != null && pos2 != null) {
                saveCurrentZone(client.player, config, cdata);
            }
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.selection_mode_off"));
            pos1 = null;
            pos2 = null;
        }
    }

    public void clearZones(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        cdata.excludedZones.clear();
        config.save();
        syncZonesFromConfig(cdata);
        client.player.sendOverlayMessage(Component.translatable("autotorch.message.zones_cleared"));
    }

    public void deleteCurrentZone(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        BlockPos pos = client.player.blockPosition();
        boolean removed = false;
        
        // Iterate backwards to safely remove from the lists
        for (int i = excludedZones.size() - 1; i >= 0; i--) {
            AABB box = excludedZones.get(i);
            if (box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                cdata.excludedZones.remove(i);
                removed = true;
            }
        }
        
        if (removed) {
            config.save();
            syncZonesFromConfig(cdata);
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.zone_deleted"));
        } else {
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.not_in_zone"));
        }
    }

    public void syncZonesFromConfig(ModConfig cdata) {
        excludedZones.clear();
        for (String zoneStr : cdata.excludedZones) {
            try {
                String[] parts = zoneStr.split("\\|");
                if (parts.length == 2) {
                    String[] p1 = parts[0].split(",");
                    String[] p2 = parts[1].split(",");
                    AABB box = new AABB(
                            Integer.parseInt(p1[0].trim()), Integer.parseInt(p1[1].trim()), Integer.parseInt(p1[2].trim()),
                            Integer.parseInt(p2[0].trim()) + 1, Integer.parseInt(p2[1].trim()) + 1, Integer.parseInt(p2[2].trim()) + 1
                    );
                    excludedZones.add(box);
                }
            } catch (Exception e) {
                logger.warn("Formato de zona inválido en config: " + zoneStr);
            }
        }
    }

    private void saveCurrentZone(Player player, ConfigHolder<ModConfig> config, ModConfig cdata) {
        if (pos1 != null && pos2 != null) {
            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX());
            int maxY = Math.max(pos1.getY(), pos2.getY());
            int maxZ = Math.max(pos1.getZ(), pos2.getZ());

            String zoneStr = minX + "," + minY + "," + minZ + "|" + maxX + "," + maxY + "," + maxZ;
            cdata.excludedZones.add(zoneStr);
            config.save(); // Guardar en el archivo JSON
            syncZonesFromConfig(cdata); // Recargar en la memoria

            player.sendSystemMessage(Component.translatable("autotorch.message.zone_saved", cdata.excludedZones.size()));
        }
    }

    public void setPos1(BlockPos pos, Player player) {
        this.pos1 = pos;
        player.sendOverlayMessage(Component.translatable("autotorch.message.point1_set", pos.getX(), pos.getY(), pos.getZ()));
    }

    public void setPos2(BlockPos pos, Player player) {
        this.pos2 = pos;
        player.sendOverlayMessage(Component.translatable("autotorch.message.point2_set", pos.getX(), pos.getY(), pos.getZ()));
    }

    public boolean isInExcludedZone(BlockPos pos) {
        for (AABB box : excludedZones) {
            if (box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }
    
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }
    public List<AABB> getExcludedZones() { return excludedZones; }
}