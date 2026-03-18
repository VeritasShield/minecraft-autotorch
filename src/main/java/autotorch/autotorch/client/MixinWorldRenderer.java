package autotorch.autotorch.client;

import autotorch.autotorch.client.AutotorchClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(net.minecraft.client.render.RenderTickCounter tickCounter, boolean renderBlockOutline, net.minecraft.client.render.Camera camera, net.minecraft.client.render.GameRenderer gameRenderer, net.minecraft.client.render.LightmapTextureManager lightmapTextureManager, org.joml.Matrix4f positionMatrix, org.joml.Matrix4f projectionMatrix, CallbackInfo ci) {
        // Ya no renderizamos aquí. Las partículas ahora se generan de forma segura en el ClientTick
    }
}