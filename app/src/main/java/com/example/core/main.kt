
package com.example.core
import android.content.Context


fun main() {
    val context = Context()
    val rtc = RTC_Engine()
    val fsAis = FS_AIS()

    // 1. define schedule detak waktu di RTC
    rtc.registerSchedule("T1", intervalSec = 1L, isKernelAlarm = false)   // 1 detik fast stream
    rtc.registerSchedule("T2", intervalSec = 5L, isKernelAlarm = true)    // 5 detik demo (5 menit kapal)
    rtc.registerSchedule("T3", intervalSec = 600L, isKernelAlarm = true)  // 10 menit (APP / Sync)
    rtc.registerSchedule("T4", intervalSec = 10800L, isKernelAlarm = true)

    val fs = FS_ForegroundService(rtc)

    // 2. registerTask 
        fs.registerTask("T1", "FS_Task_Navigation") { tick ->
            val aisData = collectAisNav(tick.seq, tick.epoch)
            println("   --> [FS-DISPLAY] ${aisData.formatDisplay()}")
        }

        fs.registerTask("T2", "FS_Task_BatchSync") { _ ->
            fsAis.processBatch()
        }

        fs.registerTask("T3", "FS_Task_Reconcile") { tick ->
            println("   --> [FS-T3 #${tick.seq}] Menjalankan rekonsiliasi data 10 menit...")
        }

        fs.registerTask("T4", "FS_Task_LongReport") { tick ->
            println("   --> [FS-T4 #${tick.seq}] Menjalankan laporan periodik 180 menit...")
        }

    // Timer demo 30 detik untuk pengujian 
    Thread({
        try {
            Thread.sleep(30000L)
            println("\n[FS] # step 3: destroy (demo 30s selesai)")
            fs.destroy()
            fsAis.shutdown()
        } catch (_: InterruptedException) {}
    }, "Demo-Timer-Thread").apply { isDaemon = true; start() }

    // # step 1: initialization
    println("[FS] # step 1: initialization")
    fs.setup(context)

    // # step 2: main loop (blocking)
    println("[FS] # step 2: main loop")
    fs.runLifeCycle()
}
