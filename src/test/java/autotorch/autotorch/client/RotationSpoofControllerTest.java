package autotorch.autotorch.client;

import autotorch.autotorch.client.mixin.LocalPlayerMixin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationSpoofControllerTest {
    private static final float EPS = 1.0E-3F;

    @Test
    void wrapsYawAndClampsPitchBounds() {
        assertEquals(-170.0F, RotationSpoofController.wrapYaw(190.0F), EPS);
        assertEquals(90.0F, RotationSpoofController.clampPitch(120.0F), EPS);
        assertEquals(-90.0F, RotationSpoofController.clampPitch(-110.0F), EPS);

        RotationSpoofController.RotationAngles accurate = RotationSpoofController.computeTargetAngles(
                new Vec3(0.5, 65.62, 0.5),
                new Vec3(0.5, 64.0, 2.5),
                545.0F,
                false,
                true
        );
        assertNotNull(accurate);
        assertEquals(-175.0F, accurate.yaw(), EPS);
        assertEquals(90.0F, accurate.pitch(), EPS);
    }

    @Test
    void enforcesSinglePacketPerTickAndRestoresNextTick() {
        RotationSpoofController controller = new RotationSpoofController();
        RotationSpoofController.RotationAngles target = new RotationSpoofController.RotationAngles(90.0F, 45.0F);

        RotationSpoofController.RotationAngles spoof = controller.beginTickSpoof(0.0F, 10.0F, target, 100L);
        assertNotNull(spoof);
        assertEquals(90.0F, spoof.yaw(), EPS);
        assertEquals(45.0F, spoof.pitch(), EPS);
        assertTrue(controller.isSpoofActiveInCurrentSync());
        assertNull(controller.beginTickSpoof(0.0F, 10.0F, target, 100L));

        RotationSpoofController.RotationAngles immediateRestore = controller.finishTickSpoofAfterSendPosition();
        assertNotNull(immediateRestore);
        assertEquals(0.0F, immediateRestore.yaw(), EPS);
        assertEquals(10.0F, immediateRestore.pitch(), EPS);
        assertFalse(controller.isSpoofActiveInCurrentSync());
        assertTrue(controller.hasPendingRestore());

        assertFalse(controller.canSpoofOnTick(101L));
        RotationSpoofController.RotationAngles nextTickRestore = controller.processNextTickRestore(0.0F, 10.0F, 101L);
        assertNotNull(nextTickRestore);
        assertEquals(0.0F, nextTickRestore.yaw(), EPS);
        assertEquals(10.0F, nextTickRestore.pitch(), EPS);
        assertFalse(controller.hasPendingRestore());
        assertFalse(controller.wasLastRestoreSkippedManual());

        assertFalse(controller.canSpoofOnTick(101L));
        assertTrue(controller.canSpoofOnTick(102L));
    }

    @Test
    void skipsNextTickRestoreWhenPlayerRotatesManually() {
        RotationSpoofController controller = new RotationSpoofController();
        RotationSpoofController.RotationAngles target = new RotationSpoofController.RotationAngles(120.0F, 50.0F);

        assertNotNull(controller.beginTickSpoof(10.0F, 5.0F, target, 200L));
        assertNotNull(controller.finishTickSpoofAfterSendPosition());

        RotationSpoofController.RotationAngles restore = controller.processNextTickRestore(35.0F, 8.0F, 201L);
        assertNull(restore);
        assertTrue(controller.wasLastRestoreSkippedManual());
        assertFalse(controller.hasPendingRestore());
    }

    @Test
    void rejectsSpoofAndClearsPendingRestoreWhenPlayerIsPassenger() {
        RotationSpoofController controller = new RotationSpoofController();
        RotationSpoofController.RotationAngles target = new RotationSpoofController.RotationAngles(75.0F, 40.0F);

        assertFalse(controller.canSpoofOnTick(300L, true));
        assertNull(controller.beginTickSpoof(0.0F, 0.0F, target, 300L, true));

        assertNotNull(controller.beginTickSpoof(0.0F, 0.0F, target, 300L, false));
        assertNotNull(controller.finishTickSpoofAfterSendPosition());
        assertTrue(controller.hasPendingRestore());

        assertNull(controller.processNextTickRestore(0.0F, 0.0F, 301L, true));
        assertFalse(controller.hasPendingRestore());
        assertFalse(controller.isSpoofActiveInCurrentSync());
    }

    @Test
    void forceRestoreIfActiveRecoversCameraOnAbortedTick() {
        RotationSpoofController controller = new RotationSpoofController();
        RotationSpoofController.RotationAngles target = new RotationSpoofController.RotationAngles(140.0F, 65.0F);

        assertNull(controller.forceRestoreIfActive());
        assertNotNull(controller.beginTickSpoof(25.0F, -12.0F, target, 400L));
        assertTrue(controller.isSpoofActiveInCurrentSync());

        RotationSpoofController.RotationAngles recovered = controller.forceRestoreIfActive();
        assertNotNull(recovered);
        assertEquals(25.0F, recovered.yaw(), EPS);
        assertEquals(-12.0F, recovered.pitch(), EPS);
        assertFalse(controller.isSpoofActiveInCurrentSync());
        assertFalse(controller.hasPendingRestore());
    }

    @Test
    void resetClearsStateOnWorldChange() {
        RotationSpoofController controller = new RotationSpoofController();
        RotationSpoofController.RotationAngles target = new RotationSpoofController.RotationAngles(60.0F, 30.0F);

        assertNotNull(controller.beginTickSpoof(0.0F, 0.0F, target, 500L));
        assertNotNull(controller.finishTickSpoofAfterSendPosition());
        assertTrue(controller.hasPendingRestore());

        controller.reset();
        assertFalse(controller.hasPendingRestore());
        assertFalse(controller.isSpoofActiveInCurrentSync());
        assertEquals(Long.MIN_VALUE, controller.getLastSpoofRotGameTime());
        assertTrue(controller.canSpoofOnTick(0L));
    }

    @Test
    void mixinConfigAndTargetMethodAreValidForMc26_3() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/autotorch.mixins.json")) {
            assertNotNull(stream, "autotorch.mixins.json must be present on classpath");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.get("required").getAsBoolean());
            assertEquals("autotorch.autotorch.client.mixin", json.get("package").getAsString());
            assertEquals("JAVA_25", json.get("compatibilityLevel").getAsString());
            assertEquals("LocalPlayerMixin", json.getAsJsonArray("client").get(0).getAsString());
        }

        assertNotNull(MixinEnvironment.CompatibilityLevel.valueOf("JAVA_25"));
        assertEquals("autotorch.autotorch.client.mixin.LocalPlayerMixin", LocalPlayerMixin.class.getName());

        Method sendChanges = LocalPlayer.class.getDeclaredMethod("sendChanges");
        assertEquals(void.class, sendChanges.getReturnType());
        assertEquals(0, sendChanges.getParameterCount());
    }
}
