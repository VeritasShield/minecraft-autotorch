package autotorch.autotorch.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneManagerTest {

    @Test
    void formatZoneStringNormalizesBoundsWithDimensionPrefix() {
        String formatted = ZoneManager.formatZoneString(
                "minecraft:overworld",
                new BlockPos(10, 70, -5),
                new BlockPos(2, 64, 8)
        );
        assertEquals("minecraft:overworld@2,64,-5|10,70,8", formatted);
    }

    @Test
    void exclusionZonesAreIsolatedPerDimensionWithLegacyFallback() {
        ModConfig config = new ModConfig();
        config.excludedZones.add("minecraft:overworld@10,60,10|20,70,20");
        config.excludedZones.add("100,60,100|105,65,105");

        ZoneManager manager = new ZoneManager();
        manager.syncZonesFromConfig(config);

        BlockPos overworldPoint = new BlockPos(15, 64, 15);
        assertTrue(manager.isInExcludedZone("minecraft:overworld", overworldPoint));
        assertFalse(manager.isInExcludedZone("minecraft:the_nether", overworldPoint));
        assertFalse(manager.isInExcludedZone("minecraft:the_end", overworldPoint));

        BlockPos legacyPoint = new BlockPos(102, 62, 102);
        assertTrue(manager.isInExcludedZone("minecraft:overworld", legacyPoint));
        assertTrue(manager.isInExcludedZone("minecraft:the_nether", legacyPoint));

        assertEquals(2, manager.getExcludedZones("minecraft:overworld").size());
        assertEquals(1, manager.getExcludedZones("minecraft:the_nether").size());
    }

    @Test
    void syncZonesRebuildsCacheAfterConfigModification() {
        ModConfig config = new ModConfig();
        config.excludedZones.add("minecraft:overworld@0,64,0|5,68,5");

        ZoneManager manager = new ZoneManager();
        manager.syncZonesFromConfig(config);
        assertTrue(manager.isInExcludedZone("minecraft:overworld", new BlockPos(3, 65, 3)));

        config.excludedZones.clear();
        config.excludedZones.add("minecraft:overworld@50,64,50|55,68,55");
        manager.syncZonesFromConfig(config);

        assertFalse(manager.isInExcludedZone("minecraft:overworld", new BlockPos(3, 65, 3)));
        assertTrue(manager.isInExcludedZone("minecraft:overworld", new BlockPos(52, 65, 52)));
    }

    @Test
    void deleteZonesContainingRemovesOnlyMatchingActiveDimensionZones() {
        ModConfig config = new ModConfig();
        config.excludedZones.add("malformed-entry");
        config.excludedZones.add("minecraft:overworld@0,60,0|5,70,5");
        config.excludedZones.add("minecraft:the_nether@0,60,0|5,70,5");
        config.excludedZones.add("minecraft:overworld@30,60,30|35,70,35");

        ZoneManager manager = new ZoneManager();
        manager.syncZonesFromConfig(config);

        boolean removed = manager.deleteZonesContaining("minecraft:overworld", new BlockPos(2, 65, 2), config);
        assertTrue(removed);
        assertFalse(manager.isInExcludedZone("minecraft:overworld", new BlockPos(2, 65, 2)));
        assertTrue(manager.isInExcludedZone("minecraft:the_nether", new BlockPos(2, 65, 2)));
        assertTrue(manager.isInExcludedZone("minecraft:overworld", new BlockPos(32, 65, 32)));
        assertEquals(
                List.of("malformed-entry", "minecraft:the_nether@0,60,0|5,70,5", "minecraft:overworld@30,60,30|35,70,35"),
                config.excludedZones
        );
    }

    @Test
    void onWorldChangedResetsSelectionStateAndPoints() {
        ZoneManager manager = new ZoneManager();
        manager.beginSelectionForTest(new BlockPos(1, 64, 1), new BlockPos(5, 68, 5));
        assertTrue(manager.isZoneSelectionMode());

        manager.onWorldChanged();
        assertFalse(manager.isZoneSelectionMode());
        assertNull(manager.getPos1());
        assertNull(manager.getPos2());
    }
}
