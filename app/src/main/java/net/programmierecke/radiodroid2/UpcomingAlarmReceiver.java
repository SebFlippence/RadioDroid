package net.programmierecke.radiodroid2;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.programmierecke.radiodroid2.BuildConfig;
import net.programmierecke.radiodroid2.R;

public class UpcomingAlarmReceiver extends BroadcastReceiver {
    static int NOTIFICATION_ID = 1;
    static String NOTIFICATION_NAME = "upcoming-alarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(BuildConfig.DEBUG) { Log.d(this.getClass().getSimpleName(),"received broadcast"); }
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.alarm_upcoming);
            String description = context.getString(R.string.alarm_upcoming_desc);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_NAME, name, importance);
            channel.setDescription(description);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        //Define Notification Manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int alarmId = intent.getIntExtra("id",-1);

        Intent upcomingAlarmReceiver = new Intent(context, SkipUpcomingAlarmReceiver.class);
        upcomingAlarmReceiver.putExtra("id", alarmId);
        PendingIntent skipUpcomingAlarmIntent = PendingIntent.getBroadcast(context, alarmId, upcomingAlarmReceiver, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, NOTIFICATION_NAME)
                .setSmallIcon(R.drawable.ic_access_alarms_black_24dp)
                .setContentTitle(context.getString(R.string.alarm_upcoming))
                .setContentText(context.getString(R.string.alarm_upcoming_expand_to_skip))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_access_alarms_black_24dp, context.getString(R.string.alarm_skip_upcoming), skipUpcomingAlarmIntent);

        //Display notification
        notificationManager.notify(this.NOTIFICATION_ID, mBuilder.build());
    }
}
