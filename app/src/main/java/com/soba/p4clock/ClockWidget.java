package com.soba.p4clock;

import android.Manifest;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Calendar;
import java.util.Random;

/**
 * Logic controlling the Widget, including updates
 *
 * @author Soba on 2022-08-01.
 */
public class ClockWidget extends AppWidgetProvider {
  private static final String LOGGER_TAG = "com.soba.p4clock.ClockWidget";

  private void runService(Context context, boolean force) {
    Intent i = new Intent(context, UpdateService.class);
    if (force) {
      i.putExtra(AppStrings.FORCE, force);
    }
    context.startService(i);
  }

  /**
   * onEnabled is called on instantiation of the first widget
   */
  public void onEnabled(Context context) {
    super.onEnabled(context);
    Log.v(LOGGER_TAG, "onEnabled: Widget added");
    runService(context, false);
  }

  /**
   * onReceive is called for every intent that we listen for
   */
  public void onReceive(Context context, Intent intent) {
    //Called whenever settings get changed
    //Essentially, the controls call this to update the Service settings
    super.onReceive(context, intent);
    Log.v(LOGGER_TAG, "Intent Received: " + intent.getAction());
    if (AppStrings.UPDATE.equals(intent.getAction())) {
      boolean force = intent.getBooleanExtra(AppStrings.FORCE, false);
      runService(context, force);
    }
  }

  /**
   * onUpdate is called at every {updatePeriodMillis} ms, and also when a widget is added
   */
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    //Only called on the creation of the widget
    super.onUpdate(context, appWidgetManager, appWidgetIds);

    Log.v(LOGGER_TAG, "onUpdate called");
    // runService(context, true);
  }

  /**
   * The reverse of onUpdate, this is called when a widget is removed
   */
  public void onDeleted(Context context, int[] appWidgetIds) {
    super.onDeleted(context, appWidgetIds);
  }

  /**
   * The reverse of onEnabled, called when the last widget is removed
   */
  public void onDisabled(Context context) {
    super.onDisabled(context);
    Log.v(LOGGER_TAG, "Widget disabled");
    context.stopService(new Intent(context, UpdateService.class));
  }

  public static final class UpdateService extends Service implements LocationListener {
    private static final String LOGGER_TAG = "com.soba.p4clock.ClockWidgetUpdateService";
    private final static IntentFilter sIntentFilter;
    // The hour at which the weather was last updated
    static int hour = -1;
    // The minute of the hour when weather will be updated
    static int minute = 0;

    static {
      sIntentFilter = new IntentFilter();
      sIntentFilter.addAction(Intent.ACTION_TIME_TICK);
      sIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      sIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
      sIntentFilter.addAction(Intent.ACTION_USER_PRESENT);
      sIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
    }

    int lat = -1;
    int lon = -1;
    boolean located = false;
    boolean setLocation = false;
    boolean disabled = false;
    SharedPreferences prefs = null;
    WidgetUpdater updater = null;
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
        updater = new WidgetUpdater();
      }

      minute = new Random().nextInt(60);
      mCalendar = Calendar.getInstance();
      //updateDateAndTime();
      Log.v(LOGGER_TAG, "Registering receiver");
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
      if (prefs == null) {
        prefs = getSharedPreferences(AppStrings.COM_NAME, MODE_PRIVATE);
      }

      setLocation = !prefs.getBoolean(AppStrings.USE_LOC, true);
      disabled = prefs.getBoolean(AppStrings.DISABLED, false);

      if (setLocation
          && prefs.contains(AppStrings.LAT)
          && prefs.contains(AppStrings.LON)) {
        lat = prefs.getInt(AppStrings.LAT, 0);
        lon = prefs.getInt(AppStrings.LON, 0);
        located = true;
      }

      Bundle extras = null;
      if (intent != null) {
        extras = intent.getExtras();
      }

      if (extras != null && extras.getBoolean(AppStrings.FORCE, false)) {
        updateDateAndTime();
        updateWeather();
      }
      else {
        checkUpdate();
      }
      return START_STICKY;
    }

    private void updateDateAndTime() {
      mCalendar.setTimeInMillis(System.currentTimeMillis());
      int h = mCalendar.get(Calendar.HOUR_OF_DAY);
      updater.updateDateAndTime(this.getApplicationContext(), h);
    }

    private void updateWeather() {
      //If weather updates are disabled
      if (disabled) {
        return;
      }

      Log.v(LOGGER_TAG, "Updating Weather");
      if (!setLocation) {
        Log.v(LOGGER_TAG, "Getting Location");
        //IDK if this actually changes anything, but try to get location from network provider first, then gps
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager man = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            long time_update = 30 * 60 * 1000;
            long dist_update = 10;
            man.requestLocationUpdates(LocationManager.GPS_PROVIDER, time_update, dist_update, this);
            Location mLastLocation = man.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (mLastLocation != null) {
              lat = (int) mLastLocation.getLatitude();
              lon = (int) mLastLocation.getLongitude();
              located = true;
            }
          }
          //Else no access to either :(
        }
        else {
          LocationManager man = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
          long time_update = 30 * 60 * 1000;
          long dist_update = 10;
          man.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, time_update, dist_update, this);
          Location mLastLocation = man.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
          if (mLastLocation != null) {
            lat = (int) mLastLocation.getLatitude();
            lon = (int) mLastLocation.getLongitude();
            located = true;
          }
        }
      }

      if (located) {
        Log.v(LOGGER_TAG, "Location found. Calling Updater");
        updater.updateWeather(this.getApplicationContext(), lat, lon);
      }
    }

    private void checkUpdate() {
      mCalendar.setTimeInMillis(System.currentTimeMillis());
      int h = mCalendar.get(Calendar.HOUR_OF_DAY);
      int m = mCalendar.get(Calendar.MINUTE);

      Log.v(LOGGER_TAG, "Updating Date/Time");
      updateDateAndTime();

      //Only update the weather when hour is different and we hit the correct minute offset
      if (hour != h && m >= minute) {
        updateWeather();
        hour = h;
      }
    }

    @Override
    public void onLocationChanged(Location location) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }
}
