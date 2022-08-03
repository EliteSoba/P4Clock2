package com.soba.p4clock;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

/**
 * Actually updateTime the views. Abstracted to allow anyone to updateTime on demand instead of just the UpdateService
 */
public class WidgetUpdater {

  private static final String LOGGER_TAG = "com.soba.p4clock.WidgetUpdater";
  private final Calendar mCalendar;
  private Typeface daydate = null, time = null;
  private int curHour, curDay;

  public WidgetUpdater() {
    mCalendar = Calendar.getInstance();
    curHour = -1;
    curDay = -1;
  }

  public int getCurHour() {
    return curHour;
  }

  public int getCurDay() {
    return curDay;
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
    if (daydate == null) {
      daydate = Typeface.createFromAsset(context.getAssets(), "fonts/Days.ttf");
    }
    if (time == null) {
      time = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoCondensed-Regular.ttf");
    }

    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setSubpixelText(true);
    paint.setTypeface(daydate);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.WHITE);
    paint.setTextSize(100);
    paint.setLetterSpacing(0.25f);

    Paint paint2 = new Paint();
    paint2.setAntiAlias(true);
    paint2.setSubpixelText(true);
    paint2.setTypeface(time);
    paint2.setStyle(Paint.Style.FILL);
    paint2.setColor(Color.WHITE);
    //Special colors for weekends. Holidays should probably also be red, but that's much
    //harder to set up and I'm not feeling it right now
    if ("SAT".equals(day)) {
      paint2.setColor(Color.rgb(165, 194, 218));
    }
    else if ("SUN".equals(day)) {
      paint2.setColor(Color.rgb(215, 157, 167));
    }
    paint2.setTextSize(65);

    int dateWidth = (int) paint.measureText(date);
    int dayWidth = (int) paint2.measureText(day);

    Bitmap myBitmap = Bitmap.createBitmap(dateWidth + 75 + dayWidth + 100,
      75, Bitmap.Config.ARGB_8888);
    Canvas myCanvas = new Canvas(myBitmap);

    myCanvas.drawText(date, 0, 75, paint);
    myCanvas.drawText(day, dateWidth + 75, 52, paint2);

    return myBitmap;
  }

  public void updateDateAndTime(Context context, int hour) {
    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget_layout);

    // Update date
    mCalendar.setTimeInMillis(System.currentTimeMillis());
    String date = (String) DateFormat.format("MM/dd", mCalendar);
    String day = (String) DateFormat.format("EEE", mCalendar);
    curDay = mCalendar.get(Calendar.DATE);
    Bitmap dayDate = buildDayDate(context, date, day.toUpperCase());
    views.setImageViewBitmap(R.id.dayDate, dayDate);
    Log.v(LOGGER_TAG, "Current day set to " + curDay);

    // Update time
    curHour = hour;
    int timeOfDay = buildTime(hour);
    views.setImageViewResource(R.id.timeOfDay, timeOfDay);
    curHour = hour;
    Log.v(LOGGER_TAG, "curHour set to " + hour);

    ComponentName widget = new ComponentName(context, ClockWidget.class.getName());
    AppWidgetManager manager = AppWidgetManager.getInstance(context);

    manager.updateAppWidget(widget, views);
  }

  public void updateWeather(Context context, int lat, int lon) {
    RemoteViews weatherView = new RemoteViews(context.getPackageName(), R.layout.clock_widget_layout);
    try {
      String url = "http://api.openweathermap.org/data/2.5/weather?"
                   + "lat=" + lat + "&lon=" + lon
                   + "&APPID=" + context.getString(R.string.openweatherKey);

      new GetWeatherTask(weatherView, context).execute(new URL(url));
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  private static class GetWeatherTask extends AsyncTask<URL, Integer, Integer> {

    RemoteViews views;
    Context context;

    GetWeatherTask(RemoteViews views, Context context) {
      this.views = views;
      this.context = context;
    }

    @Override
    protected Integer doInBackground(URL... params) {
      URL url = params[0];
      try {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
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
    protected void onPostExecute(Integer result) {
      int icon;
      switch (result / 100) {
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
      if (result > 800) {
        icon = R.drawable.cloudy;
      }
      if (result >= 900) {
        icon = R.drawable.unknown;
      }
      views.setImageViewResource(R.id.weatherIcon, icon);

      ComponentName widget = new ComponentName(context, ClockWidget.class);
      AppWidgetManager manager = AppWidgetManager.getInstance(context);
      manager.updateAppWidget(widget, views);

    }
  }
}
