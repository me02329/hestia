package io.hestia.tv;

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
        Ui.init(this);
        if (Watchdog.crashedTooOften(this)) {
            showRecovery();
            return;
        }
        home = new HomeView(this);
        setContentView(home);
        getWindow().setBackgroundDrawable(null);
    }

    /**
     * After repeated crashes: no home screen, only ways out. Google TV's launcher may be disabled on this TV, so
     * "another home" could be an empty screen: the TV settings and the app list (to re-enable it) come first.
     */
    private void showRecovery() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(96, 96, 96, 96);
        box.setBackgroundColor(Color.rgb(14, 20, 32));
        TextView t = new TextView(this);
        t.setText(Ui.tr("Hestia travou várias vezes.\nO início padrão não foi alterado.",
                "Hestia crashed several times.\nThe default home screen has not been changed."));
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, 48);
        box.addView(t);
        Button first = button(box, Ui.tr("Ajustes da TV", "TV Settings"), () -> HomeMode.openSettings(this));
        button(box, Ui.tr("Aplicativos (reativar o Google TV)", "Apps (re-enable Google TV)"), () -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            } catch (RuntimeException e) {
                HomeMode.openSettings(this);
            }
        });
        button(box, Ui.tr("Outro início", "Another home"), () -> HomeMode.openOtherHome(this));
        button(box, Ui.tr("Tentar Hestia de novo", "Retry Hestia"), () -> { Watchdog.clear(this); recreate(); });
        setContentView(box);
        first.requestFocus();
    }

    private Button button(LinearLayout box, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(720, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        box.addView(b, lp);
        return b;
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
