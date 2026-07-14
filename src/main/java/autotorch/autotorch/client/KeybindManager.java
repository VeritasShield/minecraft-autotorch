package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {
    private static final KeyMapping.Category keyCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autotorch", "options"));

    private static final KeyMapping AutoPlaceBinding = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "autotorch.autotorch.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    keyCategory
            )
    );

    private static final KeyMapping BlacklistBinding = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "autotorch.autotorch.blacklist",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    keyCategory
            )
    );

    private static final KeyMapping ZoneSelectionBinding = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "autotorch.autotorch.zoneselector",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    keyCategory
            )
    );

    private static final KeyMapping ZoneDeleteBinding = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "autotorch.autotorch.zonedelete",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    keyCategory
            )
    );

    private static final KeyMapping ZoneClearAllBinding = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "autotorch.autotorch.zoneclearall",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    keyCategory
            )
    );

    public static void init() {
        // Invocar este método vacío asegura que Java cargue la clase y registre los controles
    }

    public static void handleInputs(Minecraft client, ConfigHolder<ModConfig> config, ModConfig cdata, ZoneManager zoneManager) {
        if (AutoPlaceBinding.consumeClick()) {
            cdata.enabled = !cdata.enabled;
            var msg = cdata.enabled ? Component.translatable("autotorch.message.enabled") : Component.translatable("autotorch.message.disabled");
            client.player.sendOverlayMessage(msg);
        }

        if (BlacklistBinding.consumeClick()) {
            HitResult hit = client.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                Block block = client.level.getBlockState(pos).getBlock();
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                
                if (cdata.blacklistedBlocks.contains(blockId)) {
                    cdata.blacklistedBlocks.remove(blockId);
                    client.player.sendOverlayMessage(Component.translatable("autotorch.message.blacklist_removed", blockId));
                } else {
                    cdata.blacklistedBlocks.add(blockId);
                    client.player.sendOverlayMessage(Component.translatable("autotorch.message.blacklist_added", blockId));
                }
                config.save(); // Guarda el cambio en el archivo config al instante
            }
        }

        if (ZoneDeleteBinding.consumeClick()) {
            zoneManager.deleteCurrentZone(client, config, cdata);
        }

        if (ZoneClearAllBinding.consumeClick()) {
            zoneManager.clearZones(client, config, cdata);
        }

        if (ZoneSelectionBinding.consumeClick()) {
            zoneManager.toggleZoneSelectionMode(client, config, cdata);
        }
    }
}