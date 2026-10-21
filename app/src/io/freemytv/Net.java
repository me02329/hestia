package io.freemytv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Fixed HTTPS origins, no redirects, bounded payloads and read deadlines. */
final class Net {
    private Net() {}
    static final int JSON_LIMIT = 2 * 1024 * 1024;
    static final int IMAGE_LIMIT = 16 * 1024 * 1024;
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "api.open-meteo.com", "www.bing.com",
            // Cybersecurity RSS feeds (News)
            "feeds.feedburner.com", "www.bleepingcomputer.com", "www.securityweek.com", "cert.cyber.gouv.fr",
            "www.zataz.com"));

    static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !HOSTS.contains(url.getHost())
                || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)
                || url.getRef() != null) throw new IOException("Unapproved network endpoint");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Hestia/1.8-safe");
        return c;
    }

    static String json(String address) throws IOException {
        return text(address, "application/json");
    }

    static String feed(String address) throws IOException {
        return text(address, "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8");
    }

    private static String text(String address, String accept) throws IOException {
        HttpURLConnection c = open(address);
        c.setRequestProperty("Accept", accept);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                copy(in, out, JSON_LIMIT);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (IOException e) {
            // Transport exceptions may include the full URL and its API key.
            throw new IOException(safeError(e));
        } finally {
            c.disconnect();
        }
    }

    /** Saves the response body to `target` through a temporary file: a partial download never replaces it. */
    static void download(String address, File target, int limit) throws IOException {
        File part = new File(target.getPath() + ".part");
        HttpURLConnection c = open(address);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                copy(in, out, limit);
            }
            if (!part.renameTo(target)) throw new IOException("Couldn't save the download");
        } catch (IOException e) {
            throw new IOException(safeError(e));
        } finally {
            c.disconnect();
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    static String safeError(Exception e) {
        String m = e.getMessage();
        return m != null && m.matches("HTTP [0-9]{3}") ? m
                : "Network/data error (" + e.getClass().getSimpleName() + ")";
    }

    static void copy(InputStream in, OutputStream out, int limit) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0, n;
        long started = System.nanoTime();
        while ((n = in.read(buffer)) != -1) {
            if (n > limit - total) throw new IOException("Response exceeds size limit");
            if (System.nanoTime() - started > 30_000_000_000L) throw new IOException("Response deadline exceeded");
            out.write(buffer, 0, n);
            total += n;
        }
    }
}
