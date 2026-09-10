---
description:
# 🚀 Aturan Koding Sederhana & Praktis (KISS & Core Conventions)

Aturan sistem ini dirancang agar kode tetap **ringkas, terstruktur rapi, mudah diingat, dan langsung to-the-point** tanpa arsitektur yang berbelit-belit (overengineering), selaras dengan arsitektur `com.example.core`.

---

## 1. Aturan File & Class (One Class Per File)
- **1 Class = 1 File:** Setiap file `.kt` hanya boleh mendefinisikan satu class utama atau satu data model utama.
- **Nama File Identik dengan Class:** Nama file wajib sama persis dengan nama class yang ada di dalamnya (contoh: class `RTC_Engine` berada di file `RTC_Engine.kt`, data class `NAV_AisDataModel` berada di file `NAV_AisDataModel.kt`).

---

## 2. Aturan Naming Convention & Prefix Modul
Gunakan prefix huruf kapital yang mencerminkan fungsi modul untuk setiap Class dan Data Model:

- **`RTC_`** : Real-Time Clock / Timer / Scheduler (Contoh: `RTC_Engine`, `RTC_Tick`, `RTC_AlarmReceiver`)
- **`FS_`**  : Foreground Service / Background Worker / Task Runner (Contoh: `FS_ForegroundService`, `FS_Ais`)
- **`NAV_`** : Navigation / Data Sensor / AIS (Contoh: `NAV_AisDataModel`, `collectAisNav`)
- **`DB_`**  : Database / Storage / Cache
- **`NET_`** : Network / API / Telemetri Sync

**Format Nama:**
- **Class / Model:** `PREFIX_NamaClass` (PascalCase, contoh: `RTC_Engine`, `FS_ForegroundService`, `NAV_AisDataModel`).
- **Method / Function:** `camelCase` deskriptif dan to-the-point (contoh: `setup()`, `runLifeCycle()`, `processBatch()`, `destroy()`, `formatDisplay()`).

---

## 3. Aturan Visibilitas & Underscore (Strict Convention)
Terapkan konvensi underscore secara ketat berdasarkan tingkat aksesibilitas (visibility modifier):

- **`private` (Double Underscore `__`):**
  Semua variabel, fungsi, atau thread lokal yang bersifat private **wajib** diawali dengan double underscore.
  - *Contoh Variabel:* `private var __running = false`, `private val __ioPool = ...`, `private var __wakeLock: PowerManager.WakeLock? = null`
  - *Contoh Fungsi:* `private fun __startFastClock()`, `private fun __acquireWakeLock()`, `private fun __dispatch()`

- **`protected` / `internal` (Single Underscore `_`):**
  Semua member yang hanya diakses oleh turunan atau antar class dalam modul internal **wajib** diawali dengan single underscore.
  - *Contoh Variabel:* `internal var _activeInstance: RTC_Engine? = null`
  - *Contoh Fungsi:* `internal fun _handleAlarmTick(...)`, `protected fun _onTickProcessed(...)`

- **`public` (Tanpa Underscore):**
  Semua API, fungsi publik, dan properti yang diekspos ke luar **tidak boleh** menggunakan underscore.
  - *Contoh Properti:* `val tickQueue = ...`, `var isFsReady = false`
  - *Contoh Fungsi:* `fun setup(context: Context)`, `fun runLifeCycle()`, `fun destroy()`, `fun registerTask(...)`

---

## 4. Logic Sesimpel Mungkin (KISS - Keep It Simple, Pragmatic)
- **Hindari Overengineering:** Jangan gunakan layer/abstraksi berlebihan (seperti Ports & Adapters berlapis-lapis, branded types rumit, atau wrapper Result monad) jika kode langsung sudah jelas dan aman.
- **Pola Lifecycle 3 Langkah:** Untuk engine atau service, gunakan alur standar yang seragam:
  1. `setup(context)`: Inisialisasi resource, thread, dan lock.
  2. `runLifeCycle()`: Eksekusi loop / queue pemrosesan utama.
  3. `destroy()`: Bersihkan executor, interrupt thread, dan release lock.
- **Multithreading / Async Sederhana:** Gunakan executor pool standar (`Executors.newSingleThreadExecutor`, `LinkedBlockingQueue`) dengan penamaan thread yang deskriptif.

---

## 5. Data Model & Error Handling Sederhana
- **Data Model:** Gunakan `data class` Kotlin murni dengan tipe primitif standar (`Double`, `Long`, `String`, `Boolean`). Helper method ringkas dapat langsung disematkan di dalam data class (misal: `formatDisplay()`).
- **Error Handling:** Gunakan `try-catch` standar pada operasi I/O, network, atau parsing data. Lindungi loop utama agar tidak mudah mati tanpa perlu membungkus semua baris dengan abstraksi kompleks.
---
