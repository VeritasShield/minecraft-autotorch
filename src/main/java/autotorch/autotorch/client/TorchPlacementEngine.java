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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.inventory.ContainerInput;

public class TorchPlacementEngine {
    private int placeCooldown = 0; 
    private int pendingTorchDelay = 0;
    private BlockPos pendingTorchBasePos = null;
    private BlockPos pendingTorchPos = null;
    private Direction pendingTorchDir = null;
    
    private int revertSlotDelay = 0;
    private int revertSlotIndex = -1;
    private int revertInventorySwapSource = -1;
    private int revertInventorySwapTarget = -1;

    private int pendingSwapWaitDelay = 0;
    private BlockPos swapWaitPos = null;
    private Direction swapWaitDir = null;
    private InteractionHand swapWaitHand = null;
    private int swapWaitCurrentSlot = -1;
    private int swapWaitTorchSlot = -1;
    private int swapWaitInventoryTorchSlot = -1;

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
                    // Send packet to swap the item back from hotbar to inventory
                    // Manually read the UI slots BEFORE the swap to get the correct state
                    net.minecraft.world.inventory.Slot sourceSlot = client.player.inventoryMenu.getSlot(revertInventorySwapSource);
                    net.minecraft.world.inventory.Slot targetSlot = client.player.inventoryMenu.getSlot(36 + revertInventorySwapTarget);
                    ItemStack sourceItem = sourceSlot.getItem().copy();
                    ItemStack targetItem = targetSlot.getItem().copy();
                    
                    // Send packet to swap the item back from hotbar to inventory

                    client.gameMode.handleContainerInput(0, revertInventorySwapSource, revertInventorySwapTarget, ContainerInput.SWAP, client.player);
                    
                    // Manually update the UI slots to ensure the open inventory reflects the revert immediately and keeps accurate count
                    sourceSlot.set(targetItem);
                    targetSlot.set(sourceItem);
                    
                    revertInventorySwapSource = -1;
                    revertInventorySwapTarget = -1;
                }
            }
        }

        if (pendingSwapWaitDelay > 0) {
            pendingSwapWaitDelay--;
            if (pendingSwapWaitDelay == 0 && swapWaitPos != null && client.level != null) {
                executeTorchPlacement(client, cdata, swapWaitPos, swapWaitDir, swapWaitHand, swapWaitCurrentSlot, swapWaitTorchSlot, swapWaitInventoryTorchSlot);
                swapWaitPos = null;
                swapWaitDir = null;
                swapWaitHand = null;
            }
        }

        if (pendingTorchDelay > 0) {
            pendingTorchDelay--;
            if (pendingTorchDelay == 0 && pendingTorchPos != null && pendingTorchBasePos != null && client.level != null) {
                int lght = client.level.getBrightness(LightLayer.BLOCK, pendingTorchBasePos);
                if (lght < cdata.lightLevel) {
                    boolean placed = tryPlaceTorch(client, pendingTorchPos, pendingTorchDir, cdata);
                    if (placed) {
                        placeCooldown = cdata.humanizedDelay ? (cdata.placeCooldownTicks + 1 + (int)(Math.random() * cdata.placeCooldownVariance)) : cdata.placeCooldownTicks;
                    } else {
                        placeCooldown = 5;
                    }
                }
                pendingTorchPos = null;
                pendingTorchBasePos = null;
                pendingTorchDir = null;
            }
        }

        if (client.player == null || client.level == null) return;
        if (!cdata.enabled) return;
        
        if (cdata.pauseOnSneak && client.player.isCrouching()) return;
        if (cdata.pauseOnSprint && client.player.isSprinting()) return;
        if (cdata.pauseInCombat) {
            ItemStack mainHandStack = client.player.getMainHandItem();
            Item mainItem = mainHandStack.getItem();
            String itemName = BuiltInRegistries.ITEM.getKey(mainItem).getPath().toLowerCase();
            
            if (itemName.contains("sword") || 
                itemName.contains("axe") || 
                itemName.contains("bow") || 
                itemName.contains("trident") ||
                mainHandStack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "swords"))) ||
                mainHandStack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "axes"))) ||
                mainHandStack.typeHolder().is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "bows")))) {
                return;
            }
        }

        if (placeCooldown > 0 || pendingTorchPos != null || pendingSwapWaitDelay > 0) return;
        if (client.level.getGameTime() % cdata.scanDelayTicks != 0) return;

        BlockPos playerPos = client.player.blockPosition();

        if (client.level.getBrightness(LightLayer.BLOCK, playerPos) < cdata.lightLevel) {
            BlockPos placementPos = playerPos;
            Direction bestDir = null;
            if (cdata.preferWallPlacement) {
                BlockPos headPos = playerPos.above();
                if (!zoneManager.isInExcludedZone(headPos) && client.level.getBlockState(headPos).canBeReplaced() && client.level.getBlockState(headPos).getFluidState().isEmpty() && RaycastUtils.hasLineOfSight(client, headPos)) {
                    bestDir = getWallDirectionOnly(client, headPos, cdata);
                    if (bestDir != null) placementPos = headPos;
                }
                if (bestDir == null && !zoneManager.isInExcludedZone(playerPos) && client.level.getBlockState(playerPos).getFluidState().isEmpty()) {
                    bestDir = getWallDirectionOnly(client, playerPos, cdata);
                    if (bestDir != null) placementPos = playerPos;
                }
            }
            if (bestDir == null && !zoneManager.isInExcludedZone(playerPos) && client.level.getBlockState(playerPos).getFluidState().isEmpty()) {
                bestDir = getFloorDirectionOnly(client, playerPos, cdata);
                placementPos = playerPos;
            }
            
            if (bestDir != null) {
                if (!cdata.smartSpacing || !hasTorchNearby(client, placementPos, cdata.smartSpacingRadius)) {
                    queueTorchPlacement(playerPos, placementPos, bestDir, cdata);
                    return;
                }
            }
        }

        if (cdata.placementRadius > 0) {
            for (int r = 1; r <= cdata.placementRadius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int y = -cdata.verticalPlacementRadius; y <= cdata.verticalPlacementRadius; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (Math.abs(x) == r || Math.abs(z) == r) {
                                BlockPos checkPos = playerPos.offset(x, y, z);
                                
                                if (client.level.getBrightness(LightLayer.BLOCK, checkPos) < cdata.lightLevel) {
                                    if (client.level.getBlockState(checkPos).canBeReplaced() && RaycastUtils.hasLineOfSight(client, checkPos)) {
                                        
                                        BlockPos placementPos = checkPos;
                                        Direction bestDir = null;
                                        
                                        if (cdata.preferWallPlacement) {
                                            BlockPos headPos = checkPos.above();
                                            
                                            if (!zoneManager.isInExcludedZone(headPos) && client.level.getBlockState(headPos).canBeReplaced() && client.level.getBlockState(headPos).getFluidState().isEmpty() && RaycastUtils.hasLineOfSight(client, headPos)) {
                                                bestDir = getWallDirectionOnly(client, headPos, cdata);
                                                if (bestDir != null) {
                                                    placementPos = headPos;
                                                }
                                            }
                                            
                                            if (bestDir == null && !zoneManager.isInExcludedZone(checkPos) && client.level.getBlockState(checkPos).getFluidState().isEmpty()) {
                                                bestDir = getWallDirectionOnly(client, checkPos, cdata);
                                                if (bestDir != null) {
                                                    placementPos = checkPos;
                                                }
                                            }
                                        }
                                        
                                        if (bestDir == null && !zoneManager.isInExcludedZone(checkPos) && client.level.getBlockState(checkPos).getFluidState().isEmpty()) {
                                            bestDir = getFloorDirectionOnly(client, checkPos, cdata);
                                            placementPos = checkPos;
                                        }
                                        
                                        if (bestDir != null) {
                                            if (cdata.smartSpacing && hasTorchNearby(client, placementPos, cdata.smartSpacingRadius)) {
                                                continue;
                                            }
                                            if (cdata.requireLineOfSightAngle) {
                                                if (RaycastUtils.isLookingAt(client, placementPos, cdata)) {
                                                    queueTorchPlacement(checkPos, placementPos, bestDir, cdata);
                                                    return;
                                                }
                                            } else {
                                                queueTorchPlacement(checkPos, placementPos, bestDir, cdata);
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
    }

    private Direction getWallDirectionOnly(Minecraft client, BlockPos pos, ModConfig cdata) {
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookVec = client.player.getViewVector(1.0F).normalize();
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        
        Direction bestDir = null;
        double bestDot = -999.0;
        
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = pos.relative(dir);
            Block wallBlock = client.level.getBlockState(wallPos).getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(wallBlock).toString();
            boolean isListed = cdata.blacklistedBlocks.contains(blockId);
            boolean allowed = cdata.blockListIsWhitelist ? isListed : !isListed;
            
            if (allowed && Block.canSupportCenter(client.level, wallPos, dir.getOpposite())) {
                Vec3 wallDir = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                double rightDot = rightVec.dot(wallDir);
                
                Vec3 toTarget = Vec3.atCenterOf(wallPos).subtract(eyePos).normalize();
                double forwardDot = lookVec.dot(toTarget);
                
                double score = (rightDot * 3.0) + forwardDot;
                
                if (score > bestDot) {
                    bestDot = score;
                    bestDir = dir;
                }
            }
        }
        return bestDir;
    }

    private Direction getFloorDirectionOnly(Minecraft client, BlockPos pos, ModConfig cdata) {
        BlockPos supportPos = pos.below();
        Block supportBlock = client.level.getBlockState(supportPos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(supportBlock).toString();
        boolean isListed = cdata.blacklistedBlocks.contains(blockId);
        boolean allowed = cdata.blockListIsWhitelist ? isListed : !isListed;
        
        if (allowed && Block.canSupportCenter(client.level, supportPos, Direction.UP)) {
            return Direction.DOWN;
        }
        return null;
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

        if (cdata.preferWallPlacement) {
            Direction wallDir = getWallDirectionOnly(client, pos, cdata);
            if (wallDir != null) return wallDir;
        }
        return getFloorDirectionOnly(client, pos, cdata);
    }

    private void queueTorchPlacement(BlockPos basePos, BlockPos placementPos, Direction dir, ModConfig cdata) {
        this.pendingTorchBasePos = basePos;
        this.pendingTorchPos = placementPos;
        this.pendingTorchDir = dir;
        this.pendingTorchDelay = cdata.lightUpdateDelayTicks; 
    }

    private boolean hasTorchNearby(Minecraft client, BlockPos pos, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (client.level.getBlockState(pos.offset(x, y, z)).getBlock() instanceof TorchBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        
        BlockPos targetBlock = pos.relative(dir);
        Direction hitFace = dir.getOpposite();

        Vec3 start = client.player.getEyePosition();
        Vec3 hitPos = Vec3.atCenterOf(targetBlock).add(Vec3.atLowerCornerOf(new net.minecraft.core.Vec3i(hitFace.getStepX(), hitFace.getStepY(), hitFace.getStepZ())).scale(0.5));

        // REACH CHECK FIRST
        float reach = client.player.isCreative() ? 5.0f : 4.5f;
        if (hitPos.distanceToSqr(start) > (reach * reach)) {
            return false;
        }

        boolean didSwap = false;
        if (inventoryTorchSlot != -1) {

            
            // Manually read the UI slots BEFORE the swap to get the correct state
            net.minecraft.world.inventory.Slot sourceSlot = client.player.inventoryMenu.getSlot(inventoryTorchSlot);
            net.minecraft.world.inventory.Slot targetSlot = client.player.inventoryMenu.getSlot(36 + torchSlot);
            ItemStack sourceItem = sourceSlot.getItem().copy();
            ItemStack targetItem = targetSlot.getItem().copy();
            
            client.gameMode.handleContainerInput(0, inventoryTorchSlot, torchSlot, ContainerInput.SWAP, client.player);
            
            // Manually update the UI slots to ensure the open inventory reflects the swap immediately
            sourceSlot.set(targetItem);
            targetSlot.set(sourceItem);
            
            didSwap = true;

        }

        if (placingHand == InteractionHand.MAIN_HAND && currentSlot != torchSlot) {

            client.player.getInventory().setSelectedSlot(torchSlot);
            client.player.connection.send(new ServerboundSetCarriedItemPacket(torchSlot));
            didSwap = true;

        }

        if (didSwap && cdata.inventorySwapDelayTicks > 0) {

            this.swapWaitPos = pos;
            this.swapWaitDir = dir;
            this.swapWaitHand = placingHand;
            this.swapWaitCurrentSlot = currentSlot;
            this.swapWaitTorchSlot = torchSlot;
            this.swapWaitInventoryTorchSlot = inventoryTorchSlot;
            this.pendingSwapWaitDelay = cdata.inventorySwapDelayTicks;
            return true;
        }

        return executeTorchPlacement(client, cdata, pos, dir, placingHand, currentSlot, torchSlot, inventoryTorchSlot);
    }

    private boolean executeTorchPlacement(Minecraft client, ModConfig cdata, BlockPos pos, Direction dir, InteractionHand placingHand, int currentSlot, int torchSlot, int inventoryTorchSlot) {
        BlockPos targetBlock = pos.relative(dir);
        Direction hitFace = dir.getOpposite();

        Vec3 start = client.player.getEyePosition();
        Vec3 hitPos = Vec3.atCenterOf(targetBlock).add(Vec3.atLowerCornerOf(new net.minecraft.core.Vec3i(hitFace.getStepX(), hitFace.getStepY(), hitFace.getStepZ())).scale(0.5));

        if (cdata.advancedPacketSpoofing) {
            double dx = hitPos.x - start.x;
            double dy = hitPos.y - start.y;
            double dz = hitPos.z - start.z;
            double diffXZ = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            float pitch = (float) -(Math.atan2(dy, diffXZ) * (180.0 / Math.PI));
            client.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, client.player.onGround(), false));
        } else if (cdata.accuratePlacement) {
            client.player.connection.send(new ServerboundMovePlayerPacket.Rot(client.player.getYRot(), 90.0F, client.player.onGround(), false));
        }
        

        InteractionResult result = client.gameMode.useItemOn(client.player, placingHand, new BlockHitResult(hitPos, hitFace, targetBlock, false));
                
        if (result == InteractionResult.FAIL || result == InteractionResult.PASS) {
            if (placingHand == InteractionHand.MAIN_HAND && currentSlot != torchSlot) {
                client.player.getInventory().setSelectedSlot(currentSlot);
                client.player.connection.send(new ServerboundSetCarriedItemPacket(currentSlot));
                
                if (inventoryTorchSlot != -1) {
                    net.minecraft.world.inventory.Slot sourceSlot = client.player.inventoryMenu.getSlot(inventoryTorchSlot);
                    net.minecraft.world.inventory.Slot targetSlot = client.player.inventoryMenu.getSlot(36 + torchSlot);
                    ItemStack sourceItem = sourceSlot.getItem().copy();
                    ItemStack targetItem = targetSlot.getItem().copy();
                    
                    client.gameMode.handleContainerInput(0, inventoryTorchSlot, torchSlot, ContainerInput.SWAP, client.player);
                    
                    sourceSlot.set(targetItem);
                    targetSlot.set(sourceItem);
                }
            }
            return false;
        }

        if (cdata.swingHand) {
            client.player.swing(placingHand);
        }

        if (placingHand == InteractionHand.MAIN_HAND) {
            if (currentSlot != torchSlot || inventoryTorchSlot != -1) {
                this.revertSlotIndex = currentSlot;
                this.revertSlotDelay = cdata.humanizedDelay ? (cdata.slotRevertDelayTicks + (int)(Math.random() * cdata.slotRevertVariance)) : cdata.slotRevertDelayTicks;
                
                if (inventoryTorchSlot != -1) {
                    this.revertInventorySwapSource = inventoryTorchSlot;
                    this.revertInventorySwapTarget = torchSlot;
                }
            }
        }
        return true;
    }
}