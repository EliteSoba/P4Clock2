package com.soba.persona.p4clockultimax;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Singleton updater for the widget
 *
 * @author Soba 08-03-2022
 */
public class WidgetUpdater {
  private static WidgetUpdater instance;

  /**
   * Gets the singleton WidgetUpdater
   */
  public static WidgetUpdater get() {
    if (instance == null) {
      instance = new WidgetUpdater();
    }
    return instance;
  }

  private static final String LOGGER_TAG = "com.soba.persona.p4clock.WidgetUpdater";

  private static final String[] LOCATION_PROVIDERS = new String[] {
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
  };

  // Providers for updating date, time, and weather
  private FusedLocationProviderClient fusedLocationClient;
  private final Calendar mCalendar;
  private GetWeatherTaskExecutor executor;

  // Font faces for the date and day
  private Typeface dateFont = null;
  private Typeface dayOfWeekFont = null;

  // Last day/hour updated, to theoretically cache updates
  // but these get set even if the assets don't get updated so they're ignored
  private int curHour;
  private int curDay;

  // Location information
  SharedPreferences prefs;
  private int lat;
  private int lon;
  private boolean located;

  private WidgetUpdater() {
    mCalendar = Calendar.getInstance();
    executor = new GetWeatherTaskExecutor();

    curHour = -1;
    curDay = -1;

    prefs = null;
    lat = -1;
    lon = -1;
    located = false;
  }

  public int getCurHour() {
    return curHour;
  }

  public int getCurDay() {
    return curDay;
  }

  public void setLocation(int lat, int lon) {
    this.lat = lat;
    this.lon = lon;
    this.located = true;
    Log.v(LOGGER_TAG, "Location set to (" + lat + ", " + lon + ")");
  }

  private int buildWeatherIcon(int weatherCode) {
    int icon;
    switch (weatherCode / 100) {
      case 2:
        icon = R.drawable.thunderstorms;
        break;
      case 3:
      case 5:
        icon = R.drawable.rain;
        break;
      case 6:
        icon = R.drawable.snow;
        break;
      case 7:
        icon = R.drawable.fog;
        break;
      case 8:
        icon = R.drawable.clear;
        break;
      default:
        icon = R.drawable.unknown;
    }
    if (weatherCode > 800) {
      icon = R.drawable.cloudy;
    }
    if (weatherCode >= 900) {
      icon = R.drawable.unknown;
    }

    return icon;
  }

  private int buildTime(int hour) {
    if (hour == 0) {
      return R.drawable.midnight;
    }
    else if (hour < 5) {
      return R.drawable.night;
    }
    else if (hour < 8) {
      return R.drawable.earlymorning;
    }
    else if (hour < 12) {
      return R.drawable.morning;
    }
    else if (hour < 14) {
      return R.drawable.lunchtime;
    }
    else if (hour < 18) {
      return R.drawable.afternoon;
    }
    else if (hour < 22) {
      return R.drawable.evening;
    }
    else {
      return R.drawable.night;
    }
  }

  private Bitmap buildDayDate(Context context, String date, String day) {
    if (this.dateFont == null) {
      this.dateFont = Typeface.createFromAsset(context.getAssets(), "fonts/Days.ttf");
    }
    if (this.dayOfWeekFont == null) {
      this.dayOfWeekFont = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoCondensed-Regular.ttf");
    }
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setSubpixelText(true);
    paint.setTypeface(this.dateFont);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.WHITE);
    paint.setTextSize(100);
    paint.setLetterSpacing(0.25f);

    Paint paint2 = new Paint();
    paint2.setAntiAlias(true);
    paint2.setSubpixelText(true);
    paint2.setTypeface(this.dayOfWeekFont);
    paint2.setStyle(Paint.Style.FILL);
    paint2.setColor(Color.WHITE);

    // Special colors for weekends. Holidays should probably also be red, but that's much
    // harder to set up and I'm not feeling it right now
    if ("SAT".equals(day)) {
      paint2.setColor(Color.rgb(165, 194, 218));
    }
    else if ("SUN".equals(day)) {
      paint2.setColor(Color.rgb(215, 157, 167));
    }
    paint2.setTextSize(65);

    int dateWidth = (int) paint.measureText(date);
    int dayWidth = (int) paint2.measureText(day);

    Bitmap myBitmap = Bitmap.createBitmap(
      dateWidth + 75 + dayWidth + 100,
      75, Bitmap.Config.ARGB_8888);
    Canvas myCanvas = new Canvas(myBitmap);

    myCanvas.drawText(date, 0, 75, paint);
    myCanvas.drawText(day, dateWidth + 75, 52, paint2);

    return myBitmap;
  }

  private RemoteViews getUpdatedView(Context context) {
    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget_layout);

    // Update date
    this.mCalendar.setTimeInMillis(System.currentTimeMillis());
    String date = (String) DateFormat.format("MM/dd", mCalendar);
    String day = (String) DateFormat.format("EEE", mCalendar);
    Bitmap dayDate = this.buildDayDate(context, date, day.toUpperCase());
    views.setImageViewBitmap(R.id.dayDate, dayDate);
    this.curDay = mCalendar.get(Calendar.DATE);
    Log.v(LOGGER_TAG, "Current day set to " + curDay);

    // Update time
    int hourToSet = mCalendar.get(Calendar.HOUR_OF_DAY);
    int timeOfDay = this.buildTime(hourToSet);
    views.setImageViewResource(R.id.timeOfDay, timeOfDay);
    this.curHour = hourToSet;
    Log.v(LOGGER_TAG, "Current hour set to " + hourToSet);

    // Update weather
    SharedPreferences prefs = getPrefs(context);
    int lastWeatherCode = prefs.getInt(AppStrings.WEATHER_CODE, 900);
    int weatherIcon = this.buildWeatherIcon(lastWeatherCode);
    views.setImageViewResource(R.id.weatherIcon, weatherIcon);
    Log.v(LOGGER_TAG, "Current weather set to " + lastWeatherCode);
    return views;
  }

  /**
   * Updates a view for a specific widget
   * Currently unused
   */
  public void updateView(Context context, AppWidgetManager manager, int appWidgetId) {
    RemoteViews views = getUpdatedView(context);
    manager.updateAppWidget(appWidgetId, views);
  }

  /**
   * Fully updates the view shown by the widget, using cached data
   */
  public void updateView(Context context) {
    RemoteViews views = getUpdatedView(context);

    ComponentName widget = new ComponentName(context, ClockWidget.class.getName());
    AppWidgetManager manager = AppWidgetManager.getInstance(context);

    manager.updateAppWidget(widget, views);
  }

  private SharedPreferences getPrefs(Context context) {
    if (prefs == null) {
      prefs = context.getSharedPreferences(AppStrings.COM_NAME, Context.MODE_PRIVATE);
    }
    return prefs;
  }

  public void updateLocation(Context context, @Nullable Consumer<Integer> weatherUpdatedCallback) {
    SharedPreferences prefs = getPrefs(context);

    boolean useLocation = prefs.getBoolean(AppStrings.USE_LOC, false);
    if (useLocation) {
      Log.v(LOGGER_TAG, "Getting Location");

      if (fusedLocationClient == null) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
      }

      boolean locationEnabled = Arrays.stream(LOCATION_PROVIDERS)
        .anyMatch(provider -> ActivityCompat.checkSelfPermission(context, provider) == PackageManager.PERMISSION_GRANTED);
      if (locationEnabled) {
        fusedLocationClient.getLastLocation()
          .addOnSuccessListener(location -> {
            if (location != null) {
              lat = (int) location.getLatitude();
              lon = (int) location.getLongitude();
              setLocation(lat, lon);

              if (weatherUpdatedCallback != null) {
                weatherUpdatedCallback.accept(null);
              }
            }
          });
      }
    }
    else if (prefs.contains(AppStrings.LAT) && prefs.contains(AppStrings.LON)) {
      lat = prefs.getInt(AppStrings.LAT, 0);
      lon = prefs.getInt(AppStrings.LON, 0);
      setLocation(lat, lon);

      if (weatherUpdatedCallback != null) {
        weatherUpdatedCallback.accept(null);
      }
    }
  }

  public void updateWeather(Context context) {
    SharedPreferences prefs = getPrefs(context);
    if (prefs.getBoolean(AppStrings.DISABLED, false) || !located) {
      // We can't update if we're not located
      return;
    }

    Log.v(LOGGER_TAG, "Fetching weather for location (" + lat + ", " + lon + ")");

    try {
      String url = "http://api.openweathermap.org/data/2.5/weather?"
        + "lat=" + this.lat + "&lon=" + this.lon
        + "&APPID=" + context.getString(R.string.openweatherKey);

      GetWeatherTaskExecutor executor = new GetWeatherTaskExecutor();
      executor.execute(new GetWeatherTask(
        new URL(url),
        result -> {
          Log.v(LOGGER_TAG, "Got weather code " + result);
          SharedPreferences.Editor edit = prefs.edit();
          edit.putInt(AppStrings.WEATHER_CODE, result);
          edit.apply();
          this.updateView(context);
        }));

    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  private static class GetWeatherTaskExecutor implements Executor {
    @Override
    public void execute(Runnable runnable) {
      new Thread(runnable).start();
    }
  }

  private static class GetWeatherTask implements Runnable {
    private final Consumer<Integer> callback;
    private final URL url;

    public GetWeatherTask(URL url, Consumer<Integer> callback) {
      this.callback = callback;
      this.url = url;
    }

    private Integer getWeather() {
      try {
        Log.v(LOGGER_TAG, "Fetching weather from API");
        HttpURLConnection urlConnection = (HttpURLConnection) this.url.openConnection();
        BufferedReader read = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

        StringBuilder result = new StringBuilder();
        String line;
        while ((line = read.readLine()) != null) {
          result.append(line);
        }

        String output = result.toString();
        JSONObject jsonOut = new JSONObject(output);
        JSONArray weather = jsonOut.getJSONArray("weather");
        return weather.getJSONObject(0).getInt("id");

      }
      catch (IOException | JSONException e) {
        e.printStackTrace();
      }
      return 0;
    }

    @Override
    public void run() {
      if (this.callback != null) {
        this.callback.accept(getWeather());
      }
    }
  }
}
