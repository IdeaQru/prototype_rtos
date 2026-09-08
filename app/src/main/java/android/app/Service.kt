package android.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

open class Service : Context() {
    companion object {
        const val START_STICKY = 1
    }
    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    open fun onDestroy() {}
    open fun onBind(intent: Intent?): IBinder? = null
}

class PendingIntent(val targetEpoch: Long = 0L, val targetReceiverClass: Class<*>? = null) {
    companion object {
        const val FLAG_UPDATE_CURRENT = 134217728
        const val FLAG_IMMUTABLE = 67108864
        @Suppress("UNUSED_PARAMETER")
        fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent {
            val epoch = intent.getLongExtra("target_epoch", 0L)
            return PendingIntent(epoch, intent.cls)
        }
    }
}

class AlarmManager {
    companion object {
        const val RTC_WAKEUP = 0
        private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Hardware-RTC-Chipset-Thread").apply { isDaemon = true }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
        schedule(triggerAtMillis, operation)
    }

    @Suppress("UNUSED_PARAMETER")
    fun setExact(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
        schedule(triggerAtMillis, operation)
    }

    private fun schedule(triggerAtMillis: Long, operation: PendingIntent) {
        val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val expectedEpoch = if (operation.targetEpoch != 0L) operation.targetEpoch else triggerAtMillis
        scheduler.schedule({
            val intent = Intent().apply {
                putExtra("target_epoch", expectedEpoch)
            }
            val receiver = try {
                operation.targetReceiverClass?.getDeclaredConstructor()?.newInstance() as? BroadcastReceiver
            } catch (e: Exception) {
                null
            }
            receiver?.onReceive(Context(), intent)
        }, delayMs, TimeUnit.MILLISECONDS)
    }
}
