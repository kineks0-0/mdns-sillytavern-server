package io.github.kineks.mdnsserver

import android.content.Context
import android.os.PowerManager
import android.util.Log

class PowerManagementUtils(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "PowerManagementUtils"

    companion object {
        private const val WAKE_LOCK_TIMEOUT = 30 * 60 * 1000L // 30分钟超时
    }

    /**
     * 获取唤醒锁，保持CPU运行以处理mDNS请求
     */
    fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MDNSServer::mDNSServiceWakeLock"
            )
        }

        if (!wakeLock!!.isHeld) {
            Log.d(TAG, "获取唤醒锁以保持服务运行")
            wakeLock!!.acquire(WAKE_LOCK_TIMEOUT)
        }
    }

    /**
     * 释放唤醒锁
     */
    fun releaseWakeLock() {
        if (wakeLock != null && wakeLock!!.isHeld) {
            Log.d(TAG, "释放唤醒锁")
            wakeLock!!.release()
        }
        wakeLock = null
    }

    /**
     * 检查是否持有唤醒锁
     */
    fun isWakeLockHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
}