package io.hestia.tv.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import io.hestia.tv.R;
import io.hestia.tv.apps.AppCatalog;
import io.hestia.tv.apps.AppTiles;
import io.hestia.tv.apps.Card;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.Background;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Keys;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.Screens;
import io.hestia.tv.data.Feed;
import io.hestia.tv.data.News;
import io.hestia.tv.data.Photos;
import io.hestia.tv.data.Weather;
import io.hestia.tv.remote.KeyRouter;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Click;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.Press;
import io.hestia.tv.ui.Spring;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: the photo (PhotoCarousel), the top (HomeHeader) and three rows of cards (CardRow): the streaming
 * apps, the TV and its inputs (plus settings), and every other app. This class owns the focus, the keys and the
 * lifecycle. D-pad moves the focus, ↑ from the first row reaches the headline, OK opens on release, held down the app's
 * menu, Back returns to the first app. Rows below the second scroll up into view; the top fades out when covered.
 */
public final class HomeView extends CanvasView {
    static final float MARGIN = 96;
    private static final float ROWS_TOP = 540, ROW_STEP = 300;
    private static final long HEADLINE_EVERY = 12_000, TICK_EVERY = 15_000;

    private final Activity activity;
    private final Weather weather;
    private final Photos photos;
    private final News news;
    private final PhotoCarousel carousel;
    private final HomeHeader header;
    private final CardRow.Style rowStyle;
    private final Click click;
    private final KeyRouter router;
    private final TvInputManager tvInputs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<CardRow> rows = new ArrayList<>();
    private final Spring scrollY = new Spring(0.5f, 1f, 0);
    private final Press ok = new Press(), back = new Press();
    private final Paint fill = new Paint(), topScrim = new Paint(), bottomScrim = new Paint();
    private int row;
    private boolean running, newsOn, newsFocus;

    private final Feed.Listener weatherListener = this::invalidate;
    private final Feed.Listener photosListener = this::onPhotos;
    private final Feed.Listener newsListener = this::showHeadlines;

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) { reload(); }
        @Override public void onInputAdded(String inputId) { reload(); }
        @Override public void onInputRemoved(String inputId) { reload(); }
    };

    /** Apps installed, updated or removed while the home screen is up: fresh cards and rows right away. */
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (intent.getData() != null) AppTiles.forget(intent.getData().getSchemeSpecificPart());
            reload();
        }
    };

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!newsFocus && header.nextHeadline()) wake();
            handler.postDelayed(this, HEADLINE_EVERY);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            weather.refreshIfStale();
            photos.refreshIfStale();
            if (newsOn) news.refreshIfStale();
            if (carousel.due()) carousel.next();
            invalidate();   // the clock
            handler.postDelayed(this, TICK_EVERY);
        }
    };

    public HomeView(Activity a) {
        super(a);
        activity = a;
        weather = Weather.get(a);
        photos = Photos.get(a);
        news = News.get(a);
        click = new Click(a);
        router = new KeyRouter(a);
        tvInputs = (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
        Drawable logo = a.getDrawable(R.drawable.hestia_logo);
        carousel = new PhotoCarousel(photos, this::wake);
        header = new HomeHeader(a, logo, weather);
        rowStyle = new CardRow.Style(a, logo);
        topScrim.setShader(new LinearGradient(0, 0, 0, 380, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        bottomScrim.setShader(new LinearGradient(0, 380, 0, H, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
        setRows(AppCatalog.favoriteCards(a), AppCatalog.tvCards(a), AppCatalog.otherApps(a));
    }

    // MARK: - Lifecycle (called by HomeActivity)

    public void resume() {
        if (running) return;
        running = true;
        weather.addListener(weatherListener);
        photos.addListener(photosListener);
        newsOn = Prefs.newsEnabled(getContext());
        if (!newsOn && newsFocus) {   // headlines turned off in the settings while focused
            newsFocus = false;
            retarget(true);
        }
        if (newsOn) {
            news.addListener(newsListener);
            handler.postDelayed(rotate, HEADLINE_EVERY);
        }
        showHeadlines();
        if (tvInputs != null) tvInputs.registerCallback(inputCallback, handler);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        getContext().registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
        reload();
        if (carousel.due()) carousel.next();
        handler.post(tick);
        invalidate();
    }

    public void pause() {
        running = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(rotate);
        weather.removeListener(weatherListener);
        photos.removeListener(photosListener);
        news.removeListener(newsListener);
        router.cancel();
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
        try {
            getContext().unregisterReceiver(packages);
        } catch (IllegalArgumentException ignored) {   // pause() before any resume()
        }
    }

    public void destroy() {
        pause();
        click.release();
    }

    /** Home pressed while already home: back to the first app. */
    public void reset(boolean animated) {
        row = 0;
        newsFocus = false;
        for (CardRow r : rows) r.index = 0;
        retarget(!animated);
    }

    private void onPhotos() {
        if (carousel.isEmpty()) carousel.next();
    }

    private void showHeadlines() {
        header.setHeadlines(newsOn ? news.recent() : new ArrayList<>());
        invalidate();
    }

    // MARK: - Rows

    private void reload() {
        final Context c = getContext().getApplicationContext();
        Background.run(() -> {
            final List<Card> favorites = AppCatalog.favoriteCards(c);
            final List<Card> tv = AppCatalog.tvCards(c);
            final List<Card> others = AppCatalog.otherApps(c);
            Background.onMain(() -> setRows(favorites, tv, others));
        });
    }

    /** Replaces the rows, keeping the focus on the same card when it's still there. */
    private void setRows(List<Card> favorites, List<Card> tv, List<Card> others) {
        String focusedKey = focused() != null ? focused().key : null;
        rows.clear();
        addRow(I18n.tr("Apps", "Apps"), favorites);
        addRow(I18n.tr("TV e entradas", "TV & inputs"), tv);
        addRow(I18n.tr("Todos os apps", "All apps"), others);
        row = 0;
        if (focusedKey != null) {
            for (int r = 0; r < rows.size(); r++) {
                List<Card> cards = rows.get(r).cards;
                for (int i = 0; i < cards.size(); i++) {
                    if (focusedKey.equals(cards.get(i).key)) {
                        row = r;
                        rows.get(r).index = i;
                    }
                }
            }
        }
        retarget(true);
    }

    private void addRow(String title, List<Card> cards) {
        if (!cards.isEmpty()) rows.add(new CardRow(title, cards, rowStyle));
    }

    private Card focused() {
        if (row >= rows.size()) return null;
        CardRow r = rows.get(row);
        return r.index < r.cards.size() ? r.cards.get(r.index) : null;
    }

    // MARK: - Drawing

    @Override protected void drawScene(Canvas c) {
        carousel.draw(c);
        c.drawRect(0, 0, W, 380, topScrim);
        c.drawRect(0, 380, W, H, bottomScrim);
        float covered = Colors.clamp(scrollY.value / ROW_STEP);
        if (covered > 0) {
            fill.setColor(Colors.alpha(0xFF000000, 0.45f * covered));
            c.drawRect(0, 0, W, H, fill);
        }
        header.draw(c, 1 - covered, carousel.title());
        for (int r = 0; r < rows.size(); r++) {
            float top = rowTop(r);
            if (top <= H && top + ROW_STEP >= 0) rows.get(r).draw(c, top, r == row);
        }
    }

    private float rowTop(int r) {
        return ROWS_TOP + r * ROW_STEP - scrollY.value;
    }

    // MARK: - Motion

    /** Points every spring at the current focus. */
    private void retarget(boolean snap) {
        header.setNewsFocus(newsFocus, snap);
        scrollY.target = Math.max(0, row - 1) * ROW_STEP;
        if (snap) scrollY.snap(scrollY.target);
        for (int r = 0; r < rows.size(); r++) rows.get(r).retarget(!newsFocus && r == row, snap);
        wake();
    }

    @Override protected boolean animate(float dt) {
        boolean moving = scrollY.step(dt) | header.animate(dt) | carousel.fading();
        for (CardRow r : rows) moving |= r.animate(dt);
        return moving;
    }

    @Override protected void onSettled() {
        carousel.releaseOld();
    }

    // MARK: - Keys: the arrows, OK, Back and the buttons Hestia can set (other system keys pass through to the system)

    public boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        if (Keys.isOk(code)) {
            int press = ok.track(e);
            if (press == Press.CLICK) open();
            else if (press == Press.LONG) menu();
            return true;
        }
        if (router.onSettingsKey(e) || router.onTvKey(e)) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) move(0, 1);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (back.track(e) == Press.CLICK && (newsFocus || row != 0 || rows.isEmpty() || rows.get(0).index != 0)) {
                    reset(true);
                    click.move();
                }
                return true;
            default:
                return false;
        }
    }

    private void move(int dx, int dy) {
        if (newsFocus) {
            if (dy <= 0) return;
            newsFocus = false;
            click.move();
            retarget(false);
            return;
        }
        if (row == 0 && dy < 0 && newsOn && header.hasHeadlines()) {
            newsFocus = true;
            click.move();
            retarget(false);
            return;
        }
        if (rows.isEmpty()) return;
        CardRow r = rows.get(row);
        if (dx != 0) {
            int next = r.index + dx;
            if (next < 0 || next >= r.cards.size()) return;
            r.index = next;
        } else {
            int next = row + dy;
            if (next < 0 || next >= rows.size()) return;
            // Land on the card under the one we leave.
            CardRow to = rows.get(next);
            to.index = to.cardAt(r.cardX(r.index) + CardRow.CARD_W / 2);
            row = next;
        }
        click.move();
        retarget(false);
    }

    /** Long press OK on an app card: its menu (favorites, hide, info, Play Store, uninstall). */
    private void menu() {
        Card card = newsFocus ? null : focused();
        if (card == null || card.kind != Card.Kind.APP) return;
        click.open();
        Launcher.startOver(activity, Screens.appMenu(activity, card.pkg));
    }

    private void open() {
        if (newsFocus) {
            String title = header.currentHeadlineTitle();
            if (title == null) return;
            click.open();
            Launcher.startOver(activity, Screens.news(activity, title));
            return;
        }
        Card card = focused();
        if (card == null) return;
        CardRow r = rows.get(row);
        float x = r.cardX(r.index), y = rowTop(row) + CardRow.TITLE_GAP;
        click.open();
        if (!Launcher.openCard(activity, card, this, Math.round(x * scale), Math.round(y * scale),
                Math.round(CardRow.CARD_W * scale), Math.round(CardRow.CARD_H * scale))) {
            AppLog.w("couldn't open " + card.label);
        }
    }
}
