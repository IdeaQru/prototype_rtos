package com.example.core
import android.util.Log
import java.util.concurrent.Executors

// ──────────────────────────────────────────────────────────────────────────────
// UT1: periodicTimer — multi-task, priority OTOMATIS dari timerSec
// ──────────────────────────────────────────────────────────────────────────────
fun ut1() {
    Log.d("UT1", "=== UT1: periodicTimer — Auto Priority === ForeGroundService ")

    val rtc = RTC_Engine()
    val aisCollector = NAV_AisCollector()
    var seq = 0L

    // Priority OTOMATIS: timerSec <= 5 → 1, timerSec <= 300 → 2, > 300 → 3 (angka rendah lebih didahulukan)
    // Tidak perlu tulis priority sama sekali!
    rtc.register(taskId = "NAV_SENSOR",  timerSec = 1L, periodicFlag = true) { taskId ->
        seq++
        val data = aisCollector.collect(seq)
        Log.d("UT1", "[$taskId] ${data.formatDisplay()}")
    }

    rtc.register(taskId = "DISPLAY_UI", timerSec = 1L, periodicFlag = true) { taskId ->
        Log.d("UT1", "[$taskId] Update layar navigasi #$seq")
    }

    rtc.register(taskId = "LOG_WRITER", timerSec = 6L, periodicFlag = true) { taskId ->
        Log.d("UT1", "[$taskId] Menulis log ke storage...")
    }

    // 2. Start
    rtc.start()
    Log.d("UT1", "start() OK")

    
    // 3. Poll loop (8 putaran per 1.1 detik)
    repeat(8) { i ->
        val fired = rtc.poll()
        if (fired.isNotEmpty()) Log.d("UT1", "poll[$i] -    fired: $fired")
        try { Thread.sleep(1100L) } catch (_: InterruptedException) {}
    }

    // 4. Dump — task manager + thread info dari JVM
    Log.d("UT1", rtc.dump())
    rtc.stop()
}

// ──────────────────────────────────────────────────────────────────────────────
// UT2: nonPeriodicTimer — one-shot task, priority OTOMATIS dari timerSec
//      Alur: register → start → wait → dump
// ──────────────────────────────────────────────────────────────────────────────
fun ut2() {
    Log.d("UT2", "=== UT2: nonPeriodicTimer — Auto Priority ===")

    val rtc = RTC_Engine()
    val batchPool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Batch-IO-Thread").apply { isDaemon = true }
    }

    // Priority OTOMATIS: timerSec <= 5 → 1 (angka rendah lebih didahulukan)
    rtc.register(taskId = "ALERT_GEOFENCE", timerSec = 2L, periodicFlag = false) { taskId ->
        Log.d("UT2", "[$taskId] Peringatan! Kapal mendekati batas geofence!")
    }

    rtc.register(taskId = "BATCH_SYNC",     timerSec = 3L, periodicFlag = false) { taskId ->
        Log.d("UT2", "[$taskId] Batch sync dimulai di background...")
        batchPool.execute {
            Thread.sleep(120)
            Thread.sleep(150)
            Thread.sleep(80)
            Log.d("UT2", "[$taskId] Batch sync selesai.")
        }
    }

    rtc.register(taskId = "REPORT_GEN",     timerSec = 4L, periodicFlag = false) { taskId ->
        Log.d("UT2", "[$taskId] Generate laporan harian.")
    }

    // 2. Start
    rtc.start()
    Log.d("UT2", "start() OK — menunggu task jatuh tempo")

    // 3. Wait sampai semua task selesai
    repeat(3) {
        val fired = rtc.wait(maxWaitMs = 5000L)
        if (fired.isNotEmpty()) Log.d("UT2", "wait() → fired: $fired")
    }

    try { Thread.sleep(500L) } catch (_: InterruptedException) {}

    // 4. Dump — task manager + thread info dari JVM
    Log.d("UT2", rtc.dump())
    batchPool.shutdownNow()
    rtc.stop()
}
// ──────────────────────────────────────────────────────────────────────────────
// UT3: AlarmManager — Jadwal Interval Panjang (Setiap 4 Jam)
//      Alur: register (4 jam) → start(context) → dump awal → simulasi alarm → dump akhir
// ──────────────────────────────────────────────────────────────────────────────
fun ut3() {
    Log.d("UT3", "=== UT3: AlarmManager — Jadwal Setiap 4 Jam ===")

    val context = android.content.Context()
    val rtc = RTC_Engine()

    val fourHoursSec = 4 * 3600L // 4 jam = 14.400 detik

    // 1. Daftarkan task 4 jam (periodicFlag = false di loop polling, karena dikendalikan AlarmManager)
    // Priority otomatis: > 300s → 3 (angka semakin rendah semakin didahulukan)
    rtc.register(taskId = "SYNC_BATCH_4H", timerSec = fourHoursSec, periodicFlag = false) { taskId ->
        Log.d("UT3", "[$taskId] AlarmManager WAKEUP! Sinkronisasi batch telemetri 4-jam berhasil dieksekusi.")
    }

    // 2. Start engine dengan Context (Otomatis mendaftarkan jadwal ke AlarmManager)
    rtc.start(context)
    Log.d("UT3", "start(context) OK — AlarmManager berhasil menjadwalkan task 4 jam ke depan.")

    // 3. Tampilkan dump  table task id
    Log.d("UT3", rtc.dump())

    // 4. Simulasi: AlarmManager berdering dari kernel Android (memicu RTC_AlarmReceiver)
    Log.d("UT3", "Simulasi: AlarmManager berdering dari kernel Android...")
    val intent = android.content.Intent(context, RTC_AlarmReceiver::class.java).apply {
        putExtra("task_id", "SYNC_BATCH_4H")
    }
    RTC_AlarmReceiver().onReceive(context, intent)

    // 5. Tampilkan dump setelah alarm dieksekusi
    // (Status selesai, trigger terhitung, 
    Log.d("UT3", rtc.dump())
    rtc.stop()
}


// ──────────────────────────────────────────────────────────────────────────────
// MAIN
// ──────────────────────────────────────────────────────────────────────────────
fun main() {
    ut1()
    ut2()
    ut3()
}
