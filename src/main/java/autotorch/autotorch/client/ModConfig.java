package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.*;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

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
}