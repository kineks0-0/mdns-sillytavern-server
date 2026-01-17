package io.github.kineks.mdnsserver

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

class PowerManagementUtils(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val TAG = "PowerManagementUtils"

    companion object {
        const val WAKE_LOCK_TIMEOUT_30_MIN = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Acquire locks to keep the CPU running and WiFi active/multicast enabled.
     * @param timeout Optional timeout in milliseconds for the WakeLock. If null, acquires indefinitely.
     */
    fun acquireLocks(timeout: Long? = null) {
        // CPU WakeLock
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MDNSServer::mDNSServiceWakeLock"
            )
        }

        // WiFi Lock (High Perf)
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "MDNSServer::mDNSServiceWifiLock"
            )
            wifiLock?.setReferenceCounted(false)
        }

        // Multicast Lock
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("MDNSServer::mDNSServiceMulticastLock")
            multicastLock?.setReferenceCounted(false)
        }

        if (wakeLock?.isHeld == false) {
            Log.d(TAG, "Acquiring wake lock (timeout: ${timeout ?: "infinite"})")
            if (timeout != null) {
                wakeLock!!.acquire(timeout)
            } else {
                wakeLock!!.acquire()
            }
        }

        if (wifiLock?.isHeld == false) {
            Log.d(TAG, "Acquiring wifi lock")
            wifiLock?.acquire()
        }

        if (multicastLock?.isHeld == false) {
            Log.d(TAG, "Acquiring multicast lock")
            multicastLock?.acquire()
        }
    }

    /**
     * Release all locks.
     */
    fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "Releasing wake lock")
            try {
                wakeLock!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}")
            }
        }

        if (wifiLock?.isHeld == true) {
            Log.d(TAG, "Releasing wifi lock")
            try {
                wifiLock!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wifi lock: ${e.message}")
            }
        }

        if (multicastLock?.isHeld == true) {
            Log.d(TAG, "Releasing multicast lock")
            try {
                multicastLock!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing multicast lock: ${e.message}")
            }
        }

        wakeLock = null
        wifiLock = null
        multicastLock = null
    }

    /**
     * Check if any lock is held (primarily WakeLock as the master indicator).
     */
    fun isLockHeld(): Boolean {
        return wakeLock?.isHeld == true
    }

    // Legacy support to minimize immediate breakage during refactor, but mapped to new logic
    fun acquireWakeLock(timeout: Long? = null) = acquireLocks(timeout)
    fun releaseWakeLock() = releaseLocks()
    fun isWakeLockHeld() = isLockHeld()
}
