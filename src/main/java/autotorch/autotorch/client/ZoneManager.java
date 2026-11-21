package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages WorldEdit-style 3D exclusion zones with per-dimension caching
 * and backward compatibility for legacy unprefixed config entries.
 */
public class ZoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("autotorch");

    private record ZoneEntry(int configIndex, AABB box) {}

    private boolean zoneSelectionMode = false;
    private BlockPos pos1 = null;
    private BlockPos pos2 = null;

    private final Map<String, List<ZoneEntry>> zonesByDimension = new HashMap<>();
    private final List<ZoneEntry> globalLegacyZones = new ArrayList<>();

    public boolean isZoneSelectionMode() {
        return zoneSelectionMode;
    }

    public void onWorldChanged() {
        zoneSelectionMode = false;
        pos1 = null;
        pos2 = null;
    }

    public void beginSelectionForTest(BlockPos p1, BlockPos p2) {
        this.zoneSelectionMode = true;
        this.pos1 = p1 != null ? p1.immutable() : null;
        this.pos2 = p2 != null ? p2.immutable() : null;
    }

    public static String formatZoneString(String dimensionId, BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        return dimensionId + "@" + minX + "," + minY + "," + minZ + "|" + maxX + "," + maxY + "," + maxZ;
    }

    public void toggleZoneSelectionMode(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        if (client.player == null || client.level == null) {
            return;
        }
        zoneSelectionMode = !zoneSelectionMode;
        if (zoneSelectionMode) {
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.selection_mode_on"));
        } else {
            if (pos1 != null && pos2 != null) {
                saveCurrentZone(client.level, client.player, config, cdata);
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
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.zones_cleared"));
        }
    }

    public void deleteCurrentZone(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata) {
        if (client.player == null || client.level == null) {
            return;
        }
        boolean removed = deleteZonesContaining(getDimensionId(client.level), client.player.blockPosition(), cdata);
        if (removed) {
            config.save();
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.zone_deleted"));
        } else {
            client.player.sendOverlayMessage(Component.translatable("autotorch.message.not_in_zone"));
        }
    }

    public boolean deleteZonesContaining(String dimensionId, BlockPos pos, ModConfig cdata) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;

        Set<Integer> indicesToRemove = new LinkedHashSet<>();
        if (dimensionId != null) {
            collectMatchingIndices(zonesByDimension.getOrDefault(dimensionId, List.of()), px, py, pz, indicesToRemove);
        }
        collectMatchingIndices(globalLegacyZones, px, py, pz, indicesToRemove);

        if (indicesToRemove.isEmpty()) {
            return false;
        }

        List<Integer> sortedDesc = new ArrayList<>(indicesToRemove);
        sortedDesc.sort(Comparator.reverseOrder());
        for (int index : sortedDesc) {
            if (index >= 0 && index < cdata.excludedZones.size()) {
                cdata.excludedZones.remove(index);
            }
        }
        syncZonesFromConfig(cdata);
        return true;
    }

    public void syncZonesFromConfig(ModConfig cdata) {
        zonesByDimension.clear();
        globalLegacyZones.clear();

        if (cdata.excludedZones == null) {
            return;
        }

        for (int i = 0; i < cdata.excludedZones.size(); i++) {
            String rawZone = cdata.excludedZones.get(i);
            if (rawZone == null || rawZone.isBlank()) {
                continue;
            }
            try {
                String dimensionId = null;
                String coordsPart = rawZone.trim();
                int atIndex = coordsPart.indexOf('@');
                if (atIndex > 0) {
                    dimensionId = coordsPart.substring(0, atIndex).trim();
                    coordsPart = coordsPart.substring(atIndex + 1).trim();
                }

                String[] parts = coordsPart.split("\\|");
                if (parts.length != 2) {
                    LOGGER.warn("Invalid exclusion zone format in config: {}", rawZone);
                    continue;
                }

                String[] p1 = parts[0].split(",");
                String[] p2 = parts[1].split(",");
                if (p1.length != 3 || p2.length != 3) {
                    LOGGER.warn("Invalid exclusion zone coordinates in config: {}", rawZone);
                    continue;
                }

                int x1 = Integer.parseInt(p1[0].trim());
                int y1 = Integer.parseInt(p1[1].trim());
                int z1 = Integer.parseInt(p1[2].trim());
                int x2 = Integer.parseInt(p2[0].trim());
                int y2 = Integer.parseInt(p2[1].trim());
                int z2 = Integer.parseInt(p2[2].trim());

                AABB box = new AABB(
                        Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                        Math.max(x1, x2) + 1.0, Math.max(y1, y2) + 1.0, Math.max(z1, z2) + 1.0
                );
                ZoneEntry entry = new ZoneEntry(i, box);
                if (dimensionId != null && !dimensionId.isEmpty()) {
                    zonesByDimension.computeIfAbsent(dimensionId, k -> new ArrayList<>()).add(entry);
                } else {
                    globalLegacyZones.add(entry);
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid exclusion zone number in config: {}", rawZone, e);
            }
        }
    }

    private void saveCurrentZone(Level level, Player player, ConfigHolder<ModConfig> config, ModConfig cdata) {
        if (pos1 == null || pos2 == null) {
            return;
        }
        cdata.excludedZones.add(formatZoneString(getDimensionId(level), pos1, pos2));
        config.save();
        syncZonesFromConfig(cdata);
        player.sendSystemMessage(Component.translatable("autotorch.message.zone_saved", cdata.excludedZones.size()));
    }

    public void setPos1(BlockPos pos, Player player) {
        this.pos1 = pos.immutable();
        player.sendOverlayMessage(Component.translatable("autotorch.message.point1_set", pos.getX(), pos.getY(), pos.getZ()));
    }

    public void setPos2(BlockPos pos, Player player) {
        this.pos2 = pos.immutable();
        player.sendOverlayMessage(Component.translatable("autotorch.message.point2_set", pos.getX(), pos.getY(), pos.getZ()));
    }

    public boolean isInExcludedZone(Level level, BlockPos pos) {
        return isInExcludedZone(level != null ? getDimensionId(level) : null, pos);
    }

    public boolean isInExcludedZone(String dimensionId, BlockPos pos) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;

        if (dimensionId != null && containsPoint(zonesByDimension.getOrDefault(dimensionId, List.of()), px, py, pz)) {
            return true;
        }
        return containsPoint(globalLegacyZones, px, py, pz);
    }

    public List<AABB> getExcludedZones(Level level) {
        return getExcludedZones(level != null ? getDimensionId(level) : null);
    }

    public List<AABB> getExcludedZones(String dimensionId) {
        List<AABB> activeZones = new ArrayList<>();
        if (dimensionId != null) {
            for (ZoneEntry entry : zonesByDimension.getOrDefault(dimensionId, List.of())) {
                activeZones.add(entry.box());
            }
        }
        for (ZoneEntry entry : globalLegacyZones) {
            activeZones.add(entry.box());
        }
        return activeZones;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    private static String getDimensionId(Level level) {
        return level.dimension().identifier().toString();
    }

    private static boolean containsPoint(List<ZoneEntry> entries, double x, double y, double z) {
        for (ZoneEntry entry : entries) {
            if (entry.box().contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static void collectMatchingIndices(List<ZoneEntry> entries, double x, double y, double z, Set<Integer> out) {
        for (ZoneEntry entry : entries) {
            if (entry.box().contains(x, y, z)) {
                out.add(entry.configIndex());
            }
        }
    }
}
