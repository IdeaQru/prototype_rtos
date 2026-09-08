package com.example.core2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// IPC BOUNDARY (Kernel-Level BroadcastReceiver untuk Batch Store & Sync)
class RtcAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetEpoch = intent.getLongExtra("target_epoch", System.currentTimeMillis())
        CoreSystemEngine2.activeInstance?.handleBatchAlarm(context, targetEpoch)
    }
}
