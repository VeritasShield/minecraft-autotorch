package autotorch.autotorch.client;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;

public class TorchPlacementEngine {
    // Evita poner antorchas repetidas mientras el juego procesa la luz
    private int placeCooldown = 0; 
    // Variables para el retardo al romper bloques
    private int pendingTorchDelay = 0;
    private BlockPos pendingTorchPos = null;
    private InteractionHand pendingHand = null;
    private int pendingSlot = -1;
    
    // Variables para volver al slot anterior de forma natural
    private int revertSlotDelay = 0;
    private int revertSlotIndex = -1;

    public void tick(Minecraft client, ModConfig cdata, ZoneManager zoneManager) {
        if (placeCooldown > 0) {
            placeCooldown--;
        }

        // --- SISTEMA DE REVERSIÓN VISUAL DE SLOT ---
        if (revertSlotDelay > 0) {
            revertSlotDelay--;
            if (revertSlotDelay == 0 && revertSlotIndex != -1 && client.player != null) {
                client.player.getInventory().setSelectedSlot(revertSlotIndex);
                client.player.connection.send(new ServerboundSetCarriedItemPacket(revertSlotIndex));
                revertSlotIndex = -1;
            }
        }

        // --- SISTEMA DE RETRASO PARA BLOQUES ROTOS ---
        if (pendingTorchDelay > 0) {
            pendingTorchDelay--;
            if (pendingTorchDelay == 0 && pendingTorchPos != null && client.level != null) {
                if (needsTorch(client, pendingTorchPos, cdata, zoneManager)) {
                    placeTorch(client, pendingTorchPos, pendingHand, pendingSlot, cdata);
                    placeCooldown = cdata.humanizedDelay ? (cdata.placeCooldownTicks + 1 + (int)(Math.random() * cdata.placeCooldownVariance)) : cdata.placeCooldownTicks;
                }
                pendingTorchPos = null;
                pendingHand = null;
                pendingSlot = -1;
            }
        }
        // ---------------------------------------------

        if (client.player == null || client.level == null) return;
        if (!cdata.enabled) return;
        // Si estamos en cooldown o esperando para poner una antorcha, no buscamos más
        if (placeCooldown > 0 || pendingTorchPos != null) return;

        // --- SISTEMA DE THROTTLING DE ESCANEO ---
        if (client.level.getGameTime() % cdata.scanDelayTicks != 0) return;

        int torchSlot = -1;
        InteractionHand placingHand = InteractionHand.MAIN_HAND;

        if (isTorch(client.player.getOffhandItem())) {
            placingHand = InteractionHand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (isTorch(client.player.getInventory().getItem(i))) {
                    torchSlot = i;
                    break;
                }
            }
        }

        if (placingHand == InteractionHand.MAIN_HAND && torchSlot == -1) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();

        // 1. PRIORIDAD: Justo debajo de los pies
        if (needsTorch(client, playerPos, cdata, zoneManager)) {
            queueTorchPlacement(playerPos, placingHand, torchSlot, cdata);
            return;
        }

        // 2. ESCÁNER: Radio expansivo a la distancia configurada
        if (cdata.placementRadius > 0) {
            for (int r = 1; r <= cdata.placementRadius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int y = -cdata.verticalPlacementRadius; y <= cdata.verticalPlacementRadius; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (Math.abs(x) == r || Math.abs(z) == r) {
                                BlockPos checkPos = playerPos.offset(x, y, z);
                                
                                if (needsTorch(client, checkPos, cdata, zoneManager) && RaycastUtils.hasLineOfSight(client, checkPos)) {
                                    if (!cdata.requireLineOfSightAngle || RaycastUtils.isLookingAt(client, checkPos, cdata)) {
                                        queueTorchPlacement(checkPos, placingHand, torchSlot, cdata);
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

    private boolean isTorch(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.TORCH || item == Items.SOUL_TORCH) return true;
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof TorchBlock;
        }
        return stack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "torches")));
    }

    private boolean needsTorch(Minecraft client, BlockPos pos, ModConfig cdata, ZoneManager zoneManager) {
        return client.level.getBrightness(LightLayer.BLOCK, pos) < cdata.lightLevel && canPlaceTorch(client, pos, cdata, zoneManager);
    }

    private void queueTorchPlacement(BlockPos pos, InteractionHand hand, int slot, ModConfig cdata) {
        this.pendingTorchPos = pos;
        this.pendingHand = hand;
        this.pendingSlot = slot;
        this.pendingTorchDelay = cdata.lightUpdateDelayTicks; 
    }

    private void placeTorch(Minecraft client, BlockPos pos, InteractionHand hand, int hotbarSlot, ModConfig cdata) {
        int currentSlot = client.player.getInventory().getSelectedSlot();
        
        if (hand == InteractionHand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(hotbarSlot);
            client.player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }

        BlockPos target = pos.below();
        
        if (cdata.advancedPacketSpoofing) {
            Vec3 start = client.player.getEyePosition();
            Vec3 targetVec = Vec3.atCenterOf(target);
            double dx = targetVec.x - start.x;
            double dy = targetVec.y - start.y;
            double dz = targetVec.z - start.z;
            double diffXZ = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            float pitch = (float) -(Math.atan2(dy, diffXZ) * (180.0 / Math.PI));
            client.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, client.player.onGround(), false));
        } else if (cdata.accuratePlacement) {
            client.player.connection.send(new ServerboundMovePlayerPacket.Rot(client.player.getYRot(), 90.0F, client.player.onGround(), false));
        }
        
        InteractionResult result = client.gameMode.useItemOn(client.player, hand, new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
                
        if ((result == InteractionResult.PASS || result == InteractionResult.SUCCESS) && cdata.swingHand) {
            client.player.swing(hand);
        }

        if (hand == InteractionHand.MAIN_HAND) {
            this.revertSlotIndex = currentSlot;
            this.revertSlotDelay = cdata.humanizedDelay ? (cdata.slotRevertDelayTicks + 1 + (int)(Math.random() * cdata.slotRevertVariance)) : cdata.slotRevertDelayTicks;
        }
    }

    public boolean canPlaceTorch(Minecraft client, BlockPos pos, ModConfig cdata, ZoneManager zoneManager) {
        BlockPos supportPos = pos.below();
        Block supportBlock = client.level.getBlockState(supportPos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(supportBlock).toString();

        if (cdata.blacklistedBlocks.contains(blockId) || zoneManager.isInExcludedZone(pos)) return false;

        return (client.level.getBlockState(pos).canBeReplaced() &&
                client.level.getBlockState(pos).getFluidState().isEmpty() &&
                Block.canSupportCenter(client.level, supportPos, Direction.UP));
    }
}