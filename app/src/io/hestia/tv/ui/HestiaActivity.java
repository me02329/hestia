package io.hestia.tv.ui;

import android.app.Activity;
import android.os.Bundle;

/** Base of every Hestia screen. */
public abstract class HestiaActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
    }
}
