package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hestia's settings: favorites, headlines, screensaver preview and home choice (through Android, never forced). */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        // From ADB: am start -n io.freemytv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();     // before pausing the view: closing the preview resumes it (view.resume)
        view.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (preview != null) { preview.stop(); preview = null; }
        if (view != null) view.pause();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system).
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    /**
     * Drawn on a 1920 × 1080 canvas like the home screen: the photo of the day, darkened, with the name on the left and
     * a list on the right; "Favorite apps" is a grid of every app.
     */
    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private static final float W = 1920, H = 1080, MARGIN = 120;
        private static final int MAIN = 0, FAVORITES = 1, HOME_CHOICE = 2;
        private static final int ROW_FAVORITES = 0, ROW_NEWS = 1, ROW_SCREENSAVER = 2, ROW_HOME = 3, ROW_TV = 4;
        private static final float LIST_X = 920, LIST_R = W - MARGIN, LIST_TOP = 250, ROW_H = 104, ROW_GAP = 14;
        private static final float GRID_COLS = 5, CARD_W = 300, CARD_H = 169, CARD_GAP = 36, GRID_TOP = 300, GRID_STEP = 250;
        private static final int ACCENT = 0xFFFFB347, INK = 0xFF15161A;
        private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

        private final SettingsActivity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private final List<Spring> pop = new ArrayList<>();
        private final Spring gridScroll = new Spring(0.45f, 1f, 0);
        private int screen = MAIN, focus;
        private boolean running, framePending, okDown, backDown;
        private long lastNanos;
        private float k = 1;

        private Bitmap photo;
        private final Drawable logo;
        private List<HomeApps.Item> apps = new ArrayList<>();
        private List<String> favorites = new ArrayList<>();
        private boolean newsOn, homeIsOurs;

        private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint();
        private final Paint name, tagline, small, heading, rowLabel, rowValue, badge;
        private final TextPaint rowSub, cardLabel, hint;

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            setFocusable(true);
            logo = a.getDrawable(R.drawable.hestia_logo);
            name = Ui.text(Ui.displaySemibold, 104, Color.WHITE);
            tagline = Ui.text(Ui.regular, 32, 0xD9FFFFFF);
            small = Ui.text(Ui.regular, 24, 0x99FFFFFF);
            heading = Ui.text(Ui.displaySemibold, 56, Color.WHITE);
            rowLabel = Ui.text(Ui.medium, 34, Color.WHITE);
            rowValue = Ui.text(Ui.regular, 30, 0xA6FFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            badge = Ui.numbers(Ui.semibold, 26, INK);
            badge.setTextAlign(Paint.Align.CENTER);
            rowSub = new TextPaint(Ui.text(Ui.regular, 26, 0xA6FFFFFF));
            cardLabel = new TextPaint(Ui.text(Ui.medium, 26, Color.WHITE));
            cardLabel.setTextAlign(Paint.Align.CENTER);
            hint = new TextPaint(Ui.text(Ui.regular, 28, 0xBFFFFFFF));
            for (Paint p : new Paint[] {name, tagline, heading, hint}) p.setShadowLayer(14, 0, 2, 0x99000000);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(5);
            outline.setColor(Color.WHITE);
            shadow.setColor(Color.BLACK);
            shadow.setShadowLayer(28, 0, 12, 0x99000000);
            scrim.setShader(new LinearGradient(0, 0, W, 0, new int[] {0xD9000000, 0x99000000, 0xB3000000}, null,
                    Shader.TileMode.CLAMP));
            resetPop(count());
            retarget(true);
        }

        void resume() {
            if (running) return;
            running = true;
            refreshState();
            loadPhoto();
            schedule();
        }

        void pause() {
            running = false;
        }

        private void refreshState() {
            Context c = getContext();
            favorites = HomeApps.favorites(c);
            newsOn = News.enabled(c);
            homeIsOurs = HomeMode.isDefault(c);
            invalidate();
        }

        private void loadPhoto() {
            List<Photos.Photo> list = Photos.get(getContext()).list();
            if (photo != null || list.isEmpty()) return;
            final Photos.Photo p = list.get(0);
            LOADER.execute(() -> {
                final Bitmap b = Photos.load(p.file, (int) W, (int) H);
                handler.post(() -> {
                    photo = b;
                    invalidate();
                });
            });
        }

        private void loadApps() {
            final Context c = getContext().getApplicationContext();
            LOADER.execute(() -> {
                final List<HomeApps.Item> list = HomeApps.allApps(c);
                handler.post(() -> {
                    apps = list;
                    resetPop(apps.size());
                    retarget(true);
                });
            });
        }

        private int count() {
            switch (screen) {
                case FAVORITES: return apps.size();
                case HOME_CHOICE: return 3;
                default: return 5;
            }
        }

        private void resetPop(int n) {
            pop.clear();
            for (int i = 0; i < n; i++) pop.add(new Spring(0.28f, 0.82f, 0));
        }

        private void go(int to, int focusOn) {
            screen = to;
            focus = focusOn;
            resetPop(count());
            if (to == FAVORITES) {
                apps = new ArrayList<>();
                gridScroll.snap(0);
                loadApps();
            }
            retarget(true);
        }

        // MARK: - Drawing

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = width / W;
        }

        @Override protected void onDraw(Canvas c) {
            c.save();
            c.scale(k, k);
            if (photo != null) c.drawBitmap(photo, 0, 0, image); else c.drawColor(0xFF0E1420);
            c.drawRect(0, 0, W, H, scrim);
            if (screen == FAVORITES) drawFavorites(c); else drawMain(c);
            c.restore();
        }

        private void drawMain(Canvas c) {
            if (logo != null) {
                float lh = 380, lw = lh * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
                logo.setBounds(Math.round(MARGIN), 150, Math.round(MARGIN + lw), Math.round(150 + lh));
                logo.draw(c);
            }
            c.drawText("Hestia", MARGIN - Ui.leftBearing(name, "Hestia"), 650, name);
            c.drawText(Ui.tr("Seu Início, simples e bonito.", "Your home screen, simple and beautiful."), MARGIN, 712, tagline);
            c.drawText(version(), MARGIN, 758, small);
            List<Photos.Photo> list = Photos.get(getContext()).list();
            String credit = list.isEmpty() ? Ui.tr("Fotos do dia: Bing", "Photos of the day: Bing")
                    : Ui.tr("Foto: ", "Photo: ") + list.get(0).credit;
            c.drawText(TextUtils.ellipsize(credit, rowSub, 700, TextUtils.TruncateAt.END).toString(), MARGIN, H - 80, small);

            if (screen == MAIN) {
                c.drawText(Ui.tr("Ajustes", "Settings"), LIST_X, LIST_TOP - 50, heading);
                String[] labels = {Ui.tr("Apps favoritos", "Favorite apps"), Ui.tr("Notícias de cibersegurança", "Cyber news"),
                        Ui.tr("Tela de descanso", "Screensaver"), Ui.tr("Tela inicial padrão", "Default home screen"),
                        Ui.tr("Ajustes da TV", "TV settings")};
                String[] values = {favorites.size() + " apps", newsOn ? Ui.tr("Ligado", "On") : Ui.tr("Desligado", "Off"),
                        Ui.tr("Ver agora", "Preview"), homeIsOurs ? "Hestia" : Ui.tr("Outra", "Other"), ""};
                for (int i = 0; i < labels.length; i++) drawRow(c, i, labels[i], values[i], null);
            } else {
                c.drawText(Ui.tr("Tela inicial", "Home screen"), LIST_X, LIST_TOP - 50, heading);
                String[][] options = {
                        {Ui.tr("Abrir Hestia", "Open Hestia"), Ui.tr("Sem mudar o padrão", "Without changing the default")},
                        {Ui.tr("Abrir outra tela inicial", "Open another home"), Ui.tr("Se houver uma ativada", "If another one is enabled")},
                        {Ui.tr("Escolher a padrão", "Choose the default"), Ui.tr("Seleção do Android", "Android's own selection")}};
                for (int i = 0; i < options.length; i++) drawRow(c, i, options[i][0], "", options[i][1]);
                c.drawText(TextUtils.ellipsize(Ui.tr("Restaurar pelo computador: tools/tv_safe.py restore",
                        "Restore from your computer: tools/tv_safe.py restore"), hint, LIST_R - LIST_X, TextUtils.TruncateAt.END)
                        .toString(), LIST_X, LIST_TOP + 3 * (ROW_H + ROW_GAP) + 50, hint);
            }
        }

        /** A list row: translucent; focused, white and slightly larger (the Apple TV focus). */
        private void drawRow(Canvas c, int i, String label, String value, String sub) {
            float f = i < pop.size() ? Ui.clamp(pop.get(i).value) : 0;
            float y = LIST_TOP + i * (ROW_H + ROW_GAP);
            float s = 1 + 0.025f * f, cx = (LIST_X + LIST_R) / 2, cy = y + ROW_H / 2;
            rect.set(cx - (LIST_R - LIST_X) * s / 2, cy - ROW_H * s / 2, cx + (LIST_R - LIST_X) * s / 2, cy + ROW_H * s / 2);
            if (f > 0.01f) {
                shadow.setAlpha(Math.round(255 * f));
                c.drawRoundRect(rect, 22, 22, shadow);
            }
            fill.setColor(blend(0x33FFFFFF, 0xFFFFFFFF, f));
            c.drawRoundRect(rect, 22, 22, fill);
            rowLabel.setColor(blend(0xFFFFFFFF, INK, f));
            float base = sub == null ? cy + Ui.halfCap(rowLabel) : cy - 4;
            c.drawText(label, rect.left + 36, base, rowLabel);
            if (sub != null) {
                rowSub.setColor(blend(0xA6FFFFFF, 0x991C1C1E, f));
                c.drawText(sub, rect.left + 36, cy + 36, rowSub);
            }
            if (!value.isEmpty()) {
                rowValue.setColor(blend(0xA6FFFFFF, 0x991C1C1E, f));
                c.drawText(value, rect.right - 36, cy + Ui.halfCap(rowValue), rowValue);
            }
        }

        private void drawFavorites(Canvas c) {
            c.drawText(Ui.tr("Apps favoritos", "Favorite apps"), MARGIN, 150, heading);
            c.drawText(TextUtils.ellipsize(Ui.tr(
                    "OK adiciona ou remove. A ordem escolhida é a da primeira fileira. Voltar para salvar.",
                    "OK adds or removes. The order you pick is the order of the first row. Back when done."), hint,
                    W - 2 * MARGIN, TextUtils.TruncateAt.END).toString(), MARGIN, 206, hint);
            float gridW = GRID_COLS * CARD_W + (GRID_COLS - 1) * CARD_GAP, left = (W - gridW) / 2;
            c.save();
            c.clipRect(0, 240, W, H);
            for (int i = 0; i < apps.size(); i++) {
                if (i != focus) drawAppCard(c, i, left);
            }
            if (focus < apps.size()) drawAppCard(c, focus, left);
            c.restore();
            if (apps.isEmpty()) c.drawText(Ui.tr("Carregando…", "Loading…"), MARGIN, GRID_TOP + 60, hint);
        }

        private void drawAppCard(Canvas c, int i, float left) {
            int col = i % (int) GRID_COLS, row = i / (int) GRID_COLS;
            float x = left + col * (CARD_W + CARD_GAP), y = GRID_TOP + row * GRID_STEP - gridScroll.value;
            if (y > H || y + GRID_STEP < 240) return;
            HomeApps.Item it = apps.get(i);
            float f = i < pop.size() ? Ui.clamp(pop.get(i).value) : 0;
            float s = 1 + 0.08f * f, cx = x + CARD_W / 2, cy = y + CARD_H / 2;
            rect.set(cx - CARD_W * s / 2, cy - CARD_H * s / 2, cx + CARD_W * s / 2, cy + CARD_H * s / 2);
            int order = favorites.indexOf(it.pkg);
            if (f > 0.01f) {
                shadow.setAlpha(Math.round(255 * f));
                c.drawRoundRect(rect, 16 * s, 16 * s, shadow);
            }
            c.save();
            clip.reset();
            clip.addRoundRect(rect, 16 * s, 16 * s, Path.Direction.CW);
            c.clipPath(clip);
            image.setAlpha(order >= 0 || f > 0.5f ? 255 : 140);
            if (it.tile != null) c.drawBitmap(it.tile, null, rect, image);
            image.setAlpha(255);
            c.restore();
            if (f > 0.01f) {
                outline.setAlpha(Math.round(255 * f));
                c.drawRoundRect(rect, 16 * s, 16 * s, outline);
            }
            if (order >= 0) {
                float bx = rect.left + 30, by = rect.top + 30;
                fill.setColor(ACCENT);
                c.drawCircle(bx, by, 22, fill);
                c.drawText(String.valueOf(order + 1), bx, by + Ui.halfCap(badge), badge);
            }
            cardLabel.setColor(order >= 0 || f > 0.5f ? 0xFFFFFFFF : 0x99FFFFFF);
            c.drawText(TextUtils.ellipsize(it.label, cardLabel, CARD_W, TextUtils.TruncateAt.END).toString(),
                    cx, y + CARD_H + 48, cardLabel);
        }

        // MARK: - Motion

        private void retarget(boolean snap) {
            for (int i = 0; i < pop.size(); i++) {
                Spring s = pop.get(i);
                s.target = i == focus ? 1 : 0;
                if (snap) s.snap(s.target);
            }
            if (screen == FAVORITES) {
                int row = focus / (int) GRID_COLS;
                gridScroll.target = Math.max(0, (row - 2) * GRID_STEP);
                if (snap) gridScroll.snap(gridScroll.target);
            }
            schedule();
        }

        private void schedule() {
            invalidate();
            if (framePending) return;
            framePending = true;
            lastNanos = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override public void doFrame(long nanos) {
            framePending = false;
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (nanos - lastNanos) / 1e9f);
            lastNanos = nanos;
            boolean moving = gridScroll.step(dt);
            for (Spring s : pop) moving |= s.step(dt);
            invalidate();
            if (moving && running) {
                framePending = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        // MARK: - Keys

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            int cols = screen == FAVORITES ? (int) GRID_COLS : 1;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) moveTo(focus - cols);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveTo(Math.min(count() - 1, focus + cols));
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down && cols > 1 && focus % cols > 0) moveTo(focus - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && cols > 1 && focus % cols < cols - 1) moveTo(focus + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled()) activate();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (screen == MAIN) return false;   // leaves the settings
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) go(MAIN, screen == FAVORITES ? ROW_FAVORITES : ROW_HOME);
                        backDown = false;
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void moveTo(int index) {
            if (index < 0 || index >= count() || index == focus) return;
            focus = index;
            retarget(false);
        }

        private void activate() {
            Context c = getContext();
            if (screen == FAVORITES) {
                if (focus >= apps.size()) return;
                String pkg = apps.get(focus).pkg;
                if (favorites.contains(pkg)) {
                    favorites.remove(pkg);
                } else if (favorites.size() < HomeApps.MAX_FAVORITES) {
                    favorites.add(pkg);
                }
                HomeApps.setFavorites(c, favorites);
                invalidate();
            } else if (screen == HOME_CHOICE) {
                if (focus == 0) activity.startActivity(new Intent(activity, HomeActivity.class));
                else if (focus == 1) HomeMode.openOtherHome(activity);
                else HomeMode.openHomeSettings(activity);
                go(MAIN, ROW_HOME);
            } else if (focus == ROW_FAVORITES) {
                go(FAVORITES, 0);
            } else if (focus == ROW_NEWS) {
                newsOn = !newsOn;
                News.setEnabled(c, newsOn);
                invalidate();
            } else if (focus == ROW_SCREENSAVER) {
                pause();
                activity.showPreview();
            } else if (focus == ROW_HOME) {
                go(HOME_CHOICE, homeIsOurs ? 0 : 2);
            } else if (focus == ROW_TV) {
                HomeMode.openSettings(activity);
            }
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        private String version() {
            try {
                return "Hestia " + getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "Hestia";
            }
        }
    }
}
