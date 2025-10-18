package nl.mikekemmink.noodverlichting.noodverlichting.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override
    public Result doWork() {
        try{
            nl.mikekemmink.noodverlichting.noodverlichting.sync.SyncClient client = new nl.mikekemmink.noodverlichting.noodverlichting.sync.SyncClient(getApplicationContext());
            nl.mikekemmink.noodverlichting.noodverlichting.sync.SyncRepository repo = new nl.mikekemmink.noodverlichting.noodverlichting.sync.SyncRepository(getApplicationContext());
            // push local queue first
            repo.pushAll(client);
            // pull changes since last sync
            String raw = client.pullSince(nl.mikekemmink.noodverlichting.noodverlichting.sync.SyncConfig.lastSync(getApplicationContext()));
            repo.applyPullJson(raw);
            return Result.success();
        }catch(Exception e){
            return Result.retry();
        }
    }
}
