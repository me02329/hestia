package io.hestia.tv.panel;

/** One row of a panel: icon, label, optional detail on the right, and what OK does. */
public final class PanelEntry {
    final String symbol, label, detail;
    final Runnable action;
    final boolean dim;

    public PanelEntry(String symbol, String label, Runnable action) {
        this(symbol, label, "", false, action);
    }

    public PanelEntry(String symbol, String label, String detail, boolean dim, Runnable action) {
        this.symbol = symbol;
        this.label = label;
        this.detail = detail == null ? "" : detail;
        this.dim = dim;
        this.action = action;
    }
}
