package autotorch.autotorch.client;

import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {
    private static final KeyBinding.Category keyCategory = KeyBinding.Category.create(Identifier.of("autotorch", "optioncategory"));

    private static final KeyBinding AutoPlaceBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "autotorch.autotorch.toggle",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    keyCategory
            )
    );

    private static final KeyBinding BlacklistBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "autotorch.autotorch.blacklist",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    keyCategory
            )
    );

    private static final KeyBinding ZoneSelectionBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "autotorch.autotorch.zoneselector",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    keyCategory
            )
    );

    public static void init() {
        // Invocar este método vacío asegura que Java cargue la clase y registre los controles
    }

    public static void handleInputs(MinecraftClient client, ConfigHolder<ModConfig> config, ModConfig cdata, ZoneManager zoneManager) {
        if (AutoPlaceBinding.wasPressed()) {
            cdata.enabled = !cdata.enabled;
            var msg = cdata.enabled ? Text.translatable("autotorch.message.enabled") : Text.translatable("autotorch.message.disabled");
            client.player.sendMessage(msg, true);
        }

        if (BlacklistBinding.wasPressed()) {
            HitResult hit = client.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                Block block = client.world.getBlockState(pos).getBlock();
                String blockId = Registries.BLOCK.getId(block).toString();
                
                if (cdata.blacklistedBlocks.contains(blockId)) {
                    cdata.blacklistedBlocks.remove(blockId);
                    client.player.sendMessage(Text.translatable("autotorch.message.blacklist_removed", blockId), true);
                } else {
                    cdata.blacklistedBlocks.add(blockId);
                    client.player.sendMessage(Text.translatable("autotorch.message.blacklist_added", blockId), true);
                }
                config.save(); // Guarda el cambio en el archivo config al instante
            }
        }

        if (ZoneSelectionBinding.wasPressed()) {
            if (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                zoneManager.clearZones(client, config, cdata);
            } else {
                zoneManager.toggleZoneSelectionMode(client, config, cdata);
            }
        }
    }
}