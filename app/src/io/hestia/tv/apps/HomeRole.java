package io.hestia.tv.apps;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;

/** Read-only home detection, and ways out of Hestia. No ADB client or privileged mutation path. */
public final class HomeRole {
    private HomeRole() {}

    public static final String PACKAGE = "io.hestia.tv";

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    public static boolean isDefault(Context c) {
        ResolveInfo r = c.getPackageManager().resolveActivity(homeIntent(), 0);
        return r != null && r.activityInfo != null && PACKAGE.equals(r.activityInfo.packageName);
    }

    public static void openHomeSettings(Activity a) {
        if (Launcher.startOver(a, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        Toast.makeText(a, "Home selection unavailable. Use tools/tv_safe.py restore on your computer.",
                Toast.LENGTH_LONG).show();
        openTvSettings(a);
    }

    public static void openTvSettings(Activity a) {
        if (Launcher.startOver(a, Launcher.tvSettings())) return;
        Toast.makeText(a, "TV settings unavailable. Use ADB from your computer.", Toast.LENGTH_LONG).show();
    }

    public static void openAppList(Activity a) {
        if (!Launcher.startOver(a, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))) openTvSettings(a);
    }

    /** Opens an enabled alternative; does not change the default home. */
    public static void openOtherHome(Activity a) {
        for (ResolveInfo r : a.getPackageManager().queryIntentActivities(homeIntent(), 0)) {
            if (r.activityInfo == null || PACKAGE.equals(r.activityInfo.packageName)) continue;
            Intent other = homeIntent().setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
            if (Launcher.startOver(a, other)) return;
        }
        openTvSettings(a);
    }
}
