package nl.mikekemmink.noodverlichting.noodverlichting.columns;

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import nl.mikekemmink.noodverlichting.R;

public class ColumnSettingsActivity extends AppCompatActivity {

    private List<ColumnConfig> config;
    private ColumnOrderAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_column_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Kolommen");
        }

        config = ColumnConfigManager.load(this);

        RecyclerView rv = findViewById(R.id.rvColumns);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ColumnOrderAdapter(config);
        rv.setAdapter(adapter);

// Long-press drag inschakelen via ItemTouchHelper
        ItemTouchHelper helper = new ItemTouchHelper(ColumnOrderAdapter.makeTouchHelperCallback(adapter));
        helper.attachToRecyclerView(rv);

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            // Neem altijd de actuele lijst uit de adapter
            List<ColumnConfig> current = adapter.getItems();

            // Minimaal 1 zichtbaar afdwingen
            int visibleCount = 0;
            for (ColumnConfig cc : current) if (cc.visible) visibleCount++;
            if (visibleCount == 0) {
                boolean fixed = false;
                for (ColumnConfig cc : current) { if ("code".equals(cc.alias)) { cc.visible = true; fixed = true; break; } }
                if (!fixed) for (ColumnConfig cc : current) { if ("nr".equals(cc.alias)) { cc.visible = true; fixed = true; break; } }
                if (!fixed && !current.isEmpty()) current.get(0).visible = true;
                android.widget.Toast.makeText(this,
                        "Minimaal één kolom moet zichtbaar zijn. Ik heb er één voor je aangezet.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            android.util.Log.d("Columns", "SAVE -> " + current);
            // 🔐 Bewaar via de manager die FixtureListActivity ook gebruikt
            ColumnConfigManager.save(this, current);

            setResult(RESULT_OK);
            finish();
        });

    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}