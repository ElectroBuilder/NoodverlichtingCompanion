package nl.mikekemmink.noodverlichting.noodverlichting.sync.ui;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.sync.*;

public class SyncStatusActivity extends AppCompatActivity {
    private TextView tvServer, tvLastSync, tvPushDefects, tvPushMarkers, tvErrors, tvLastResult, tvQueueDefects, tvQueueMarkers;
    private Button btnSyncNow;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_status);

        tvServer = findViewById(R.id.tvServer);
        tvLastSync = findViewById(R.id.tvLastSync);
        tvPushDefects = findViewById(R.id.tvPushDefects);
        tvPushMarkers = findViewById(R.id.tvPushMarkers);
        tvErrors = findViewById(R.id.tvErrors);
        tvLastResult = findViewById(R.id.tvLastResult);
        tvQueueDefects = findViewById(R.id.tvQueueDefects);
        tvQueueMarkers = findViewById(R.id.tvQueueMarkers);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        progress = findViewById(R.id.progress);

        String server = SyncConfig.host(this) + ":" + SyncConfig.port(this);
        tvServer.setText("Server: " + server);
        tvLastSync.setText("Laatste sync: " + SyncConfig.lastSync(this));

        showQueue();

        btnSyncNow.setOnClickListener(v -> runSync());
    }

    private void showQueue(){
        SyncRepository repo = new SyncRepository(this);
        SyncRepository.QueueStats qs = repo.getQueueStats();
        tvQueueDefects.setText("In wachtrij (gebreken): " + qs.defects);
        tvQueueMarkers.setText("In wachtrij (markers): " + qs.markers);
    }

    private void runSync() {
        btnSyncNow.setEnabled(false);
        progress.setVisibility(android.view.View.VISIBLE);
        tvLastResult.setText("Bezig met synchroniseren...");

        new Thread(() -> {
            SyncClient client = new SyncClient(this);
            SyncRepository repo = new SyncRepository(this);
            SyncRepository.PushStats stats = new SyncRepository.PushStats();
            String resultMsg = "OK";

            try {
                // 0) Scan lokale DB voor niet-gequeue'de gebreken
                int newlyQueued = DefectQueueScanner.scanAndQueueNew(this);

                // 1) Ping
                client.ping();

                // 2) PUSH alles (met tellers)
                stats = repo.pushAllWithStats(client);

                // 2b) markeer huidig-gequeue'de gebreken als seen (zodat scanner niet her-queue't)
                DefectQueueScanner.markCurrentQueueAsSeen(this);

                // 3) PULL sinds laatste timestamp en toepassen
                String since = SyncConfig.lastSync(this);
                String raw = client.pullSince(since);
                repo.applyPullJson(raw);

                resultMsg = "OK (nieuw in queue: " + newlyQueued + ")";
            } catch (Exception e) {
                stats.errors++;
                resultMsg = "Error: " + e.getMessage();
            }

            SyncRepository.PushStats finalStats = stats;
            String finalResultMsg = resultMsg;

            runOnUiThread(() -> {
                progress.setVisibility(android.view.View.GONE);
                btnSyncNow.setEnabled(true);
                tvLastSync.setText("Laatste sync: " + SyncConfig.lastSync(this));
                tvPushDefects.setText(String.format(
                        "Gebreken push: %d toegevoegd / %d geskipt",
                        finalStats.defectsAdded, finalStats.defectsSkipped));
                tvPushMarkers.setText(String.format(
                        "Markers push: %d toegevoegd / %d bijgewerkt / %d geskipt",
                        finalStats.markersAdded, finalStats.markersUpdated, finalStats.markersSkipped));
                tvErrors.setText("Fouten: " + finalStats.errors);
                tvLastResult.setText("Laatste resultaat: " + finalResultMsg);
                showQueue();
            });
        }).start();
    }
}
