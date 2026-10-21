package io.freemytv;

public final class FeedTextTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        // Only plain https links reach the browser.
        require(FeedText.safeLink(" https://www.bleepingcomputer.com/news/a-b/ ").equals("https://www.bleepingcomputer.com/news/a-b/"), "https kept");
        for (String bad : new String[] {null, "", "http://example.com/", "javascript:alert(1)", "file:///etc/passwd",
                "intent://x#Intent;end", "https://user:pw@example.com/", "https://example.com/a b",
                "https://example.com/\"onload", "https://" + "a".repeat(700) + ".com/", "content://x"}) {
            require(FeedText.safeLink(bad).isEmpty(), "rejected: " + bad);
        }
        require(FeedText.domain("https://www.thehackernews.com/2026/x.html").equals("thehackernews.com"), "domain");
        // Titles: one line of plain text, bounded.
        require(FeedText.clean("<b>Hi</b> &amp; <i>bye</i>\n  now").equals("Hi & bye now"), "clean");
        require(FeedText.clean("x".repeat(500)).length() == FeedText.TITLE_MAX, "bounded");
        require(FeedText.clean(null).isEmpty(), "null title");
        // RSS (RFC 822) and Atom (ISO 8601) dates; garbage is 0 (dropped).
        require(FeedText.parseDate("Thu, 22 Oct 2026 00:27:34 +0530") == 1792609054000L, "rfc822");
        require(FeedText.parseDate("2026-10-21T18:57:34Z") == 1792609054000L, "iso8601");
        require(FeedText.parseDate("yesterday") == 0 && FeedText.parseDate(null) == 0, "garbage");
        System.out.println("Feed text checks passed.");
    }
}
