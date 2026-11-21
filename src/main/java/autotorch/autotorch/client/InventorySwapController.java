package autotorch.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages hotbar selection, server-authoritative ContainerInput.SWAP
 * transactions, and atomic slot reversion without phantom client mutations.
 */
public class InventorySwapController {
    private static final Logger LOGGER = LoggerFactory.getLogger("autotorch");
    private static final int HOTBAR_MENU_OFFSET = 36;

    public enum PostSwapStatus {
        SYNCED,
        REJECTED_NO_OP,
        PARTIAL_DESYNC
    }

    private final TorchInventoryClassifier classifier = new TorchInventoryClassifier();

    private int revertSlotDelay = 0;
    private int originalSelectedSlot = -1;
    private int expectedSelectedSlot = -1;
    private int revertInventorySwapSource = -1;
    private int revertInventorySwapTarget = -1;
    private ItemStack expectedDisplacedStack = ItemStack.EMPTY;

    public static PostSwapStatus classifyPostSwapState(
            boolean sourceMatchesBeforeTarget,
            boolean targetMatchesBeforeSource,
            boolean sourceMatchesBeforeSource,
            boolean targetMatchesBeforeTarget
    ) {
        if (sourceMatchesBeforeTarget && targetMatchesBeforeSource) {
            return PostSwapStatus.SYNCED;
        }
        if (sourceMatchesBeforeSource && targetMatchesBeforeTarget) {
            return PostSwapStatus.REJECTED_NO_OP;
        }
        return PostSwapStatus.PARTIAL_DESYNC;
    }

    public static boolean isPostSwapApproved(PostSwapStatus status) {
        return status == PostSwapStatus.SYNCED;
    }

    public static boolean canSafelyRevertSwapSlots(boolean hotbarEmptyOrTorch, boolean inventoryMatchesDisplaced) {
        return hotbarEmptyOrTorch && inventoryMatchesDisplaced;
    }

    public boolean isHoldingWeapon(LocalPlayer player) {
        return classifier.isHoldingWeapon(player);
    }

    public boolean isTorch(ItemStack stack) {
        return classifier.isTorch(stack);
    }

    public TorchInventoryClassifier.TorchSource findTorchSource(LocalPlayer player, ModConfig cdata) {
        return classifier.findTorchSource(player, cdata);
    }

    public boolean canSafelyManipulateInventory(Minecraft client) {
        LocalPlayer player = client.player;
        return player != null
                && client.gameMode != null
                && player.isAlive()
                && client.gui != null
                && client.gui.screen() == null
                && player.containerMenu == player.inventoryMenu
                && player.containerMenu.containerId == 0;
    }

    public boolean selectHotbarSlot(Minecraft client, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= TorchInventoryClassifier.HOTBAR_SIZE
                || client.player == null || client.player.connection == null) {
            return false;
        }
        client.player.getInventory().setSelectedSlot(slotIndex);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slotIndex));
        return true;
    }

    public boolean swapInventoryWithHotbar(Minecraft client, int inventorySlot, int hotbarSlot) {
        return executeContainerSwap(client, inventorySlot, hotbarSlot, true);
    }

    private boolean executeContainerSwap(Minecraft client, int inventorySlot, int hotbarSlot, boolean recordDisplaced) {
        if (!canSafelyManipulateInventory(client)) {
            return false;
        }
        if (inventorySlot < TorchInventoryClassifier.MAIN_INVENTORY_START
                || inventorySlot >= TorchInventoryClassifier.MAIN_INVENTORY_END
                || hotbarSlot < 0 || hotbarSlot >= TorchInventoryClassifier.HOTBAR_SIZE) {
            return false;
        }

        LocalPlayer player = client.player;
        Slot sourceSlot = player.inventoryMenu.getSlot(inventorySlot);
        Slot targetSlot = player.inventoryMenu.getSlot(HOTBAR_MENU_OFFSET + hotbarSlot);

        ItemStack beforeSource = sourceSlot.getItem().copy();
        ItemStack beforeTarget = targetSlot.getItem().copy();
        if (beforeSource.isEmpty() && beforeTarget.isEmpty()) {
            return false;
        }
        if (!beforeSource.isEmpty() && (!sourceSlot.mayPickup(player)
                || !targetSlot.mayPlace(beforeSource)
                || beforeSource.getCount() > targetSlot.getMaxStackSize(beforeSource))) {
            return false;
        }
        if (!beforeTarget.isEmpty() && (!targetSlot.mayPickup(player)
                || !sourceSlot.mayPlace(beforeTarget)
                || beforeTarget.getCount() > sourceSlot.getMaxStackSize(beforeTarget))) {
            return false;
        }

        client.gameMode.handleContainerInput(0, inventorySlot, hotbarSlot, ContainerInput.SWAP, player);

        ItemStack afterSource = sourceSlot.getItem();
        ItemStack afterTarget = targetSlot.getItem();
        PostSwapStatus status = classifyPostSwapState(
                ItemStack.matches(afterSource, beforeTarget),
                ItemStack.matches(afterTarget, beforeSource),
                ItemStack.matches(afterSource, beforeSource),
                ItemStack.matches(afterTarget, beforeTarget)
        );

        sourceSlot.setChanged();
        targetSlot.setChanged();

        if (!isPostSwapApproved(status)) {
            // Avoid overwriting client slots on PARTIAL_DESYNC so server-authoritative updates are never clobbered.
            LOGGER.warn("Aborted SWAP state ({}) for slots {} <-> {}; deferring to server state", status, inventorySlot, hotbarSlot);
            reset();
            return false;
        }

        if (recordDisplaced) {
            this.expectedDisplacedStack = beforeTarget.copy();
        }
        return true;
    }

    public void scheduleRevert(int delayTicks, int originalSlot, int activeSlot, int inventoryTorchSlot) {
        if (originalSlot == activeSlot && inventoryTorchSlot == -1) {
            return;
        }
        this.revertSlotDelay = Math.max(1, delayTicks);
        this.originalSelectedSlot = originalSlot != activeSlot ? originalSlot : -1;
        this.expectedSelectedSlot = originalSlot != activeSlot ? activeSlot : -1;
        this.revertInventorySwapSource = inventoryTorchSlot != -1 ? inventoryTorchSlot : -1;
        this.revertInventorySwapTarget = inventoryTorchSlot != -1 ? activeSlot : -1;
        if (inventoryTorchSlot == -1) {
            this.expectedDisplacedStack = ItemStack.EMPTY;
        }
    }

    public boolean advanceRevertTimer(boolean canManipulateInventory, int currentSelectedSlot) {
        if (revertSlotDelay <= 0) {
            return false;
        }
        if (!canManipulateInventory) {
            reset();
            return false;
        }
        if (expectedSelectedSlot != -1 && currentSelectedSlot != expectedSelectedSlot) {
            originalSelectedSlot = -1;
            expectedSelectedSlot = -1;
        }
        revertSlotDelay--;
        return revertSlotDelay == 0;
    }

    public void revertNow(Minecraft client, InteractionHand hand, int originalSlot, int activeSlot, int inventoryTorchSlot) {
        if (hand != InteractionHand.MAIN_HAND || !canSafelyManipulateInventory(client)) {
            reset();
            return;
        }
        LocalPlayer player = client.player;
        if (originalSlot != activeSlot && player.getInventory().getSelectedSlot() == activeSlot) {
            selectHotbarSlot(client, originalSlot);
        }
        if (inventoryTorchSlot != -1) {
            if (isSwapRevertSafe(player, inventoryTorchSlot, activeSlot)) {
                executeContainerSwap(client, inventoryTorchSlot, activeSlot, false);
            } else {
                LOGGER.warn("Skipped immediate SWAP revert due to unexpected slot contents ({} <-> {})", inventoryTorchSlot, activeSlot);
            }
        }
        reset();
    }

    public void tickRevert(Minecraft client) {
        boolean safe = canSafelyManipulateInventory(client);
        int selected = client.player != null ? client.player.getInventory().getSelectedSlot() : -1;
        if (!advanceRevertTimer(safe, selected)) {
            return;
        }

        LocalPlayer player = client.player;
        if (originalSelectedSlot != -1 && selected == expectedSelectedSlot) {
            selectHotbarSlot(client, originalSelectedSlot);
        }
        if (revertInventorySwapSource != -1 && revertInventorySwapTarget != -1) {
            if (isSwapRevertSafe(player, revertInventorySwapSource, revertInventorySwapTarget)) {
                executeContainerSwap(client, revertInventorySwapSource, revertInventorySwapTarget, false);
            } else {
                LOGGER.warn("Skipped delayed SWAP revert due to slot modification ({} <-> {})", revertInventorySwapSource, revertInventorySwapTarget);
            }
        }
        reset();
    }

    public boolean hasPendingRevert() { return revertSlotDelay > 0; }
    int getRevertSlotDelay() { return revertSlotDelay; }
    int getOriginalSelectedSlot() { return originalSelectedSlot; }
    int getExpectedSelectedSlot() { return expectedSelectedSlot; }
    int getRevertInventorySwapSource() { return revertInventorySwapSource; }
    int getRevertInventorySwapTarget() { return revertInventorySwapTarget; }

    public void reset() {
        revertSlotDelay = 0;
        originalSelectedSlot = -1;
        expectedSelectedSlot = -1;
        revertInventorySwapSource = -1;
        revertInventorySwapTarget = -1;
        expectedDisplacedStack = ItemStack.EMPTY;
    }

    private boolean isSwapRevertSafe(LocalPlayer player, int inventorySlot, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= TorchInventoryClassifier.HOTBAR_SIZE
                || inventorySlot < TorchInventoryClassifier.MAIN_INVENTORY_START
                || inventorySlot >= TorchInventoryClassifier.MAIN_INVENTORY_END) {
            return false;
        }
        ItemStack hotbarStack = player.getInventory().getItem(hotbarSlot);
        ItemStack invStack = player.getInventory().getItem(inventorySlot);
        boolean hotbarOk = hotbarStack.isEmpty() || isTorch(hotbarStack);
        boolean displacedOk = (invStack.isEmpty() && expectedDisplacedStack.isEmpty())
                || ItemStack.matches(invStack, expectedDisplacedStack);
        return canSafelyRevertSwapSlots(hotbarOk, displacedOk);
    }
}
