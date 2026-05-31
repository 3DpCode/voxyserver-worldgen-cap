package com.holzkommando.voxyserverworldgencap.mixin;

import com.dripps.voxyserver.server.LodStreamingService;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Suppresses the eager dirty-path broadcast ONLY for sections that are still
// in their initial-load phase (fresh WG generation or chunk-load re-ingest).
// Block edits — which go through DirtyTracker.markChunkPendingDirty +
// ChunkVoxelizer.revoxelizeChunk and never touch initialLoadSections — still
// get the immediate dirty push so long-range LOD updates remain instant.
//
// Also relaxes the 3-of-4-chunks-loaded gate so fresh-load dirty events flow
// through to processDirtySection's version bump (the scan path needs the
// bump to know to re-deliver).
@Mixin(value = LodStreamingService.class, remap = false)
public abstract class LodStreamingServiceMixin {

    @Shadow @Final private ConcurrentHashMap<Long, Long> initialLoadSections;

    // Set at HEAD of processDirtySection; read at HEAD of pushDirtySection.
    // The stream worker is a single-threaded executor so this is safe without
    // synchronization.
    @Unique private boolean voxyserverworldgencap$currentIsFreshLoad;

    @Inject(method = "isInitialLoadReady(JI)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void voxyserverworldgencap$relaxInitialLoadReady(long deadline, int loadedChunkCount, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "processDirtySection",
            at = @At("HEAD"),
            remap = false)
    private void voxyserverworldgencap$captureFreshness(MinecraftServer server, long compositeKey, CallbackInfo ci) {
        this.voxyserverworldgencap$currentIsFreshLoad = this.initialLoadSections.containsKey(compositeKey);
    }

    @Inject(method = "pushDirtySection",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void voxyserverworldgencap$skipFreshPush(MinecraftServer server, ServerLevel level, Identifier dimension, long sectionKey, int version, CallbackInfo ci) {
        if (this.voxyserverworldgencap$currentIsFreshLoad) {
            ci.cancel();
        }
    }
}
