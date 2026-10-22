package io.hestia.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every cybersecurity headline fetched (News), newest first. D-pad up/down, OK opens the article in the TV's browser
 * (an https link from the feed, nothing else), Back returns home. Drawn like the settings: the photo of the day,
 * darkened, behind a list.
 */
public class NewsActivity extends Activity {
    static final String EXTRA_TITLE = "title";   // the headline that was on the home screen: focused first

    private NewsView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new NewsView(this, getIntent().getStringExtra(EXTRA_TITLE));
        setContentView(view);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** The article in a real browser: the default one if it isn't Android's empty "stub", otherwise the first other. */
    void openLink(String link) {
        if (link.isEmpty()) {
            Toast.makeText(this, Ui.tr("Sem link para esta notícia.", "No link for this headline."), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(view, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals("com.android.tv.frameworkpackagestubs")) {
                view.setPackage(pkg);
                break;
            }
        }
        try {
            startActivity(view);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, Ui.tr("Nenhum navegador instalado.", "No web browser installed."), Toast.LENGTH_LONG).show();
        }
    }

    static final class NewsView extends View implements Choreographer.FrameCallback {
        private static final float W = 1920, H = 1080, MARGIN = 120, TOP = 250, ROW_H = 128, ROW_GAP = 12;
        private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

        private final NewsActivity activity;
        private final List<News.Headline> items;
        private final List<Spring> pop = new ArrayList<>();
        private final Spring scroll = new Spring(0.45f, 1f, 0);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final RectF rect = new RectF();
        private int focus;
        private boolean framePending, okDown;
        private long lastNanos;
        private float k = 1;
        private Bitmap photo;

        private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint scrim = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint heading, sub, pillText;
        private final TextPaint title, meta, footer;

        NewsView(NewsActivity a, String current) {
            super(a);
            activity = a;
            setFocusable(true);
            items = News.get(a).recent();
            for (int i = 0; i < items.size(); i++) {
                pop.add(new Spring(0.28f, 0.82f, 0));
                if (items.get(i).title.equals(current)) focus = i;
            }
            heading = Ui.text(Ui.displaySemibold, 56, Color.WHITE);
            sub = Ui.text(Ui.regular, 28, 0xBFFFFFFF);
            pillText = Ui.text(Ui.semibold, 20, Color.WHITE);
            pillText.setLetterSpacing(0.08f);
            title = new TextPaint(Ui.text(Ui.medium, 34, Color.WHITE));
            meta = new TextPaint(Ui.text(Ui.regular, 24, 0xB3FFFFFF));
            footer = new TextPaint(Ui.text(Ui.regular, 26, 0xBFFFFFFF));
            pill.setColor(0xFFE5484D);
            shadow.setColor(Color.BLACK);
            shadow.setShadowLayer(28, 0, 12, 0x99000000);
            scrim.setShader(new LinearGradient(0, 0, 0, H, 0xD9000000, 0xF2000000, Shader.TileMode.CLAMP));
            List<Photos.Photo> photos = Photos.get(a).list();
            if (!photos.isEmpty()) {
                final Photos.Photo p = photos.get(0);
                LOADER.execute(() -> {
                    final Bitmap b = Photos.load(p.file, (int) W, (int) H);
                    handler.post(() -> {
                        photo = b;
                        invalidate();
                    });
                });
            }
            retarget(true);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = width / W;
        }

        @Override protected void onDraw(Canvas c) {
            c.save();
            c.scale(k, k);
            if (photo != null) c.drawBitmap(photo, 0, 0, image); else c.drawColor(0xFF0E1420);
            c.drawRect(0, 0, W, H, scrim);
            c.drawText(Ui.tr("Notícias de cibersegurança", "Cyber news"), MARGIN, 140, heading);
            c.drawText(items.size() + Ui.tr(" manchetes · OK abre no navegador · Voltar para sair",
                    " headlines · OK opens in the browser · Back to leave"), MARGIN, 196, sub);
            if (items.isEmpty()) {
                c.drawText(Ui.tr("Nenhuma notícia por enquanto.", "No headlines yet."), MARGIN, TOP + 60, sub);
            }
            c.save();
            c.clipRect(0, TOP - 30, W, H - 90);
            for (int i = 0; i < items.size(); i++) drawItem(c, i);
            c.restore();
            if (focus < items.size() && !items.get(focus).link.isEmpty()) {
                c.drawText(TextUtils.ellipsize("↗ " + FeedText.domain(items.get(focus).link), footer, W - 2 * MARGIN,
                        TextUtils.TruncateAt.END).toString(), MARGIN, H - 46, footer);
            }
            c.restore();
        }

        private void drawItem(Canvas c, int i) {
            float y = TOP + i * (ROW_H + ROW_GAP) - scroll.value;
            if (y > H || y + ROW_H < TOP - 30) return;
            News.Headline h = items.get(i);
            float f = Ui.clamp(pop.get(i).value);
            float s = 1 + 0.015f * f, cx = W / 2, cy = y + ROW_H / 2, half = (W - 2 * MARGIN) / 2;
            rect.set(cx - half * s, cy - ROW_H * s / 2, cx + half * s, cy + ROW_H * s / 2);
            if (f > 0.01f) {
                shadow.setAlpha(Math.round(255 * f));
                c.drawRoundRect(rect, 22, 22, shadow);
            }
            fill.setColor(blend(0x26FFFFFF, 0xFFFFFFFF, f));
            c.drawRoundRect(rect, 22, 22, fill);
            float x = rect.left + 32;
            String source = h.source.toUpperCase(java.util.Locale.ROOT);
            float pw = pillText.measureText(source) + 24;
            RectF p = new RectF(x, rect.top + 20, x + pw, rect.top + 52);
            c.drawRoundRect(p, 16, 16, pill);
            c.drawText(source, x + 12, p.centerY() + Ui.halfCap(pillText), pillText);
            meta.setColor(blend(0xB3FFFFFF, 0x991C1C1E, f));
            c.drawText(News.age(h.at), p.right + 16, p.centerY() + Ui.halfCap(meta), meta);
            title.setColor(blend(0xFFFFFFFF, 0xFF15161A, f));
            c.drawText(TextUtils.ellipsize(h.title, title, rect.width() - 64, TextUtils.TruncateAt.END).toString(),
                    x, rect.bottom - 26, title);
        }

        private void retarget(boolean snap) {
            for (int i = 0; i < pop.size(); i++) {
                Spring s = pop.get(i);
                s.target = i == focus ? 1 : 0;
                if (snap) s.snap(s.target);
            }
            float visible = (H - 90 - TOP) / (ROW_H + ROW_GAP);
            scroll.target = Math.max(0, (focus - (int) visible + 2) * (ROW_H + ROW_GAP));
            if (snap) scroll.snap(scroll.target);
            invalidate();
            if (!framePending) {
                framePending = true;
                lastNanos = 0;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        @Override public void doFrame(long nanos) {
            framePending = false;
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (nanos - lastNanos) / 1e9f);
            lastNanos = nanos;
            boolean moving = scroll.step(dt);
            for (Spring s : pop) moving |= s.step(dt);
            invalidate();
            if (moving) {
                framePending = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) { focus--; retarget(false); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < items.size() - 1) { focus++; retarget(false); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled() && focus < items.size()) activity.openLink(items.get(focus).link);
                        okDown = false;
                    }
                    return true;
                default:
                    return false;   // Back leaves (the system finishes the activity)
            }
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }
    }
}
