package autotorch.autotorch.client;

import com.google.common.collect.ImmutableSet;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.Block;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class AutotorchClient implements ClientModInitializer {
    private MinecraftClient client;
    public ConfigHolder<ModConfig> CONFIG;
    private ModConfig CDATA;
    private static final ImmutableSet<Item> TorchSet = ImmutableSet.of(Items.TORCH, Items.SOUL_TORCH);

    private static final KeyBinding.Category keyCategory = KeyBinding.Category.create(Identifier.of("autotorch", "optioncategory"));
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger("autotorch");

    private static final KeyBinding AutoPlaceBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "autotorch.autotorch.toggle",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    keyCategory
            )
    );

    // Evita poner antorchas repetidas mientras el juego procesa la luz
    private int placeCooldown = 0; 

    @Override
    public void onInitializeClient() {
        this.client = MinecraftClient.getInstance();
        CONFIG = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        CDATA = CONFIG.getConfig();
        CONFIG.registerLoadListener((manager, data) -> {
            CDATA = data;
            return ActionResult.SUCCESS;
        });
    }

    public void tick(MinecraftClient client) {
        if (placeCooldown > 0) {
            placeCooldown--;
        }

        if (client.player != null && client.world != null) {
            if (AutoPlaceBinding.wasPressed()) {
                CDATA.enabled = !CDATA.enabled;
                var msg = CDATA.enabled ? Text.translatable("autotorch.message.enabled") : Text.translatable("autotorch.message.disabled");
                client.player.sendMessage(msg, false);
            }
            if (!CDATA.enabled) return;
            
            if (placeCooldown > 0) return;

            int torchSlot = -1;
            Hand placingHand = Hand.MAIN_HAND;

            if (TorchSet.contains(client.player.getOffHandStack().getItem())) {
                placingHand = Hand.OFF_HAND;
            } else {
                for (int i = 0; i < 9; i++) {
                    if (TorchSet.contains(client.player.getInventory().getStack(i).getItem())) {
                        torchSlot = i;
                        break;
                    }
                }
            }

            if (placingHand == Hand.MAIN_HAND && torchSlot == -1) {
                return;
            }

            BlockPos playerPos = client.player.getBlockPos();

            // 1. PRIORIDAD: Justo debajo de los pies
            if (needsTorch(client, playerPos)) {
                placeTorch(playerPos, placingHand, torchSlot);
                placeCooldown = CDATA.humanizedDelay ? (6 + (int)(Math.random() * 6)) : 5; // Pausa de 0.25 seg
                return;
            }

            // 2. ESCÁNER: Radio expansivo a la distancia configurada
            if (CDATA.placementRadius > 0) {
                // Escanea el nivel actual y un bloque arriba/abajo
                for (int r = 1; r <= CDATA.placementRadius; r++) {
                    for (int x = -r; x <= r; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -r; z <= r; z++) {
                                // Analiza solo los bordes del anillo cuadrado actual
                                if (Math.abs(x) == r || Math.abs(z) == r) {
                                    BlockPos checkPos = playerPos.add(x, y, z);
                                    
                                    if (needsTorch(client, checkPos) && hasLineOfSight(client, checkPos)) {
                                        // Validar que lo estamos mirando (si la opción de seguridad está activa)
                                        if (!CDATA.requireLineOfSightAngle || isLookingAt(client, checkPos)) {
                                            placeTorch(checkPos, placingHand, torchSlot);
                                            placeCooldown = CDATA.humanizedDelay ? (6 + (int)(Math.random() * 6)) : 5;
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private boolean needsTorch(MinecraftClient client, BlockPos pos) {
        return client.world.getLightLevel(LightType.BLOCK, pos) < CDATA.lightLevel && canPlaceTorch(pos);
    }

    // El "láser" que evita poner antorchas a través de las paredes
    private boolean hasLineOfSight(MinecraftClient client, BlockPos target) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(target);
        RaycastContext context = new RaycastContext(
                eyePos, targetVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        );
        BlockHitResult hit = client.world.raycast(context);
        return hit.getType() == HitResult.Type.MISS || 
               hit.getBlockPos().equals(target) || 
               hit.getBlockPos().equals(target.down());
    }

    // Calcula si el bloque está en un cono configurable frente a la cámara del jugador
    private boolean isLookingAt(MinecraftClient client, BlockPos target) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(target);
        
        // Vector hacia el bloque
        Vec3d toTarget = targetVec.subtract(eyePos).normalize();
        // Vector hacia donde está mirando el jugador
        Vec3d lookVec = client.player.getRotationVec(1.0F).normalize();
        
        // El producto punto da 1.0 si miras exacto, 0.0 si está a 90 grados.
        double angle = CDATA.lineOfSightAngle;
        double minDotProduct = Math.cos(Math.toRadians(angle / 2.0));
        return lookVec.dotProduct(toTarget) > minDotProduct;
    }

    private void placeTorch(BlockPos pos, Hand hand, int hotbarSlot) {
        int previousSlot = client.player.getInventory().getSelectedSlot();
        
        if (hand == Hand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(hotbarSlot);
        }

        BlockPos target = pos.down();
        Vec3d hitVec = Vec3d.ofCenter(target);
        
        if (CDATA.accuratePlacement) {
            PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(client.player.getYaw(), 90.0F, true, false);
            client.player.networkHandler.sendPacket(packet);
        }
        
        ActionResult result = client.interactionManager.interactBlock(client.player, hand,
                new BlockHitResult(hitVec, Direction.UP, target, false));
                
        if (result == ActionResult.PASS || result == ActionResult.SUCCESS) {
            if (CDATA.swingHand) {
                client.player.swingHand(hand); // ¡Esto salva vidas en servidores!
            }
        }

        if (hand == Hand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    public boolean canPlaceTorch(BlockPos pos) {
        return (client.world.getBlockState(pos).isReplaceable() &&
                client.world.getBlockState(pos).getFluidState().isEmpty() &&
                Block.sideCoversSmallSquare(client.world, pos.down(), Direction.UP));
    }
}