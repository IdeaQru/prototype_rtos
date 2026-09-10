package com.example.core
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RTC_AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        RTC_Engine._activeInstance?._handleAlarmTick(context, taskId)
    }

    companion object {
        fun schedule(context: Context, taskId: String, targetEpoch: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RTC_AlarmReceiver::class.java).apply {
                putExtra("task_id", taskId)
                putExtra("target_epoch", targetEpoch)
            }
            val pi = PendingIntent.getBroadcast(
                context, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpoch, pi)
        }
    }
}

