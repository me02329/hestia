package io.hestia.tv;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

import io.hestia.tv.apps.HomeRole;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.I18n;
import io.hestia.tv.data.Feed;
import io.hestia.tv.data.News;
import io.hestia.tv.data.Photos;
import io.hestia.tv.data.Updates;
import io.hestia.tv.data.Weather;
import io.hestia.tv.panel.PanelActivity;
import io.hestia.tv.panel.PanelEntry;
import io.hestia.tv.power.SleepDelay;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Status: whether each optional access really works right now, and how fresh the data is. Read only: the
 * accesses are given over ADB (SAFE-DEPLOYMENT.md), and they can drop silently after an update.
 */
public class StatusActivity extends PanelActivity {
    @Override protected String title() {
        return I18n.tr("Estado", "Status");
    }

    @Override protected String subtitle() {
        return "Hestia " + Updates.get(this).installed();
    }

    @Override protected void onResume() {
        Updates.get(this).refreshIfStale();
        super.onResume();
    }

    @Override protected List<PanelEntry> entries() {
        List<PanelEntry> list = new ArrayList<>();
        String update = Updates.get(this).available();
        if (!update.isEmpty()) {
            list.add(new PanelEntry("sparkles", I18n.tr("Nova versão", "New version"), update, false,
                    () -> Launcher.openLink(this, Updates.PAGE)));
        }
        add(list, I18n.tr("Tela inicial padrão", "Default home screen"), HomeRole.isDefault(this));
        add(list, I18n.tr("Botões em qualquer app", "Buttons in any app"), keysServiceBound());
        add(list, I18n.tr("Leitor do painel", "Panel's player"), playerAllowed());
        add(list, I18n.tr("Atrasos de suspensão", "Sleep delays"), SleepDelay.canChange(this));
        add(list, I18n.tr("Tela de descanso", "Screensaver"), screensaverIsOurs());
        addFeed(list, I18n.tr("Meteorologia", "Weather"), Weather.get(this));
        addFeed(list, I18n.tr("Notícias de cibersegurança", "Cyber news"), News.get(this));
        addFeed(list, I18n.tr("Fotos do dia", "Photos of the day"), Photos.get(this));
        return list;
    }

    private void add(List<PanelEntry> list, String label, boolean on) {
        list.add(new PanelEntry(on ? "check" : "bell", label, on ? I18n.tr("Ativo", "Working") : I18n.tr("Inativo", "Not working"),
                !on, () -> { }));
    }

    private void addFeed(List<PanelEntry> list, String label, Feed feed) {
        String age = feed.fetchedAt() == 0 ? "—" : News.age(feed.fetchedAt());
        list.add(new PanelEntry(feed.failing() ? "bell" : "sync", label,
                feed.failing() ? I18n.tr("Falha · ", "Failing · ") + age : age, feed.failing(), () -> { }));
    }

    /** Bound, not just enabled: an update can leave it listed but not connected. */
    private boolean keysServiceBound() {
        ComponentName ours = new ComponentName(this, RemoteKeysService.class);
        for (AccessibilityServiceInfo info : getSystemService(AccessibilityManager.class)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (ours.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        }
        return false;
    }

    private boolean playerAllowed() {
        return getSystemService(NotificationManager.class)
                .isNotificationListenerAccessGranted(new ComponentName(this, MediaListener.class));
    }

    private boolean screensaverIsOurs() {
        try {
            String current = Settings.Secure.getString(getContentResolver(), "screensaver_components");
            return current != null && current.contains(getPackageName());
        } catch (SecurityException e) {
            return false;
        }
    }
}
