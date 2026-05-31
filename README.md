# VoxyServer-WorldGen Cap

A **server-side-only** Fabric mod that lets you run a 128-chunk Voxy LOD horizon on a normal residential uplink instead of a datacenter pipe.

Clients do **not** need to install anything. Plain Voxy on the client side.

## Results

Same configs, same teleport, same two minutes of standing still after arriving.

| Without the mod (~25 Mbit) | With the mod (~3 Mbit) |
|---|---|
| ![before](assets/before-2min-25mbit.png) | ![after](assets/after-2min-3mbit.png) |

**Plain English:** stock Voxy sends LOD data the moment any chunk finishes generating, with no rate limit. That spikes your upload, scatters far-away terrain into view before nearby terrain is ready (red ellipse), and leaves holes where chunks raced ahead of the safety check (red X's). With the mod, the server delivers LODs at a steady budget per tick, walking outward from the player. Result is a clean, predictable fill at a fraction of the bandwidth.

**Technically:** WG's broadcast and VoxyServer's eager dirty-push are both event-driven (per-chunk-completion). The mod cancels them for fresh-load sections and leaves all delivery to VoxyServer's existing scan path, which is rate-bounded by `maxSectionsPerTickPerPlayer`.

## What it does

**Plain English:** stops the two mods from each sending the same LOD data over different channels at full speed. New terrain gets delivered slower but predictably; player-built things still appear instantly.

**Technically:**

- `NetworkHandlerMixin` cancels `voxy_worldgen_v2`'s `broadcastLODData` and `sendLODData`.
- `LodStreamingServiceMixin` cancels `VoxyServer.pushDirtySection` only when the section is in `initialLoadSections` (the fresh-load path). Block edits go through `markChunkPendingDirty` + `revoxelizeChunk`, which never touch that map — they keep the ~1 tick dirty push.
- Also relaxes the `isInitialLoadReady` 3-of-4-chunks gate so fresh dirty events flow to the version bump that the scan path needs.

## Dependencies

- [VoxyServer](https://modrinth.com/mod/voxyserver) (server-side)
- [Voxy World Gen V2](https://modrinth.com/mod/voxy-world-gen-v2) (server-side)
- [Voxy](https://modrinth.com/mod/voxy) (client-side renderer — already required by the others)

## Tested setup

Only the following combination has been tested:

| Mod | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 |
| Voxy | 0.2.15-beta+26.1 |
| VoxyServer | 1.1.6 |
| Voxy World Gen V2 | 26.1.2-2.2.4 |
| VoxyServer-WorldGen Cap | 1.0.0 |

Java 25.

## Minimal config

Reference values producing **~3 Mbit/player** at a **128-chunk LOD horizon**:

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

Scale `maxSectionsPerTickPerPlayer` and `sectionsPerPacket` up together to trade bandwidth for fill speed.

## Caveats

The fresh-vs-edit discriminator relies on `markChunkPendingInitialLoad` being the only path that populates `initialLoadSections`. If a future VoxyServer release changes which paths touch that map, the mod could mis-classify block edits or fresh loads. Re-verify if you upgrade VoxyServer past 1.1.6.

## License

MIT.
