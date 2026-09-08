package com.example.core2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

// 1. DATA MODEL (Immutable Navigasi Kapal)
data class FusedLocation2(
    val lat: Double, val lon: Double, val speedKts: Double, val headingDeg: Double, val timestamp: Long
)

// 2. PESAN SISTEM (Pola Event / Message Passing)
sealed class SystemMessage {
    data class RealtimeTelemetry(val epoch: Long, val seq: Long) : SystemMessage()
    data class StoreAndSync(val epoch: Long, val seq: Long) : SystemMessage()
}

// 3. KONTRAK LISTENER TUNGGAL
interface ClockListener {
    fun onMessage(message: SystemMessage)
}

// 4. CORE SYSTEM ENGINE 2 (Arsitektur Terpangkas: Fast Stream 1x Register + Kernel Batch Alarm)
class CoreSystemEngine2(private val __batchIntervalSec: Long = 5L) {

    companion object {
        @Volatile internal var activeInstance: CoreSystemEngine2? = null
    }

    @Volatile private var __running = false
    private val __listeners = CopyOnWriteArrayList<ClockListener>()
    private val __telemetrySeq = AtomicLong(0L)
    private val __batchSeq = AtomicLong(0L)
    private var __startEpoch = 0L
    private val __log = Logger.getLogger("CoreSystemEngine2")

    // Single-registration executor untuk Fast Telemetry 1 Detik
    private var __fastTelemetryScheduler: ScheduledExecutorService? = null

    fun subscribe(listener: ClockListener) = __listeners.addIfAbsent(listener)
    fun unsubscribe(listener: ClockListener) = __listeners.remove(listener)

    fun start(context: Context) {
        if (__running) return
        __running = true
        activeInstance = this
        __telemetrySeq.set(0L)
        __batchSeq.set(0L)
        __startEpoch = System.currentTimeMillis()

        __log.info("Memulai Core 2 (Fast Telemetry 1x Register + Kernel Batch Alarm)...")

        // 1. DETAK 1 DETIK: Didaftarkan CUKUP 1 KALI di awal (Tanpa Re-Arm tiap detik)
        __fastTelemetryScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "Core2-FastClock-Thread").apply { isDaemon = true }
        }.apply {
            scheduleAtFixedRate({
                if (__running) {
                    val seq = __telemetrySeq.incrementAndGet()
                    val expectedEpoch = __startEpoch + (seq * 1000L)
                    __listeners.forEach { it.onMessage(SystemMessage.RealtimeTelemetry(expectedEpoch, seq)) }
                }
            }, 1000L, 1000L, TimeUnit.MILLISECONDS)
        }

        // 2. BATCH STORE & SYNC: Dijadwalkan ke AlarmManager Kernel OS untuk batch pertama
        __scheduleNextBatchAlarm(context, __startEpoch + (__batchIntervalSec * 1000L))
    }

    fun stop() {
        __running = false
        if (activeInstance == this) activeInstance = null
        __fastTelemetryScheduler?.shutdownNow()
        __fastTelemetryScheduler = null
        __listeners.clear()
        __log.info("Core 2 dihentikan.")
    }

    // Dipanggil saat Alarm Kernel AlarmManager berbunyi untuk Batch Store & Sync
    internal fun handleBatchAlarm(context: Context, expectedEpoch: Long) {
        if (!__running) return
        val currentBatch = __batchSeq.incrementAndGet()

        __listeners.forEach { it.onMessage(SystemMessage.StoreAndSync(expectedEpoch, currentBatch)) }

        if (!__running) return
        __scheduleNextBatchAlarm(context, __startEpoch + ((currentBatch + 1) * __batchIntervalSec * 1000L))
    }

    private fun __scheduleNextBatchAlarm(context: Context, targetEpoch: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RtcAlarmReceiver::class.java).apply {
            putExtra("target_epoch", targetEpoch)
        }
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpoch, pi)
    }
}

// 5. Runner
fun main() {
    System.setProperty("java.util.logging.SimpleFormatter.format", "[%1\$tT] [%4\$s] [%3\$s] %5\$s%n")

    val engine = CoreSystemEngine2(1L)
    val service = ForeGroundService2(engine)
    service.onCreate()

    val context = Context()
    engine.start(context)

    Thread.sleep(30000L) 

    service.onDestroy()
    engine.stop()
}
