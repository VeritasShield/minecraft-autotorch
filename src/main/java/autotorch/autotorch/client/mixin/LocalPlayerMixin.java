package autotorch.autotorch.client.mixin;

import autotorch.autotorch.client.AutotorchClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks LocalPlayer.sendChanges() so spoofed rotations share the single
 * vanilla movement packet of the tick instead of emitting extra packets.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void autotorch$onBeforeSendChanges(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (AutotorchClient.INSTANCE != null
                && player.connection != null
                && player.connection.hasClientLoaded()) {
            AutotorchClient.INSTANCE.onBeforePlayerSendChanges(player);
        }
    }

    @Inject(method = "sendChanges", at = @At("RETURN"))
    private void autotorch$onAfterSendChanges(CallbackInfo ci) {
        if (AutotorchClient.INSTANCE != null) {
            AutotorchClient.INSTANCE.onAfterPlayerSendChanges((LocalPlayer) (Object) this);
        }
    }
}
