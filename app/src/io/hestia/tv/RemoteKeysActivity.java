package io.hestia.tv;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Remote buttons: the three keys Hestia can set, each with what it opens; OK on one lists every choice
 * (the same activity with EXTRA_KEY), OK on a choice saves it.
 */
public class RemoteKeysActivity extends PanelActivity {
    static final String EXTRA_KEY = "key";

    private String key() {
        return getIntent().getStringExtra(EXTRA_KEY);
    }

    @Override String title() {
        String key = key();
        return key == null ? Ui.tr("Botões do controle", "Remote buttons") : RemoteKeys.keyName(key);
    }

    @Override String subtitle() {
        return key() == null ? Ui.tr("Na tela inicial; Início segurado em qualquer lugar",
                "On the home screen; Home held down anywhere") : "";
    }

    @Override List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        String key = key();
        if (key == null) {
            for (String k : RemoteKeys.KEYS) {
                list.add(new Entry("gamepad", RemoteKeys.keyName(k), RemoteKeys.label(this, RemoteKeys.get(this, k)), false,
                        () -> startActivity(new Intent(this, RemoteKeysActivity.class).putExtra(EXTRA_KEY, k))));
            }
            return list;
        }
        if (!java.util.Arrays.asList(RemoteKeys.KEYS).contains(key)) return list;
        String current = RemoteKeys.get(this, key);
        for (RemoteKeys.Choice ch : RemoteKeys.choices(this)) {
            list.add(new Entry(ch.action.equals(current) ? "check" : ch.symbol, ch.label, "", false, () -> {
                RemoteKeys.set(this, key, ch.action);
                finish();
            }));
        }
        return list;
    }
}
