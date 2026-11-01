package io.hestia.tv;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Screens;
import io.hestia.tv.news.NewsView;
import io.hestia.tv.ui.HestiaActivity;

/** The list of headlines; OK opens the article in the TV's browser (an https link from the feed, nothing else). */
public class NewsActivity extends HestiaActivity {
    private NewsView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new NewsView(this, getIntent().getStringExtra(Screens.EXTRA_TITLE), this::openLink);
        setContentView(view);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void openLink(String link) {
        if (link.isEmpty()) {
            Toast.makeText(this, I18n.tr("Sem link para esta notícia.", "No link for this headline."), Toast.LENGTH_SHORT).show();
        } else if (!Launcher.openLink(this, link)) {
            Toast.makeText(this, I18n.tr("Nenhum navegador instalado.", "No web browser installed."), Toast.LENGTH_LONG).show();
        }
    }
}
