package autotorch.autotorch.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class AutotorchClient implements ClientModInitializer {
    public static AutotorchClient INSTANCE;
    private static final Logger LOGGER = LoggerFactory.getLogger("autotorch");

    public ConfigHolder<ModConfig> CONFIG;
    private ModConfig cdata;

    public final ZoneManager zoneManager = new ZoneManager();
    public final TorchPlacementEngine placementEngine = new TorchPlacementEngine();

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        CONFIG = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        cdata = CONFIG.getConfig();

        KeybindManager.init();

        CONFIG.registerLoadListener((manager, data) -> {
            cdata = data;
            zoneManager.syncZonesFromConfig(cdata);
            return InteractionResult.SUCCESS;
        });
        CONFIG.registerSaveListener((manager, data) -> {
            cdata = data;
            zoneManager.syncZonesFromConfig(cdata);
            return InteractionResult.SUCCESS;
        });
        zoneManager.syncZonesFromConfig(cdata);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            placementEngine.resetAllState();
            zoneManager.onWorldChanged();
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()
                    && hand == InteractionHand.MAIN_HAND
                    && zoneManager.isZoneSelectionMode()
                    && player.getMainHandItem().isEmpty()) {
                zoneManager.setPos1(pos, player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()
                    && hand == InteractionHand.MAIN_HAND
                    && zoneManager.isZoneSelectionMode()
                    && player.getMainHandItem().isEmpty()) {
                zoneManager.setPos2(hitResult.getBlockPos(), player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Auto Torch Enhanced initialized");
    }

    public void onBeforePlayerSendChanges(LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == player && client.level != null) {
            placementEngine.syncLevelState(client.level, zoneManager);
            placementEngine.onBeforeSendChanges(client, cdata, zoneManager);
        }
    }

    public void onAfterPlayerSendChanges(LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.player == player && client.level != null) {
                placementEngine.onAfterSendChanges(client, cdata);
            }
        } finally {
            placementEngine.ensureCameraRestored(player);
        }
    }

    public void tick(Minecraft client) {
        placementEngine.syncLevelState(client.level, zoneManager);
        placementEngine.ensureCameraRestored(client.player);

        if (client.player != null && client.level != null) {
            KeybindManager.handleInputs(client, CONFIG, cdata, zoneManager);
            SelectionRenderer.spawnSelectionParticles(client, zoneManager);
            SelectionRenderer.renderDarknessESP(client, cdata);
        }
    }
}
