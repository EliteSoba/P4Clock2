package com.soba.persona.p4clockultimax;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.function.Consumer;

/**
 * Helper class to listen to the time change intents and trigger the update service
 *
 * @author Soba 08-04-2022
 */
public class TimeChangeReceiver extends BroadcastReceiver {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.TimeChangeReceiver";

  Consumer callback;

  public TimeChangeReceiver(Consumer callback) {
    this.callback = callback;
    Log.v(LOGGER_TAG, "TimeChangeReceiver created");
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.v(LOGGER_TAG, "Received Intent. Checking for updates");
    this.callback.accept(null);
  }
}
