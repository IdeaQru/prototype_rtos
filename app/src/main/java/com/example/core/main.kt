package com.example.core

import android.util.Log

// ------------------------------------------------------------------------------
// UT1: periodicTimer - Hanya menggunakan Task TXT dan LOG
// ------------------------------------------------------------------------------
fun ut1() {
    Log.d("UT1", "=== [CORE2] UT1: periodicTimer - TXT & LOG Saja ===")

    val rtc = RTC_Engine()
    var seq = 0L
    val aisCollector = NAV_AisCollector()


    // 1. Task TXT: Memproses teks pesan berkala
    rtc.register(taskName = "TXT_TASK", timerSec = 1L, periodicFlag = true) { taskName ->
        seq++
        val data = aisCollector.collect(seq)
        Log.d("UT1", "[$taskName] Memproses ${data.formatDisplay()}")
    }

    // 2. Task LOG: Mencatat status log berkala
    rtc.register(taskName = "LOG_TASK", timerSec = 2L, periodicFlag = true) { taskName ->
        Log.d("UT1", "[$taskName] Menulis log sistem ke catatan teks...")
    }

    // Start Scheduler
    rtc.start()
    Log.d("UT1", "start() OK - Scheduler aktif")

    // Poll loop (6 putaran per 1.1 detik)
    repeat(6) { i ->
        try { Thread.sleep(1100L) } catch (_: InterruptedException) {}
        val fired = rtc.poll()
        val loopCount = i + 1
        if (fired.isNotEmpty()) Log.d("UT1", "poll[$loopCount] - fired: $fired")
    }

    // Cetak tabel ringkasan
    Log.d("DUMP TABLE UT1", rtc.dump())
    rtc.stop()
}

// ------------------------------------------------------------------------------
// UT2: nonPeriodicTimer - One-Shot TXT & LOG
// ------------------------------------------------------------------------------
fun ut2() {
    Log.d("UT2", "=== [CORE2] UT2: nonPeriodicTimer - One-Shot TXT & LOG ===")

    val rtc = RTC_Engine()

    rtc.register(taskName = "LOG_ALERT", timerSec = 2L, periodicFlag = false) { taskName ->
        Log.d("UT2", "[$taskName] Notifikasi Log: Task satu kali jalan selesai dicatat!")
    }

    rtc.start()
    Log.d("UT2", "start() OK - Menunggu task one-shot...")

    val fired = rtc.wait(maxWaitMs = 3000L)
    Log.d("UT2", "wait() selesai - fired: $fired")

    Log.d("UT2", rtc.dump())
    rtc.stop()
}

// ------------------------------------------------------------------------------
// MAIN
// ------------------------------------------------------------------------------
fun main() {
    ut1()
    ut2()
}
