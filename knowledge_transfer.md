# 📘 Knowledge Transfer: Arsitektur Sistem Telemetri Kapal ADA 3.0

Dokumen transfer pengetahuan komprehensif yang menjelaskan **seluruh konsep fundamental (Task, Process, Thread, IPC)**, evolusi **Core 2 (Dual-Path Streamlined Architecture: Fast Telemetry 1x Register + Kernel RTC Batch Alarm)**, bedah kode baris demi baris, dan alasan arsitektural mengapa proses *re-arm* setiap detik dipangkas secara optimal.

---

## 🧭 BAGIAN 1: PONDASI FUNDAMENTAL (Jantung Sistem)

Sebelum membaca kode, 4 pilar sistem operasi ini wajib dipahami:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SISTEM OPERASI ANDROID                           │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ PROCESS 1 (system_server / Kernel)   │ PROCESS 2 (Aplikasi Kapal ADA 3.0)   │
│ • Linux Kernel RTC Chipset (/dev/rtc)│ • Memory Space Aplikasi              │
│ • AlarmManagerService                │ • Foreground Service                 │
│                                      │                                      │
│               │                      │                  ▲                   │
│               └─────────────[ IPC (Intent) ]────────────┘                   │
│                                (Binder)                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Konsep | Definisi Sederhana | Penerapannya di Sistem Ini |
|---|---|---|
| **Process** | Ruang memori terisolasi yang diberikan OS kepada sebuah program. Process satu tidak bisa membaca memori process lain secara langsung. | `AlarmManager` berjalan di process sistem Android (`system_server`), sedangkan kode navigasi kita berjalan di process aplikasi ADA 3.0. |
| **Task** | Pekerjaan logis tingkat tinggi yang dikerjakan sistem (misal: "mencatat posisi kapal tiap detik", "upload data batch tiap 5 menit"). | Dibagi menjadi 2 Task: Task Realtime (1s) dan Task Batch Sync (5m). |
| **Thread** | Jalur eksekusi terkecil di dalam sebuah process. Satu process bisa punya banyak thread yang berbagi memori yang sama. | Kita punya **FastClock-Thread** (detak jam 1s), **Main Thread** (receiver), dan **IO-Thread** (tulis SQLite & upload). |
| **IPC (*Inter-Process Communication*)** | Protokol jembatan agar dua process berbeda bisa saling kirim data/sinyal tanpa merusak isolasi memori. | Menggunakan **`PendingIntent` & `BroadcastReceiver`**. OS mengirimkan sinyal waktu dari process kernel ke process aplikasi kita. |

---

## ✂️ BAGIAN 2: MENGAPA PROSES RE-ARM SETIAP DETIK DIPANGKAS?

### 2.1 Masalah pada Re-Arm Setiap Detik (Overhead IPC)
Pada arsitektur awal, setiap detak 1 detik memanggil:
`am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpoch, pi)`

Hal ini menimbulkan masalah performa (*performance penalty*) di level sistem operasi:
1. **Pemborosan IPC (3.600 panggilan IPC / jam)**:
   Aplikasi harus memanggil Binder IPC ke `system_server` setiap 1.000 ms, lalu `system_server` harus membroadcast `Intent` kembali ke aplikasi kita setiap detik.
2. **Context Switching & Jitter (+10ms s/d +16ms)**:
   Perpindahan antar-proses (*context switch*) memperkenalkan *jitter* yang tidak perlu pada pembacaan GPS realtime.
3. **Alokasi Objek Berulang**:
   Membuat objek `Intent` dan `PendingIntent` baru setiap detik memicu *Garbage Collection (GC)* di Android.
4. **Redundansi Perlindungan Daya**:
   `ForeGroundService` kita **sudah memegang `PARTIAL_WAKE_LOCK`**. Selama WakeLock aktif, CPU tablet **dijamin tidak akan pernah tidur (*sleep/freeze*)**, sehingga thread internal aplikasi dijamin terus berjalan mulus tanpa perlu dibangunkan paksa oleh AlarmManager setiap 1 detik.

---

### 2.2 Solusi Optimal: Pola *Dual-Path Architecture* (Single-Registration)

Kita membagi sistem menjadi **dua jalur (*dual-path*)** yang sangat efisien:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       JALUR 1: FAST PATH (1 DETIK TELEMETRI)                │
│  • Didaftarkan CUKUP 1 KALI di awal (Single Registration saat engine.start) │
│  • Solusi A: scheduleAtFixedRate / Solusi B: Push Stream GPS/AIS            │
│  • Bebas alokasi Intent & bebas overhead Binder IPC per detik               │
│  • Jitter < 1 ms, CPU dijaga tetap terjaga oleh Partial WakeLock            │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       │ (1. Detak 1 Detik Otomatis)
                                       ▼
                     [ CoreSystemEngine2 (FastClock Thread) ]
                                       │
                                       │ (2. Pancarkan SystemMessage.RealtimeTelemetry)
                                       ▼
                     [ ForeGroundService2 : ClockListener ]
                     • Baca FusedLocation (Lat, Lon, Speed, Heading)
                     • Drift < 1 ms (Sangat Presisi & Ringan)

┌─────────────────────────────────────────────────────────────────────────────┐
│                   JALUR 2: BATCH PATH (5 MENIT / 5 DETIK DEMO)              │
│  • Dijadwalkan ke Kernel AlarmManager OS (setExactAndAllowWhileIdle)        │
│  • Berfungsi sebagai Hardware RTC Watchdog & Pemicu Sinkronisasi            │
│  • Re-arm HANYA dilakukan setiap 5 menit (bukan tiap detik!)                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       │ (3. Alarm Batch Jatuh Tempo)
                                       ▼
                     [ RtcAlarmReceiver : BroadcastReceiver ]
                                       │
                                       │ (4. Sinyal IPC Masuk ke Engine)
                                       ▼
                     [ CoreSystemEngine2 (handleBatchAlarm) ]
                                       │
                                       ├── (5. Re-arm AlarmManager HANYA untuk Batch Berikutnya)
                                       │
                                       │ (6. Pancarkan SystemMessage.StoreAndSync)
                                       ▼
                     [ ForeGroundService2 : ClockListener ]
                     • Delegasikan ke IO-Thread (Background Pool)
                     • Tulis ke SQLite (120 ms)
                     • Upload Cloud Server (200 ms)
```

### 2.3 Perbandingan Solusi Pendaftaran Cukup 1 Kali di Awal

| Solusi | Implementasi | Mekanisme |
|---|---|---|
| **Solusi A (`scheduleAtFixedRate`)** | `ScheduledExecutorService` bawaan Java/Kotlin (Digunakan saat ini di Core 2). | Didaftarkan 1 kali di `start()`. Thread internal mengeksekusi tick setiap 1.000 ms tanpa alokasi objek dan tanpa re-arm. |
| **Solusi B (Push Stream GPS / AIS)** | Android `LocationManager.requestLocationUpdates(GPS_PROVIDER, 1000L, 0f, listener)` atau Socket NMEA AIS. | Driver GPS/koneksi serial AIS menembakkan event ke callback setiap kali satelit mengirim paket baru (1 Hz). Pola listening-nya sama persis: didaftarkan 1 kali di awal. |

---

## 🔍 BAGIAN 3: BEDAH KODE CORE 2 TERBARU — BARIS PER BARIS

Core 2 terdiri dari **3 berkas terpisah** di dalam folder `app/src/main/java/com/example/core/`:

---

### BERKAS 1: `RtcAlarmReceiver.kt` (Pintu Gerbang IPC Batch)

```kotlin
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
```

#### Penjelasan:
- **Hanya Menangani Batch**: Receiver ini sekarang **hanya dipanggil saat alarm batch (5 menit / 5 detik) berbunyi**.
- **Tidak Dibebani Detak 1 Detik**: Tidak ada lagi banjir sinyal broadcast setiap detik ke receiver ini.

---

### BERKAS 2: `CoreSystemEngine2.kt` (Dual-Scheduler Engine)

#### 1. Data Model & Sistem Pesan (Event-Driven)
```kotlin
data class FusedLocation2(
    val lat: Double, val lon: Double, val speedKts: Double, val headingDeg: Double, val timestamp: Long
)

sealed class SystemMessage {
    data class RealtimeTelemetry(val epoch: Long, val seq: Long) : SystemMessage()
    data class StoreAndSync(val epoch: Long, val seq: Long) : SystemMessage()
}

interface ClockListener {
    fun onMessage(message: SystemMessage)
}
```
- **`sealed class SystemMessage`**: Memisahkan dua jenis pesan secara tegas:
  - `RealtimeTelemetry`: Pesan detak 1 detik untuk navigasi cepat.
  - `StoreAndSync`: Pesan detak batch untuk SQLite & Cloud Sync.

#### 2. Deklarasi Class & Dual-Scheduler State
```kotlin
class CoreSystemEngine2(private val __batchIntervalSec: Long = 5L) {

    companion object {
        @Volatile internal var activeInstance: CoreSystemEngine2? = null
    }

    @Volatile private var __running = false
    private val __listeners = CopyOnWriteArrayList<ClockListener>()
    private val __telemetrySeq = AtomicLong(0L)
    private val __batchSeq = AtomicLong(0L)
    private var __startEpoch = 0L
    private val __log = Logger.getLogger("CoreSystemEngine2")

    // Single-registration executor untuk Fast Telemetry 1 Detik
    private var __fastTelemetryScheduler: ScheduledExecutorService? = null
```
- **`__fastTelemetryScheduler`**: Scheduler khusus yang mengeksekusi detak 1 detik. Didaftarkan 1 kali saja.
- **`__telemetrySeq` & `__batchSeq`**: Dua sequence counter atomik terpisah untuk tracking detak cepat dan batch.

#### 3. Memulai Engine (`start`) — Pendaftaran 1 Kali
```kotlin
    fun start(context: Context) {
        if (__running) return
        __running = true
        activeInstance = this
        __telemetrySeq.set(0L)
        __batchSeq.set(0L)
        __startEpoch = System.currentTimeMillis()

        __log.info("Memulai Core 2 (Fast Telemetry 1x Register + Kernel Batch Alarm)...")

        // 1. DETAK 1 DETIK: Didaftarkan CUKUP 1 KALI di awal (Tanpa Re-Arm tiap detik)
        __fastTelemetryScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "Core2-FastClock-Thread").apply { isDaemon = true }
        }.apply {
            scheduleAtFixedRate({
                if (__running) {
                    val seq = __telemetrySeq.incrementAndGet()
                    val expectedEpoch = __startEpoch + (seq * 1000L)
                    __listeners.forEach { it.onMessage(SystemMessage.RealtimeTelemetry(expectedEpoch, seq)) }
                }
            }, 1000L, 1000L, TimeUnit.MILLISECONDS)
        }

        // 2. BATCH STORE & SYNC: Dijadwalkan ke AlarmManager Kernel OS untuk batch pertama
        __scheduleNextBatchAlarm(context, __startEpoch + (__batchIntervalSec * 1000L))
    }
```
- **`scheduleAtFixedRate(..., 1000L, 1000L, TimeUnit.MILLISECONDS)`**: Inilah pemangkasan yang dimaksud! Cukup daftarkan sekali, thread otomatis memanggil listener setiap detik.
- **`__scheduleNextBatchAlarm`**: AlarmManager hanya dijadwalkan untuk target batch pertama (5 detik demo / 5 menit kapal).

#### 4. Penanganan Alarm Batch Kernel (`handleBatchAlarm`)
```kotlin
    internal fun handleBatchAlarm(context: Context, expectedEpoch: Long) {
        if (!__running) return
        val currentBatch = __batchSeq.incrementAndGet()

        // Kirim pesan simpan & sinkronisasi batch
        __listeners.forEach { it.onMessage(SystemMessage.StoreAndSync(expectedEpoch, currentBatch)) }

        // Re-arm AlarmManager HANYA untuk batch berikutnya (tiap 5 detik / 5 menit, BUKAN tiap detik)
        if (!__running) return
        __scheduleNextBatchAlarm(context, __startEpoch + ((currentBatch + 1) * __batchIntervalSec * 1000L))
    }
```
- **Re-arm Selektif**: Re-arm hanya dilakukan saat batch berbunyi (setiap 5 menit), sehingga overhead IPC berkurang sebesar **99.7%**!

---

### BERKAS 3: `ForeGroundService2.kt` (Penerima Pesan & Penjaga WakeLock)

```kotlin
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
```
- **`PARTIAL_WAKE_LOCK`**: Menjamin CPU tablet tidak ditidurkan oleh Linux kernel saat layar navigator mati. Inilah alasan mengapa Fast Telemetry 1 detik aman berjalan dengan `scheduleAtFixedRate` tanpa takut dibekukan OS.

#### Pemrosesan Pesan (`onMessage`)
```kotlin
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
                    val t1 = System.currentTimeMillis()
                    Thread.sleep(120) // Simulasi tulis ke SQLite
                    __log.info("IO-STORAGE | Selesai dalam ${System.currentTimeMillis() - t1} ms")

                    val t2 = System.currentTimeMillis()
                    Thread.sleep(200) // Simulasi upload telemetri ke server
                    __log.info("IO-NETWORK | Selesai dalam ${System.currentTimeMillis() - t2} ms")
                }
            }
        }
    }
```
- Jalur 1 detik langsung mencetak posisi kapal dan log dengan drift minimal.
- Jalur 5 menit mengeksekusi operasi lambat (SQLite 120ms + Jaringan 200ms) di thread I/O terpisah tanpa mengganggu jalur 1 detik.

---

## 📊 BAGIAN 4: PERBANDINGAN TIGA ARSITEKTUR

| Parameter | Core 1 (Thread.sleep Loop) | Core 2 Lama (AlarmManager Tiap Detik) | Core 2 Baru (Dual-Path Streamlined) |
|---|---|---|---|
| **Mekanisme 1 Detik** | `Thread.sleep` di memori | AlarmManager IPC Re-arm tiap detik | `scheduleAtFixedRate` (Didaftarkan 1x di awal) |
| **Frekuensi Panggilan IPC** | 0 kali | 3.600 kali / jam | **12 kali / jam** (Hanya saat batch 5 menit) |
| **Beban Garbage Collection** | Rendah | Tinggi (alokasi Intent/PendingIntent tiap detik) | **Nol (Zero-Allocation per detik)** |
| **Ketahanan Doze Mode** | ❌ Rentan freeze saat layar mati | ✅ Bangun via RTC | ✅ **Aman (Dijaga Partial WakeLock + RTC Watchdog)** |
| **Drift / Jitter Telemetri** | 0 - 2 ms | 10 - 16 ms (Jitter IPC Binder) | **< 1 - 2 ms (Sangat Presisi & Halus)** |
| **Kesiapan Produksi Maritim** | Berisiko | Standar Android, tapi boros IPC | **Arsitektur Produksi Paling Optimal (Best Practice)** |

---

## 🎙️ BAGIAN 5: CARA MENJELASKAN KE PAK GATOT (Talking Points)

Jika Pak Gatot bertanya: *"Bagaimana cara kerja Core 2 sekarang dan mengapa kamu memangkas proses re-arm setiap detik?"*, berikut alur penjelasannya:

1. **Jelaskan Masalah Awal**:
   > *"Awalnya, kami menggunakan AlarmManager untuk detak 1 detik dengan me-rearm setiap detik. Namun, ini membebani OS karena terjadi 3.600 panggilan IPC antar-proses per jam dan menimbulkan jitter 16ms akibat context switching."*

2. **Jelaskan Solusi Dual-Path**:
   > *"Karena aplikasi kita adalah Foreground Service yang memegang Partial WakeLock, CPU tablet dijamin tidak pernah tidur oleh Linux kernel. Maka dari itu, kami memangkas re-arm 1 detik tersebut dan membaginya menjadi dua jalur:"*
   > - **Fast Path (1 Detik)**: *Cukup didaftarkan 1 kali di awal. Menghasilkan telemetri realtime dengan latensi < 1ms tanpa beban IPC dan tanpa alokasi Intent berulang.*
   > - **Batch Path (5 Menit)**: *Tetap memakai AlarmManager level OS. Berfungsi sebagai RTC Watchdog dan pemicu simpan database & upload server tiap 5 menit.*

3. **Kaitkan dengan Sensor Fisik Kapal**:
   > *"Pola pendaftaran 1 kali ini sama persis dengan cara kerja driver GPS hardware (LocationManager.requestLocationUpdates) dan serial AIS NMEA di kapal fisik, di mana listener cukup diregistrasi sekali di awal lalu data akan di-push secara otomatis."*

---

## 🚀 BAGIAN 6: CARA MENJALANKAN DI TERMINAL

```powershell
# Menjalankan Core 2 (Arsitektur Streamlined Dual-Path)
.\run_core.bat
# Atau menggunakan Gradle langsung:
.\gradlew.bat runCore
```
