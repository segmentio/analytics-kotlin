package com.segment.analytics.next

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MyFirebaseService : FirebaseMessagingService() {

    val TAG = "MyFirebaseService"

    override fun onNewToken(s: String) {
        Log.e(TAG, s)
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result.token
                Log.e(TAG, token)
            })
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("PRAY", "push notification received")
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = data["title"]
        val body = data["content"]

        MainApplication.analytics.track("Push Notification Received", buildJsonObject {
            val campaign = buildJsonObject {
                put("medium", "Push")
                put("source", "FCM")
                put("name", title)
                put("content", body)
            }
            put("campaign", campaign)
        })

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("push_notification", true)
        }
        val pi = PendingIntent.getActivity(applicationContext, 101, intent, 0)
        val nm = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        var channel: NotificationChannel? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val att = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            channel = NotificationChannel("222", "my_channel", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, "222")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(body)
            .setAutoCancel(true)
            .setContentText(title)
            .setContentIntent(pi)
            .setDeleteIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        nm.notify(123456789, builder.build())
    }
}