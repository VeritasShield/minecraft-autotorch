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
import net.minecraft.world.inventory.ClickType;

public class TorchPlacementEngine {
    private int placeCooldown = 0; 
    private int pendingTorchDelay = 0;
    private BlockPos pendingTorchPos = null;
    private Direction pendingTorchDir = null;
    
    private int revertSlotDelay = 0;
    private int revertSlotIndex = -1;
    private int revertInventorySwapSource = -1;
    private int revertInventorySwapTarget = -1;

    public void tick(Minecraft client, ModConfig cdata, ZoneManager zoneManager) {
        if (placeCooldown > 0) {
            placeCooldown--;
        }

        if (revertSlotDelay > 0) {
            revertSlotDelay--;
            if (revertSlotDelay == 0 && client.player != null) {
                if (revertSlotIndex != -1) {
                    client.player.getInventory().setSelectedSlot(revertSlotIndex);
                    client.player.connection.send(new ServerboundSetCarriedItemPacket(revertSlotIndex));
                    revertSlotIndex = -1;
                }
                if (revertInventorySwapSource != -1 && revertInventorySwapTarget != -1) {
                    // Swap back the item from hotbar to inventory
                    client.gameMode.handleInventoryMouseClick(0, revertInventorySwapSource, revertInventorySwapTarget, ClickType.SWAP, client.player);
                    revertInventorySwapSource = -1;
                    revertInventorySwapTarget = -1;
                }
            }
        }

        if (pendingTorchDelay > 0) {
            pendingTorchDelay--;
            if (pendingTorchDelay == 0 && pendingTorchPos != null && client.level != null) {
                Direction bestDir = getBestPlacementDirection(client, pendingTorchPos, cdata, zoneManager);
                if (bestDir != null && client.level.getBrightness(LightLayer.BLOCK, pendingTorchPos) < cdata.lightLevel) {
                    boolean placed = tryPlaceTorch(client, pendingTorchPos, bestDir, cdata);
                    if (placed) {
                        placeCooldown = cdata.humanizedDelay ? (cdata.placeCooldownTicks + 1 + (int)(Math.random() * cdata.placeCooldownVariance)) : cdata.placeCooldownTicks;
                    }
                }
                pendingTorchPos = null;
                pendingTorchDir = null;
            }
        }

        if (client.player == null || client.level == null) return;
        if (!cdata.enabled) return;
        if (placeCooldown > 0 || pendingTorchPos != null) return;
        if (client.level.getGameTime() % cdata.scanDelayTicks != 0) return;

        BlockPos playerPos = client.player.blockPosition();

        Direction bestPlayerPosDir = getBestPlacementDirection(client, playerPos, cdata, zoneManager);
        if (bestPlayerPosDir != null && client.level.getBrightness(LightLayer.BLOCK, playerPos) < cdata.lightLevel) {
            queueTorchPlacement(playerPos, bestPlayerPosDir, cdata);
            return;
        }

        if (cdata.placementRadius > 0) {
            for (int r = 1; r <= cdata.placementRadius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int y = -cdata.verticalPlacementRadius; y <= cdata.verticalPlacementRadius; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (Math.abs(x) == r || Math.abs(z) == r) {
                                BlockPos checkPos = playerPos.offset(x, y, z);
                                
                                if (client.level.getBrightness(LightLayer.BLOCK, checkPos) < cdata.lightLevel) {
                                    Direction bestDir = getBestPlacementDirection(client, checkPos, cdata, zoneManager);
                                    if (bestDir != null && RaycastUtils.hasLineOfSight(client, checkPos)) {
                                        if (!cdata.requireLineOfSightAngle || RaycastUtils.isLookingAt(client, checkPos, cdata)) {
                                            queueTorchPlacement(checkPos, bestDir, cdata);
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
        return stack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "torches")));
    }

    private Direction getBestPlacementDirection(Minecraft client, BlockPos pos, ModConfig cdata, ZoneManager zoneManager) {
        if (zoneManager.isInExcludedZone(pos)) return null;
        if (!client.level.getBlockState(pos).canBeReplaced() || !client.level.getBlockState(pos).getFluidState().isEmpty()) return null;

        Direction bestDir = null;
        double bestDot = -2.0;
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getViewVector(1.0F).normalize();

        if (cdata.preferWallPlacement) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos wallPos = pos.relative(dir);
                Block wallBlock = client.level.getBlockState(wallPos).getBlock();
                String blockId = BuiltInRegistries.BLOCK.getKey(wallBlock).toString();
                if (!cdata.blacklistedBlocks.contains(blockId) && Block.canSupportCenter(client.level, wallPos, dir.getOpposite())) {
                    Vec3 toTarget = Vec3.atCenterOf(wallPos).subtract(eyePos).normalize();
                    double dot = lookVec.dot(toTarget);
                    if (dot > bestDot) {
                        bestDot = dot;
                        bestDir = dir;
                    }
                }
            }
            if (bestDir != null) return bestDir;
        }

        BlockPos supportPos = pos.below();
        Block supportBlock = client.level.getBlockState(supportPos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(supportBlock).toString();
        if (!cdata.blacklistedBlocks.contains(blockId) && Block.canSupportCenter(client.level, supportPos, Direction.UP)) {
            return Direction.DOWN;
        }

        return null;
    }

    private void queueTorchPlacement(BlockPos pos, Direction dir, ModConfig cdata) {
        this.pendingTorchPos = pos;
        this.pendingTorchDir = dir;
        this.pendingTorchDelay = cdata.lightUpdateDelayTicks; 
    }

    private boolean tryPlaceTorch(Minecraft client, BlockPos pos, Direction dir, ModConfig cdata) {
        int torchSlot = -1;
        int inventoryTorchSlot = -1;
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
            if (torchSlot == -1 && cdata.useInventoryTorches) {
                // In survival inventory (container id 0), slots 9-35 are the main inventory.
                // The slot index in the packet for main inventory starts at 9.
                for (int i = 9; i < 36; i++) {
                    if (isTorch(client.player.getInventory().getItem(i))) {
                        inventoryTorchSlot = i;
                        torchSlot = cdata.refillHotbarSlot - 1;
                        break;
                    }
                }
            }
        }

        if (placingHand == InteractionHand.MAIN_HAND && torchSlot == -1) return false;

        int currentSlot = client.player.getInventory().getSelectedSlot();
        
        if (inventoryTorchSlot != -1) {
            // Swap item from inventory to the target hotbar slot
            client.gameMode.handleInventoryMouseClick(0, inventoryTorchSlot, torchSlot, ClickType.SWAP, client.player);
        }

        if (placingHand == InteractionHand.MAIN_HAND) {
            client.player.getInventory().setSelectedSlot(torchSlot);
            client.player.connection.send(new ServerboundSetCarriedItemPacket(torchSlot));
        }

        BlockPos targetBlock = pos.relative(dir);
        Direction hitFace = dir.getOpposite();

        if (cdata.advancedPacketSpoofing) {
            Vec3 start = client.player.getEyePosition();
            Vec3 targetVec = Vec3.atCenterOf(targetBlock).add(Vec3.atLowerCornerOf(hitFace.getNormal()).scale(0.5));
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
        
        InteractionResult result = client.gameMode.useItemOn(client.player, placingHand, new BlockHitResult(Vec3.atCenterOf(targetBlock), hitFace, targetBlock, false));
                
        if ((result == InteractionResult.PASS || result == InteractionResult.SUCCESS) && cdata.swingHand) {
            client.player.swing(placingHand);
        }

        if (placingHand == InteractionHand.MAIN_HAND) {
            this.revertSlotIndex = currentSlot;
            if (inventoryTorchSlot != -1) {
                this.revertInventorySwapSource = inventoryTorchSlot;
                this.revertInventorySwapTarget = torchSlot;
            }
            this.revertSlotDelay = cdata.humanizedDelay ? (cdata.slotRevertDelayTicks + 1 + (int)(Math.random() * cdata.slotRevertVariance)) : cdata.slotRevertDelayTicks;
            if (this.revertSlotDelay == 0) {
                this.revertSlotDelay = 1; // Ensure it happens next tick
            }
        }
        return true;
    }
}