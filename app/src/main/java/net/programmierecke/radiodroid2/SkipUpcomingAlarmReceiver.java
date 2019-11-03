package net.programmierecke.radiodroid2;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import net.programmierecke.radiodroid2.BuildConfig;
import net.programmierecke.radiodroid2.R;

public class SkipUpcomingAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(BuildConfig.DEBUG) { Log.d(this.getClass().getSimpleName(),"received broadcast"); }

        int alarmId = intent.getIntExtra("id",-1);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("alarm_skipped_"+alarmId, true);
        editor.commit();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(UpcomingAlarmReceiver.NOTIFICATION_ID);

        Toast toast = Toast.makeText(context, context.getString(R.string.alarm_next_skipped), Toast.LENGTH_SHORT);
        toast.show();
    }
}
