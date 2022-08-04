package com.soba.persona.p4clockultimax;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

/**
 * Main activity when launching the app. Handles general configuration of the whole thing
 *
 * @author Soba 08-02-2022
 */
public class MainActivity extends Activity {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.MainActivity";

  private SharedPreferences prefs;

  public void initEditTexts() {
    EditText latitude = findViewById(R.id.latText);
    latitude.addTextChangedListener(new LonLatWatcher(AppStrings.LAT));
    EditText longitude = findViewById(R.id.lonText);
    longitude.addTextChangedListener(new LonLatWatcher(AppStrings.LON));

    if (prefs.contains(AppStrings.LAT)) {
      String lat = "" + prefs.getInt(AppStrings.LAT, 0);
      latitude.setText(lat);
    }
    if (prefs.contains(AppStrings.LON)) {
      String lon = "" + prefs.getInt(AppStrings.LON, 0);
      longitude.setText(lon);
    }
  }

  public void initRadios() {
    if (prefs.getBoolean(AppStrings.USE_LOC, false)) {
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

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);
    prefs = getSharedPreferences(AppStrings.COM_NAME, MODE_PRIVATE);

    initEditTexts();
    initRadios();

    if (prefs.getBoolean(AppStrings.DISABLED, false)) {
      CheckBox disableBox = findViewById(R.id.disableBox);
      disableBox.setChecked(true);
      disableBox.callOnClick();
    }
  }

  public void updateWidget(boolean force) {
    Intent intent = new Intent(this, ClockWidget.class);
    intent.setAction(AppStrings.UPDATE);
    intent.putExtra(AppStrings.FORCE, force);
    sendBroadcast(intent);
  }

  public void removeKey(String key) {
    if (prefs.contains(key)) {
      SharedPreferences.Editor edit = prefs.edit();
      edit.remove(key);
      edit.apply();
      updateWidget(false);
    }
  }

  public void putInt(String key, int value) {
    SharedPreferences.Editor edit = prefs.edit();
    edit.putInt(key, value);
    edit.apply();
    updateWidget(false);
  }

  public void putBoolean(String key, boolean value, boolean force) {
    SharedPreferences.Editor edit = prefs.edit();
    edit.putBoolean(key, value);
    edit.apply();
    updateWidget(force);
  }

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
    putBoolean(AppStrings.DISABLED, !enabled, false);
  }

  public void updateButton(View view) {
    updateWidget(true);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 1) {
      boolean accepted = Arrays.stream(grantResults)
        .anyMatch(result -> result == PackageManager.PERMISSION_GRANTED);

      if (accepted) {
        putBoolean(AppStrings.USE_LOC, true, true);
      }
      else {
        RadioButton choose = findViewById(R.id.chooseButton);
        choose.setChecked(true);
      }
    }
  }

  public void useLocation(View view) {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }
    else {
      putBoolean(AppStrings.USE_LOC, true, false);
    }
  }

  public void setLocation(View view) {
    putBoolean(AppStrings.USE_LOC, false, false);
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
          putInt(this.key, Integer.parseInt(s.toString()));
          Log.v(LOGGER_TAG, this.key + " set to " + s);
        }
        catch (NumberFormatException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
