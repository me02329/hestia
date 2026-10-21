package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;

/** Home UI with a dependency-light recovery screen after repeated Java crashes. */
public class HomeActivity extends Activity {
    private HomeView home;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        if (Watchdog.crashedTooOften(this)) {
            LinearLayout recovery = new LinearLayout(this);
            recovery.setOrientation(LinearLayout.VERTICAL);
            recovery.setGravity(Gravity.CENTER);
            recovery.setPadding(48, 48, 48, 48);
            recovery.setBackgroundColor(Color.rgb(7, 11, 30));
            TextView t = new TextView(this);
            t.setText("Hestia encountered repeated crashes.\nOpen another home or restore from your computer.\nThe default home has not been changed.");
            t.setTextColor(Color.WHITE);
            t.setTextSize(22);
            t.setGravity(Gravity.CENTER);
            recovery.addView(t);
            Button original = new Button(this);
            original.setText("Open another home");
            original.setOnClickListener(v -> HomeMode.openOtherHome(this));
            recovery.addView(original);
            Button settings = new Button(this);
            settings.setText("TV settings");
            settings.setOnClickListener(v -> HomeMode.openSettings(this));
            recovery.addView(settings);
            Button retry = new Button(this);
            retry.setText("Retry Hestia");
            retry.setOnClickListener(v -> { Watchdog.clear(this); recreate(); });
            recovery.addView(retry);
            setContentView(recovery);
            original.requestFocus();
            return;
        }
        Ui.init(this);
        home = new HomeView(this);
        setContentView(home);
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (home != null) home.resume();
    }

    @Override protected void onPause() {
        if (home != null) home.pause();
        super.onPause();
    }

    /** Home button pressed on the home screen: back to the entry state (focus on the first app, sheet closed). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home != null) home.reset(true);
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

}
