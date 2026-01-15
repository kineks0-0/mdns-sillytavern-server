package io.github.kineks.mdnsserver

import android.content.Context
import android.os.PowerManager
import android.util.Log

class PowerManagementUtils(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "PowerManagementUtils"

    companion object {
        const val WAKE_LOCK_TIMEOUT_30_MIN = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Acquire a wake lock to keep the CPU running.
     * @param timeout Optional timeout in milliseconds. If null, acquires indefinitely.
     */
    fun acquireWakeLock(timeout: Long? = null) {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MDNSServer::mDNSServiceWakeLock"
            )
        }

        if (!wakeLock!!.isHeld) {
            Log.d(TAG, "Acquiring wake lock (timeout: ${timeout ?: "infinite"})")
            if (timeout != null) {
                wakeLock!!.acquire(timeout)
            } else {
                wakeLock!!.acquire()
            }
        }
    }

    /**
     * Release the wake lock.
     */
    fun releaseWakeLock() {
        if (wakeLock != null && wakeLock!!.isHeld) {
            Log.d(TAG, "Releasing wake lock")
            try {
                wakeLock!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}")
            }
        }
        wakeLock = null
    }

    /**
     * Check if the wake lock is held.
     */
    fun isWakeLockHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
}
