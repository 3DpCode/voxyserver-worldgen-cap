# VoxyServer-WorldGen Cap

A small **server-side-only** Fabric mod that bounds the per-player uplink cost of running a 128-chunk Voxy LOD horizon on a dedicated Minecraft server.

Clients do **not** need this mod. There is no client component, no wire format change, and no payload that the vanilla [Voxy] client doesn't already understand.

## Results

Same `voxyserver.json` scan config (`maxSectionsPerTickPerPlayer=30`, `sectionsPerPacket=15`), same `voxyworldgenv2.json` (`maxActiveTasks=20`), same teleport target, same 2 minutes elapsed since teleport.

| Without the mod (~25 Mbit) | With the mod (~3 Mbit) |
|---|---|
| ![before](assets/before-2min-25mbit.png) | ![after](assets/after-2min-3mbit.png) |

- **Without:** ~25 Mbit even standing still. Loaded LODs arrive scattered — a few far-away fragments visible because WG's `DistanceGraph` finishes batches in roughly-circular distance order and broadcasts each chunk completion immediately, so distant chunks that happen to finish first appear before near chunks finish ingesting. Small holes near the player from the upstream `isInitialLoadReady` 3-of-4 ticket-footprint gate dropping sections that WG already released tickets on.
- **With:** ~3 Mbit, sustained low and even. The scan path walks the radius in concentric square rings outward from the player, so loaded LODs grow as a rectangle from the player position. No holes. The visible square edge is the current scan front; once it walks the full 128-chunk radius the horizon is complete.

The two delivery patterns are different by design: WG's broadcast is event-driven on per-chunk completion (circular fill order with bursty per-packet overhead), while the scan path is cursor-driven on per-tick budget (rectangular fill order with packed multi-section payloads).

## What it does

This mod patches the two other Voxy ecosystem mods so they stop saturating the uplink with redundant LOD broadcasts:

- **Voxy WorldGen V2** — cancels `broadcastLODData` and `sendLODData`. WG still pre-generates terrain; it just no longer pushes raw chunk bytes to every nearby player via its own channel.
- **VoxyServer** — cancels `pushDirtySection` _only for sections still in the initial-load phase_, i.e. fresh WG generations and chunk-load re-ingests. Those would otherwise spam size-1 `LODBulkPayload` packets at WG's full chunks-per-second rate. The section's version is still bumped, so VoxyServer's batched scan path (`streamForSnapshot`) delivers it through the compact paletted channel on its next pass.
- **Block edits are unaffected** — they go through `DirtyTracker.markChunkPendingDirty` + `ChunkVoxelizer.revoxelizeChunk`, neither of which touches `initialLoadSections`. The mod uses that as the discriminator inside `processDirtySection`, so player edits to existing terrain still get the immediate dirty push and propagate to long-range LOD viewers within ~1 tick.
- Also relaxes the `isInitialLoadReady` 3-of-4-chunks-loaded gate — WG releases vanilla chunk tickets aggressively and the gate fails for most freshly-generated sections, leaving pending markers to expire silently. With the relaxation, version bumps land immediately on dirty.

End result: the scan path is the bandwidth-bounded delivery mechanism for new terrain (capped at `maxSectionsPerTickPerPlayer × (20 / tickInterval)` sections per second per player), while block edits stay on the fast path.

## Dependencies

Hard runtime dependencies (declared in `fabric.mod.json`):

- `voxyserver` — [VoxyServer by dripps](https://modrinth.com/mod/voxyserver)
- `voxyworldgenv2` — [Voxy World Gen V2](https://modrinth.com/mod/voxy-world-gen-v2)

And implicitly the client-side renderer it's all built around:

- `voxy` — [Voxy by Cortex](https://modrinth.com/mod/voxy)

The mod will refuse to load if either of the two server-side dependencies is missing.

## Tested setup

Only the following combination has been tested end-to-end:

| Mod | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 |
| Voxy | 0.2.15-beta+26.1 |
| VoxyServer | 1.1.6 |
| Voxy World Gen V2 | 26.1.2-2.2.4 |
| VoxyServer-WorldGen Cap | 1.0.0 |

Server side is Fabric. Java 25.

## Minimal config

Reference settings producing **~3 Mbit/player** at a **128-chunk LOD horizon** on the tested combination:

`config/voxyserver.json`:
```jsonc
{
  "lodStreamRadius": 128,              // default: 256
  "maxSectionsPerTickPerPlayer": 30,   // default: 100
  "sectionsPerPacket": 15              // default: 50
}
```

`config/voxyworldgenv2.json`:
```jsonc
{
  "maxActiveTasks": 10                 // optional — default: 20
}
```

Halving `maxActiveTasks` to 10 keeps WG roughly in step with VoxyServer's scan delivery rate at the above scan settings; the default 20 produces excess work that piles up in the WorldEngine without being delivered any faster.

To trade bandwidth for fill speed, scale `maxSectionsPerTickPerPlayer` and `sectionsPerPacket` together — the scan rate scales roughly linearly with both, and so does the resulting bandwidth.

## Caveats

- There is no reconnect persistence. Every reconnect re-fills the radius via the scan path (~10-12 minutes at the example config). This is a known limitation of the upstream `PlayerLodTracker` (its dedup map is in-memory) and is out of scope here — the underlying protocol is server-push only and the client never tells the server what it already has on disk.
- The fresh-vs-edit discriminator relies on `markChunkPendingInitialLoad` being the only path that populates `initialLoadSections`. If a future VoxyServer release changes which paths touch that map, the mod could mis-classify a block edit as a fresh load (delayed LOD update) or vice versa (extra eager push). Re-verify if you upgrade VoxyServer past 1.1.6.

## License

MIT.
