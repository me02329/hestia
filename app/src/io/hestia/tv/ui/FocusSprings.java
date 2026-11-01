package io.hestia.tv.ui;

import java.util.ArrayList;
import java.util.List;

/** One spring per item of a list or grid: the focused one rises to 1, the others fall to 0. */
public final class FocusSprings {
    private final List<Spring> springs = new ArrayList<>();
    private final float response, damping;

    public FocusSprings(float response, float damping) {
        this.response = response;
        this.damping = damping;
    }

    public void resize(int count) {
        while (springs.size() < count) springs.add(new Spring(response, damping, 0));
        while (springs.size() > count) springs.remove(springs.size() - 1);
    }

    /** Points the springs at the focused index (-1: none). */
    public void focus(int index, boolean snap) {
        for (int i = 0; i < springs.size(); i++) {
            Spring s = springs.get(i);
            s.target = i == index ? 1 : 0;
            if (snap) s.snap(s.target);
        }
    }

    /** 0 to 1 and beyond while bouncing; 0 for an index it doesn't have yet. */
    public float raw(int i) {
        return i < springs.size() ? springs.get(i).value : 0;
    }

    public float value(int i) {
        return Colors.clamp(raw(i));
    }

    /** True while one still moves. */
    public boolean step(float dt) {
        boolean moving = false;
        for (Spring s : springs) moving |= s.step(dt);
        return moving;
    }
}
