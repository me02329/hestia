package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Photos of the day (Bing's daily image archive, no key): the last ~15 landscapes at 1920 × 1080, saved on disk so the
 * home screen and the screensaver open with a picture, refreshed every 6 h while one of them is visible.
 */
final class Photos {
    static final class Photo {
        final File file;
        final String title, credit;

        Photo(File file, String title, String credit) {
            this.file = file;
            this.title = title;
            this.credit = credit;
        }
    }

    interface Listener { void onPhotos(); }

    private static final String ARCHIVE = "https://www.bing.com/HPImageArchive.aspx?format=js&n=8";
    /** Only Bing's own thumbnail path: the image URL is built from it and can't leave www.bing.com. */
    private static final Pattern URLBASE = Pattern.compile("/th\\?id=[A-Za-z0-9._-]{1,120}");
    private static final long EVERY = 6 * 3600_000L;
    private static final int KEEP = 24;

    private static Photos instance;

    static synchronized Photos get(Context c) {
        if (instance == null) instance = new Photos(c.getApplicationContext());
        return instance;
    }

    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile List<Photo> photos = new ArrayList<>();
    private volatile long fetchedAt;
    private volatile boolean fetching;

    private Photos(Context c) {
        dir = new File(c.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        io.execute(() -> {
            try {
                JSONObject index = new JSONObject(read(new File(dir, "index.json")));
                fetchedAt = index.optLong("at");
                photos = parse(index.getJSONArray("photos"));
                notifyListeners();
            } catch (IOException | org.json.JSONException ignored) {
            }
        });
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Newest first; only the ones on disk. */
    List<Photo> list() {
        return photos;
    }

    void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (fetching || (now - fetchedAt < EVERY && now >= fetchedAt && !photos.isEmpty())) return;
        fetching = true;
        io.execute(() -> {
            try {
                fetch();
            } catch (Exception e) {
                AppLog.w("photos: " + Net.safeError(e));
            } finally {
                fetching = false;
            }
        });
    }

    private void fetch() throws Exception {
        String market = Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry();
        if (!market.matches("[a-z]{2}-[A-Z]{2}")) market = "en-US";
        JSONArray found = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int idx : new int[] {0, 7}) {   // the archive serves 8 at a time, up to 15 days back
            JSONArray images = new JSONObject(Net.json(ARCHIVE + "&idx=" + idx + "&mkt=" + market)).getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject o = images.getJSONObject(i);
                String base = o.optString("urlbase");
                if (!URLBASE.matcher(base).matches()) continue;
                String id = base.substring("/th?id=".length());
                if (!seen.add(id)) continue;
                File f = new File(dir, id + ".jpg");
                if (!f.exists()) {
                    try {
                        Net.download("https://www.bing.com" + base + "_1920x1080.jpg", f, Net.IMAGE_LIMIT);
                    } catch (IOException e) {
                        AppLog.w("photo " + id + ": " + e.getMessage());
                        continue;
                    }
                }
                found.put(new JSONObject().put("id", id).put("title", o.optString("title"))
                        .put("credit", o.optString("copyright")));
            }
        }
        if (found.length() == 0) return;
        fetchedAt = System.currentTimeMillis();
        write(new File(dir, "index.json"), new JSONObject().put("at", fetchedAt).put("photos", found).toString());
        photos = parse(found);
        prune();
        notifyListeners();
    }

    private List<Photo> parse(JSONArray list) {
        List<Photo> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id");
            if (!id.matches("[A-Za-z0-9._-]{1,120}")) continue;
            File f = new File(dir, id + ".jpg");
            if (f.exists()) out.add(new Photo(f, o.optString("title"), o.optString("credit")));
        }
        return out;
    }

    /** Keeps the photos in the index plus the newest others, up to KEEP. */
    private void prune() {
        Set<String> keep = new HashSet<>();
        for (Photo p : photos) keep.add(p.file.getName());
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int kept = keep.size();
        for (File f : files) {
            if (keep.contains(f.getName())) continue;
            if (kept < KEEP) {
                kept++;
                continue;
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onPhotos(); });
    }

    /** Decodes and center-crops the photo to exactly w × h (background thread). */
    static Bitmap load(File f, int w, int h) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= w && bounds.outHeight / (opts.inSampleSize * 2) >= h) {
            opts.inSampleSize *= 2;
        }
        Bitmap src = BitmapFactory.decodeFile(f.getPath(), opts);
        if (src == null) return null;
        if (src.getWidth() == w && src.getHeight() == h) return src;
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        float sw = w / scale, sh = h / scale;
        float sx = (src.getWidth() - sw) / 2, sy = (src.getHeight() - sh) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh)),
                new RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        src.recycle();
        return out;
    }

    private static String read(File f) throws IOException {
        if (f.length() > Net.JSON_LIMIT) throw new IOException("index too large");
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void write(File f, String text) throws IOException {
        File part = new File(f.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!part.renameTo(f)) throw new IOException("couldn't save the photo index");
    }
}
