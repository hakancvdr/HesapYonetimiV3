package com.example.hesapyonetimi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.widget.Toast

class OdemeIslemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bildirimId = intent.getIntExtra("bildirim_id", -1)
        val baslik = intent.getStringExtra("baslik") ?: "Ödeme"

        if (bildirimId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(bildirimId)
            
            Toast.makeText(context, "$baslik işaretlendi", Toast.LENGTH_SHORT).show()
        }
    }
}