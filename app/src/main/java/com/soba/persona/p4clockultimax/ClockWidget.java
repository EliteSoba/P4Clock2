package com.soba.persona.p4clockultimax;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;

/**
 * Logic controlling the Widget, including updates
 *
 * @author Soba on 2022-08-01.
 */
public class ClockWidget extends AppWidgetProvider {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.ClockWidget";
  private static final String[] LISTENABLE_INTENTS = new String[] {
    // ACTION_DATE_CHANGED is unreliable lmao
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_USER_PRESENT
  };

  private void runService(Context context, boolean force) {
    Intent i = new Intent(context, ClockWidgetUpdateService.class);
    if (force) {
      i.putExtra(AppStrings.FORCE, true);
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
      WidgetUpdater updater = WidgetUpdater.get();
      if (force) {
        Log.v(LOGGER_TAG, "Updating Date, Time, and Weather");
        runService(context, true);
      }
      else {
        Log.v(LOGGER_TAG, "Updating Date, and Time");
        updater.updateView(context);
      }
    }
    else if (Arrays.stream(LISTENABLE_INTENTS).anyMatch(i -> i.equals(intent.getAction()))) {
      Log.v(LOGGER_TAG, "Updating Date, and Time from listened intent");
      WidgetUpdater updater = WidgetUpdater.get();
      updater.updateView(context);
    }
  }

  /**
   * onUpdate is called at every {updatePeriodMillis} ms, and also when a widget is added
   */
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    super.onUpdate(context, appWidgetManager, appWidgetIds);

    Log.v(LOGGER_TAG, "onUpdate called");
    WidgetUpdater.get().updateView(context);

    // Try rerunning the service
    runService(context, false);
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
    context.stopService(new Intent(context, ClockWidgetUpdateService.class));
  }
}
