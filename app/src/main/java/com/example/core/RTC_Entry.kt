package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RTC_Entry(
    val taskName: String,
    val timerSec: Long,
    val periodicFlag: Boolean,
    var priority: Int = -1,
    var status: String = "WAITING",
    var nextRunEpoch: Long = 0L,
    var lastRunEpoch: Long = 0L,
    var triggerCount: Long = 0L,
    var pid: Long = -1L,
) {

    companion object {
        const val STATUS_WAITING = "WAITING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE    = "DONE"

        fun dumpTable(entries: Collection<RTC_Entry>): String {
            val col = "%-7s | %-18s | %8s | %8s | %8s | %-8s | %s"
            val header = col.format(
                "PID", "TASK_NAME", "PRIORITY", "INTERVAL", "PERIODIC", "STATUS", "LAST RUN"
            )
            val sep = "-".repeat(header.length)
            val rows = entries
                .sortedBy { it.priority }
                .joinToString("\n") { it.formatRow() }
            return "\n$sep\n$header\n$sep\n$rows\n$sep"
        }
    }

    // ==========================================
    // STATUS LIFECYCLE & EXECUTION (Single Source of Truth)
    // ==========================================

    /** Dipanggil saat Engine start untuk mengaktifkan jadwal awal */
    fun activate(now: Long = System.currentTimeMillis()) {
        nextRunEpoch = now + (timerSec * 1000L)
        status = STATUS_WAITING
    }

    /** Mengecek apakah task berkala siap dieksekusi */
    fun shouldRun(now: Long = System.currentTimeMillis()): Boolean {
        return periodicFlag && status == STATUS_WAITING && now >= nextRunEpoch
    }

    /** Mengecek apakah task one-shot masih menunggu dieksekusi */
    fun isWaitingOneShot(): Boolean {
        return !periodicFlag && status == STATUS_WAITING
    }

    /**
     * Satu-satunya tempat pemrosesan status saat runtime:
     * 1. Ubah jadi RUNNING
     * 2. Jalankan aksi
     * 3. Selesai: kembali ke WAITING (jika periodic) atau DONE (jika one-shot)
     */
    fun execute(action: ((String) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        status = STATUS_RUNNING
        triggerCount++
        lastRunEpoch = now

        try {
            action?.invoke(taskName)
        } finally {
            if (periodicFlag) {
                status = STATUS_WAITING
                nextRunEpoch = now + (timerSec * 1000L)
            } else {
                status = STATUS_DONE
            }
        }
    }

    // ==========================================
    // PRIVATE HELPERS
    // ==========================================

    private fun __calculatePriority(): Int {
        return when {
            timerSec <= 5 -> 1 // Paling cepat / High Priority
            timerSec <= 300 -> 2 // Normal
            else -> 3 // Background
        }
    }

    private fun __resolvePid(): Long {
        return try {
            ProcessHandle.current().pid()
        } catch (_: Throwable) {
            -1L
        }
    }

    init {
        this.priority = __calculatePriority()
        this.pid = __resolvePid()
        this.nextRunEpoch = System.currentTimeMillis() + (this.timerSec * 1000L)
    }

    fun formatRow(): String {
        val lastRun =
            if (lastRunEpoch == 0L) "-"
            else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastRunEpoch))
        val pidStr = if (pid == -1L) "-" else pid.toString()
        val col = "%-7s | %-18s | %8s | %8s | %8s | %-8s | %s"
        return col.format(
            pidStr,
            taskName,
            priority.toString(),
            "${timerSec}s",
            if (periodicFlag) "YES" else "NO",
            status,
            lastRun,
        )
    }
}
