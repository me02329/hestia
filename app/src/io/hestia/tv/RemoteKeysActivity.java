package io.hestia.tv;

import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Screens;
import io.hestia.tv.panel.PanelActivity;
import io.hestia.tv.panel.PanelEntry;
import io.hestia.tv.remote.KeyBindings;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Remote buttons: the keys Hestia can set, each with what it opens; OK on one lists every choice (the same
 * activity with EXTRA_KEY), OK on a choice saves it.
 */
public class RemoteKeysActivity extends PanelActivity {
    private String key() {
        return getIntent().getStringExtra(Screens.EXTRA_KEY);
    }

    @Override protected String title() {
        String key = key();
        return key == null ? I18n.tr("Botões do controle", "Remote buttons") : KeyBindings.keyName(key);
    }

    @Override protected String subtitle() {
        return key() == null ? I18n.tr("Em qualquer app, com o serviço de acessibilidade",
                "In any app, with the accessibility service") : "";
    }

    @Override protected List<PanelEntry> entries() {
        List<PanelEntry> list = new ArrayList<>();
        String key = key();
        List<KeyBindings.Choice> choices = KeyBindings.choices(this);
        if (key == null) {
            for (String k : KeyBindings.KEYS) {
                list.add(new PanelEntry("gamepad", KeyBindings.keyName(k), KeyBindings.label(choices, KeyBindings.get(this, k)), false,
                        () -> Launcher.startOver(this, Screens.remoteKeys(this, k))));
            }
            return list;
        }
        if (!KeyBindings.KEYS.contains(key)) return list;
        String current = KeyBindings.get(this, key);
        for (KeyBindings.Choice ch : choices) {
            list.add(new PanelEntry(ch.action.equals(current) ? "check" : ch.symbol, ch.label, () -> {
                KeyBindings.set(this, key, ch.action);
                finish();
            }));
        }
        return list;
    }
}
