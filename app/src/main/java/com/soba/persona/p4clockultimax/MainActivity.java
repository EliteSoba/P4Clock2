package com.soba.persona.p4clockultimax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Arrays;

/**
 * Main activity when launching the app. Handles general configuration of the whole thing
 *
 * @author Soba 08-02-2022
 */
public class MainActivity extends Activity {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.MainActivity";

  private SharedPreferences prefs;
  private FusedLocationProviderClient fusedLocationClient;

  /**
   * Initialize the latitude/longitude fields with saved data
   */
  public void initEditTexts() {
    EditText latitude = findViewById(R.id.latText);
    latitude.addTextChangedListener(new LonLatWatcher(AppConstants.LAT));
    EditText longitude = findViewById(R.id.lonText);
    longitude.addTextChangedListener(new LonLatWatcher(AppConstants.LON));

    if (prefs.contains(AppConstants.LAT)) {
      String lat = "" + prefs.getFloat(AppConstants.LAT, 0);
      latitude.setText(lat);
    }
    if (prefs.contains(AppConstants.LON)) {
      String lon = "" + prefs.getFloat(AppConstants.LON, 0);
      longitude.setText(lon);
    }
  }

  /**
   * Initialize the location choice preference, between current or predetermined location
   */
  public void initRadios() {
    if (prefs.getBoolean(AppConstants.USE_LOC, false)) {
      RadioButton useLoc = findViewById(R.id.curLocationButton);
      useLoc.setChecked(true);
      useLoc.callOnClick();
    }
    else {
      RadioButton chooseLoc = findViewById(R.id.chooseButton);
      chooseLoc.setChecked(true);
      chooseLoc.callOnClick();
    }
  }

  /**
   * Called on creation of the app. Initialize fields.
   */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);
    prefs = getSharedPreferences(AppConstants.COM_NAME, MODE_PRIVATE);
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    initEditTexts();
    initRadios();

    if (prefs.getBoolean(AppConstants.DISABLED, false)) {
      CheckBox disableBox = findViewById(R.id.disableBox);
      disableBox.setChecked(true);
      disableBox.callOnClick();
    }
  }

  /**
   * Helper function to send an update request to the widget (and subsequently to the updater)
   */
  public void updateWidget(boolean force) {
    Intent intent = new Intent(this, ClockWidget.class);
    intent.setAction(AppConstants.UPDATE);
    intent.putExtra(AppConstants.FORCE, force);
    sendBroadcast(intent);
  }

  /**
   * Helper function to remove a key from the SharedPreferences object
   * Updates the widget
   */
  public void removeKey(String key) {
    if (prefs.contains(key)) {
      SharedPreferences.Editor edit = prefs.edit();
      edit.remove(key);
      edit.apply();
      updateWidget(false);
    }
  }

  /**
   * Helper function to put a float into the SharedPreferences object
   * Updates the widget
   */
  public void putFloat(String key, float value) {
    SharedPreferences.Editor edit = prefs.edit();
    edit.putFloat(key, value);
    edit.apply();
    updateWidget(false);
  }

  /**
   * Helper function to put a boolean into the SharedPreferences object
   * Updates the widget, optionally forcefully if location data is changed
   */
  public void putBoolean(String key, boolean value, boolean force) {
    SharedPreferences.Editor edit = prefs.edit();
    edit.putBoolean(key, value);
    edit.apply();
    updateWidget(force);
  }

  /**
   * Click handler for the disable weather checkbox that disables weather updates
   */
  public void disableWeather(View view) {
    CheckBox disableBox = findViewById(R.id.disableBox);
    boolean enabled = !disableBox.isChecked();
    RadioButton button2 = findViewById(R.id.curLocationButton);
    button2.setEnabled(enabled);
    RadioButton button3 = findViewById(R.id.chooseButton);
    button3.setEnabled(enabled);
    EditText lat = findViewById(R.id.lonText);
    EditText lon = findViewById(R.id.latText);
    lat.setEnabled(enabled);
    lon.setEnabled(enabled);

    if (getCurrentFocus() != null) {
      if (!enabled) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
      }
    }
    putBoolean(AppConstants.DISABLED, !enabled, false);
  }

  /**
   * Updates the latitude and longitude text fields with the location provider's location
   */
  @SuppressLint("SetTextI18n")
  private void updateLonLat() {
    boolean locationEnabled = Arrays.stream(AppConstants.LOCATION_PROVIDERS)
      .anyMatch(provider -> ActivityCompat.checkSelfPermission(this, provider) == PackageManager.PERMISSION_GRANTED);
    if (!locationEnabled) {
      Toast.makeText(this, R.string.couldnt_find_location, Toast.LENGTH_LONG).show();
      updateFindLocationText(false);
      return;
    }
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
      .addOnSuccessListener(location -> {
        updateFindLocationText(false);
        if (location != null) {
          float lat = (float)(Math.round(location.getLatitude() * 100) / 100.0);
          float lon = (float)(Math.round(location.getLongitude() * 100) / 100.0);
          EditText latitude = findViewById(R.id.latText);
          EditText longitude = findViewById(R.id.lonText);
          latitude.setText("" + lat);
          longitude.setText("" + lon);
          putFloat(AppConstants.LAT, lat);
          putFloat(AppConstants.LON, lon);
        }
        else {
          Toast.makeText(this, R.string.couldnt_find_location, Toast.LENGTH_LONG).show();
        }
      });
  }

  /**
   * Click handler for the force update button
   */
  public void updateButton(View view) {
    updateWidget(true);
  }

  /**
   * Callback for the location permissions request
   * Called when using current location for weather, or for finding the current location
   */
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 1) {
      boolean accepted = Arrays.stream(grantResults)
        .anyMatch(result -> result == PackageManager.PERMISSION_GRANTED);

      if (accepted) {
        putBoolean(AppConstants.USE_LOC, true, true);
      }
      else {
        RadioButton choose = findViewById(R.id.chooseButton);
        choose.setChecked(true);
      }
    }
    else if (requestCode == 2) {
      boolean accepted = Arrays.stream(grantResults)
        .anyMatch(result -> result == PackageManager.PERMISSION_GRANTED);
      if (accepted) {
        updateLonLat();
      }
      else {
        Toast.makeText(this, R.string.couldnt_find_location, Toast.LENGTH_LONG).show();
        updateFindLocationText(false);
      }
    }
  }

  /**
   * Helper function to disable/enable the find location button
   */
  public void updateFindLocationText(boolean finding) {
    Button findLocation = findViewById(R.id.locateButton);
    if (finding) {
      findLocation.setText(R.string.finding_location);
      findLocation.setActivated(false);
      findLocation.getBackground().setTint(Color.parseColor("#FFCCCCCC"));
    }
    else {
      findLocation.setText(R.string.find_location);
      findLocation.setActivated(true);
      findLocation.setBackgroundTintList(null);
    }
  }

  /**
   * Click handler for the find location button, which updates the lat/lon fields with the user's current location
   */
  public void findLocation(View view) {
    updateFindLocationText(true);
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
          || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
    }
    else {
      updateLonLat();
    }
  }

  /**
   * Click handler for the use location radio button,
   * which changes the app to track the user's current location for weather updates
   */
  public void useLocation(View view) {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }
    else {
      putBoolean(AppConstants.USE_LOC, true, false);
    }
  }

  /**
   * Click handler for the set location radio button,
   * which changes the app to use a provided location for weather udpates
   */
  public void setLocation(View view) {
    putBoolean(AppConstants.USE_LOC, false, false);
  }

  /**
   * Helper class that listens for text changes for the longitude/latitude field changes
   */
  private class LonLatWatcher implements TextWatcher {
    private final String key;

    public LonLatWatcher(String key) {
      this.key = key;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      if (s.length() == 0 || s.toString().equals("-")) {
        removeKey(this.key);
        Log.v(LOGGER_TAG, this.key + " unset");
      }
      else {
        try {
          putFloat(this.key, Float.parseFloat(s.toString()));
          Log.v(LOGGER_TAG, this.key + " set to " + s);
        }
        catch (NumberFormatException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
