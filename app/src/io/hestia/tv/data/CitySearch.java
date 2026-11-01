package io.hestia.tv.data;

import io.hestia.tv.core.I18n;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Looks up a city by name (Open-Meteo's geocoding, no key). Sends only what was typed. Blocks: not on the main thread. */
public final class CitySearch {
    private CitySearch() {}

    public static List<City> search(String query) throws IOException, org.json.JSONException {
        List<City> found = new ArrayList<>();
        String url = City.searchUrl(query, I18n.lang);
        if (url == null) return found;
        JSONArray results = new JSONObject(Net.json(url)).optJSONArray("results");   // absent when nothing matches
        if (results == null) return found;
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            City city = City.of(o.optString("name"), o.optString("admin1"), o.optString("country"),
                    o.optDouble("latitude"), o.optDouble("longitude"));
            if (city != null) found.add(city);
        }
        return found;
    }
}
