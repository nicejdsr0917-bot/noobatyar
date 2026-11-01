package xyz.sattar.javid.proqueue.core.ui.components.map

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.time.Clock

/** The platform's cache folder (the OS may clear it when space runs low). */
internal expect fun platformCacheDir(): String

/**
 * Map tiles on the phone, already recoloured, so a neighbourhood seen once
 * opens at once next time and the map still shows offline.
 *
 * One file per tile, `z/x/y.t`: an 8-byte save time, then the PNG. Fresh for
 * [MAX_AGE_MS]; a stale tile is still used when the network fails. Kept under
 * [MAX_BYTES] by [trim], oldest first. Every failure is a cache miss, never a
 * crash: the map then just downloads as before.
 */
internal object TileDiskCache {
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_BYTES = 100L * 1024 * 1024

    /**
     * Bump when the tile source or [TileRecolor] changes, so old tiles are not
     * shown in the new style; the old folder is cleared by [trim].
     */
    private const val VERSION = "osm-hot-v1"

    private val base: Path? by lazy { runCatching { Path(platformCacheDir(), "map-tiles") }.getOrNull() }
    private val root: Path? by lazy { base?.let { Path(it, VERSION) } }

    class Hit(val png: ByteArray, val fresh: Boolean)

    private fun file(z: Int, x: Int, y: Int): Path? = root?.let { Path(it, "$z", "$x", "$y.t") }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    fun read(z: Int, x: Int, y: Int): Hit? = runCatching {
        val f = file(z, x, y) ?: return null
        if (!SystemFileSystem.exists(f)) return null
        val all = SystemFileSystem.source(f).buffered().use { it.readByteArray() }
        if (all.size <= 8) return null
        Hit(all.copyOfRange(8, all.size), now() - all.readLong() < MAX_AGE_MS)
    }.getOrNull()

    /** True when a fresh copy is on disk; reads only the 8-byte header. */
    fun hasFresh(z: Int, x: Int, y: Int): Boolean = runCatching {
        val f = file(z, x, y) ?: return false
        if (!SystemFileSystem.exists(f)) return false
        val head = SystemFileSystem.source(f).buffered().use { it.readByteArray(8) }
        now() - head.readLong() < MAX_AGE_MS
    }.getOrDefault(false)

    fun write(z: Int, x: Int, y: Int, png: ByteArray) {
        runCatching {
            val f = file(z, x, y) ?: return
            val dir = f.parent ?: return
            SystemFileSystem.createDirectories(dir)
            // Written aside and moved in, so a reader on another worker never
            // sees half a tile.
            val tmp = Path(dir, "${f.name}.${Random.nextInt(0, Int.MAX_VALUE)}.tmp")
            SystemFileSystem.sink(tmp).buffered().use {
                it.write(now().toBytes())
                it.write(png)
            }
            SystemFileSystem.atomicMove(tmp, f)
        }
    }

    /**
     * Drops old-version folders, then the oldest tiles until the cache is
     * back under 80 % of [MAX_BYTES]. Walks every file, so call it off the
     * main thread, once a launch.
     */
    fun trim() {
        runCatching {
            val base = base ?: return
            if (!SystemFileSystem.exists(base)) return
            SystemFileSystem.list(base).filter { it.name != VERSION }.forEach { deleteTree(it) }
            val root = root ?: return
            if (!SystemFileSystem.exists(root)) return
            class Entry(val path: Path, val size: Long, val savedAt: Long)
            val entries = mutableListOf<Entry>()
            walk(root) { p ->
                val size = SystemFileSystem.metadataOrNull(p)?.size ?: return@walk
                val savedAt = runCatching { SystemFileSystem.source(p).buffered().use { it.readByteArray(8) }.readLong() }.getOrDefault(0L)
                entries += Entry(p, size, savedAt)
            }
            var total = entries.sumOf { it.size }
            if (total <= MAX_BYTES) return
            val target = MAX_BYTES * 8 / 10
            for (e in entries.sortedBy { it.savedAt }) {
                if (total <= target) break
                SystemFileSystem.delete(e.path, mustExist = false)
                total -= e.size
            }
        }
    }

    private fun walk(dir: Path, onFile: (Path) -> Unit) {
        SystemFileSystem.list(dir).forEach { p ->
            if (SystemFileSystem.metadataOrNull(p)?.isDirectory == true) walk(p, onFile) else onFile(p)
        }
    }

    private fun deleteTree(p: Path) {
        if (SystemFileSystem.metadataOrNull(p)?.isDirectory == true) SystemFileSystem.list(p).forEach { deleteTree(it) }
        SystemFileSystem.delete(p, mustExist = false)
    }
}

private fun Long.toBytes() = ByteArray(8) { i -> (this shr (56 - 8 * i)).toByte() }

private fun ByteArray.readLong(): Long {
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (this[i].toLong() and 0xFF)
    return v
}
