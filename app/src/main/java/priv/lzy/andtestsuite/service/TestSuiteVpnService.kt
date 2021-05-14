package priv.lzy.andtestsuite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.megatronking.netbare.NetBareService
import priv.lzy.andtestsuite.ATSApplication
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.activity.PackageCaptureActivity
import java.net.Socket

class TestSuiteVpnService : NetBareService() {
    companion object {
        private val CHANNEL_ID = ATSApplication.getInstance().applicationContext.packageName + ".NOTIFICATION_CHANNEL_ID"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID,
                    getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    override fun protect(socket: Socket?): Boolean {
        return super.protect(socket)
    }

    override fun notificationId(): Int {
        return 100
    }

    override fun createNotification(): Notification {
        val intent = Intent(this, PackageCaptureActivity::class.java)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.action = Intent.ACTION_MAIN
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT)
        //TODO 替换图标等信息
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.app_name))
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}