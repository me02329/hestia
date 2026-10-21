package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen, drawn on a 1920 × 1080 canvas (scaled to the screen):
 *  - a photo of the day in full screen (Photos), a new one every 2 minutes with a crossfade;
 *  - top left the time and date, top center the Hestia emblem and name, top right the weather and the photo's title;
 *  - under the date, the latest cybersecurity headline (News), a new one every 12 s (can be turned off in settings);
 *    ↑ from the first row focuses it, OK opens the list of headlines (NewsActivity);
 *  - three rows of 16:9 cards: the streaming apps, the TV and its inputs (plus settings), and every other app.
 * D-pad moves the focus (the focused card grows and gets a white outline), OK opens it on release, Back returns to the
 * first app. Rows below the second scroll up into view; the time and weather fade out when they would be covered.
 */
final class HomeView extends View implements Choreographer.FrameCallback, HomeData.Listener, Photos.Listener,
        News.Listener {
    private static final float W = 1920, H = 1080;
    private static final float MARGIN = 96, ROWS_TOP = 540, ROW_STEP = 300, TITLE_GAP = 26;
    private static final float CARD_W = 320, CARD_H = 180, GAP = 36, RADIUS = 18, FOCUS_GROW = 0.1f;
    private static final long PHOTO_EVERY = 120_000, FADE_MS = 1_500, HEADLINE_EVERY = 12_000, HEADLINE_MS = 600;
    private static final float LOGO_TOP = 44, LOGO_H = 132;
    /** The TV button of TCL remotes (scan 0x0c005b mapped by TCL's key layout; not an Android KEYCODE_ constant). */
    private static final int KEYCODE_TCL_TV = 4001;
    /** Cards fade out over this width at the screen edges instead of being cut. */
    private static final float EDGE_FADE = 160;

    /** One row: its title, cards, focused index and horizontal scroll. */
    private static final class Row {
        final String title;
        final List<HomeApps.Item> items;
        final List<Spring> pop = new ArrayList<>();
        final Spring scroll = new Spring(0.45f, 1f, 0);
        int index;

        Row(String title, List<HomeApps.Item> items) {
            this.title = title;
            this.items = items;
            for (int i = 0; i < items.size(); i++) pop.add(new Spring(0.28f, 0.82f, 0));
        }
    }

    private final Activity activity;
    private final HomeData data;
    private final Photos photos;
    private final News news;
    private List<News.Headline> headlines = new ArrayList<>();
    private int headline;
    private long headlineAt;
    private boolean newsOn, newsFocus, okLong;
    private final Spring newsPop = new Spring(0.28f, 0.85f, 0);
    private final Click click;
    private final TvInputManager tvInputs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

    private final List<Row> rows = new ArrayList<>();
    private int row;
    private final Spring scrollY = new Spring(0.5f, 1f, 0);
    private boolean running, framePending, okDown, backDown;
    private long lastNanos;
    private float k = 1;

    private Bitmap photo, oldPhoto;
    private Photos.Photo shown;
    private long fadeStart, photoAt;
    private int photoIndex = -1;

    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topScrim = new Paint(), bottomScrim = new Paint();
    private final Paint edgeMask = new Paint(), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pillText, sourcePaint, brandPaint, hintPaint;
    private final Paint logoHalo = new Paint();
    private final Paint newsScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable logo;
    private TextPaint headlinePaint;
    private Paint clockPaint, datePaint, tempPaint, rowPaint;
    private TextPaint smallPaint, cardLabel;
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final android.graphics.Matrix cardMatrix = new android.graphics.Matrix();
    private final android.util.SparseArray<LinearGradient> cardShaders = new android.util.SparseArray<>();

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) { reload(); }
        @Override public void onInputAdded(String inputId) { reload(); }
        @Override public void onInputRemoved(String inputId) { reload(); }
    };

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (headlines.size() > 1 && !newsFocus) {
                headline = (headline + 1) % headlines.size();
                headlineAt = SystemClock.uptimeMillis();
                schedule();
            }
            handler.postDelayed(this, HEADLINE_EVERY);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            photos.refreshIfStale();
            if (newsOn) news.refreshIfStale();
            if (SystemClock.uptimeMillis() - photoAt >= PHOTO_EVERY) nextPhoto();
            invalidate();   // the clock
            handler.postDelayed(this, 15_000);
        }
    };

    HomeView(Activity a) {
        super(a);
        activity = a;
        data = HomeData.get(a);
        photos = Photos.get(a);
        news = News.get(a);
        click = new Click(a);
        tvInputs = (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
        logo = a.getDrawable(R.drawable.hestia_logo);
        setFocusable(true);
        makePaints();
        setRows(HomeApps.shelf(a), inputsRow(a), othersRow(a));
    }

    // MARK: - Lifecycle (called by HomeActivity)

    void resume() {
        if (running) return;
        running = true;
        lastNanos = 0;
        data.addListener(this);
        photos.addListener(this);
        newsOn = News.enabled(getContext());
        if (!newsOn && newsFocus) {   // headlines turned off in the settings while focused
            newsFocus = false;
            retarget(true);
        }
        if (newsOn) {
            news.addListener(this);
            onNews();
            handler.postDelayed(rotate, HEADLINE_EVERY);
        }
        if (tvInputs != null) tvInputs.registerCallback(inputCallback, handler);
        reload();
        if (photo == null || SystemClock.uptimeMillis() - photoAt >= PHOTO_EVERY) nextPhoto();
        handler.post(tick);
        invalidate();
    }

    void pause() {
        running = false;
        handler.removeCallbacks(tick);
        data.removeListener(this);
        photos.removeListener(this);
        news.removeListener(this);
        handler.removeCallbacks(rotate);
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
    }

    void destroy() {
        pause();
        click.release();
    }

    /** Home pressed while already home: back to the first app. */
    void reset(boolean animated) {
        row = 0;
        newsFocus = false;
        for (Row r : rows) r.index = 0;
        retarget(!animated);
    }

    @Override public void onData() { invalidate(); }

    @Override public void onPhotos() {
        if (photo == null) nextPhoto();
    }

    @Override public void onNews() {
        String current = headline < headlines.size() ? headlines.get(headline).title : null;
        headlines = news.recent();
        headline = 0;
        for (int i = 0; i < headlines.size(); i++) if (headlines.get(i).title.equals(current)) headline = i;
        invalidate();
    }

    // MARK: - Rows

    private static List<HomeApps.Item> inputsRow(Context c) {
        List<HomeApps.Item> list = HomeApps.allInputs(c);
        HomeApps.Item search = HomeApps.search(c);
        if (search != null) list.add(search);
        HomeApps.Item store = HomeApps.playStore(c);
        if (store != null) list.add(store);
        list.addAll(HomeApps.settingsShortcuts());
        return list;
    }

    private static List<HomeApps.Item> othersRow(Context c) {
        return HomeApps.others(c);
    }

    private void reload() {
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            final List<HomeApps.Item> apps = HomeApps.shelf(c);
            final List<HomeApps.Item> inputs = inputsRow(c);
            final List<HomeApps.Item> others = othersRow(c);
            handler.post(() -> setRows(apps, inputs, others));
        });
    }

    /** Replaces the rows, keeping the focus on the same card when it's still there. */
    private void setRows(List<HomeApps.Item> apps, List<HomeApps.Item> inputs, List<HomeApps.Item> others) {
        String focusedKey = focused() != null ? focused().key : null;
        rows.clear();
        if (!apps.isEmpty()) rows.add(new Row(Ui.tr("Apps", "Apps"), apps));
        if (!inputs.isEmpty()) rows.add(new Row(Ui.tr("TV e entradas", "TV & inputs"), inputs));
        if (!others.isEmpty()) rows.add(new Row(Ui.tr("Todos os apps", "All apps"), others));
        row = 0;
        if (focusedKey != null) {
            for (int r = 0; r < rows.size(); r++) {
                List<HomeApps.Item> items = rows.get(r).items;
                for (int i = 0; i < items.size(); i++) {
                    if (focusedKey.equals(items.get(i).key)) {
                        row = r;
                        rows.get(r).index = i;
                    }
                }
            }
        }
        retarget(true);
    }

    private HomeApps.Item focused() {
        if (row >= rows.size()) return null;
        Row r = rows.get(row);
        return r.index < r.items.size() ? r.items.get(r.index) : null;
    }

    // MARK: - Photo

    private void nextPhoto() {
        List<Photos.Photo> list = photos.list();
        photoAt = SystemClock.uptimeMillis();
        if (list.isEmpty()) {
            photos.refreshIfStale();
            return;
        }
        photoIndex = (photoIndex + 1) % list.size();
        final Photos.Photo p = list.get(photoIndex);
        LOADER.execute(() -> {
            final Bitmap b = Photos.load(p.file, (int) W, (int) H);
            if (b == null) return;
            b.prepareToDraw();
            handler.post(() -> {
                if (oldPhoto != null && oldPhoto != photo) oldPhoto.recycle();
                oldPhoto = photo;
                photo = b;
                shown = p;
                fadeStart = SystemClock.uptimeMillis();
                schedule();
            });
        });
    }

    // MARK: - Drawing

    private void makePaints() {
        clockPaint = Ui.numbers(Ui.light, 128, Color.WHITE);
        datePaint = Ui.text(Ui.medium, 32, 0xE6FFFFFF);
        tempPaint = Ui.numbers(Ui.light, 64, Color.WHITE);
        tempPaint.setTextAlign(Paint.Align.RIGHT);
        smallPaint = new TextPaint(Ui.text(Ui.regular, 26, 0xCCFFFFFF));
        smallPaint.setTextAlign(Paint.Align.RIGHT);
        rowPaint = Ui.text(Ui.semibold, 30, 0xF2FFFFFF);
        cardLabel = new TextPaint(Ui.text(Ui.medium, 26, Color.WHITE));
        cardLabel.setTextAlign(Paint.Align.CENTER);
        for (Paint p : new Paint[] {clockPaint, datePaint, tempPaint, smallPaint, rowPaint}) {
            p.setShadowLayer(12, 0, 2, 0x99000000);
        }
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(5);
        outline.setColor(Color.WHITE);
        shadow.setColor(0xFF000000);
        shadow.setShadowLayer(30, 0, 14, 0xAA000000);
        topScrim.setShader(new LinearGradient(0, 0, 0, 380, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        pillText = Ui.text(Ui.semibold, 20, Color.WHITE);
        pillText.setLetterSpacing(0.08f);
        sourcePaint = Ui.text(Ui.medium, 24, 0xBFFFFFFF);
        headlinePaint = new TextPaint(Ui.text(Ui.medium, 36, Color.WHITE));
        for (Paint p : new Paint[] {sourcePaint, headlinePaint}) p.setShadowLayer(12, 0, 2, 0x99000000);
        pill.setColor(0xD9E5484D);
        hintPaint = Ui.text(Ui.medium, 22, Color.WHITE);
        hintPaint.setTextAlign(Paint.Align.RIGHT);
        brandPaint = Ui.text(Ui.semibold, 24, 0xFFE9C587);
        brandPaint.setTextAlign(Paint.Align.CENTER);
        brandPaint.setLetterSpacing(0.35f);
        brandPaint.setShadowLayer(12, 0, 2, 0xCC000000);
        // Soft dark band behind the headline: white text stays readable on bright photos.
        newsScrim.setShader(new LinearGradient(0, 0, 1750, 0, new int[] {0xA6000000, 0x73000000, 0x00000000},
                new float[] {0, 0.6f, 1}, Shader.TileMode.CLAMP));
        newsScrim.setMaskFilter(new android.graphics.BlurMaskFilter(48, android.graphics.BlurMaskFilter.Blur.NORMAL));
        logoHalo.setShader(new RadialGradient(W / 2, LOGO_TOP + LOGO_H / 2, LOGO_H, 0x8C000000, 0x00000000,
                Shader.TileMode.CLAMP));
        edgeMask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        bottomScrim.setShader(new LinearGradient(0, 380, 0, H, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        k = width / W;
    }

    @Override protected void onDraw(Canvas c) {
        c.save();
        c.scale(k, k);
        drawPhoto(c);
        c.drawRect(0, 0, W, 380, topScrim);
        c.drawRect(0, 380, W, H, bottomScrim);
        float covered = Ui.clamp(scrollY.value / ROW_STEP);
        if (covered > 0) {
            fill.setColor(Ui.alpha(0xFF000000, 0.45f * covered));
            c.drawRect(0, 0, W, H, fill);
        }
        drawHeader(c, 1 - covered);
        for (int r = 0; r < rows.size(); r++) drawRow(c, r);
        c.restore();
    }

    private void drawPhoto(Canvas c) {
        if (photo == null) {
            c.drawColor(0xFF0E1420);
            return;
        }
        float f = Ui.clamp((SystemClock.uptimeMillis() - fadeStart) / (float) FADE_MS);
        if (oldPhoto != null && f < 1) {
            image.setAlpha(255);
            c.drawBitmap(oldPhoto, 0, 0, image);
        }
        image.setAlpha(Math.round(255 * f));
        c.drawBitmap(photo, 0, 0, image);
        image.setAlpha(255);
    }

    private void drawHeader(Canvas c, float alpha) {
        if (alpha <= 0.01f) return;
        int a = Math.round(255 * alpha);
        long now = System.currentTimeMillis();
        clockPaint.setAlpha(a);
        datePaint.setAlpha(Math.round(0.9f * a));
        c.drawText(Ui.clock(now), MARGIN - Ui.leftBearing(clockPaint, Ui.clock(now)), 180, clockPaint);
        String date = Ui.date(now);
        c.drawText(Character.toUpperCase(date.charAt(0)) + date.substring(1), MARGIN, 236, datePaint);

        drawHeadline(c, alpha);
        drawBrand(c, alpha);

        HomeData.Weather wx = data.weather;
        float right = W - MARGIN;
        smallPaint.setAlpha(Math.round(0.8f * a));
        if (!Double.isNaN(wx.temp)) {
            tempPaint.setAlpha(a);
            String temp = HomeData.degrees(wx.temp);
            c.drawText(temp, right, 150, tempPaint);
            float iconX = right - tempPaint.measureText(temp) - 44;
            Symbols.draw(getContext(), c, Symbols.weather(wx.code, wx.isDay), iconX, 150 - Ui.halfCap(tempPaint),
                    56, Ui.alpha(Color.WHITE, alpha));
            String where = Config.CITY.trim();
            String line = HomeData.condition(wx.code, wx.isDay);
            if (!where.isEmpty()) line = where + " · " + line;
            c.drawText(line, right, 196, smallPaint);
        }
        if (shown != null && !shown.title.isEmpty()) {
            smallPaint.setAlpha(Math.round(0.6f * a));
            c.drawText(TextUtils.ellipsize(shown.title, smallPaint, 700,
                    TextUtils.TruncateAt.END).toString(), right, 236, smallPaint);
        }
    }

    /** The emblem (vector, transparent) over a soft shadow, and the name in spaced gold capitals. */
    private void drawBrand(Canvas c, float alpha) {
        if (logo == null) return;
        logoHalo.setAlpha(Math.round(255 * alpha));
        c.drawRect(W / 2 - LOGO_H, LOGO_TOP - LOGO_H / 2, W / 2 + LOGO_H, LOGO_TOP + LOGO_H * 1.5f, logoHalo);
        float lw = LOGO_H * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
        logo.setBounds(Math.round(W / 2 - lw / 2), Math.round(LOGO_TOP), Math.round(W / 2 + lw / 2), Math.round(LOGO_TOP + LOGO_H));
        logo.setAlpha(Math.round(255 * alpha));
        logo.draw(c);
        brandPaint.setAlpha(Math.round(255 * alpha));
        c.drawText("HESTIA", W / 2 + 4, LOGO_TOP + LOGO_H + 38, brandPaint);   // + 4: the trailing letter spacing
    }

    /** The current headline sliding up over the previous one. */
    private void drawHeadline(Canvas c, float alpha) {
        if (!newsOn || headlines.isEmpty()) return;
        newsScrim.setAlpha(Math.round(255 * alpha));
        c.drawRoundRect(-60, 262, 1750, 404, 60, 60, newsScrim);
        float t = Ui.clamp((SystemClock.uptimeMillis() - headlineAt) / (float) HEADLINE_MS);
        t = t * t * (3 - 2 * t);
        String label = "CYBER";
        float pw = pillText.measureText(label) + 28;
        rect.set(MARGIN, 284, MARGIN + pw, 320);
        pill.setAlpha(Math.round(217 * alpha));
        c.drawRoundRect(rect, 18, 18, pill);
        pillText.setAlpha(Math.round(255 * alpha));
        c.drawText(label, MARGIN + 14, rect.centerY() + Ui.halfCap(pillText), pillText);
        float f = Ui.clamp(newsPop.value);
        if (f > 0.01f) {
            float tw = Math.min(W - 2 * MARGIN, headlinePaint.measureText(headlines.get(headline).title));
            rect.set(MARGIN - 28, 262, MARGIN + Math.max(tw, 760) + 28, 398);
            fill.setColor(Ui.alpha(0x40FFFFFF, f));
            c.drawRoundRect(rect, 26, 26, fill);
            outline.setAlpha(Math.round(255 * f));
            c.drawRoundRect(rect, 26, 26, outline);
            hintPaint.setAlpha(Math.round(230 * f * alpha));
            c.drawText(Ui.tr("OK · todas as notícias", "OK · all headlines"), rect.right - 24, 310, hintPaint);
        }
        int previous = (headline - 1 + headlines.size()) % headlines.size();
        if (t < 1 && headlines.size() > 1) drawHeadlineText(c, headlines.get(previous), MARGIN + pw + 16, -24 * t, (1 - t) * alpha);
        drawHeadlineText(c, headlines.get(headline), MARGIN + pw + 16, 24 * (1 - t), t * alpha);
    }

    private void drawHeadlineText(Canvas c, News.Headline h, float sourceX, float dy, float alpha) {
        if (alpha <= 0.01f) return;
        sourcePaint.setAlpha(Math.round(191 * alpha));
        c.drawText(h.source + " · " + News.age(h.at), sourceX, 302 + Ui.halfCap(sourcePaint) + dy, sourcePaint);
        headlinePaint.setAlpha(Math.round(255 * alpha));
        c.drawText(TextUtils.ellipsize(h.title, headlinePaint, W - 2 * MARGIN, TextUtils.TruncateAt.END).toString(),
                MARGIN, 372 + dy, headlinePaint);
    }

    private float rowTop(int r) {
        return ROWS_TOP + r * ROW_STEP - scrollY.value;
    }

    private float cardX(Row r, int i) {
        return MARGIN + i * (CARD_W + GAP) - r.scroll.value;
    }

    private void drawRow(Canvas c, int index) {
        Row r = rows.get(index);
        float top = rowTop(index);
        if (top > H || top + ROW_STEP < 0) return;
        boolean active = index == row;
        rowPaint.setAlpha(active ? 255 : 190);
        c.drawText(r.title, MARGIN, top, rowPaint);
        float cardTop = top + TITLE_GAP;
        // The cards go into a layer whose edges are then faded (DST_IN), so they don't stop dead at the screen edge.
        float left = Ui.clamp(r.scroll.value / 80f);
        float overflow = MARGIN + r.items.size() * (CARD_W + GAP) - GAP - r.scroll.value - (W - MARGIN);
        float right = Ui.clamp(overflow / 80f);
        boolean fade = left > 0 || right > 0;
        int layer = fade ? c.saveLayer(0, cardTop - 60, W, cardTop + CARD_H + 60, null) : -1;
        // Unfocused cards first, so the focused one (bigger) sits on top of its neighbours.
        for (int i = 0; i < r.items.size(); i++) {
            if (i != r.index || !active) drawCard(c, r, i, cardTop);
        }
        if (active && r.index < r.items.size()) drawCard(c, r, r.index, cardTop);
        if (fade) {
            if (left > 0) {
                edgeMask.setShader(new LinearGradient(0, 0, EDGE_FADE, 0, Ui.alpha(0xFF000000, 1 - left), 0xFF000000,
                        Shader.TileMode.CLAMP));
                c.drawRect(0, cardTop - 60, EDGE_FADE, cardTop + CARD_H + 60, edgeMask);
            }
            if (right > 0) {
                edgeMask.setShader(new LinearGradient(W - EDGE_FADE, 0, W, 0, 0xFF000000, Ui.alpha(0xFF000000, 1 - right),
                        Shader.TileMode.CLAMP));
                c.drawRect(W - EDGE_FADE, cardTop - 60, W, cardTop + CARD_H + 60, edgeMask);
            }
            c.restoreToCount(layer);
        }
    }

    private void drawCard(Canvas c, Row r, int i, float top) {
        float x = cardX(r, i);
        if (x > W || x + CARD_W < 0) return;
        HomeApps.Item it = r.items.get(i);
        float p = Ui.clamp(r.pop.get(i).value);
        float s = 1 + FOCUS_GROW * r.pop.get(i).value;
        float cx = x + CARD_W / 2, cy = top + CARD_H / 2;
        rect.set(cx - CARD_W * s / 2, cy - CARD_H * s / 2, cx + CARD_W * s / 2, cy + CARD_H * s / 2);
        float dim = it.kind == HomeApps.INPUT && !it.connected ? 0.5f : 1f;
        if (p > 0.01f) {
            shadow.setAlpha(Math.round(255 * p));
            c.drawRoundRect(rect, RADIUS * s, RADIUS * s, shadow);
        }
        if (it.kind == HomeApps.APP && it.tile != null) {
            c.save();
            clip.reset();
            clip.addRoundRect(rect, RADIUS * s, RADIUS * s, Path.Direction.CW);
            c.clipPath(clip);
            image.setAlpha(Math.round(255 * (0.82f + 0.18f * p)));
            c.drawBitmap(it.tile, null, rect, image);
            image.setAlpha(255);
            c.restore();
        } else {
            // Colored card (diagonal gradient per kind); a unit-square shader moved onto the card, no allocation.
            cardMatrix.setScale(rect.width(), rect.height());
            cardMatrix.postTranslate(rect.left, rect.top);
            LinearGradient g = cardShader(it.kind);
            g.setLocalMatrix(cardMatrix);
            fill.setShader(g);
            fill.setAlpha(Math.round(255 * dim));
            c.drawRoundRect(rect, RADIUS * s, RADIUS * s, fill);
            fill.setShader(null);
            fill.setAlpha(255);
            int ink = Ui.alpha(Color.WHITE, dim < 1 ? 0.7f : 1f);
            if (it.kind == HomeApps.SETTINGS && logo != null) {
                float lh = 82 * s, lw = lh * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
                logo.setBounds(Math.round(rect.centerX() - lw / 2), Math.round(rect.centerY() - 22 * s - lh / 2),
                        Math.round(rect.centerX() + lw / 2), Math.round(rect.centerY() - 22 * s + lh / 2));
                logo.setAlpha(255);
                logo.draw(c);
            } else {
                Symbols.draw(getContext(), c, it.symbol, rect.centerX(), rect.centerY() - 24 * s, 52 * s, ink);
            }
            cardLabel.setColor(ink);
            cardLabel.setTextSize(26 * s);
            c.drawText(TextUtils.ellipsize(it.label, cardLabel, rect.width() - 24,
                    TextUtils.TruncateAt.END).toString(), rect.centerX(), rect.centerY() + 52 * s, cardLabel);
        }
        if (p > 0.01f) {
            outline.setAlpha(Math.round(255 * p));
            c.drawRoundRect(rect, RADIUS * s, RADIUS * s, outline);
        }
    }

    /** Shortcut card colors: live TV coral, inputs blue, search violet, Hestia night (emblem), TV settings teal. */
    private LinearGradient cardShader(int kind) {
        LinearGradient g = cardShaders.get(kind);
        if (g == null) {
            int from, to;
            switch (kind) {
                case HomeApps.LIVE_TV: from = 0xFFF0625F; to = 0xFF9B1C3A; break;
                case HomeApps.INPUT: from = 0xFF4F8CF7; to = 0xFF3B2FB8; break;
                case HomeApps.SETTINGS: from = 0xFF2E2448; to = 0xFF120C1E; break;
                case HomeApps.SEARCH: from = 0xFF8B6CFF; to = 0xFF2F5BD3; break;
                default: from = 0xFF1FB5A3; to = 0xFF0E5470; break;
            }
            g = new LinearGradient(0, 0, 1, 1, from, to, Shader.TileMode.CLAMP);
            cardShaders.put(kind, g);
        }
        return g;
    }

    // MARK: - Motion

    /** Points every spring at the current focus. */
    private void retarget(boolean snap) {
        newsPop.target = newsFocus ? 1 : 0;
        if (snap) newsPop.snap(newsPop.target);
        scrollY.target = Math.max(0, row - 1) * ROW_STEP;
        if (snap) scrollY.snap(scrollY.target);
        for (int r = 0; r < rows.size(); r++) {
            Row rw = rows.get(r);
            rw.index = Math.max(0, Math.min(rw.items.size() - 1, rw.index));
            // Keep the focused card (and a bit of its neighbour) inside the margins.
            float x = MARGIN + rw.index * (CARD_W + GAP);
            float target = rw.scroll.target;
            if (x - target < MARGIN) target = x - MARGIN - (rw.index > 0 ? CARD_W * 0.35f : 0);
            if (x + CARD_W - target > W - MARGIN) target = x + CARD_W - (W - MARGIN) + (rw.index < rw.items.size() - 1 ? CARD_W * 0.35f : 0);
            float max = Math.max(0, MARGIN + rw.items.size() * (CARD_W + GAP) - GAP - (W - MARGIN));
            rw.scroll.target = Math.max(0, Math.min(max, target));
            if (snap) rw.scroll.snap(rw.scroll.target);
            for (int i = 0; i < rw.pop.size(); i++) {
                Spring s = rw.pop.get(i);
                s.target = !newsFocus && r == row && i == rw.index ? 1 : 0;
                if (snap) s.snap(s.target);
            }
        }
        schedule();
    }

    private void schedule() {
        if (framePending) return;
        framePending = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        framePending = false;
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        boolean moving = scrollY.step(dt) | newsPop.step(dt);
        for (Row r : rows) {
            moving |= r.scroll.step(dt);
            for (Spring s : r.pop) moving |= s.step(dt);
        }
        moving |= SystemClock.uptimeMillis() - fadeStart < FADE_MS;
        moving |= SystemClock.uptimeMillis() - headlineAt < HEADLINE_MS;
        invalidate();
        if (moving) {
            framePending = true;
            Choreographer.getInstance().postFrameCallback(this);
        } else if (oldPhoto != null) {
            oldPhoto.recycle();
            oldPhoto = null;
        }
    }

    // MARK: - Keys: only the arrows, OK and Back (system keys pass through to the system)

    boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
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
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Opens on release, and only if the press happened here: the release of an OK pressed inside another
                // app opens nothing. Held down (Android's long-press timeout): the app's menu instead.
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        okDown = true;
                        okLong = false;
                    } else if (okDown && !okLong && e.isLongPress()) {
                        okLong = true;
                        menu();
                    }
                } else {
                    if (okDown && !okLong && !e.isCanceled()) open();
                    okDown = false;
                }
                return true;
            // The remote's Settings button (sent as NOTIFICATION on this TCL): the quick panel, as Google TV did.
            case KeyEvent.KEYCODE_NOTIFICATION:
            case KeyEvent.KEYCODE_SETTINGS:
                if (!down && !RemoteKeys.run(activity, RemoteKeys.get(activity, RemoteKeys.SETTINGS))) {
                    RemoteKeys.run(activity, RemoteKeys.PANEL);
                }
                return true;
            // The TV button: this TCL remote sends its own key code (4001); GUIDE/TV on other remotes.
            case KEYCODE_TCL_TV:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_TV:
                if (!down && !RemoteKeys.run(activity, RemoteKeys.get(activity, RemoteKeys.TV))) openLiveTv();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) {
                    if (e.getRepeatCount() == 0) backDown = true;
                } else {
                    if (backDown && !e.isCanceled() && (newsFocus || row != 0 || rows.isEmpty() || rows.get(0).index != 0)) {
                        reset(true);
                        click.move();
                    }
                    backDown = false;
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
        if (row == 0 && dy < 0 && newsOn && !headlines.isEmpty()) {
            newsFocus = true;
            click.move();
            retarget(false);
            return;
        }
        if (rows.isEmpty()) return;
        Row r = rows.get(row);
        if (dx != 0) {
            int next = r.index + dx;
            if (next < 0 || next >= r.items.size()) return;
            r.index = next;
        } else {
            int next = row + dy;
            if (next < 0 || next >= rows.size()) return;
            // Land on the card under the one we leave.
            float x = cardX(r, r.index) + CARD_W / 2;
            Row to = rows.get(next);
            int best = 0;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < to.items.size(); i++) {
                float d = Math.abs(cardX(to, i) + CARD_W / 2 - x);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = i;
                }
            }
            to.index = best;
            row = next;
        }
        click.move();
        retarget(false);
    }

    /** Long press OK on an app card: its menu (favorites, hide, info, Play Store, uninstall). */
    private void menu() {
        HomeApps.Item it = newsFocus ? null : focused();
        if (it == null || it.kind != HomeApps.APP) return;
        click.open();
        activity.startActivity(new android.content.Intent(activity, AppMenuActivity.class)
                .putExtra(AppMenuActivity.EXTRA_PACKAGE, it.pkg));
    }

    private void openLiveTv() {
        android.content.Intent live = HomeApps.liveTv(activity);
        if (live == null) return;
        try {
            activity.startActivity(live.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            AppLog.w("live TV: " + e.getMessage());
        }
    }

    private void open() {
        if (newsFocus) {
            if (headlines.isEmpty()) return;
            click.open();
            activity.startActivity(new android.content.Intent(activity, NewsActivity.class)
                    .putExtra(NewsActivity.EXTRA_TITLE, headlines.get(headline).title));
            return;
        }
        HomeApps.Item it = focused();
        if (it == null) return;
        Row r = rows.get(row);
        float x = cardX(r, r.index), y = rowTop(row) + TITLE_GAP;
        click.open();
        if (!HomeApps.open(activity, it, this, Math.round(x * k), Math.round(y * k),
                Math.round(CARD_W * k), Math.round(CARD_H * k))) {
            AppLog.w("couldn't open " + it.label);
        }
    }
}
