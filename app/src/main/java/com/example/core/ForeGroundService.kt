package com.example.core2 

import android.app.Service
import android.content.Context
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.logging.Logger

// FOREGROUND SERVICE - Listener & Pemegang WakeLock
class ForeGroundService2(private val __engine: CoreSystemEngine2) : Service(), ClockListener {

    private val __ioPool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Core2-IO-Thread").apply { isDaemon = true }
    }
    private val __log = Logger.getLogger("ForeGroundService2")
    private val __timeFormat = SimpleDateFormat("HH:mm:ss.SSS")
    private var __wakeLock: PowerManager.WakeLock? = null
    private var __lastEpoch = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        // Kunci CPU dengan Partial WakeLock agar CPU tidak pernah tidur saat layar mati
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        __wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ADA3::NavigationLock")?.apply {
            acquire()
            __log.info("Partial WakeLock aktif: CPU tablet dijaga tetap menyala saat layar mati.")
        }
        __engine.subscribe(this)
    }

    override fun onDestroy() {
        __engine.unsubscribe(this)
        __wakeLock?.release()
        __ioPool.shutdownNow()
        super.onDestroy()
    }

    //  PENERIMA PESAN DARI CORE SYSTEM
    override fun onMessage(message: SystemMessage) {
        when (message) {
            is SystemMessage.RealtimeTelemetry -> {
                val realEpoch = System.currentTimeMillis()
                __lastEpoch = realEpoch
                val drift = realEpoch - message.epoch
                val loc = __readLocation(message.seq, message.epoch)

                println(String.format(
                    Locale.US,
                    "   --> [CORE2-FAST-1S] Lat: %.6f | Lon: %.6f | Speed: %.1f kts | Heading: %.1f deg",
                    loc.lat, loc.lon, loc.speedKts, loc.headingDeg
                ))
                __log.info("FAST TICK #${message.seq} | Expected: ${__time(message.epoch)} | Real: ${__time(realEpoch)} | Drift: +${drift}ms")
            }

            is SystemMessage.StoreAndSync -> {
                val realEpoch = System.currentTimeMillis()
                val drift = realEpoch - message.epoch
                __log.warning("BATCH TICK (5s/5m) #${message.seq} | Expected: ${__time(message.epoch)} | Real: ${__time(realEpoch)} | Drift: +${drift}ms")
                __ioPool.execute {
                    try {
                        
                        val t1 = System.currentTimeMillis()
                        Thread.sleep(120) // Simulasi tulis ke SQLite
                        __log.info("IO-STORAGE | Selesai dalam ${System.currentTimeMillis() - t1} ms")

                        val t2 = System.currentTimeMillis()
                        Thread.sleep(200) // Simulasi upload telemetri ke server
                        __log.info("IO-NETWORK | Selesai dalam ${System.currentTimeMillis() - t2} ms")
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }

            }
        }
    }

    private fun __readLocation(seq: Long, epoch: Long) = FusedLocation2(
        lat = -6.175392 + (seq * 0.00001),
        lon = 106.827153 + (seq * 0.00001),
        speedKts = 12.4 + (Math.sin(seq.toDouble()) * 0.5),
        headingDeg = (45.0 + (seq * 0.2)) % 360.0,
        timestamp = epoch
    )

    private fun __time(epoch: Long): String = __timeFormat.format(Date(epoch))
}
