package io.hestia.tv.settings;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;

import io.hestia.tv.R;
import io.hestia.tv.apps.AppCatalog;
import io.hestia.tv.apps.Card;
import io.hestia.tv.apps.HomeRole;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.core.Background;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Keys;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.Screens;
import io.hestia.tv.data.Photos;
import io.hestia.tv.ui.Backdrop;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.FocusSprings;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Press;
import io.hestia.tv.ui.Spring;
import io.hestia.tv.ui.Symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hestia's settings: the photo of the day, darkened, with the name on the left and a list on the right; "Favorite apps"
 * and "Hidden apps" are grids of every app. A new setting is one more SettingsRow in mainRows().
 */
public final class SettingsView extends CanvasView {
    private enum Screen { MAIN, FAVORITES, HIDDEN, HOME_CHOICE }

    private static final float MARGIN = 120;
    private static final String[] SORTS = {AppCatalog.SORT_NAME, AppCatalog.SORT_USAGE, AppCatalog.SORT_RECENT};
    private static final String[] LANGUAGES = {I18n.AUTO, I18n.LANG_FR, I18n.LANG_EN, I18n.LANG_PT};
    private static final float LIST_X = 920, LIST_R = W - MARGIN, LIST_TOP = 210, ROW_H = 80, ROW_GAP = 9;
    private static final int GRID_COLS = 5;
    private static final float CARD_W = 300, CARD_H = 169, CARD_GAP = 36, GRID_TOP = 300, GRID_STEP = 250;
    private static final int ACCENT = 0xFFFFB347, INK = 0xFF15161A;

    private final Activity activity;
    private final Runnable showPreview;
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final FocusSprings pop = new FocusSprings(0.28f, 0.82f);
    private final Spring gridScroll = new Spring(0.45f, 1f, 0);
    private final Press ok = new Press(), back = new Press();
    private final Backdrop backdrop = new Backdrop();
    private final List<SettingsRow> mainRows, homeRows;
    private final int rowFavorites = 0, rowHidden = 1, rowHome;
    private Screen screen = Screen.MAIN;
    private int focus;

    private final Drawable logo;
    private List<Card> apps = new ArrayList<>();
    private List<String> favorites = new ArrayList<>();
    private Set<String> hidden = new HashSet<>();
    private boolean newsOn, homeIsOurs;

    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint();
    private final Paint name, tagline, small, heading, rowLabel, rowValue, badge;
    private final TextPaint rowSub, cardLabel, hint;

    public SettingsView(Activity a, Runnable showPreview) {
        super(a);
        activity = a;
        this.showPreview = showPreview;
        logo = a.getDrawable(R.drawable.hestia_logo);
        name = Paints.text(Fonts.displaySemibold, 104, Color.WHITE);
        tagline = Paints.text(Fonts.regular, 32, 0xD9FFFFFF);
        small = Paints.text(Fonts.regular, 24, 0x99FFFFFF);
        heading = Paints.text(Fonts.displaySemibold, 56, Color.WHITE);
        rowLabel = Paints.text(Fonts.medium, 34, Color.WHITE);
        rowValue = Paints.text(Fonts.regular, 30, 0xA6FFFFFF);
        rowValue.setTextAlign(Paint.Align.RIGHT);
        badge = Paints.numbers(Fonts.semibold, 26, INK);
        badge.setTextAlign(Paint.Align.CENTER);
        rowSub = new TextPaint(Paints.text(Fonts.regular, 26, 0xA6FFFFFF));
        cardLabel = new TextPaint(Paints.text(Fonts.medium, 26, Color.WHITE));
        cardLabel.setTextAlign(Paint.Align.CENTER);
        hint = new TextPaint(Paints.text(Fonts.regular, 28, 0xBFFFFFFF));
        for (Paint p : new Paint[] {name, tagline, heading, hint}) p.setShadowLayer(14, 0, 2, 0x99000000);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(5);
        outline.setColor(Color.WHITE);
        shadow.setColor(Color.BLACK);
        shadow.setShadowLayer(28, 0, 12, 0x99000000);
        scrim.setShader(new LinearGradient(0, 0, W, 0, new int[] {0xD9000000, 0x99000000, 0xB3000000}, null,
                Shader.TileMode.CLAMP));
        mainRows = buildMainRows();
        rowHome = mainRows.size() - 2;
        homeRows = buildHomeRows();
        pop.resize(count());
        retarget(true);
    }

    // MARK: - What's in the lists

    private List<SettingsRow> buildMainRows() {
        Context c = getContext();
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(new SettingsRow(() -> I18n.tr("Apps favoritos", "Favorite apps"), () -> favorites.size() + " apps",
                () -> go(Screen.FAVORITES, 0)));
        rows.add(new SettingsRow(() -> I18n.tr("Apps ocultos", "Hidden apps"), () -> String.valueOf(hidden.size()),
                () -> go(Screen.HIDDEN, 0)));
        rows.add(new SettingsRow(() -> I18n.tr("Ordem de Todos os apps", "Sort all apps"), this::sortName, () -> {
            int i = Arrays.asList(SORTS).indexOf(AppCatalog.sortMode(c));
            Prefs.setSort(c, SORTS[(i + 1) % SORTS.length]);
            invalidate();
        }));
        rows.add(new SettingsRow(() -> I18n.tr("Botões do controle", "Remote buttons"), () -> "",
                () -> Launcher.startOver(activity, Screens.remoteKeys(c, null))));
        rows.add(new SettingsRow(() -> I18n.tr("Idioma", "Language"), this::languageName, () -> {
            int i = Arrays.asList(LANGUAGES).indexOf(Prefs.language(c).toLowerCase(Locale.ROOT));
            String choice = LANGUAGES[(i + 1) % LANGUAGES.length];
            Prefs.setLanguage(c, choice);
            I18n.apply(choice);
            invalidate();
        }));
        rows.add(new SettingsRow(() -> I18n.tr("Notícias de cibersegurança", "Cyber news"),
                () -> newsOn ? I18n.tr("Ligado", "On") : I18n.tr("Desligado", "Off"), () -> {
            newsOn = !newsOn;
            Prefs.setNewsEnabled(c, newsOn);
            invalidate();
        }));
        rows.add(new SettingsRow(() -> I18n.tr("Tela de descanso", "Screensaver"), () -> I18n.tr("Ver agora", "Preview"),
                showPreview));
        rows.add(new SettingsRow(() -> I18n.tr("Tela inicial padrão", "Default home screen"),
                () -> homeIsOurs ? "Hestia" : I18n.tr("Outra", "Other"), () -> go(Screen.HOME_CHOICE, homeIsOurs ? 0 : 2)));
        rows.add(new SettingsRow(() -> I18n.tr("Ajustes da TV", "TV settings"), () -> "",
                () -> HomeRole.openTvSettings(activity)));
        return rows;
    }

    private List<SettingsRow> buildHomeRows() {
        Context c = getContext();
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(new SettingsRow(() -> I18n.tr("Abrir Hestia", "Open Hestia"), () -> "",
                I18n.tr("Sem mudar o padrão", "Without changing the default"), () -> {
            Launcher.startOver(activity, Screens.home(c));
            go(Screen.MAIN, rowHome);
        }));
        rows.add(new SettingsRow(() -> I18n.tr("Abrir outra tela inicial", "Open another home"), () -> "",
                I18n.tr("Se houver uma ativada", "If another one is enabled"), () -> {
            HomeRole.openOtherHome(activity);
            go(Screen.MAIN, rowHome);
        }));
        rows.add(new SettingsRow(() -> I18n.tr("Escolher a padrão", "Choose the default"), () -> "",
                I18n.tr("Seleção do Android", "Android's own selection"), () -> {
            HomeRole.openHomeSettings(activity);
            go(Screen.MAIN, rowHome);
        }));
        return rows;
    }

    // MARK: - State

    public void resume() {
        Context c = getContext();
        favorites = AppCatalog.favorites(c);
        hidden = Prefs.hidden(c);
        newsOn = Prefs.newsEnabled(c);
        homeIsOurs = HomeRole.isDefault(c);
        backdrop.load(c, this);
        wake();
    }

    private boolean onGrid() {
        return screen == Screen.FAVORITES || screen == Screen.HIDDEN;
    }

    private List<SettingsRow> rows() {
        return screen == Screen.HOME_CHOICE ? homeRows : mainRows;
    }

    private int count() {
        return onGrid() ? apps.size() : rows().size();
    }

    private void loadApps() {
        final Context c = getContext().getApplicationContext();
        Background.run(() -> {
            final List<Card> list = AppCatalog.allApps(c);
            Background.onMain(() -> {
                apps = list;
                pop.resize(apps.size());
                retarget(true);
            });
        });
    }

    private void go(Screen to, int focusOn) {
        screen = to;
        focus = focusOn;
        if (onGrid()) {
            apps = new ArrayList<>();
            gridScroll.snap(0);
            loadApps();
        }
        pop.resize(count());
        retarget(true);
    }

    // MARK: - Drawing

    @Override protected void drawScene(Canvas c) {
        backdrop.draw(c);
        c.drawRect(0, 0, W, H, scrim);
        if (onGrid()) drawGrid(c); else drawLists(c);
    }

    private void drawLists(Canvas c) {
        if (logo != null) {
            float lh = 380, lw = lh * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
            logo.setBounds(Math.round(MARGIN), 150, Math.round(MARGIN + lw), Math.round(150 + lh));
            logo.draw(c);
        }
        c.drawText("Hestia", MARGIN - Paints.leftBearing(name, "Hestia"), 650, name);
        c.drawText(I18n.tr("Seu Início, simples e bonito.", "Your home screen, simple and beautiful."), MARGIN, 712, tagline);
        c.drawText(version(), MARGIN, 758, small);
        Symbols.draw(getContext(), c, "storage", MARGIN + 14, 808 - Paints.halfCap(small), 24, 0x99FFFFFF);
        c.drawText(storage(), MARGIN + 40, 808, small);
        List<Photos.Photo> list = Photos.get(getContext()).list();
        String credit = list.isEmpty() ? I18n.tr("Fotos do dia: Bing", "Photos of the day: Bing")
                : I18n.tr("Foto: ", "Photo: ") + list.get(0).credit;
        c.drawText(TextUtils.ellipsize(credit, rowSub, 700, TextUtils.TruncateAt.END).toString(), MARGIN, H - 80, small);

        String title = screen == Screen.MAIN ? I18n.tr("Ajustes", "Settings") : I18n.tr("Tela inicial", "Home screen");
        c.drawText(title, LIST_X, LIST_TOP - 50, heading);
        List<SettingsRow> rows = rows();
        for (int i = 0; i < rows.size(); i++) drawRow(c, i, rows.get(i));
        if (screen == Screen.HOME_CHOICE) {
            c.drawText(TextUtils.ellipsize(I18n.tr("Restaurar pelo computador: tools/tv_safe.py restore",
                    "Restore from your computer: tools/tv_safe.py restore"), hint, LIST_R - LIST_X, TextUtils.TruncateAt.END)
                    .toString(), LIST_X, LIST_TOP + rows.size() * (ROW_H + ROW_GAP) + 50, hint);
        }
    }

    private String sortName() {
        switch (AppCatalog.sortMode(getContext())) {
            case AppCatalog.SORT_USAGE: return I18n.tr("Mais usados", "Most used");
            case AppCatalog.SORT_RECENT: return I18n.tr("Usados recentemente", "Recently used");
            default: return I18n.tr("A–Z", "A–Z");
        }
    }

    private String languageName() {
        switch (Prefs.language(getContext()).toLowerCase(Locale.ROOT)) {
            case "fr": return "Français";
            case "en": return "English";
            case "pt": return "Português";
            default: return I18n.tr("Automático", "Automatic");
        }
    }

    /** A list row: translucent; focused, white and slightly larger (the Apple TV focus). */
    private void drawRow(Canvas c, int i, SettingsRow row) {
        float f = pop.value(i);
        float y = LIST_TOP + i * (ROW_H + ROW_GAP);
        float s = 1 + 0.025f * f, cx = (LIST_X + LIST_R) / 2, cy = y + ROW_H / 2;
        rect.set(cx - (LIST_R - LIST_X) * s / 2, cy - ROW_H * s / 2, cx + (LIST_R - LIST_X) * s / 2, cy + ROW_H * s / 2);
        if (f > 0.01f) {
            shadow.setAlpha(Math.round(255 * f));
            c.drawRoundRect(rect, 22, 22, shadow);
        }
        fill.setColor(Colors.blend(0x33FFFFFF, 0xFFFFFFFF, f));
        c.drawRoundRect(rect, 22, 22, fill);
        rowLabel.setColor(Colors.blend(0xFFFFFFFF, INK, f));
        float base = row.sub == null ? cy + Paints.halfCap(rowLabel) : cy - 6;
        c.drawText(row.label.get(), rect.left + 36, base, rowLabel);
        if (row.sub != null) {
            rowSub.setColor(Colors.blend(0xA6FFFFFF, 0x991C1C1E, f));
            c.drawText(row.sub, rect.left + 36, cy + 30, rowSub);
        }
        String value = row.value.get();
        if (!value.isEmpty()) {
            rowValue.setColor(Colors.blend(0xA6FFFFFF, 0x991C1C1E, f));
            c.drawText(value, rect.right - 36, cy + Paints.halfCap(rowValue), rowValue);
        }
    }

    private void drawGrid(Canvas c) {
        boolean hiding = screen == Screen.HIDDEN;
        c.drawText(hiding ? I18n.tr("Apps ocultos", "Hidden apps") : I18n.tr("Apps favoritos", "Favorite apps"), MARGIN, 150, heading);
        String help = hiding ? I18n.tr("OK oculta ou mostra. Os apps ocultos continuam instalados. Voltar para salvar.",
                "OK hides or shows. Hidden apps stay installed. Back when done.")
                : I18n.tr("OK adiciona ou remove. A ordem escolhida é a da primeira fileira. Voltar para salvar.",
                "OK adds or removes. The order you pick is the order of the first row. Back when done.");
        c.drawText(TextUtils.ellipsize(help, hint, W - 2 * MARGIN, TextUtils.TruncateAt.END).toString(), MARGIN, 206, hint);
        float gridW = GRID_COLS * CARD_W + (GRID_COLS - 1) * CARD_GAP, left = (W - gridW) / 2;
        c.save();
        c.clipRect(0, 240, W, H);
        for (int i = 0; i < apps.size(); i++) {
            if (i != focus) drawAppCard(c, i, left);
        }
        if (focus < apps.size()) drawAppCard(c, focus, left);
        c.restore();
        if (apps.isEmpty()) c.drawText(I18n.tr("Carregando…", "Loading…"), MARGIN, GRID_TOP + 60, hint);
    }

    private void drawAppCard(Canvas c, int i, float left) {
        int col = i % GRID_COLS, row = i / GRID_COLS;
        float x = left + col * (CARD_W + CARD_GAP), y = GRID_TOP + row * GRID_STEP - gridScroll.value;
        if (y > H || y + GRID_STEP < 240) return;
        Card card = apps.get(i);
        float f = pop.value(i);
        float s = 1 + 0.08f * f, cx = x + CARD_W / 2, cy = y + CARD_H / 2;
        rect.set(cx - CARD_W * s / 2, cy - CARD_H * s / 2, cx + CARD_W * s / 2, cy + CARD_H * s / 2);
        int order = screen == Screen.HIDDEN ? -1 : favorites.indexOf(card.pkg);
        boolean isHidden = screen == Screen.HIDDEN && hidden.contains(card.pkg);
        if (f > 0.01f) {
            shadow.setAlpha(Math.round(255 * f));
            c.drawRoundRect(rect, 16 * s, 16 * s, shadow);
        }
        c.save();
        clip.reset();
        clip.addRoundRect(rect, 16 * s, 16 * s, Path.Direction.CW);
        c.clipPath(clip);
        image.setAlpha(screen == Screen.HIDDEN ? (isHidden ? 90 : 255) : order >= 0 || f > 0.5f ? 255 : 140);
        Bitmap tile = card.tile;
        if (tile != null) c.drawBitmap(tile, null, rect, image);
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
            c.drawText(String.valueOf(order + 1), bx, by + Paints.halfCap(badge), badge);
        }
        if (isHidden) {
            fill.setColor(0xE6000000);
            rect.set(cx - 70, cy - 22, cx + 70, cy + 22);
            c.drawRoundRect(rect, 22, 22, fill);
            cardLabel.setColor(0xFFFFFFFF);
            c.drawText(I18n.tr("Oculto", "Hidden"), cx, cy + Paints.halfCap(cardLabel), cardLabel);
        }
        cardLabel.setColor(order >= 0 || isHidden || f > 0.5f ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawText(TextUtils.ellipsize(card.label, cardLabel, CARD_W, TextUtils.TruncateAt.END).toString(),
                cx, y + CARD_H + 48, cardLabel);
    }

    // MARK: - Motion

    private void retarget(boolean snap) {
        pop.focus(focus, snap);
        if (onGrid()) {
            gridScroll.target = Math.max(0, (focus / GRID_COLS - 2) * GRID_STEP);
            if (snap) gridScroll.snap(gridScroll.target);
        }
        wake();
    }

    @Override protected boolean animate(float dt) {
        return gridScroll.step(dt) | pop.step(dt);
    }

    // MARK: - Keys

    public boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        int cols = onGrid() ? GRID_COLS : 1;
        if (Keys.isOk(code)) {
            if (ok.track(e) == Press.CLICK) activate();
            return true;
        }
        switch (code) {
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
            case KeyEvent.KEYCODE_BACK:
                if (screen == Screen.MAIN) return false;   // leaves the settings
                if (back.track(e) == Press.CLICK) {
                    go(Screen.MAIN, screen == Screen.FAVORITES ? rowFavorites : screen == Screen.HIDDEN ? rowHidden : rowHome);
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
        if (!onGrid()) {
            rows().get(focus).action.run();
        } else if (focus < apps.size()) {
            String pkg = apps.get(focus).pkg;
            if (screen == Screen.HIDDEN) {
                AppCatalog.setHidden(c, pkg, !hidden.contains(pkg));
                hidden = Prefs.hidden(c);
            } else {
                if (favorites.contains(pkg)) favorites.remove(pkg);
                else if (favorites.size() < AppCatalog.MAX_FAVORITES) favorites.add(pkg);
                AppCatalog.setFavorites(c, favorites);
            }
            invalidate();
        }
    }

    /** "Storage: 12.3 GB free of 32 GB" (the internal storage, where apps go). */
    private static String storage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            double free = fs.getAvailableBytes() / 1e9, total = fs.getTotalBytes() / 1e9;
            boolean fr = I18n.LANG_FR.equals(I18n.lang);
            String unit = fr ? " Go" : " GB";
            return I18n.tr("Armazenamento", "Storage") + (fr ? " : " : ": ") + String.format(I18n.locale(), "%.1f", free)
                    + unit + " " + I18n.tr("livres de", "free of") + " " + Math.round(total) + unit;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String version() {
        try {
            return "Hestia " + getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Hestia";
        }
    }
}
