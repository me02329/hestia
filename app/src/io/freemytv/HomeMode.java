package io.freemytv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;

/** Read-only home detection. No ADB client or privileged mutation path. */
final class HomeMode {
    private HomeMode() {}
    static final String PKG = "io.freemytv";
    static final String FREE = "free", ORIGINAL = "original";

    static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    static boolean isDefault(Context c) {
        ResolveInfo r = c.getPackageManager().resolveActivity(homeIntent(), 0);
        return r != null && r.activityInfo != null && PKG.equals(r.activityInfo.packageName);
    }

    static JSONObject state(Context c) {
        JSONObject result = new JSONObject();
        try { result.put("mode", isDefault(c) ? FREE : ORIGINAL); }
        catch (org.json.JSONException ignored) {}
        return result;
    }

    static void openHomeSettings(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException e) {
            Toast.makeText(a, "Home selection unavailable. Use tools/tv_safe.py restore on your computer.",
                    Toast.LENGTH_LONG).show();
            openSettings(a);
        }
    }

    static void openSettings(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException e) {
            Toast.makeText(a, "TV settings unavailable. Use ADB from your computer.", Toast.LENGTH_LONG).show();
        }
    }

    /** Opens an enabled alternative; does not change the default home. */
    static void openOtherHome(Activity a) {
        for (ResolveInfo r : a.getPackageManager().queryIntentActivities(homeIntent(), 0)) {
            if (r.activityInfo == null || PKG.equals(r.activityInfo.packageName)) continue;
            try {
                a.startActivity(homeIntent().setComponent(new ComponentName(
                        r.activityInfo.packageName, r.activityInfo.name)));
                return;
            } catch (RuntimeException ignored) {}
        }
        openSettings(a);
    }
}
