package io.hestia.tv.data;

/** Compares versions like "1.13-safe" and the release tag "v1.14". Plain Java: tested on a JVM (tests/java). */
final class Version {
    private Version() {}

    /** A well-formed tag: what the network says is only shown if it looks like a version. */
    static boolean isTag(String tag) {
        return tag != null && tag.matches("v?[0-9]{1,4}(\\.[0-9]{1,4}){0,2}(-[a-z]{1,10})?");
    }

    static boolean isNewer(String candidate, String current) {
        int[] a = parts(candidate), b = parts(current);
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    private static int[] parts(String version) {
        String[] numbers = version.replaceFirst("^v", "").replaceFirst("-.*$", "").split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < out.length && i < numbers.length; i++) {
            try {
                out[i] = Integer.parseInt(numbers[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
