package com.soba.persona.p4clockultimax;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.Calendar;
import java.util.Random;

/**
 * Service which calls the WidgetUpdater on every tick if possible
 *
 * @author Soba 08-03-2022
 */
public class ClockWidgetUpdateService extends Service {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.ClockWidgetUpdateService";

  // The hour at which the weather was last updated
  static int hour = -1;
  // The minute of the hour when weather will be updated
  static int minute = 0;

  private WidgetUpdater updater = null;
  private Calendar mCalendar;

  private final BroadcastReceiver mTimeChangedReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.v(LOGGER_TAG, "Received Intent. Checking for updates");
      checkUpdate();
    }
  };

  @Override
  public void onCreate() {
    //Called on the first creation of this service
    //Seems to get called when the app gets closed too
    super.onCreate();
    hour = -1;
    if (updater == null) {
      updater = WidgetUpdater.get();
    }

    // Pick a random minute of the hour to use for updating weather
    minute = new Random().nextInt(60);

    mCalendar = Calendar.getInstance();

    // Register broadcast receiver to listen for time changes
    // Also listen for the screen turning on because sometimes the widget
    // won't update with the screen off
    Log.v(LOGGER_TAG, "Registering receiver");
    IntentFilter sIntentFilter = new IntentFilter();
    sIntentFilter.addAction(Intent.ACTION_TIME_TICK);
    sIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    sIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
    sIntentFilter.addAction(Intent.ACTION_USER_PRESENT);
    sIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(mTimeChangedReceiver, sIntentFilter);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.v(LOGGER_TAG, "Unregistering receiver");
    unregisterReceiver(mTimeChangedReceiver);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    //Called multiple times. Essentially once per update of configuration settings
    Log.v(LOGGER_TAG, "onStartCommand");

    Bundle extras = null;
    if (intent != null) {
      extras = intent.getExtras();
    }

    if (extras != null && extras.getBoolean(AppStrings.FORCE, false)) {
      updater.updateLocation(
        this.getApplicationContext(),
        nothing -> updater.updateWeather(this.getApplicationContext())
      );
    }
    else {
      checkUpdate();
    }
    return START_STICKY;
  }

  private void checkUpdate() {
    mCalendar.setTimeInMillis(System.currentTimeMillis());
    int h = mCalendar.get(Calendar.HOUR_OF_DAY);
    int m = mCalendar.get(Calendar.MINUTE);

    //Only update the weather when hour is different and we hit the correct minute offset
    if (hour != h && m >= minute) {
      updater.updateLocation(
        this.getApplicationContext(),
        nothing -> updater.updateWeather(this.getApplicationContext())
      );
      hour = h;
    }
    else {
      Log.v(LOGGER_TAG, "Updating Date/Time");
      updater.updateView(this.getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}

