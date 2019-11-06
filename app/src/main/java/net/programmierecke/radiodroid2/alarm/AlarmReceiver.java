package net.programmierecke.radiodroid2.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import net.programmierecke.radiodroid2.BuildConfig;
import net.programmierecke.radiodroid2.IPlayerService;
import net.programmierecke.radiodroid2.service.PlayerService;
import net.programmierecke.radiodroid2.R;
import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.station.DataRadioStation;

import okhttp3.OkHttpClient;

public class AlarmReceiver extends BroadcastReceiver {
    String url;
    int alarmId;
    DataRadioStation station;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private final String TAG = "RECV";
    static int BACKUP_NOTIFICATION_ID = 2;
    static String BACKUP_NOTIFICATION_NAME = "backup-alarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(BuildConfig.DEBUG) { Log.d(TAG,"received broadcast"); }
        aquireLocks(context);
        
        Toast toast = Toast.makeText(context, context.getResources().getText(R.string.alert_alarm_working), Toast.LENGTH_SHORT);
        toast.show();

        alarmId = intent.getIntExtra("id",-1);
        if(BuildConfig.DEBUG) { Log.d(TAG,"alarm id:"+alarmId); }

        RadioDroidApp radioDroidApp = (RadioDroidApp)context.getApplicationContext();
        RadioAlarmManager ram = radioDroidApp.getAlarmManager();
        station = ram.getStation(alarmId);
        ram.resetAllAlarms();

        if (station != null && alarmId >= 0) {
            if(BuildConfig.DEBUG) { Log.d(TAG,"radio id:"+alarmId); }
            Play(context, station.StationUuid);
        }else{
            toast = Toast.makeText(context, context.getResources().getText(R.string.alert_alarm_not_working), Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private void aquireLocks(Context context) {
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmReceiver:");
        }
        if (!wakeLock.isHeld()) {
            if(BuildConfig.DEBUG) { Log.d(TAG,"acquire wakelock"); }
            wakeLock.acquire();
        }
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            if (wifiLock == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AlarmReceiver");
                } else {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "AlarmReceiver");
                }
            }
            if (!wifiLock.isHeld()) {
                if(BuildConfig.DEBUG) { Log.d(TAG,"acquire wifilock"); }
                wifiLock.acquire();
            }
        }else{
            Log.e(TAG,"could not acquire wifi lock");
        }
    }

    private void releaseLocks() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
            if(BuildConfig.DEBUG) { Log.d(TAG,"release wakelock"); }
        }
        if (wifiLock != null) {
            wifiLock.release();
            wifiLock = null;
            if(BuildConfig.DEBUG) { Log.d(TAG,"release wifilock"); }
        }
    }

    IPlayerService itsPlayerService;
    private ServiceConnection svcConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            if(BuildConfig.DEBUG) { Log.d(TAG, "Service came online"); }
            itsPlayerService = IPlayerService.Stub.asInterface(binder);
            try {
                station.playableUrl = url;
                itsPlayerService.SetStation(station);
                graduallyIncreaseAlarmVolume(itsPlayerService);
                itsPlayerService.Play(true);
                // default timeout 1 hour
                itsPlayerService.addTimer(timeout*60);
            } catch (RemoteException e) {
                Log.e(TAG,"play error:"+e);
            }

            releaseLocks();
        }

        public void onServiceDisconnected(ComponentName className) {
            if(BuildConfig.DEBUG) { Log.d(TAG, "Service offline"); }
            itsPlayerService = null;
        }
    };

    int timeout = 10;

    private void Play(final Context context, final String stationId) {
        RadioDroidApp radioDroidApp = (RadioDroidApp) context.getApplicationContext();
        final OkHttpClient httpClient = radioDroidApp.getHttpClient();

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String result = null;
                for (int i=0;i<20;i++){
                    result = Utils.getRealStationLink(httpClient, context, stationId);
                    if (result != null){
                        return result;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG,"Play() "+e);
                    }
                }
                return result;
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    url = result;

                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean play_external = sharedPref.getBoolean("alarm_external", false);
                    String packageName = sharedPref.getString("shareapp_package",null);
                    String activityName = sharedPref.getString("shareapp_activity",null);
                    try {
                        timeout = Integer.parseInt(sharedPref.getString("alarm_timeout", "10"));
                    }catch(Exception e){
                        timeout = 10;
                    }
                    try {
                        if (play_external && packageName != null && activityName != null){
                            Intent share = new Intent(Intent.ACTION_VIEW);
                            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            share.setClassName(packageName,activityName);
                            share.setDataAndType(Uri.parse(url), "audio/*");
                            context.startActivity(share);
                            if (wakeLock != null) {
                                wakeLock.release();
                                wakeLock = null;
                            }
                            if (wifiLock != null) {
                                wifiLock.release();
                                wifiLock = null;
                            }
                        } else {
                            Intent anIntent = new Intent(context, PlayerService.class);
                            context.getApplicationContext().bindService(anIntent, svcConn, context.BIND_AUTO_CREATE);
                            context.getApplicationContext().startService(anIntent);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting alarm intent "+e);
                        PlaySystemAlarm(context);
                    }
                } else {
                    Log.e(TAG, "Could not connect to radio station");
                    Toast toast = Toast.makeText(context, context.getResources().getText(R.string.error_station_load), Toast.LENGTH_SHORT);
                    toast.show();
                    PlaySystemAlarm(context);
                    if (wakeLock != null) {
                        wakeLock.release();
                        wakeLock = null;
                    }
                    if (wifiLock != null) {
                        wifiLock.release();
                        wifiLock = null;
                    }
                }
                super.onPostExecute(result);
            }
        }.execute();
    }

    private void graduallyIncreaseAlarmVolume(IPlayerService playerService) {
        String foo = "VOLUME";
//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
//        int slowWakeMillis = sharedPref.getInt("gradually_increase_volume", 0) * 10000;
        int slowWakeMillis = 120000;

        if (slowWakeMillis == 0) {
            if (BuildConfig.DEBUG) { Log.d(foo, "Gradual alarm volume disabled"); }
            return;
        }

        float maxVolume = 100f;
        float minVolume = 0f;
        float volumeRange = maxVolume - minVolume;

        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            long triggerMillis = 0;
            long elapsedMillis = 0;
            float currentVolume = minVolume;
            @Override
            public void run() {
                if(BuildConfig.DEBUG) { Log.d(foo, "Increase volume loop"); }

                boolean isPlaying;
                try {
                    isPlaying = playerService.isPlaying();
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) { Log.d(foo, "Couldn't get isPlaying"); }
                    isPlaying = false;
                }

                if (elapsedMillis > 30000 && !isPlaying) {
                    if (BuildConfig.DEBUG) { Log.d(foo, "No longer playing stopping loop"); }
                    handler.removeCallbacks(this);
                    return;
                } else if (!isPlaying) {
                    elapsedMillis = System.currentTimeMillis() - triggerMillis;
                    handler.postDelayed(this, 1000);
                } else {
                    if (triggerMillis == 0) {
                        triggerMillis = System.currentTimeMillis();
                        try {
                            if (BuildConfig.DEBUG) { Log.d(foo, "Setting volume to "+minVolume); }
                            playerService.SetVolume(minVolume);
                        } catch (Exception e) {
                            Log.e(foo, "Couldn't set volume "+e);
                        }
                    }

                    elapsedMillis = System.currentTimeMillis() - triggerMillis;

                    float slowWakeProgress = (float) elapsedMillis / slowWakeMillis;

                    if (currentVolume < maxVolume) {
                        float newVolume = minVolume + Math.min(maxVolume, slowWakeProgress * volumeRange);
                        if (newVolume != currentVolume) {
                            try {
                                newVolume = newVolume / 100;
                                if (BuildConfig.DEBUG) {
                                    Log.d(foo, "Setting volume to " + newVolume);
                                }
                                itsPlayerService.SetVolume(newVolume);
                            } catch (Exception e) {
                                Log.e(foo, "Couldn't set volume " + e);
                            }
                            currentVolume = newVolume;
                        }
                        handler.postDelayed(this, 1000);
                    } else {
                        handler.removeCallbacks(this);
                    }
                }
            }
        };
        handler.post(runnable);
    }

    private void PlaySystemAlarm(Context context) {
        if(BuildConfig.DEBUG) { Log.d(TAG, "Starting system alarm"); }

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.alarm_backup);
            String description = context.getString(R.string.alarm_back_desc);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(BACKUP_NOTIFICATION_NAME, name, importance);
            channel.setDescription(description);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            channel.setSound(soundUri, audioAttributes);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, BACKUP_NOTIFICATION_NAME)
                .setSmallIcon(R.drawable.ic_access_alarms_black_24dp)
                .setContentTitle(context.getString(R.string.action_alarm))
                .setContentText(context.getString(R.string.alarm_fallback_info))
                .setDefaults(Notification.DEFAULT_SOUND)
                .setSound(soundUri)
                .setAutoCancel(true);

        notificationManager.notify(BACKUP_NOTIFICATION_ID, mBuilder.build());
    }
}
