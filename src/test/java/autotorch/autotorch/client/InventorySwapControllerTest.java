package autotorch.autotorch.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySwapControllerTest {

    @Test
    void classifiesAndApprovesOnlySyncedPostSwapState() {
        assertEquals(
                InventorySwapController.PostSwapStatus.SYNCED,
                InventorySwapController.classifyPostSwapState(true, true, false, false)
        );
        assertEquals(
                InventorySwapController.PostSwapStatus.REJECTED_NO_OP,
                InventorySwapController.classifyPostSwapState(false, false, true, true)
        );
        assertEquals(
                InventorySwapController.PostSwapStatus.PARTIAL_DESYNC,
                InventorySwapController.classifyPostSwapState(false, true, false, false)
        );
        assertEquals(
                InventorySwapController.PostSwapStatus.PARTIAL_DESYNC,
                InventorySwapController.classifyPostSwapState(true, false, false, false)
        );

        assertTrue(InventorySwapController.isPostSwapApproved(InventorySwapController.PostSwapStatus.SYNCED));
        assertFalse(InventorySwapController.isPostSwapApproved(InventorySwapController.PostSwapStatus.REJECTED_NO_OP));
        assertFalse(InventorySwapController.isPostSwapApproved(InventorySwapController.PostSwapStatus.PARTIAL_DESYNC));
    }

    @Test
    void normalRevertCountdownCompletesAtZero() {
        InventorySwapController controller = new InventorySwapController();
        controller.scheduleRevert(2, 0, 8, 12);

        assertTrue(controller.hasPendingRevert());
        assertFalse(controller.advanceRevertTimer(true, 8));
        assertEquals(1, controller.getRevertSlotDelay());
        assertEquals(0, controller.getOriginalSelectedSlot());

        assertTrue(controller.advanceRevertTimer(true, 8));
        assertEquals(0, controller.getRevertSlotDelay());
        assertEquals(12, controller.getRevertInventorySwapSource());
        assertEquals(8, controller.getRevertInventorySwapTarget());
    }

    @Test
    void manualHotbarSwitchCancelsSlotSnapBackWhileKeepingSwapRevert() {
        InventorySwapController controller = new InventorySwapController();
        controller.scheduleRevert(2, 1, 8, 15);

        // Player manually switches hotbar from slot 8 to slot 3 before revert fires.
        assertFalse(controller.advanceRevertTimer(true, 3));
        assertEquals(-1, controller.getOriginalSelectedSlot());
        assertEquals(-1, controller.getExpectedSelectedSlot());
        assertEquals(15, controller.getRevertInventorySwapSource());
        assertEquals(8, controller.getRevertInventorySwapTarget());

        assertTrue(controller.advanceRevertTimer(true, 3));
        assertEquals(-1, controller.getOriginalSelectedSlot());
    }

    @Test
    void unsafeEnvironmentOrResetClearsPendingRevertState() {
        InventorySwapController controller = new InventorySwapController();
        controller.scheduleRevert(3, 0, 8, 20);

        // Open screen, chest container, or player death aborts revert immediately.
        assertFalse(controller.advanceRevertTimer(false, 8));
        assertFalse(controller.hasPendingRevert());
        assertEquals(-1, controller.getRevertInventorySwapSource());
        assertEquals(-1, controller.getRevertInventorySwapTarget());

        controller.scheduleRevert(2, 1, 7, 18);
        controller.reset();
        assertFalse(controller.hasPendingRevert());
        assertEquals(0, controller.getRevertSlotDelay());
    }

    @Test
    void revertSafetyRequiresBothTorchOrEmptyHotbarAndMatchingDisplacedSlot() {
        assertTrue(InventorySwapController.canSafelyRevertSwapSlots(true, true));
        assertFalse(InventorySwapController.canSafelyRevertSwapSlots(false, true));
        assertFalse(InventorySwapController.canSafelyRevertSwapSlots(true, false));
        assertFalse(InventorySwapController.canSafelyRevertSwapSlots(false, false));
    }

    @Test
    void weaponClassifierIdentifiesVanillaAndModdedWeaponsSafely() {
        assertTrue(TorchInventoryClassifier.isWeaponItemPathOrTagged("diamond_sword", false));
        assertTrue(TorchInventoryClassifier.isWeaponItemPathOrTagged("netherite_axe", false));
        assertTrue(TorchInventoryClassifier.isWeaponItemPathOrTagged("crossbow", false));
        assertTrue(TorchInventoryClassifier.isWeaponItemPathOrTagged("trident", false));
        assertTrue(TorchInventoryClassifier.isWeaponItemPathOrTagged("modded_halberd", true));

        assertFalse(TorchInventoryClassifier.isWeaponItemPathOrTagged("iron_pickaxe", false));
        assertFalse(TorchInventoryClassifier.isWeaponItemPathOrTagged("bowl", false));
        assertFalse(TorchInventoryClassifier.isWeaponItemPathOrTagged("torch", false));
        assertFalse(TorchInventoryClassifier.isWeaponItemPathOrTagged("", false));
        assertFalse(TorchInventoryClassifier.isWeaponItemPathOrTagged(null, false));
    }
}
