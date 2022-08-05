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
import java.util.concurrent.Semaphore;
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

  // Providers for updating date, time, and weather
  private FusedLocationProviderClient fusedLocationClient;
  private final Calendar mCalendar;
  private GetWeatherTaskExecutor executor;

  // Font faces for the date and day
  private Typeface dateFont = null;
  private Typeface dayOfWeekFont = null;

  // Location information
  SharedPreferences prefs;
  private float lat;
  private float lon;
  private boolean located;

  /**
   * Control flow boolean to prevent multiple concurrent API calls
   */
  private boolean fetching;

  private WidgetUpdater() {
    mCalendar = Calendar.getInstance();
    executor = new GetWeatherTaskExecutor();

    prefs = null;
    lat = -1;
    lon = -1;
    located = false;

    fetching = false;
  }

  public void setLocation(float lat, float lon) {
    this.lat = lat;
    this.lon = lon;
    this.located = true;
    Log.v(LOGGER_TAG, "Location set to (" + lat + ", " + lon + ")");
  }

  /**
   * Converts the stored weatherCode into an actual weather symbol
   * TODO: Change stored weather code to stored weather enum,
   *  so it's not tied so heavily to the API's response codes
   *  and allows interchanging of backing weather API
   */
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

  /**
   * Converts the current hour into an asset describing the current time of day
   * TODO: Allow user input to adjust these windows, because not everyone has lunch
   *  from 12:00-14:00
   * TODO: Consider adding minute-level granularity
   * TODO: The openweathermap API gives sunrise and sunset, so consider if those are usable
   *  for determining morning/evening/night
   */
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

  /**
   * Creates a Bitmap image describing the current day/date to show to the user
   */
  private Bitmap buildDayDate(Context context, String date, String day) {
    if (this.dateFont == null) {
      this.dateFont = Typeface.createFromAsset(context.getAssets(), "fonts/Days.ttf");
    }
    if (this.dayOfWeekFont == null) {
      this.dayOfWeekFont = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoCondensed-Regular.ttf");
    }
    Paint datePaint = new Paint();
    datePaint.setAntiAlias(true);
    datePaint.setSubpixelText(true);
    datePaint.setTypeface(this.dateFont);
    datePaint.setStyle(Paint.Style.FILL);
    datePaint.setColor(Color.WHITE);
    datePaint.setTextSize(100);
    datePaint.setLetterSpacing(0.25f);

    Paint dayOfWeekPaint = new Paint();
    dayOfWeekPaint.setAntiAlias(true);
    dayOfWeekPaint.setSubpixelText(true);
    dayOfWeekPaint.setTypeface(this.dayOfWeekFont);
    dayOfWeekPaint.setStyle(Paint.Style.FILL);
    dayOfWeekPaint.setColor(Color.WHITE);

    // Special colors for weekends. Holidays should probably also be red, but that's much
    // harder to set up and I'm not feeling it right now
    if ("SAT".equals(day)) {
      dayOfWeekPaint.setColor(Color.rgb(165, 194, 218));
    }
    else if ("SUN".equals(day)) {
      dayOfWeekPaint.setColor(Color.rgb(215, 157, 167));
    }
    dayOfWeekPaint.setTextSize(65);

    int dateWidth = (int) datePaint.measureText(date);
    int dayWidth = (int) dayOfWeekPaint.measureText(day);

    Bitmap myBitmap = Bitmap.createBitmap(
      dateWidth + 75 + dayWidth + 100,
      75, Bitmap.Config.ARGB_8888);
    Canvas myCanvas = new Canvas(myBitmap);

    myCanvas.drawText(date, 0, 75, datePaint);
    myCanvas.drawText(day, dateWidth + 75, 52, dayOfWeekPaint);

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
    Log.v(LOGGER_TAG, "Current date set to " + date + "(" + day + ")");

    // Update time
    int hourToSet = mCalendar.get(Calendar.HOUR_OF_DAY);
    int timeOfDay = this.buildTime(hourToSet);
    views.setImageViewResource(R.id.timeOfDay, timeOfDay);
    Log.v(LOGGER_TAG, "Current hour set to " + hourToSet);

    // Update weather
    SharedPreferences prefs = getPrefs(context);
    int lastWeatherCode = prefs.getInt(AppConstants.WEATHER_CODE, 900);
    int weatherIcon = this.buildWeatherIcon(lastWeatherCode);
    views.setImageViewResource(R.id.weatherIcon, weatherIcon);
    Log.v(LOGGER_TAG, "Current weather set to " + lastWeatherCode);
    return views;
  }

  /**
   * Fully updates the view shown by the widget, using cached data
   */
  private void updateView(Context context) {
    RemoteViews views = getUpdatedView(context);

    ComponentName widget = new ComponentName(context, ClockWidget.class.getName());
    AppWidgetManager manager = AppWidgetManager.getInstance(context);

    manager.updateAppWidget(widget, views);
  }

  /**
   * Public facing method to actually update the widget.
   */
  public void doUpdate(Context context, boolean force) {
    boolean updateWeather = force;

    SharedPreferences prefs = getPrefs(context);

    // Only do a weather update if it's forced or if enough time has elapsed since the last update
    if (!updateWeather) {
      long lastUpdate = prefs.getLong(AppConstants.WEATHER_LAST_UPDATED, 0);
      updateWeather = System.currentTimeMillis() - lastUpdate > AppConstants.WEATHER_UPDATE_FREQUENCY;
    }

    if (updateWeather) {
      // Fetch the current location and then update the weather and subsequently the date/time
      this.updateLocation(context, nothing -> this.updateWeather(context));
    }
    else {
      // Otherwise just update based on cached weather data
      this.updateView(context);
    }
  }

  /**
   * Gets the SharedPreferences object for this app
   */
  private SharedPreferences getPrefs(Context context) {
    if (prefs == null) {
      prefs = context.getSharedPreferences(AppConstants.COM_NAME, Context.MODE_PRIVATE);
    }
    return prefs;
  }

  /**
   * Updates the stored location for the updater
   */
  private void updateLocation(Context context, @Nullable Consumer<Integer> weatherUpdatedCallback) {
    SharedPreferences prefs = getPrefs(context);

    boolean useLocation = prefs.getBoolean(AppConstants.USE_LOC, false);
    if (useLocation) {
      Log.v(LOGGER_TAG, "Getting Location");

      if (fusedLocationClient == null) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
      }

      boolean locationEnabled = Arrays.stream(AppConstants.LOCATION_PROVIDERS)
        .anyMatch(provider -> ActivityCompat.checkSelfPermission(context, provider) == PackageManager.PERMISSION_GRANTED);
      if (locationEnabled) {
        fusedLocationClient.getLastLocation()
          .addOnSuccessListener(location -> {
            if (location != null) {
              lat = (float)(Math.round(location.getLatitude() * 100) / 100.0);
              lon = (float)(Math.round(location.getLongitude() * 100) / 100.0);
              setLocation(lat, lon);

              if (weatherUpdatedCallback != null) {
                weatherUpdatedCallback.accept(null);
              }
            }
          });
      }
    }
    else if (prefs.contains(AppConstants.LAT) && prefs.contains(AppConstants.LON)) {
      // Only get 2 decimals of precision for lat/lon because otherwise it could be infinite lmao
      lat = (float)(Math.round(prefs.getFloat(AppConstants.LAT, 0) * 100) / 100.0);
      lon = (float)(Math.round(prefs.getFloat(AppConstants.LON, 0) * 100) / 100.0);
      setLocation(lat, lon);

      if (weatherUpdatedCallback != null) {
        weatherUpdatedCallback.accept(null);
      }
    }
  }

  /**
   * Fetches weather data from the backing API and updates the widget
   * A bit misnamed because it updates date/time on top of weather,
   * but I can't think of a better name
   */
  private void updateWeather(Context context) {
    SharedPreferences prefs = getPrefs(context);
    if (prefs.getBoolean(AppConstants.DISABLED, false) || !located) {
      // We can't update if we're not located
      return;
    }
    if (fetching) {
      // Don't do an API request if we're in the middle of another one
      // I sure hope I'm getting the async logic right here
      return;
    }

    Log.v(LOGGER_TAG, "Fetching weather for location (" + lat + ", " + lon + ")");

    synchronized (this) {
      if (fetching) {
        // Finer filter because idk if the synchronized block also affects the callback
        return;
      }
      try {
        fetching = true;
        String url = "http://api.openweathermap.org/data/2.5/weather?"
          + "lat=" + this.lat + "&lon=" + this.lon
          + "&APPID=" + context.getString(R.string.openweatherKey);

        executor.execute(new GetWeatherTask(
          new URL(url),
          result -> {
            if (result == -1) {
              Log.v(LOGGER_TAG, "Failed to get weather. Trying again in 30 minutes");
              SharedPreferences.Editor edit = prefs.edit();
              edit.putLong(AppConstants.WEATHER_LAST_UPDATED, System.currentTimeMillis() - 100 * 60 * 30);
              edit.apply();
              fetching = false;

              // Update the view anyway for date/time
              this.updateView(context);
            }
            else {
              Log.v(LOGGER_TAG, "Got weather code " + result);
              SharedPreferences.Editor edit = prefs.edit();
              edit.putInt(AppConstants.WEATHER_CODE, result);
              edit.putLong(AppConstants.WEATHER_LAST_UPDATED, System.currentTimeMillis());
              edit.apply();
              fetching = false;
              this.updateView(context);
            }
          }));
      }
      catch (MalformedURLException e) {
        fetching = false;
        e.printStackTrace();
      }
    }
  }

  /**
   * Executor helper class to run a GetWeatherTask
   */
  private static class GetWeatherTaskExecutor implements Executor {
    @Override
    public void execute(Runnable runnable) {
      new Thread(runnable).start();
    }
  }

  /**
   * Runnable helper class to interact with the backing API
   */
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
        Log.v(LOGGER_TAG, "Got response from API");

        StringBuilder result = new StringBuilder();
        String line;
        while ((line = read.readLine()) != null) {
          result.append(line);
        }

        String output = result.toString();
        JSONObject jsonOut = new JSONObject(output);
        Log.v(LOGGER_TAG, "Parsed response from API");
        JSONArray weather = jsonOut.getJSONArray("weather");
        return weather.getJSONObject(0).getInt("id");
      }
      catch (IOException e) {
        Log.v(LOGGER_TAG, "Hit an exception trying to fetch from the API");
        e.printStackTrace();
      }
      catch (JSONException e) {
        Log.v(LOGGER_TAG, "Hit an exception trying to parse the response");
        e.printStackTrace();
      }
      catch (Exception e) {
        Log.v(LOGGER_TAG, "Hit an unknown exception");
        e.printStackTrace();
      }
      return -1;
    }

    @Override
    public void run() {
      if (this.callback != null) {
        this.callback.accept(getWeather());
      }
    }
  }
}
