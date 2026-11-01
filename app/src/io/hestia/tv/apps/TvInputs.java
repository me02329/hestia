package io.hestia.tv.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import io.hestia.tv.core.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The TV side: live TV and the HDMI inputs. */
public final class TvInputs {
    private TvInputs() {}

    /** Live TV apps of some brands (the antenna's channels open through the standard Android request first). */
    static final String[] LIVE_TV_APPS = {"com.tcl.tv", "com.google.android.tv", "com.mediatek.wwtv.tvcenter", "com.sony.dtv.tvx"};

    /** Live TV and every HDMI input (the ones with no device are marked not connected). */
    public static List<Card> liveAndInputs(Context c) {
        List<Card> cards = new ArrayList<>();
        if (liveTv(c) != null) cards.add(Card.shortcut(Card.Kind.LIVE_TV, "live", I18n.tr("TV ao Vivo", "Live TV"), "tv"));
        cards.addAll(hdmi(c));
        return cards;
    }

    /** Every HDMI input. A name you gave the input in the TV's settings is used as the label. */
    public static List<Card> hdmi(Context c) {
        List<Card> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> list = new ArrayList<>();
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) list.add(info);
            }
        } catch (RuntimeException e) {
            return out;
        }
        Collections.sort(list, (a, b) -> a.getId().compareTo(b.getId()));
        Pattern digit = Pattern.compile("(\\d)");
        for (int i = 0; i < list.size(); i++) {
            TvInputInfo info = list.get(i);
            int number = i + 1;
            Matcher m = digit.matcher(String.valueOf(info.loadLabel(c)));
            if (m.find()) number = Integer.parseInt(m.group(1));
            // TCL TVs report "connected standby" for every port, with or without a device; only truly "connected"
            // means a device that is on and sending a signal.
            int state = TvInputManager.INPUT_STATE_DISCONNECTED;
            try {
                state = tim.getInputState(info.getId());
            } catch (RuntimeException ignored) {
            }
            CharSequence custom = null;
            try {
                custom = info.loadCustomLabel(c);
            } catch (RuntimeException ignored) {
            }
            String label = custom != null && custom.length() > 0 ? custom.toString() : "HDMI " + number;
            Card card = Card.shortcut(Card.Kind.INPUT, "hdmi" + number, label, "hdmi");
            card.inputId = info.getId();
            card.number = String.valueOf(number);
            card.connected = state == TvInputManager.INPUT_STATE_CONNECTED;
            out.add(card);
        }
        Collections.sort(out, (a, b) -> a.number.compareTo(b.number));
        return out;
    }

    /**
     * Live TV: the antenna input's channels through the standard Android request (opening the TV app by itself may go
     * back to the last input used, like an HDMI); otherwise a known live TV app. Null = this TV has none.
     */
    public static Intent liveTv(Context c) {
        PackageManager pm = c.getPackageManager();
        String tuner = tunerInput(c);
        if (tuner != null) {
            Intent channels = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(tuner));
            if (channels.resolveActivity(pm) != null) return channels;
        }
        for (String pkg : LIVE_TV_APPS) {
            Intent app = Launcher.appIntent(pm, pkg);
            if (app != null) return app;
        }
        return null;
    }

    /**
     * The TV's antenna (tuner) input, or null. Some "tuner" inputs aren't antennas: on TCL, AirPlay and the IPTV
     * channels declare TYPE_TUNER too (and AirPlay is listed first), so those are skipped; a service named like a tuner
     * comes first, then the TV maker's own, then any other.
     */
    private static String tunerInput(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return null;
        String best = null;
        int bestScore = -1;
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() != TvInputInfo.TYPE_TUNER || info.isPassthroughInput()) continue;
                String id = info.getId().toLowerCase(Locale.ROOT);
                if (id.contains("airplay") || id.contains("iptv") || id.contains("channelplus")) continue;
                int score = 0;
                if (id.contains("tuner")) score += 2;
                if (info.getServiceInfo() != null && info.getServiceInfo().applicationInfo != null
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = info.getId();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return best;
    }
}
