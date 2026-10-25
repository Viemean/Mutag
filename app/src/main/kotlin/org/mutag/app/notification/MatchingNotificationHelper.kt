package org.mutag.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.mutag.app.MainActivity
import org.mutag.app.R

/**
 * 批量匹配后台运行通知与存疑结果弹窗提醒工具。
 */
object MatchingNotificationHelper {

    const val CHANNEL_ID_PROGRESS = "mutag_matching_progress_v2"
    const val CHANNEL_ID_ALERTS = "mutag_matching_alerts_v2"

    const val NOTIFICATION_ID_PROGRESS = 1001
    const val NOTIFICATION_ID_ALERT = 1002

    const val EXTRA_OPEN_UNCERTAIN = "extra_open_uncertain"

    /**
     * 注册批量匹配通知渠道配置（进度与弹窗提醒）。
     *
     * @param context Android 上下文实例
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 清理旧有缓存渠道配置
            nm.deleteNotificationChannel("mutag_matching_progress")
            nm.deleteNotificationChannel("mutag_matching_alerts")

            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "批量匹配进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示批量匹配与写入标签的实时进度"
                enableVibration(false)
                setSound(null, null)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "存疑匹配提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "批量匹配遇到低置信度存疑或失败曲目时的弹窗提醒"
                enableVibration(true)
            }

            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    /**
     * 发送或刷新批量匹配实时进度前台通知。
     *
     * @param context Android 上下文实例
     * @param current 当前已处理曲目计数
     * @param total 待处理曲目总数
     * @param songTitle 当前正在处理的歌曲名称
     */
    fun showProgress(context: Context, current: Int, total: Int, songTitle: String) {
        ensureChannels(context)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(createNotificationLargeIcon(context))
                .setColor(0xFFD0BCFF.toInt())
                .setContentTitle("正在匹配标签 ($current/$total)")
                .setContentText(songTitle.ifBlank { "正在处理..." })
                .setProgress(total, current, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授予通知权限时静默忽略
        }
    }

    /**
     * 取消批量匹配进度常驻通知。
     *
     * @param context Android 上下文实例
     */
    fun cancelProgress(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
        } catch (_: Exception) {
            // 安全取消通知
        }
    }

    /**
     * 弹出批量匹配存疑与失败曲目的提醒通知。
     *
     * @param context Android 上下文实例
     * @param uncertainCount 低置信度存疑曲目数量
     * @param failedCount 匹配失败曲目数量
     */
    fun showAlert(context: Context, uncertainCount: Int, failedCount: Int) {
        ensureChannels(context)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UNCERTAIN, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val totalIssues = uncertainCount + failedCount
            val text = when {
                uncertainCount > 0 && failedCount > 0 -> "发现 $uncertainCount 首存疑，另有 $failedCount 首未命中"
                uncertainCount > 0 -> "发现 $uncertainCount 首版本存疑曲目，点击核对"
                else -> "有 $failedCount 首曲目未找到匹配结果"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(createNotificationLargeIcon(context))
                .setColor(0xFFD0BCFF.toInt())
                .setContentTitle("批量匹配完成 (需核对 $totalIssues 首)")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授予通知权限时静默忽略
        }
    }

    /**
     * 取消批量匹配存疑提醒通知。
     *
     * @param context Android 上下文实例
     */
    fun cancelAlert(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ALERT)
        } catch (_: Exception) {
            // 安全取消通知
        }
    }

    /**
     * 根据当前剩余存疑与失败曲目数量更新或取消提醒通知。
     *
     * @param context Android 上下文实例
     * @param uncertainCount 剩余存疑曲目数量
     * @param failedCount 剩余失败曲目数量
     */
    fun updateAlertOrCancel(context: Context, uncertainCount: Int, failedCount: Int = 0) {
        if (uncertainCount <= 0 && failedCount <= 0) {
            cancelAlert(context)
        } else {
            showAlert(context, uncertainCount, failedCount)
        }
    }

    private fun createNotificationLargeIcon(context: Context): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1D1B20.toInt()
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 40f, 40f, paint)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification)?.mutate()
        if (drawable != null) {
            drawable.setTint(0xFFD0BCFF.toInt())
            val padding = 36
            drawable.setBounds(padding, padding, size - padding, size - padding)
            drawable.draw(canvas)
        }

        return bitmap
    }
}
