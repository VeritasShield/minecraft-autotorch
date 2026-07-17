package autotorch.autotorch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class AutotorchClient implements ClientModInitializer {
    public static AutotorchClient INSTANCE;
    private Minecraft client;
    public ConfigHolder<ModConfig> CONFIG;
    private ModConfig CDATA;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("autotorch");

    public final ZoneManager zoneManager = new ZoneManager();
    public final TorchPlacementEngine placementEngine = new TorchPlacementEngine();

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        this.client = Minecraft.getInstance();
        CONFIG = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        CDATA = CONFIG.getConfig();
        
        KeybindManager.init(); // Asegurar que las teclas se registran en Fabric
        
        CONFIG.registerLoadListener((manager, data) -> {
            CDATA = data;
            zoneManager.syncZonesFromConfig(CDATA);
            return InteractionResult.SUCCESS;
        });
        CONFIG.registerSaveListener((manager, data) -> {
            CDATA = data;
            zoneManager.syncZonesFromConfig(CDATA);
            return InteractionResult.SUCCESS;
        });
        zoneManager.syncZonesFromConfig(CDATA);

        // Registrar evento de Click Izquierdo (Punto 1)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide() && hand == InteractionHand.MAIN_HAND && zoneManager.isZoneSelectionMode() && player.getMainHandItem().isEmpty()) {
                zoneManager.setPos1(pos, player);
                return InteractionResult.SUCCESS; // Cancela romper el bloque
            }
            return InteractionResult.PASS;
        });

        // Registrar evento de Click Derecho (Punto 2)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() && hand == InteractionHand.MAIN_HAND && zoneManager.isZoneSelectionMode() && player.getMainHandItem().isEmpty()) {
                zoneManager.setPos2(hitResult.getBlockPos(), player);
                return InteractionResult.SUCCESS; // Cancela la interacción estándar
            }
            return InteractionResult.PASS;
        });
    }

    public void tick(Minecraft client) {
        if (client.player != null && client.level != null) {
            KeybindManager.handleInputs(client, CONFIG, CDATA, zoneManager);
            
            // Generar las partículas usando el reloj de Ticks en lugar de los Frames gráficos
            SelectionRenderer.spawnSelectionParticles(client, zoneManager);
            SelectionRenderer.renderDarknessESP(client, CDATA);

            placementEngine.tick(client, CDATA, zoneManager);
        }
    }
}