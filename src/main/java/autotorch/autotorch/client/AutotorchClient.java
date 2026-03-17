package autotorch.autotorch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
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
import net.minecraft.block.TorchBlock;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

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
    // Variables para el retardo al romper bloques
    private int pendingTorchDelay = 0;
    private BlockPos pendingTorchPos = null;
    private Hand pendingHand = null;
    private int pendingSlot = -1;
    
    // Variables para volver al slot anterior de forma natural
    private int revertSlotDelay = 0;
    private int revertSlotIndex = -1;

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

        // --- SISTEMA DE REVERSIÓN VISUAL DE SLOT ---
        if (revertSlotDelay > 0) {
            revertSlotDelay--;
            if (revertSlotDelay == 0 && revertSlotIndex != -1 && client.player != null) {
                client.player.getInventory().setSelectedSlot(revertSlotIndex);
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(revertSlotIndex));
                revertSlotIndex = -1;
            }
        }

        // --- SISTEMA DE RETRASO PARA BLOQUES ROTOS ---
        if (pendingTorchDelay > 0) {
            pendingTorchDelay--;
            if (pendingTorchDelay == 0 && pendingTorchPos != null && client.world != null) {
                // Volver a comprobar la luz después del retraso
                if (needsTorch(client, pendingTorchPos)) {
                    placeTorch(pendingTorchPos, pendingHand, pendingSlot);
                    placeCooldown = CDATA.humanizedDelay ? (CDATA.placeCooldownTicks + 1 + (int)(Math.random() * 6)) : CDATA.placeCooldownTicks;
                }
                // Limpiar la cola
                pendingTorchPos = null;
                pendingHand = null;
                pendingSlot = -1;
            }
        }
        // ---------------------------------------------

        if (client.player != null && client.world != null) {
            if (AutoPlaceBinding.wasPressed()) {
                CDATA.enabled = !CDATA.enabled;
                var msg = CDATA.enabled ? Text.translatable("autotorch.message.enabled") : Text.translatable("autotorch.message.disabled");
                client.player.sendMessage(msg, true);
            }
            if (!CDATA.enabled) return;
            
            // Si estamos en cooldown o esperando para poner una antorcha, no buscamos más
            if (placeCooldown > 0 || pendingTorchPos != null) return;

            int torchSlot = -1;
            Hand placingHand = Hand.MAIN_HAND;

            if (isTorch(client.player.getOffHandStack())) {
                placingHand = Hand.OFF_HAND;
            } else {
                for (int i = 0; i < 9; i++) {
                    if (isTorch(client.player.getInventory().getStack(i))) {
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
                queueTorchPlacement(playerPos, placingHand, torchSlot);
                return;
            }

            // 2. ESCÁNER: Radio expansivo a la distancia configurada
            if (CDATA.placementRadius > 0) {
                for (int r = 1; r <= CDATA.placementRadius; r++) {
                    for (int x = -r; x <= r; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -r; z <= r; z++) {
                                if (Math.abs(x) == r || Math.abs(z) == r) {
                                    BlockPos checkPos = playerPos.add(x, y, z);
                                    
                                    if (needsTorch(client, checkPos) && hasLineOfSight(client, checkPos)) {
                                        if (!CDATA.requireLineOfSightAngle || isLookingAt(client, checkPos)) {
                                            queueTorchPlacement(checkPos, placingHand, torchSlot);
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

    private boolean isTorch(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.TORCH || item == Items.SOUL_TORCH) return true;
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof TorchBlock;
        }
        // Fallback por si el servidor provee el tag común de la comunidad modder
        return stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "torches")));
    }

    private boolean needsTorch(MinecraftClient client, BlockPos pos) {
        return client.world.getLightLevel(LightType.BLOCK, pos) < CDATA.lightLevel && canPlaceTorch(pos);
    }
    // Método para encolar la colocación de la antorcha con un pequeño retraso
    private void queueTorchPlacement(BlockPos pos, Hand hand, int slot) {
        this.pendingTorchPos = pos;
        this.pendingHand = hand;
        this.pendingSlot = slot;
        // Esperamos unos ticks para dar tiempo a que la luz se actualice
        this.pendingTorchDelay = CDATA.lightUpdateDelayTicks; 
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
        int currentSlot = client.player.getInventory().getSelectedSlot();
        
        if (hand == Hand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(hotbarSlot);
            // Notificar al servidor que hemos cambiado a la antorcha para que parezca natural
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
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
            // Encolamos el retorno del arma para unos ticks después en vez de hacerlo instantáneamente
            this.revertSlotIndex = currentSlot;
            this.revertSlotDelay = CDATA.humanizedDelay ? (CDATA.slotRevertDelayTicks + 1 + (int)(Math.random() * 3)) : CDATA.slotRevertDelayTicks;
        }
    }

    public boolean canPlaceTorch(BlockPos pos) {
        return (client.world.getBlockState(pos).isReplaceable() &&
                client.world.getBlockState(pos).getFluidState().isEmpty() &&
                Block.sideCoversSmallSquare(client.world, pos.down(), Direction.UP));
    }
}