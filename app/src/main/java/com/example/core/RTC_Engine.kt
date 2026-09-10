package com.example.core

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class RTC_Engine {

    companion object {
        @Volatile internal var _activeInstance: RTC_Engine? = null
    }

    @Volatile private var __running = false
    private val __tasks   = ConcurrentHashMap<String, RTC_Task>()
    private val __actions = ConcurrentHashMap<String, (String) -> Unit>()

    // ── PUBLIC API ─────────────────────────────────────────────────────────────

    fun register(
        taskId: String,
        timerSec: Long,
        periodicFlag: Boolean,
        priority: Int = RTC_Task.autoPriority(timerSec),
        action: ((String) -> Unit)? = null
    ) {
        val key = taskId.uppercase()
        __tasks[key] = RTC_Task(
            taskId = key,
            timerSec = timerSec,
            periodicFlag = periodicFlag,
            priority = priority
        )
        if (action != null) __actions[key] = action
    }
    
    fun start(context: Context? = null) {
        __running = true
        _activeInstance = this
        val now = System.currentTimeMillis()
        __tasks.values.forEach { task ->
            task.nextRunEpoch = now + (task.timerSec * 1000L)
            task.status       = RTC_Task.STATUS_WAITING
            if (context != null && !task.periodicFlag) {
                RTC_AlarmReceiver.schedule(context, task.taskId, task.nextRunEpoch)
            }
        }
    }

    fun poll(): List<String> {
        if (!__running) return emptyList()
        val now = System.currentTimeMillis()
        return __tasks.values
            .filter { it.periodicFlag && now >= it.nextRunEpoch }
            .sortedBy { it.priority }
            .onEach { it.execute(__actions[it.taskId]) }
            .map { it.taskId }
    }

    fun wait(maxWaitMs: Long? = null): List<String> {
        if (!__running) return emptyList()
        val nonPeriodic = __tasks.values.filter { !it.periodicFlag && it.status != RTC_Task.STATUS_DONE }
        if (nonPeriodic.isEmpty()) return emptyList()

        val sleepMs = (nonPeriodic.minOf { it.nextRunEpoch } - System.currentTimeMillis()).coerceAtLeast(0L)
        val waitTime = if (maxWaitMs != null) minOf(sleepMs, maxWaitMs) else sleepMs
        if (waitTime > 0) try { Thread.sleep(waitTime) } catch (_: InterruptedException) {}

        val now = System.currentTimeMillis()
        return nonPeriodic
            .filter { now >= it.nextRunEpoch }
            .sortedBy { it.priority }
            .onEach { it.execute(__actions[it.taskId]) }
            .map { it.taskId }
    }

    fun dump(): String = RTC_Task.dumpTable(__tasks.values)

    fun stop() {
        __running = false
        if (_activeInstance == this) _activeInstance = null
    }

    fun triggerAlarm(taskId: String) {
        _handleAlarmTick(null, taskId)
    }

    // ── KERNEL ALARM DELEGATE ──────────────────────────────────────────────────
    internal fun _handleAlarmTick(context: Context?, taskId: String) {
        if (!__running) return
        val task = __tasks[taskId.uppercase()] ?: return
        task.nextRunEpoch = System.currentTimeMillis() + (task.timerSec * 1000L)
        task.execute(__actions[task.taskId])
        if (context != null) {
            RTC_AlarmReceiver.schedule(context, task.taskId, task.nextRunEpoch)
        }
    }
}


