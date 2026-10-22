package io.hestia.tv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class NetTest {
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    public static void main(String[] args) throws Exception {
        for (String address : new String[] {"http://www.bing.com/", "https://example.com/",
                "https://www.bing.com.evil.test/", "https://user@www.bing.com/",
                "https://www.bing.com:1234/", "https://www.bing.com/#fragment", "https://api.themoviedb.org/3"}) {
            try { Net.open(address); throw new AssertionError("Accepted " + address); }
            catch (IOException expected) {}
        }
        HttpURLConnection c = Net.open("https://www.bing.com/HPImageArchive.aspx");
        require(!c.getInstanceFollowRedirects());
        require(c.getConnectTimeout() > 0 && c.getReadTimeout() > 0);
        c.disconnect(); // Never connected; these tests perform no network I/O.
        byte[] bytes = "12345".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Net.copy(new ByteArrayInputStream(bytes), out, 5);
        require(out.size() == 5);
        try {
            Net.copy(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), 4);
            throw new AssertionError("Oversized payload accepted");
        } catch (IOException expected) {}
        require(!Net.safeError(new IOException("https://www.bing.com/?token=secret")).contains("secret"));
        require(Net.safeError(new IOException("HTTP 401")).equals("HTTP 401"));
        System.out.println("Network boundary checks passed (offline).");
    }
}
