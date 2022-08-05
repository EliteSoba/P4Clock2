package com.soba.persona.p4clockultimax;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Service which calls the WidgetUpdater on every tick if possible
 *
 * @author Soba 08-03-2022
 */
public class ClockWidgetUpdateService extends Service {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.ClockWidgetUpdateService";

  private WidgetUpdater updater = null;

  private TimeChangeReceiver timeChangeReceiver;

  boolean registered;
  IntentFilter sIntentFilter;

  /**
   * Called on the first creation of this service
   */
  @Override
  public void onCreate() {
    super.onCreate();
    if (updater == null) {
      updater = WidgetUpdater.get();
    }

    // Register broadcast receiver to listen for time changes
    // Also listen for the screen turning on because sometimes the widget
    // won't update with the screen off
    Log.v(LOGGER_TAG, "Registering receiver");
    sIntentFilter = new IntentFilter();
    sIntentFilter.addAction(Intent.ACTION_TIME_TICK);
    sIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    sIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
    sIntentFilter.addAction(Intent.ACTION_USER_PRESENT);
    sIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
    timeChangeReceiver = new TimeChangeReceiver(ignored -> this.doUpdate(false));
    registerReceiver(timeChangeReceiver, sIntentFilter);
    registered = true;
  }

  /**
   * Called whenever this service is destroyed, either from the Android system killing it
   * or if all widgets have been removed
   */
  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.v(LOGGER_TAG, "Unregistering receiver");
    unregisterReceiver(timeChangeReceiver);
    registered = false;
  }

  /**
   * Called each time the service is started.
   * Simply a middleman to the WidgetUpdater
   */
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.v(LOGGER_TAG, "onStartCommand");

    if (!registered) {
      registerReceiver(timeChangeReceiver, sIntentFilter);
      registered = true;
    }

    Bundle extras = null;
    if (intent != null) {
      extras = intent.getExtras();
    }

    boolean force = false;

    if (extras != null && extras.getBoolean(AppConstants.FORCE, false)) {
      force = true;
    }

    doUpdate(force);
    return START_STICKY;
  }

  /**
   * Helper function that calls the widget updater.
   * Needs to be a separate function for TimeChangeReceiver to handle it in the passed callback
   */
  private void doUpdate(boolean force) {
    updater.doUpdate(this.getApplicationContext(), force);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}

