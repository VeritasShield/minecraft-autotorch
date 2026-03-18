package autotorch.autotorch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
    private MinecraftClient client;
    public ConfigHolder<ModConfig> CONFIG;
    private ModConfig CDATA;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("autotorch");

    public final ZoneManager zoneManager = new ZoneManager();
    public final TorchPlacementEngine placementEngine = new TorchPlacementEngine();

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        this.client = MinecraftClient.getInstance();
        CONFIG = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        CDATA = CONFIG.getConfig();
        
        KeybindManager.init(); // Asegurar que las teclas se registran en Fabric
        
        CONFIG.registerLoadListener((manager, data) -> {
            CDATA = data;
            zoneManager.syncZonesFromConfig(CDATA);
            return ActionResult.SUCCESS;
        });
        CONFIG.registerSaveListener((manager, data) -> {
            CDATA = data;
            zoneManager.syncZonesFromConfig(CDATA);
            return ActionResult.SUCCESS;
        });
        zoneManager.syncZonesFromConfig(CDATA);

        // Registrar evento de Click Izquierdo (Punto 1)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND && zoneManager.isZoneSelectionMode() && player.getMainHandStack().isEmpty()) {
                zoneManager.setPos1(pos, player);
                return ActionResult.SUCCESS; // Cancela romper el bloque
            }
            return ActionResult.PASS;
        });

        // Registrar evento de Click Derecho (Punto 2)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND && zoneManager.isZoneSelectionMode() && player.getMainHandStack().isEmpty()) {
                zoneManager.setPos2(hitResult.getBlockPos(), player);
                return ActionResult.SUCCESS; // Cancela la interacción estándar
            }
            return ActionResult.PASS;
        });
    }

    public void tick(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            KeybindManager.handleInputs(client, CONFIG, CDATA, zoneManager);
            
            // Generar las partículas usando el reloj de Ticks en lugar de los Frames gráficos
            SelectionRenderer.spawnSelectionParticles(client, zoneManager);

            placementEngine.tick(client, CDATA, zoneManager);
        }
    }
}