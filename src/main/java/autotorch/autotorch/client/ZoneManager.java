package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ZoneManager {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("autotorch");

    private boolean zoneSelectionMode = false;
    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private final List<Box> excludedZones = new ArrayList<>();

    public boolean isZoneSelectionMode() {
        return zoneSelectionMode;
    }

    public void toggleZoneSelectionMode(MinecraftClient client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        zoneSelectionMode = !zoneSelectionMode;
        if (zoneSelectionMode) {
            client.player.sendMessage(Text.translatable("autotorch.message.selection_mode_on"), true);
        } else {
            if (pos1 != null && pos2 != null) {
                saveCurrentZone(client.player, config, cdata);
            }
            client.player.sendMessage(Text.translatable("autotorch.message.selection_mode_off"), true);
            pos1 = null;
            pos2 = null;
        }
    }

    public void clearZones(MinecraftClient client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        cdata.excludedZones.clear();
        config.save();
        syncZonesFromConfig(cdata);
        client.player.sendMessage(Text.translatable("autotorch.message.zones_cleared"), true);
    }

    public void syncZonesFromConfig(ModConfig cdata) {
        excludedZones.clear();
        for (String zoneStr : cdata.excludedZones) {
            try {
                String[] parts = zoneStr.split("\\|");
                if (parts.length == 2) {
                    String[] p1 = parts[0].split(",");
                    String[] p2 = parts[1].split(",");
                    Box box = new Box(
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

    private void saveCurrentZone(PlayerEntity player, ConfigHolder<ModConfig> config, ModConfig cdata) {
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

            player.sendMessage(Text.translatable("autotorch.message.zone_saved", cdata.excludedZones.size()), false);
        }
    }

    public void setPos1(BlockPos pos, PlayerEntity player) {
        this.pos1 = pos;
        player.sendMessage(Text.translatable("autotorch.message.point1_set", pos.getX(), pos.getY(), pos.getZ()), true);
    }

    public void setPos2(BlockPos pos, PlayerEntity player) {
        this.pos2 = pos;
        player.sendMessage(Text.translatable("autotorch.message.point2_set", pos.getX(), pos.getY(), pos.getZ()), true);
    }

    public boolean isInExcludedZone(BlockPos pos) {
        for (Box box : excludedZones) {
            if (box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }
    
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }
    public List<Box> getExcludedZones() { return excludedZones; }
}