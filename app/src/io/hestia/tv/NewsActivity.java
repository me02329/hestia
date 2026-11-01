package io.hestia.tv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
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
    private static final String BROWSER_STUB = "com.android.tv.frameworkpackagestubs";

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

    /** The article in a real browser: the default one if it isn't Android's empty "stub", otherwise the first other. */
    private void openLink(String link) {
        if (link.isEmpty()) {
            Toast.makeText(this, I18n.tr("Sem link para esta notícia.", "No link for this headline."), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent article = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(article, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(BROWSER_STUB)) {
                article.setPackage(pkg);
                break;
            }
        }
        if (!Launcher.start(this, article)) {
            Toast.makeText(this, I18n.tr("Nenhum navegador instalado.", "No web browser installed."), Toast.LENGTH_LONG).show();
        }
    }
}
