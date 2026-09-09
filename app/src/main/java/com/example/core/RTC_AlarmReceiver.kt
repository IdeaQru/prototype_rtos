package com.example.core
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
// for receiving data from application level (not implemented yet)
class RTC_AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tickId = intent.getStringExtra("tick_id") ?: "T2"
        val epoch = intent.getLongExtra("target_epoch", System.currentTimeMillis())
        RTC_Engine._activeInstance?._handleAlarmTick(context, tickId, epoch)
    }
}
