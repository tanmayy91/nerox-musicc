---
name: ExoPlayer buffer tuning
description: DefaultLoadControl settings chosen for faster song start without excessive rebuffering
---

## Settings applied (MusicService.kt `createExoPlayer()`)
```kotlin
DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        minBufferMs              = 15_000,  // was 50s default
        maxBufferMs              = 60_000,
        bufferForPlaybackMs      = 1_500,   // was 2500ms — starts playback faster
        bufferForPlaybackAfterRebufferMs = 3_000  // was 5000ms
    )
    .setPrioritizeTimeOverSizeThresholds(true)
```

**Why:** Default 2.5s bufferForPlaybackMs caused noticeable delay before audio starts. 1.5ms is aggressive enough to feel instant on Wi-Fi/4G while still safe on 3G (15s min buffer catches up quickly).

**How to apply:** If users report frequent rebuffering on poor connections, raise `bufferForPlaybackMs` to 2000ms and `bufferForPlaybackAfterRebufferMs` to 4000ms.
