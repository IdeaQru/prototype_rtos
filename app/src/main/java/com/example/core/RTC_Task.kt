package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RTC_Task(
    val taskId: String,
    val timerSec: Long,
    val periodicFlag: Boolean,
    var priority: Int = autoPriority(timerSec),
    var status: String = STATUS_WAITING,
    var nextRunEpoch: Long = 0L,
    var lastRunEpoch: Long = 0L,
    var triggerCount: Long = 0L,
    var pid: Long = -1L,
    var threadId: Long = -1L
) {
    /**
     * Konversi priority angka RTOS (1 = Tertinggi, 2 = Normal, 3 = Rendah)
     * ke JVM Thread priority (10 = MAX_PRIORITY, 5 = NORM_PRIORITY, 2 = MIN+1)
     */
    fun toThreadPriority(): Int = when (priority) {
        1    -> Thread.MAX_PRIORITY
        2    -> Thread.NORM_PRIORITY
        else -> Thread.MIN_PRIORITY + 1
    }

    /**
     * Eksekusi task: alihkan thread priority, catat PID/epoch otomatis,
     * jalankan callback action, dan pulihkan thread priority semula secara aman.
     */
    fun execute(action: ((String) -> Unit)? = null) {
        val originalPriority = Thread.currentThread().priority
        val now = System.currentTimeMillis()
        status = STATUS_RUNNING
        triggerCount++
        lastRunEpoch = now
        if (periodicFlag) {
            nextRunEpoch += (timerSec * 1000L)
        }

        // Ambil PID (Process ID) otomatis dari JVM/OS:
        pid = try { ProcessHandle.current().pid() } catch (_: Throwable) { -1L }
        threadId = Thread.currentThread().threadId()
        Thread.currentThread().priority = toThreadPriority()
        try {
            action?.invoke(taskId)
        } catch (_: Exception) {
        } finally {
            Thread.currentThread().priority = originalPriority
            status = if (periodicFlag) STATUS_WAITING else STATUS_DONE
        }
    }

    fun formatRow(): String {
        val lastRun = if (lastRunEpoch == 0L) "-" else
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastRunEpoch))
        val pidStr = if (pid == -1L) "-" else pid.toString()
        val col = "%-7s | %-18s | %8s | %8s | %8s | %-8s | %s"
        return col.format(
            pidStr,
            taskId,
            priority.toString(),
            "${timerSec}s",
            if (periodicFlag) "YES" else "NO",
            status,
            lastRun
        )
    }

    companion object {
        const val STATUS_WAITING = "WAITING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE    = "DONE"

        /** Otomatis dari timerSec: semakin rendah angkanya semakin didahulukan (RTOS convention) */
        fun autoPriority(timerSec: Long): Int = when {
            timerSec <= 5   -> 1  // Paling didahulukan (Real-Time / Cepat)
            timerSec <= 300 -> 2  // Normal
            else            -> 3  // Rendah (Background / Long Interval)
        }

        fun dumpTable(tasks: Collection<RTC_Task>): String {
            val col = "%-7s | %-18s | %8s | %8s | %8s | %-8s | %s"
            val header = col.format(
                "PID", "TASK_ID", "PRIORITY", "INTERVAL", "PERIODIC", "STATUS", "LAST RUN"
            )
            val sep = "-".repeat(header.length)
            val rows = tasks
                .sortedBy { it.priority }
                .joinToString("\n") { it.formatRow() }
            return "\n$sep\n$header\n$sep\n$rows\n$sep"
        }
    }
}

