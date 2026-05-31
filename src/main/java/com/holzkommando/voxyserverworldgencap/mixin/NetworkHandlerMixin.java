package com.holzkommando.voxyserverworldgencap.mixin;

import com.ethan.voxyworldgenv2.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Short-circuits Voxy WorldGen V2's two outbound LOD send paths. VoxyServer's
// own pipeline serves the same data via its compact paletted channel with
// per-player dedup; WG's redundant broadcast was the dominant bandwidth source.
@Mixin(value = NetworkHandler.class, remap = false)
public abstract class NetworkHandlerMixin {

    @Inject(method = "broadcastLODData",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void voxyserverworldgencap$skipBroadcast(LevelChunk chunk, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "sendLODData",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void voxyserverworldgencap$skipSend(ServerPlayer player, LevelChunk chunk, CallbackInfo ci) {
        ci.cancel();
    }
}
