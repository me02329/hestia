package io.hestia.tv;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import io.hestia.tv.apps.AppCatalog;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.Screens;
import io.hestia.tv.media.PlayerCard;
import io.hestia.tv.panel.PanelActivity;
import io.hestia.tv.panel.PanelEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Long press OK on an app card: open it, add/remove/move it in the favorites, hide it from "All apps", the system app
 * info page, its Play Store page, or uninstall it (Android asks for confirmation; system apps can't be).
 */
public class AppMenuActivity extends PanelActivity {
    private String pkg, label = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        pkg = getIntent().getStringExtra(Screens.EXTRA_PACKAGE);
        boolean valid = AppCatalog.isValidPackage(pkg);
        if (!valid) pkg = null;
        else label = PlayerCard.appLabel(this, pkg);
        super.onCreate(savedInstanceState);
        if (!valid) finish();
    }

    @Override protected String title() {
        return label;
    }

    @Override protected List<PanelEntry> entries() {
        List<PanelEntry> list = new ArrayList<>();
        if (pkg == null) return list;
        List<String> favorites = AppCatalog.favorites(this);
        int at = favorites.indexOf(pkg);
        if (Launcher.appIntent(getPackageManager(), pkg) != null) {
            list.add(new PanelEntry("play", I18n.tr("Abrir", "Open"), () -> {
                if (Launcher.openApp(this, pkg)) finish();
            }));
        }
        if (at < 0) {
            list.add(new PanelEntry("sparkles", I18n.tr("Adicionar aos favoritos", "Add to favorites"), () -> {
                if (favorites.size() < AppCatalog.MAX_FAVORITES) favorites.add(pkg);
                saveAndClose(favorites);
            }));
            boolean hidden = Prefs.hidden(this).contains(pkg);
            list.add(new PanelEntry("check", hidden ? I18n.tr("Mostrar em Todos os apps", "Show in All apps")
                    : I18n.tr("Ocultar de Todos os apps", "Hide from All apps"), () -> {
                AppCatalog.setHidden(this, pkg, !hidden);
                finish();
            }));
        } else {
            if (at > 0) list.add(new PanelEntry("chevron_left", I18n.tr("Mover para a esquerda", "Move left"), () -> {
                favorites.add(at - 1, favorites.remove(at));
                saveAndClose(favorites);
            }));
            if (at < favorites.size() - 1) list.add(new PanelEntry("chevron_right", I18n.tr("Mover para a direita", "Move right"), () -> {
                favorites.add(at + 1, favorites.remove(at));
                saveAndClose(favorites);
            }));
            list.add(new PanelEntry("check", I18n.tr("Remover dos favoritos", "Remove from favorites"), () -> {
                favorites.remove(pkg);
                saveAndClose(favorites);
            }));
        }
        list.add(new PanelEntry("settings", I18n.tr("Informações do app", "App info"), () -> {
            if (Launcher.startOrSay(this, Launcher.appDetails(pkg))) finish();
        }));
        if (AppCatalog.hasPlayStore(this)) {
            list.add(new PanelEntry("apps", I18n.tr("Ver na Play Store", "View in Play Store"), () -> {
                if (Launcher.startOrSay(this, Launcher.playStorePage(pkg))) finish();
            }));
        }
        if (!isSystem()) {
            list.add(new PanelEntry("bell", I18n.tr("Desinstalar", "Uninstall"), () -> {
                if (Launcher.startOrSay(this, Launcher.uninstall(pkg))) finish();
            }));
        }
        return list;
    }

    private void saveAndClose(List<String> favorites) {
        AppCatalog.setFavorites(this, favorites);
        finish();
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
