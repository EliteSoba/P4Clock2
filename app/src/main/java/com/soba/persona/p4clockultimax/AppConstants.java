package com.soba.persona.p4clockultimax;

import android.Manifest;

/**
 * Holder class for all constants used throughout the Java side of things
 *
 * @author Soba 08-02-2022
 */
public class AppConstants {
  /** The name of the SharedPrefs storage */
  public static final String COM_NAME = "com.soba.persona.p4clockultimax.prefs";

  /** Key for if weather updates are disabled */
  public static final String DISABLED = "com.soba.persona.p4clockultimax.disabled";

  /** Key for whether to use the user's current location based on location services */
  public static final String USE_LOC = "com.soba.persona.p4clockultimax.useLoc";

  /** Key for the user's stored latitude */
  public static final String LAT = "com.soba.persona.p4clockultimax.lat";

  /** Key for the user's stored longitude */
  public static final String LON = "com.soba.persona.p4clockultimax.lon";

  /** Key for the last weather code received from the API */
  public static final String WEATHER_CODE = "com.soba.persona.p4clockultimax.weatherCode";

  /** Intent extra field to force weather updates */
  public static final String FORCE = "com.soba.persona.p4clockultimax.force";

  /** Generic update Intent name */
  public static final String UPDATE = "com.soba.persona.p4clockultimax.APPWIDGET_UPDATE";

  /** Epoch time of the last time weather was fetched from the API */
  public static final String WEATHER_LAST_UPDATED = "com.soba.persona.p4clockultimax.lastWeatherUpdate";

  /** How frequently to fetch weather updates from the backend, in ms */
  public static final long WEATHER_UPDATE_FREQUENCY = 1000 * 60 * 60; // Update weather every hour

  /** List of the possible location providers, to check permissions */
  public static final String[] LOCATION_PROVIDERS = new String[] {
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
  };

  private AppConstants() { }
}
