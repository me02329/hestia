package io.hestia.tv;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Long press OK on an app card: open it, add/remove/move it in the favorites, hide it from "All apps", the system app
 * info page, its Play Store page, or uninstall it (Android asks for confirmation; system apps can't be).
 */
public class AppMenuActivity extends PanelActivity {
    static final String EXTRA_PACKAGE = "package";

    private String pkg, label = "";

    @Override protected void onCreate(android.os.Bundle savedInstanceState) {
        pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) {
            super.onCreate(savedInstanceState);
            finish();
            return;
        }
        try {
            PackageManager pm = getPackageManager();
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            label = pkg;
        }
        super.onCreate(savedInstanceState);
    }

    @Override String title() {
        return label;
    }

    @Override List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        if (pkg == null) return list;
        List<String> favorites = HomeApps.favorites(this);
        int at = favorites.indexOf(pkg);
        Intent launch = HomeApps.launchIntent(getPackageManager(), pkg);
        if (launch != null) {
            list.add(new Entry("play", Ui.tr("Abrir", "Open"), "", false, () -> {
                HomeApps.recordLaunch(this, pkg);
                if (launch(launch)) finish();
            }));
        }
        if (at < 0) {
            list.add(new Entry("sparkles", Ui.tr("Adicionar aos favoritos", "Add to favorites"), "", false, () -> {
                if (favorites.size() < HomeApps.MAX_FAVORITES) favorites.add(pkg);
                HomeApps.setFavorites(this, favorites);
                finish();
            }));
            boolean hidden = HomeApps.hidden(this).contains(pkg);
            list.add(new Entry("check", hidden ? Ui.tr("Mostrar em Todos os apps", "Show in All apps")
                    : Ui.tr("Ocultar de Todos os apps", "Hide from All apps"), "", false, () -> {
                HomeApps.setHidden(this, pkg, !hidden);
                finish();
            }));
        } else {
            if (at > 0) list.add(new Entry("chevron_left", Ui.tr("Mover para a esquerda", "Move left"), "", false, () -> {
                favorites.add(at - 1, favorites.remove(at));
                HomeApps.setFavorites(this, favorites);
                finish();
            }));
            if (at < favorites.size() - 1) list.add(new Entry("chevron_right", Ui.tr("Mover para a direita", "Move right"), "", false, () -> {
                favorites.add(at + 1, favorites.remove(at));
                HomeApps.setFavorites(this, favorites);
                finish();
            }));
            list.add(new Entry("check", Ui.tr("Remover dos favoritos", "Remove from favorites"), "", false, () -> {
                favorites.remove(pkg);
                HomeApps.setFavorites(this, favorites);
                finish();
            }));
        }
        list.add(new Entry("settings", Ui.tr("Informações do app", "App info"), "", false, () -> {
            if (launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)))) finish();
        }));
        if (HomeApps.launchIntent(getPackageManager(), "com.android.vending") != null) {
            list.add(new Entry("apps", Ui.tr("Ver na Play Store", "View in Play Store"), "", false, () -> {
                Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                        .setPackage("com.android.vending");
                if (launch(store)) finish();
            }));
        }
        if (!isSystem()) {
            list.add(new Entry("bell", Ui.tr("Desinstalar", "Uninstall"), "", false, () -> {
                if (launch(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))) finish();
            }));
        }
        return list;
    }

    private boolean isSystem() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }
}
