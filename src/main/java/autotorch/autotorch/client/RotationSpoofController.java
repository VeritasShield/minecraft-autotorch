package autotorch.autotorch.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinates single-movement-packet-per-tick rotation spoofing inside
 * LocalPlayer.sendChanges() so vanilla sendPosition() emits the spoofed angles
 * without adding extra movement packets per tick.
 */
public class RotationSpoofController {
    private static final float ROTATION_EPSILON_DEGREES = 1.5F;
    private static final float MANUAL_ROTATION_EPSILON = 1.0E-3F;

    public record RotationAngles(float yaw, float pitch) {}

    private long lastSpoofRotGameTime = Long.MIN_VALUE;
    private boolean spoofActiveInCurrentSync = false;
    private boolean pendingRotationRestore = false;
    private boolean lastRestoreSkippedManual = false;
    private float preSpoofYRot = 0.0F;
    private float preSpoofXRot = 0.0F;

    public static float wrapYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    public static float clampPitch(float pitch) {
        return Mth.clamp(pitch, -90.0F, 90.0F);
    }

    public static RotationAngles computeLookAngles(Vec3 eyePos, Vec3 hitPos) {
        double dx = hitPos.x - eyePos.x;
        double dy = hitPos.y - eyePos.y;
        double dz = hitPos.z - eyePos.z;
        double diffXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = wrapYaw((float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = clampPitch((float) -(Math.atan2(dy, diffXZ) * (180.0 / Math.PI)));
        return new RotationAngles(yaw, pitch);
    }

    public static RotationAngles computeTargetAngles(
            Vec3 eyePos,
            Vec3 hitPos,
            float currentYaw,
            boolean advancedSpoofing,
            boolean accuratePlacement
    ) {
        if (advancedSpoofing) {
            return computeLookAngles(eyePos, hitPos);
        }
        if (accuratePlacement) {
            return new RotationAngles(wrapYaw(currentYaw), 90.0F);
        }
        return null;
    }

    public static boolean isRotationDeltaSignificant(float currentYaw, float currentPitch, RotationAngles target) {
        return Math.abs(Mth.degreesDifference(currentYaw, target.yaw())) > ROTATION_EPSILON_DEGREES
                || Math.abs(currentPitch - target.pitch()) > ROTATION_EPSILON_DEGREES;
    }

    public RotationAngles processNextTickRestore(float currentYaw, float currentPitch, long currentGameTime) {
        return processNextTickRestore(currentYaw, currentPitch, currentGameTime, false);
    }

    public RotationAngles processNextTickRestore(
            float currentYaw,
            float currentPitch,
            long currentGameTime,
            boolean isPassenger
    ) {
        if (isPassenger) {
            pendingRotationRestore = false;
            spoofActiveInCurrentSync = false;
            lastRestoreSkippedManual = false;
            return null;
        }
        if (!pendingRotationRestore || currentGameTime == lastSpoofRotGameTime) {
            return null;
        }
        pendingRotationRestore = false;

        boolean manuallyRotated = Math.abs(Mth.degreesDifference(currentYaw, preSpoofYRot)) > MANUAL_ROTATION_EPSILON
                || Math.abs(currentPitch - preSpoofXRot) > MANUAL_ROTATION_EPSILON;
        if (manuallyRotated) {
            lastRestoreSkippedManual = true;
            return null;
        }

        lastRestoreSkippedManual = false;
        lastSpoofRotGameTime = currentGameTime;
        return new RotationAngles(wrapYaw(preSpoofYRot), clampPitch(preSpoofXRot));
    }

    public boolean canSpoofOnTick(long currentGameTime) {
        return canSpoofOnTick(currentGameTime, false);
    }

    public boolean canSpoofOnTick(long currentGameTime, boolean isPassenger) {
        return !isPassenger
                && currentGameTime != lastSpoofRotGameTime
                && !spoofActiveInCurrentSync
                && !pendingRotationRestore;
    }

    public RotationAngles beginTickSpoof(
            float currentYaw,
            float currentPitch,
            RotationAngles target,
            long currentGameTime
    ) {
        return beginTickSpoof(currentYaw, currentPitch, target, currentGameTime, false);
    }

    public RotationAngles beginTickSpoof(
            float currentYaw,
            float currentPitch,
            RotationAngles target,
            long currentGameTime,
            boolean isPassenger
    ) {
        if (!canSpoofOnTick(currentGameTime, isPassenger) || target == null) {
            return null;
        }
        RotationAngles normalized = new RotationAngles(wrapYaw(target.yaw()), clampPitch(target.pitch()));
        preSpoofYRot = currentYaw;
        preSpoofXRot = currentPitch;
        lastRestoreSkippedManual = false;

        if (!isRotationDeltaSignificant(currentYaw, currentPitch, normalized)) {
            return new RotationAngles(wrapYaw(currentYaw), clampPitch(currentPitch));
        }

        spoofActiveInCurrentSync = true;
        lastSpoofRotGameTime = currentGameTime;
        return normalized;
    }

    public RotationAngles finishTickSpoofAfterSendPosition() {
        if (!spoofActiveInCurrentSync) {
            return null;
        }
        spoofActiveInCurrentSync = false;
        pendingRotationRestore = true;
        return new RotationAngles(preSpoofYRot, preSpoofXRot);
    }

    public RotationAngles forceRestoreIfActive() {
        if (!spoofActiveInCurrentSync) {
            return null;
        }
        spoofActiveInCurrentSync = false;
        pendingRotationRestore = false;
        return new RotationAngles(preSpoofYRot, preSpoofXRot);
    }

    public boolean isSpoofActiveInCurrentSync() {
        return spoofActiveInCurrentSync;
    }

    public boolean hasPendingRestore() {
        return pendingRotationRestore;
    }

    public boolean wasLastRestoreSkippedManual() {
        return lastRestoreSkippedManual;
    }

    public long getLastSpoofRotGameTime() {
        return lastSpoofRotGameTime;
    }

    public void reset() {
        spoofActiveInCurrentSync = false;
        pendingRotationRestore = false;
        lastRestoreSkippedManual = false;
        lastSpoofRotGameTime = Long.MIN_VALUE;
    }
}
