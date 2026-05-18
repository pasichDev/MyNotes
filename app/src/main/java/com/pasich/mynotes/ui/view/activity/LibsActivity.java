package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.lib.LibItem;
import com.pasich.mynotes.data.model.lib.LibSection;
import com.pasich.mynotes.databinding.ActivityLibsBinding;
import com.pasich.mynotes.utils.adapters.LibsSectionAdapter;
import dagger.hilt.android.AndroidEntryPoint;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

@AndroidEntryPoint
public class LibsActivity extends BaseActivity {

    protected ActivityLibsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityLibsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        List<LibItem> allLibs = loadLibsJson();

        List<LibSection> sections = buildSections(allLibs);

        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.libsList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new LibsSectionAdapter(sections));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /** Reads the generated libs.json file from raw/ and converts it into a LibItem list. */
    private List<LibItem> loadLibsJson() {
        try {
            InputStream is = getResources().openRawResource(R.raw.libs);
            byte[] b = new byte[is.available()];
            is.read(b);

            JSONArray arr = new JSONArray(new String(b, StandardCharsets.UTF_8));
            List<LibItem> list = new ArrayList<>();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(
                        new LibItem(
                                o.getString("id"),
                                o.optString("version", ""),
                                o.getString("source")));
            }

            return list;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Builds sections for the UI. Groups dependencies by source (gradle, js, js-dev), sorts each
     * section by library name.
     */
    private List<LibSection> buildSections(List<LibItem> libs) {

        Map<String, List<LibItem>> map = new HashMap<>();

        for (LibItem l : libs) {
            if (!map.containsKey(l.source())) {
                map.put(l.source(), new ArrayList<>());
            }
            Objects.requireNonNull(map.get(l.source())).add(l);
        }

        for (List<LibItem> section : map.values()) {
            section.sort(Comparator.comparing(LibItem::id));
        }

        List<LibSection> sections = new ArrayList<>();

        if (map.containsKey("gradle"))
            sections.add(new LibSection("Gradle Dependencies", map.get("gradle")));

        if (map.containsKey("js")) sections.add(new LibSection("JS Dependencies", map.get("js")));

        if (map.containsKey("js-dev"))
            sections.add(new LibSection("JS Dev Dependencies", map.get("js-dev")));

        return sections;
    }

    @Override
    public void initListeners() {
        // Not impl
    }
}
