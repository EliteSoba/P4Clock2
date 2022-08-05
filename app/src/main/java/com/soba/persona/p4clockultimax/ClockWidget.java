package com.soba.persona.p4clockultimax;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;

/**
 * Logic controlling the Widget, including updates
 *
 * @author Soba on 2022-08-01.
 */
public class ClockWidget extends AppWidgetProvider {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.ClockWidget";

  /** The intents this widget can listen to */
  private static final String[] LISTENABLE_INTENTS = new String[] {
    // ACTION_DATE_CHANGED is unreliable lmao
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_USER_PRESENT
  };

  /** Helper function to run the ClockWidgetUpdateService */
  private void runService(Context context, boolean force) throws RuntimeException {
    Intent i = new Intent(context, ClockWidgetUpdateService.class);
    if (force) {
      i.putExtra(AppConstants.FORCE, true);
    }
    context.startService(i);
  }

  /** Helper function to try running the updat service if possible */
  private void tryRunningService(Context context, boolean force) {
    try {
      // Try to start the updater service
      Log.v(LOGGER_TAG, "Trying to start up updater service");
      runService(context, force);
      Log.v(LOGGER_TAG, "Update service started");
    }
    catch (IllegalStateException e) {
      // We're in the background and can't start the service intent,
      // so just update normally
      Log.v(LOGGER_TAG, "Update service failed to start. Simply updating view");
      WidgetUpdater.get().doUpdate(context, true);
    }
  }

  /**
   * onEnabled is called on instantiation of the first widget
   */
  public void onEnabled(Context context) {
    super.onEnabled(context);
    Log.v(LOGGER_TAG, "onEnabled: Widget added");
    tryRunningService(context, false);
  }

  /**
   * onReceive is called for every intent that we listen for,
   * either the System Intents we listen for to update,
   * or Intents from MainActivity whenever the settings get changed
   */
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    Log.v(LOGGER_TAG, "Intent Received: " + intent.getAction());

    if (AppConstants.UPDATE.equals(intent.getAction())) {
      // This block is for updates requested by the main app
      // Since the app is necessarily live for these intents,
      // we can try to run the service without Android complaining to us
      boolean force = intent.getBooleanExtra(AppConstants.FORCE, false);
      if (force) {
        Log.v(LOGGER_TAG, "Updating Date, Time, and Weather");
        tryRunningService(context, true);
      }
      else {
        Log.v(LOGGER_TAG, "Updating Date, and Time");
        tryRunningService(context, false);
      }
    }
    else if (Arrays.stream(LISTENABLE_INTENTS).anyMatch(i -> i.equals(intent.getAction()))) {
      // This block is for the Intents we registered in AndroidManifest
      Log.v(LOGGER_TAG, "Updating Date, and Time from listened intent");
      tryRunningService(context, false);
    }
  }

  /**
   * onUpdate is called at every {updatePeriodMillis} ms, and also when a widget is added
   */
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    super.onUpdate(context, appWidgetManager, appWidgetIds);

    Log.v(LOGGER_TAG, "onUpdate called");
    tryRunningService(context, false);
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
