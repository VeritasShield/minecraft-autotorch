package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.*;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import java.util.ArrayList;
import java.util.List;

@Config(name="autotorch")
public class ModConfig implements ConfigData {

    // --- PESTAÑA GENERAL ---
    @ConfigEntry.Category("general")
    @Comment("Enable the Auto Torch Mod")
    boolean enabled = true;

    @ConfigEntry.Category("general")
    @Comment("The block light level below which the torches are placed.")
    @ConfigEntry.BoundedDiscrete(min = 1, max = 14)
    int lightLevel = 1;

    @ConfigEntry.Category("general")
    @Comment("Max distance in blocks to place torches (0 = only at feet)")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
    int placementRadius = 3;

    @ConfigEntry.Category("general")
    @Comment("Max vertical distance (up/down) to place torches")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
    int verticalPlacementRadius = 1;

    // --- PESTAÑA SERVER FRIENDLY ---
    @ConfigEntry.Category("server_friendly")
    @Comment("Forces the player to swing their hand (Bypasses NoSwing checks)")
    boolean swingHand = true;

    @ConfigEntry.Category("server_friendly")
    @Comment("Adds a random delay between placements so it doesn't look like a machine")
    boolean humanizedDelay = true;

    @ConfigEntry.Category("server_friendly")
    @Comment("Enable accurate torch placement directly on block below.\n" +
            "WARNING: Disabling this is safer for servers.")
    boolean accuratePlacement = false;

    @ConfigEntry.Category("server_friendly")
    @Comment("Only place torches in the general direction you are looking (FOV check)")
    boolean requireLineOfSightAngle = true;

    @ConfigEntry.Category("server_friendly")
    @Comment("The angle of the cone in which to place torches, from 10 to 180 degrees.")
    @ConfigEntry.BoundedDiscrete(min = 10, max = 180)
    int lineOfSightAngle = 120;

    // --- PESTAÑA AVANZADO ---
    @ConfigEntry.Category("advanced")
    @Comment("Base cooldown (in ticks) between placing torches")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 40)
    int placeCooldownTicks = 5;

    @ConfigEntry.Category("advanced")
    @Comment("Ticks to wait for light level to update before placing a torch")
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    int lightUpdateDelayTicks = 4;

    @ConfigEntry.Category("advanced")
    @Comment("Base delay (in ticks) to return to the previously selected hotbar slot")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
    int slotRevertDelayTicks = 2;

    @ConfigEntry.Category("advanced")
    @Comment("Max random ticks added to the place cooldown (if humanized delay is on)")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
    int placeCooldownVariance = 6;

    @ConfigEntry.Category("advanced")
    @Comment("Max random ticks added to the slot revert delay (if humanized delay is on)")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
    int slotRevertVariance = 3;

    @ConfigEntry.Category("advanced")
    @Comment("List of block IDs where torches should NEVER be placed (e.g. minecraft:glass)")
    List<String> blacklistedBlocks = new ArrayList<>();

    @ConfigEntry.Category("advanced")
    @Comment("List of Anti-Torch zones (Format: x1,y1,z1|x2,y2,z2)")
    public List<String> excludedZones = new ArrayList<>();
}