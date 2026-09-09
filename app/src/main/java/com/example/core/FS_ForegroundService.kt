package com.example.core

import android.app.Service
import android.content.Context
import android.os.PowerManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FS_ForegroundService(
    private val __rtc: RTC_Engine = RTC_Engine()
) : Service() {

    @Volatile private var __running = false
    private var __wakeLock: PowerManager.WakeLock? = null

   // Daftar Handler Task
    private val __taskHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(RTC_Tick) -> Unit>>()

    // Public API: Daftarkan aksi task apa pun 
    fun registerTask(tickId: String, taskName: String, handler: (RTC_Tick) -> Unit) {
        val key = tickId.uppercase()
        __rtc.registerMe(taskName, key)
        __taskHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(handler)
    }

    // # STEP 1: INITIALIZATION
    fun setup(context: Context) {
        __acquireWakeLock(context)
        __rtc.start(context)
        __rtc.isFsReady = true
        __running = true
    }

    // # STEP 2: MAIN LOOP 
    fun runLifeCycle() {
        while (__running) {
            try {
                // # step 2.1: listen tick (blocking)
                val tick = __rtc.tickQueue.take()

                // # step 2.2: eksekusi generik sesuai schedule yang terdaftar
                __dispatch(tick)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    // # STEP 3: DESTROY
    fun destroy() {
        __running = false
        __rtc.stop()
        __releaseWakeLock()
    }

    // Eksekusi generik: memanggil semua handler yang terdaftar pada tickId tersebut
    private fun __dispatch(tick: RTC_Tick) {
        val handlers = __taskHandlers[tick.type.uppercase()] ?: return
        handlers.forEach { handler ->
            try {
                handler(tick)
            } catch (_: Exception) {}
        }
    }

    private fun __acquireWakeLock(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        __wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ADA3::NavigationLock")?.apply {
            acquire()
        }
    }

    private fun __releaseWakeLock() {
        __wakeLock?.release()
        __wakeLock = null
    }
}
