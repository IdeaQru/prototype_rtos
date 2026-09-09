package com.example.core
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class RTC_Tick(val type: String, val epoch: Long, val seq: Long)

data class RTC_ScheduleConfig(val intervalSec: Long, val isKernelAlarm: Boolean)

class RTC_Engine {

    companion object {
        @Volatile internal var _activeInstance: RTC_Engine? = null
    }

    @Volatile private var __running = false
    @Volatile var isFsReady = false

    // Antrean pesan blocking untuk FS
    val tickQueue = LinkedBlockingQueue<RTC_Tick>()

    // Dynamic Registry: Menyimpan schedule, subscriber, dan sequence 
    private val __schedules = ConcurrentHashMap<String, RTC_ScheduleConfig>()
    private val __subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val __sequences = ConcurrentHashMap<String, AtomicLong>()

    private val __executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "RTC-Clock-Thread").apply { isDaemon = true }
    }
    private var __startupThread: Thread? = null
    private var __startEpoch = 0L

    fun registerSchedule(tickId: String, intervalSec: Long, isKernelAlarm: Boolean = false) {
        __schedules[tickId.uppercase()] = RTC_ScheduleConfig(intervalSec, isKernelAlarm)
    }

    fun registerMe(taskId: String, tickId: String) {
        val key = tickId.uppercase()
        __subscribers.computeIfAbsent(key) { CopyOnWriteArrayList() }.apply {
            if (!contains(taskId)) add(taskId)
        }
    }

    fun start(context: Context) {
        if (__running) return
        __running = true
        _activeInstance = this

        __startupThread = Thread({
            // Loop menunggu FS selesai setup
            while (!isFsReady && __running) {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            if (!__running) return@Thread
            __startEpoch = System.currentTimeMillis()

            __schedules.forEach { (tickId, config) ->
                if (config.isKernelAlarm) {
                    __scheduleKernelAlarm(context, tickId, __startEpoch + (config.intervalSec * 1000L))
                } else {
                    __startFastClock(tickId, config.intervalSec)
                }
            }
        }, "RTC-Startup-Thread").apply { isDaemon = true; start() }
    }

    fun stop() {
        __running = false
        if (_activeInstance == this) _activeInstance = null
        __startupThread?.interrupt()
        __startupThread = null
        __executor.shutdownNow()
        tickQueue.clear()
    }

    private fun __startFastClock(tickId: String, intervalSec: Long) {
        val seqCounter = __sequences.computeIfAbsent(tickId) { AtomicLong(0L) }
        __executor.scheduleAtFixedRate({
            if (__running) {
                val seq = seqCounter.incrementAndGet()
                tickQueue.offer(RTC_Tick(tickId, System.currentTimeMillis(), seq))
            }
        }, intervalSec * 1000L, intervalSec * 1000L, TimeUnit.MILLISECONDS)
    }

    internal fun _handleAlarmTick(context: Context, tickId: String, expectedEpoch: Long) {
        if (!__running) return
        val key = tickId.uppercase()
        val config = __schedules[key] ?: return
        val seq = __sequences.computeIfAbsent(key) { AtomicLong(0L) }.incrementAndGet()

        tickQueue.offer(RTC_Tick(key, expectedEpoch, seq))

        // Re-arm alarm berikutnya 
        if (!__running) return
        __scheduleKernelAlarm(context, key, expectedEpoch + (config.intervalSec * 1000L))
    }

    private fun __scheduleKernelAlarm(context: Context, tickId: String, targetEpoch: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RTC_AlarmReceiver::class.java).apply {
            putExtra("tick_id", tickId)
            putExtra("target_epoch", targetEpoch)
        }
        val pi = PendingIntent.getBroadcast(context, tickId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpoch, pi)
    }
}
