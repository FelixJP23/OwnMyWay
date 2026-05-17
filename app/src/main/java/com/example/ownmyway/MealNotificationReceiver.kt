package com.example.ownmyway

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class MealNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEAL_TYPE = "meal_type"
        const val MEAL_LUNCH      = "lunch"
        const val MEAL_DINNER     = "dinner"
        const val CHANNEL_ID      = "ownmyway_meals"

        fun scheduleLunch(context: Context) {
            schedule(context, MEAL_LUNCH, 11, 0, 1001)
        }

        fun scheduleDinner(context: Context) {
            schedule(context, MEAL_DINNER, 19, 0, 1002)
        }

        fun cancelAll(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            listOf(1001, 1002).forEach { reqCode ->
                val intent = Intent(context, MealNotificationReceiver::class.java)
                val pi = PendingIntent.getBroadcast(context, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarmManager.cancel(pi)
            }
        }

        private fun schedule(context: Context, mealType: String, hour: Int, minute: Int, reqCode: Int) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val intent = Intent(context, MealNotificationReceiver::class.java).apply {
                putExtra(EXTRA_MEAL_TYPE, mealType)
            }
            val pi = PendingIntent.getBroadcast(context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Sugestões de Refeições",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Sugestões de almoço e jantar durante a viagem" }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        createChannel(context)
        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE) ?: return
        val isLunch  = mealType == MEAL_LUNCH

        val title   = if (isLunch) "🍽️ Está quase na hora do almoço!" else "🌙 Está quase na hora do jantar!"
        val message = if (isLunch)
            "Vamos repor as energias! Toque para encontrar um restaurante perto de você."
        else
            "Vamos terminar bem o dia! Toque para encontrar um restaurante perto de você."

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("meal_suggestion", mealType)
        }
        val pi = PendingIntent.getActivity(context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF4A2080.toInt())
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(if (isLunch) 2001 else 2002, notification)
    }
}
