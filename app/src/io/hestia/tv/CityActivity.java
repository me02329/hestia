package io.hestia.tv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.Background;
import io.hestia.tv.core.I18n;
import io.hestia.tv.data.City;
import io.hestia.tv.data.CitySearch;
import io.hestia.tv.data.Net;
import io.hestia.tv.data.Weather;
import io.hestia.tv.panel.PanelActivity;
import io.hestia.tv.panel.PanelEntry;

import java.util.ArrayList;
import java.util.List;

/** Settings › City: type a name with the TV's keyboard, OK on a result makes it the city of the weather. */
public class CityActivity extends PanelActivity {
    private List<City> results = new ArrayList<>();
    private String status = "";
    private int searches;   // only the latest search may show its results

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askName();
    }

    @Override protected String title() {
        return I18n.tr("Cidade", "City");
    }

    @Override protected String subtitle() {
        return Weather.get(this).place();
    }

    @Override protected List<PanelEntry> entries() {
        List<PanelEntry> list = new ArrayList<>();
        list.add(new PanelEntry("search", I18n.tr("Procurar uma cidade…", "Search for a city…"), this::askName));
        if (!status.isEmpty()) list.add(new PanelEntry("sync", status, "", true, () -> { }));
        for (City city : results) {
            list.add(new PanelEntry("home", city.name, city.region, false, () -> {
                Weather.get(this).moveTo(city);
                finish();
            }));
        }
        return list;
    }

    private void askName() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setHint(I18n.tr("Nome da cidade", "City name"));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.tr("Cidade", "City"))
                .setView(field)
                .setPositiveButton(I18n.tr("Pesquisar", "Search"), (d, which) -> search(field.getText().toString()))
                .setNegativeButton(I18n.tr("Cancelar", "Cancel"), null)
                .create();
        field.setOnEditorActionListener((v, action, event) -> {
            search(field.getText().toString());
            dialog.dismiss();
            return true;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        field.requestFocus();
    }

    private void search(String query) {
        final int search = ++searches;
        results = new ArrayList<>();
        status = I18n.tr("Procurando…", "Searching…");
        refresh();
        Background.run(() -> {
            List<City> found = new ArrayList<>();
            boolean failed = false;
            try {
                found = CitySearch.search(query);
            } catch (Exception e) {
                AppLog.w("city search: " + Net.safeError(e));
                failed = true;
            }
            final List<City> cities = found;
            final String outcome = failed ? I18n.tr("Falha na pesquisa. Verifique a rede.", "Search failed. Check the network.")
                    : found.isEmpty() ? I18n.tr("Nenhuma cidade encontrada.", "No city found.") : "";
            Background.onMain(() -> {
                if (search != searches || isDestroyed()) return;
                results = cities;
                status = outcome;
                refresh();
                if (!cities.isEmpty()) focusEntry(1);
            });
        });
    }
}
