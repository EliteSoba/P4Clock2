package com.soba.persona.p4clockultimax;

import android.Manifest;
import android.appwidget.AppWidgetManager;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

/**
 * Unused class that _could_ be used to launch a configuration activity
 * when adding a widget.
 *
 * Last updated - v1.1
 *
 * @author Soba 08-02-2022
 */
public class ConfigurationActivity extends AppCompatActivity {
  private static final String LOGGER_TAG = "com.soba.persona.p4clock.ConfigurationActivity";
  private int id;
  private SharedPreferences prefs;

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

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    if (extras != null) {
      id = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
    }
    setResult(RESULT_CANCELED);

    setContentView(R.layout.activity_main);
    prefs = getSharedPreferences(AppConstants.COM_NAME, MODE_PRIVATE);

    Button start = findViewById(R.id.updateButton);
    start.setText(R.string.confirm);
    start.setCompoundDrawables(null, null, null, null);

    TextView textView = findViewById(R.id.infoText);
    textView.setVisibility(View.GONE);

    initEditTexts();
    initRadios();

    if (prefs.getBoolean(AppConstants.DISABLED, false)) {
      CheckBox disableBox = findViewById(R.id.disableBox);
      disableBox.setChecked(true);
      disableBox.callOnClick();
    }

    AppWidgetManager awm = AppWidgetManager.getInstance(this);
    RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.clock_widget_layout);
    awm.updateAppWidget(id, views);
  }

  public void updateWidget(boolean force) {
    Intent intent = new Intent(this, ClockWidget.class);
    intent.setAction(AppConstants.UPDATE);
    intent.putExtra(AppConstants.FORCE, force);
    sendBroadcast(intent);

    AppWidgetManager awm = AppWidgetManager.getInstance(this);
    RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.clock_widget_layout);
    awm.updateAppWidget(id, views);
  }

  public void removeKey(String key) {
    if (prefs.contains(key)) {
      SharedPreferences.Editor edit = prefs.edit();
      edit.remove(key);
      edit.apply();
      updateWidget(false);
    }
  }

  public void putFloat(String key, float value) {
    SharedPreferences.Editor edit = prefs.edit();
    edit.putFloat(key, value);
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
    putBoolean(AppConstants.DISABLED, !enabled, false);
  }

  public void updateButton(View view) {
    updateWidget(true);

    Intent result = new Intent();
    result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    setResult(RESULT_OK, result);
    finish();
  }

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
  }

  public void useLocation(View view) {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }
    else {
      putBoolean(AppConstants.USE_LOC, true, false);
    }
  }

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
