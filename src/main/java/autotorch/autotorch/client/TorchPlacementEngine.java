package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Tick-driven coordinator for dark-spot torch placement synchronized with
 * LocalPlayer.sendChanges() for true single-movement-packet-per-tick cadence.
 */
public class TorchPlacementEngine {
    private static final int FAILED_PLACEMENT_COOLDOWN_TICKS = 5;

    private record ReadyPlacement(BlockPos pos, Direction dir, InteractionHand placingHand,
                                  int currentSlot, int torchSlot, int inventoryTorchSlot) {}

    private final TorchTargetScanner scanner = new TorchTargetScanner();
    private final InventorySwapController inventoryController = new InventorySwapController();
    private final RotationSpoofController rotationController = new RotationSpoofController();

    private ClientLevel lastLevel = null;
    private int placeCooldown = 0;
    private int pendingTorchDelay = 0;
    private TorchTargetScanner.PlacementCandidate pendingLightCandidate = null;

    private int pendingSwapWaitDelay = 0;
    private ReadyPlacement pendingSwapPlacement = null;
    private ReadyPlacement readyPlacement = null;

    public void syncLevelState(ClientLevel currentLevel, ZoneManager zoneManager) {
        if (currentLevel != lastLevel) {
            lastLevel = currentLevel;
            resetAllState();
            zoneManager.onWorldChanged();
        }
    }

    public void resetAllState() {
        placeCooldown = 0;
        pendingTorchDelay = 0;
        pendingLightCandidate = null;
        pendingSwapWaitDelay = 0;
        pendingSwapPlacement = null;
        readyPlacement = null;
        rotationController.reset();
        inventoryController.reset();
    }

    public void ensureCameraRestored(LocalPlayer player) {
        RotationSpoofController.RotationAngles stuck = rotationController.forceRestoreIfActive();
        if (stuck != null && player != null) {
            player.setYRot(stuck.yaw());
            player.setXRot(stuck.pitch());
        }
    }

    public void onBeforeSendChanges(Minecraft client, ModConfig cdata, ZoneManager zoneManager) {
        if (client.player == null || client.level == null) {
            return;
        }
        ensureCameraRestored(client.player);
        readyPlacement = null;

        long currentGameTime = client.level.getGameTime();
        rotationController.processNextTickRestore(
                client.player.getYRot(), client.player.getXRot(), currentGameTime, client.player.isPassenger()
        );

        if (placeCooldown > 0) {
            placeCooldown--;
        }
        inventoryController.tickRevert(client);

        if (pendingSwapWaitDelay > 0) {
            tickPendingSwapWait(client, cdata, currentGameTime);
        }
        if (pendingTorchDelay > 0) {
            tickPendingTorchDelay(client, cdata, currentGameTime);
        }

        if (!canScanForCandidate(client, cdata, currentGameTime)) {
            return;
        }

        TorchTargetScanner.PlacementCandidate candidate = scanner.findNextCandidate(client, cdata, zoneManager);
        if (candidate != null) {
            this.pendingLightCandidate = candidate;
            this.pendingTorchDelay = cdata.lightUpdateDelayTicks;
        }
    }

    public void onAfterSendChanges(Minecraft client, ModConfig cdata) {
        ReadyPlacement armed = this.readyPlacement;
        this.readyPlacement = null;

        try {
            if (client.player != null) {
                RotationSpoofController.RotationAngles restore = rotationController.finishTickSpoofAfterSendPosition();
                if (restore != null) {
                    client.player.setYRot(restore.yaw());
                    client.player.setXRot(restore.pitch());
                }
            } else {
                rotationController.forceRestoreIfActive();
            }

            if (armed == null) {
                return;
            }
            if (client.player == null || client.level == null || !inventoryController.canSafelyManipulateInventory(client)) {
                inventoryController.revertNow(client, armed.placingHand(), armed.currentSlot(), armed.torchSlot(), armed.inventoryTorchSlot());
                placeCooldown = FAILED_PLACEMENT_COOLDOWN_TICKS;
                return;
            }

            boolean placed = executeArmedPlacement(client, cdata, armed);
            placeCooldown = placed
                    ? computeHumanizedTicks(cdata.placeCooldownTicks, cdata.placeCooldownVariance, cdata.humanizedDelay)
                    : FAILED_PLACEMENT_COOLDOWN_TICKS;
        } finally {
            ensureCameraRestored(client.player);
        }
    }

    private boolean canScanForCandidate(Minecraft client, ModConfig cdata, long currentGameTime) {
        if (!cdata.enabled || !client.player.isAlive() || !inventoryController.canSafelyManipulateInventory(client)) {
            return false;
        }
        if (cdata.pauseOnSneak && client.player.isCrouching()) {
            return false;
        }
        if (cdata.pauseOnSprint && client.player.isSprinting()) {
            return false;
        }
        if (cdata.pauseInCombat && inventoryController.isHoldingWeapon(client.player)) {
            return false;
        }
        if (placeCooldown > 0 || readyPlacement != null || pendingLightCandidate != null
                || pendingSwapWaitDelay > 0 || rotationController.hasPendingRestore()
                || inventoryController.hasPendingRevert()) {
            return false;
        }
        return currentGameTime % cdata.scanDelayTicks == 0;
    }

    private void tickPendingSwapWait(Minecraft client, ModConfig cdata, long currentGameTime) {
        ReadyPlacement waiting = this.pendingSwapPlacement;
        if (!isPendingSwapValid(client, client.player, waiting)) {
            if (waiting != null && inventoryController.canSafelyManipulateInventory(client)) {
                inventoryController.revertNow(
                        client, waiting.placingHand(), waiting.currentSlot(), waiting.torchSlot(), waiting.inventoryTorchSlot()
                );
            } else {
                inventoryController.reset();
            }
            pendingSwapWaitDelay = 0;
            pendingSwapPlacement = null;
            placeCooldown = FAILED_PLACEMENT_COOLDOWN_TICKS;
            return;
        }

        pendingSwapWaitDelay--;
        if (pendingSwapWaitDelay == 0) {
            pendingSwapPlacement = null;
            if (!armPlacementForCurrentSync(client, cdata, currentGameTime, waiting)) {
                placeCooldown = FAILED_PLACEMENT_COOLDOWN_TICKS;
            }
        }
    }

    private boolean isPendingSwapValid(Minecraft client, LocalPlayer player, ReadyPlacement waiting) {
        if (waiting == null || !inventoryController.canSafelyManipulateInventory(client)) {
            return false;
        }
        if (waiting.placingHand() == InteractionHand.OFF_HAND) {
            return inventoryController.isTorch(player.getOffhandItem());
        }
        return player.getInventory().getSelectedSlot() == waiting.torchSlot()
                && inventoryController.isTorch(player.getInventory().getItem(waiting.torchSlot()));
    }

    private void tickPendingTorchDelay(Minecraft client, ModConfig cdata, long currentGameTime) {
        pendingTorchDelay--;
        if (pendingTorchDelay == 0 && pendingLightCandidate != null) {
            TorchTargetScanner.PlacementCandidate candidate = pendingLightCandidate;
            pendingLightCandidate = null;
            if (client.level.getBrightness(LightLayer.BLOCK, candidate.basePos()) < cdata.lightLevel) {
                boolean ok = prepareTorchPlacement(client, currentGameTime, candidate.placementPos(), candidate.supportDir(), cdata);
                if (!ok && pendingSwapWaitDelay == 0 && readyPlacement == null) {
                    placeCooldown = FAILED_PLACEMENT_COOLDOWN_TICKS;
                }
            }
        }
    }

    private boolean prepareTorchPlacement(Minecraft client, long currentGameTime, BlockPos pos, Direction dir, ModConfig cdata) {
        if (!inventoryController.canSafelyManipulateInventory(client)) {
            return false;
        }
        LocalPlayer player = client.player;
        TorchInventoryClassifier.TorchSource source = inventoryController.findTorchSource(player, cdata);
        if (source == null) {
            return false;
        }

        Vec3 hitPos = RaycastUtils.computeTargetHitPos(pos, dir);
        double reach = player.blockInteractionRange();
        if (hitPos.distanceToSqr(player.getEyePosition()) > reach * reach) {
            return false;
        }

        int currentSlot = player.getInventory().getSelectedSlot();
        boolean didSwap = false;
        if (source.inventorySlot() != -1) {
            if (!inventoryController.swapInventoryWithHotbar(client, source.inventorySlot(), source.hotbarSlot())) {
                return false;
            }
            didSwap = true;
        }
        if (source.hand() == InteractionHand.MAIN_HAND && currentSlot != source.hotbarSlot()) {
            inventoryController.selectHotbarSlot(client, source.hotbarSlot());
            didSwap = true;
        }

        ReadyPlacement pending = new ReadyPlacement(pos, dir, source.hand(), currentSlot, source.hotbarSlot(), source.inventorySlot());
        if (didSwap && cdata.inventorySwapDelayTicks > 0) {
            this.pendingSwapPlacement = pending;
            this.pendingSwapWaitDelay = cdata.inventorySwapDelayTicks;
            return true;
        }
        return armPlacementForCurrentSync(client, cdata, currentGameTime, pending);
    }

    private boolean armPlacementForCurrentSync(
            Minecraft client, ModConfig cdata, long currentGameTime, ReadyPlacement candidate
    ) {
        LocalPlayer player = client.player;
        if (cdata.advancedPacketSpoofing || cdata.accuratePlacement) {
            Vec3 hitPos = RaycastUtils.computeTargetHitPos(candidate.pos(), candidate.dir());
            RotationSpoofController.RotationAngles target = RotationSpoofController.computeTargetAngles(
                    player.getEyePosition(), hitPos, player.getYRot(),
                    cdata.advancedPacketSpoofing, cdata.accuratePlacement
            );
            RotationSpoofController.RotationAngles spoof = rotationController.beginTickSpoof(
                    player.getYRot(), player.getXRot(), target, currentGameTime, player.isPassenger()
            );
            if (spoof == null) {
                inventoryController.revertNow(
                        client, candidate.placingHand(), candidate.currentSlot(), candidate.torchSlot(), candidate.inventoryTorchSlot()
                );
                return false;
            }
            if (rotationController.isSpoofActiveInCurrentSync()) {
                player.setYRot(spoof.yaw());
                player.setXRot(spoof.pitch());
            }
        }
        this.readyPlacement = candidate;
        return true;
    }

    private boolean executeArmedPlacement(Minecraft client, ModConfig cdata, ReadyPlacement armed) {
        LocalPlayer player = client.player;
        BlockPos targetBlock = armed.pos().relative(armed.dir());
        Direction hitFace = armed.dir().getOpposite();
        Vec3 hitPos = RaycastUtils.computeTargetHitPos(armed.pos(), armed.dir());

        ItemStack activeStack = player.getItemInHand(armed.placingHand());
        SwingAnimation interactAnim = activeStack.isEmpty() ? SwingAnimation.DEFAULT : activeStack.getInteractAnimation();

        InteractionResult result = client.gameMode.useItemOn(
                player, armed.placingHand(), new BlockHitResult(hitPos, hitFace, targetBlock, false)
        );
        if (result == InteractionResult.FAIL || result == InteractionResult.PASS) {
            inventoryController.revertNow(
                    client, armed.placingHand(), armed.currentSlot(), armed.torchSlot(), armed.inventoryTorchSlot()
            );
            return false;
        }
        if (cdata.swingHand) {
            player.swing(armed.placingHand(), interactAnim, false);
        }
        if (armed.placingHand() == InteractionHand.MAIN_HAND
                && (armed.currentSlot() != armed.torchSlot() || armed.inventoryTorchSlot() != -1)) {
            int revertDelay = computeHumanizedTicks(cdata.slotRevertDelayTicks, cdata.slotRevertVariance, cdata.humanizedDelay);
            inventoryController.scheduleRevert(revertDelay, armed.currentSlot(), armed.torchSlot(), armed.inventoryTorchSlot());
        }
        return true;
    }

    private int computeHumanizedTicks(int baseTicks, int variance, boolean humanized) {
        return (!humanized || variance <= 0) ? baseTicks : baseTicks + ThreadLocalRandom.current().nextInt(variance + 1);
    }
}
