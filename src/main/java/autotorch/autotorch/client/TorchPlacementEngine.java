package autotorch.autotorch.client;

import net.minecraft.block.Block;
import net.minecraft.block.TorchBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

public class TorchPlacementEngine {
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

    public void tick(MinecraftClient client, ModConfig cdata, ZoneManager zoneManager) {
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

        if (client.player == null || client.world == null) return;
        if (!cdata.enabled) return;
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
                                BlockPos checkPos = playerPos.add(x, y, z);
                                
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
        return stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "torches")));
    }

    private boolean needsTorch(MinecraftClient client, BlockPos pos, ModConfig cdata, ZoneManager zoneManager) {
        return client.world.getLightLevel(LightType.BLOCK, pos) < cdata.lightLevel && canPlaceTorch(client, pos, cdata, zoneManager);
    }

    private void queueTorchPlacement(BlockPos pos, Hand hand, int slot, ModConfig cdata) {
        this.pendingTorchPos = pos;
        this.pendingHand = hand;
        this.pendingSlot = slot;
        this.pendingTorchDelay = cdata.lightUpdateDelayTicks; 
    }

    private void placeTorch(MinecraftClient client, BlockPos pos, Hand hand, int hotbarSlot, ModConfig cdata) {
        int currentSlot = client.player.getInventory().getSelectedSlot();
        
        if (hand == Hand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(hotbarSlot);
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }

        BlockPos target = pos.down();
        
        if (cdata.accuratePlacement) {
            client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(client.player.getYaw(), 90.0F, true, false));
        }
        
        ActionResult result = client.interactionManager.interactBlock(client.player, hand, new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false));
                
        if ((result == ActionResult.PASS || result == ActionResult.SUCCESS) && cdata.swingHand) {
            client.player.swingHand(hand);
        }

        if (hand == Hand.MAIN_HAND) {
            this.revertSlotIndex = currentSlot;
            this.revertSlotDelay = cdata.humanizedDelay ? (cdata.slotRevertDelayTicks + 1 + (int)(Math.random() * cdata.slotRevertVariance)) : cdata.slotRevertDelayTicks;
        }
    }

    public boolean canPlaceTorch(MinecraftClient client, BlockPos pos, ModConfig cdata, ZoneManager zoneManager) {
        BlockPos supportPos = pos.down();
        Block supportBlock = client.world.getBlockState(supportPos).getBlock();
        String blockId = Registries.BLOCK.getId(supportBlock).toString();

        if (cdata.blacklistedBlocks.contains(blockId) || zoneManager.isInExcludedZone(pos)) return false;

        return (client.world.getBlockState(pos).isReplaceable() &&
                client.world.getBlockState(pos).getFluidState().isEmpty() &&
                Block.sideCoversSmallSquare(client.world, supportPos, Direction.UP));
    }
}