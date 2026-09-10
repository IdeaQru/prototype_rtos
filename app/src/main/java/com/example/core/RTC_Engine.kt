package com.example.core
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class RTC_Engine(
    initialEntry: RTC_Entry? = null
) {
    companion object {
        @Volatile internal var _activeInstance: RTC_Engine? = null
    }

    @Volatile private var __running = false
    private val __entries = ConcurrentHashMap<String, RTC_Entry>()
    private val __actions = ConcurrentHashMap<String, (String) -> Unit>()

    init {
        if (initialEntry != null) {
            __storeEntry(initialEntry)
        }
    }

    // ── PUBLIC API ─────────────────────────────────────────────────────────────

    fun register(
        taskName: String,
        timerSec: Long,
        periodicFlag: Boolean,
        priority: Int = -1,
        action: ((String) -> Unit)? = null
    ) {
        val entry = 
            RTC_Entry(
                taskName = taskName.uppercase(),
                timerSec = timerSec,
                periodicFlag = periodicFlag,
                priority = priority
            )
            __storeEntry(entry,action)
        
    }

    private fun __storeEntry(entry: RTC_Entry, action: ((String) -> Unit)? = null) {
        val key = entry.taskName.uppercase()
        __entries[key] = entry
        if (action != null) __actions[key] = action
    }

    fun start(context: Context? = null) {
        __running = true
        _activeInstance = this
        val now = System.currentTimeMillis()
        // Tidak ada manipulasi status di sini, serahkan ke entry:
        __entries.values.forEach { entry ->
            entry.activate(now)
        }
    }

    fun stop() {
        __running = false
        if (_activeInstance == this) _activeInstance = null
    }

    fun poll(): List<String> {
        if (!__running) return emptyList()
        val now = System.currentTimeMillis()
        // Entry yang memutuskan apakah saatnya jalan, dan execute() yang mengubah statusnya:
        return __entries.values
            .filter { it.shouldRun(now) }
            .sortedBy { it.priority }
            .onEach { it.execute(__actions[it.taskName]) }
            .map { it.taskName }
    }

    fun wait(maxWaitMs: Long? = null): List<String> {
        if (!__running) return emptyList()
        val nonPeriodic = __entries.values.filter { it.isWaitingOneShot() }
        if (nonPeriodic.isEmpty()) return emptyList()

        val sleepMs = (nonPeriodic.minOf { it.nextRunEpoch } - System.currentTimeMillis()).coerceAtLeast(0L)
        val waitTime = if (maxWaitMs != null) minOf(sleepMs, maxWaitMs) else sleepMs
        if (waitTime > 0) try { Thread.sleep(waitTime) } catch (_: InterruptedException) {}

        val now = System.currentTimeMillis()
        return nonPeriodic
            .filter { now >= it.nextRunEpoch }
            .sortedBy { it.priority }
            .onEach { it.execute(__actions[it.taskName]) }
            .map { it.taskName }
    }

    fun dump(): String = RTC_Entry.dumpTable(__entries.values)
}