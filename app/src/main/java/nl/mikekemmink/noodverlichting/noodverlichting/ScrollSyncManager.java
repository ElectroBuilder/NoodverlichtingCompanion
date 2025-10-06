
package nl.mikekemmink.noodverlichting.noodverlichting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ScrollSyncManager {
    private final List<WeakReference<SyncHorizontalScrollView>> clients = new ArrayList<>();
    private int currentX = 0;
    private boolean broadcasting = false;

    public void register(SyncHorizontalScrollView v) {
        if (v == null) return;
        cleanup();
        for (WeakReference<SyncHorizontalScrollView> ref : clients) {
            SyncHorizontalScrollView ex = ref.get();
            if (ex == v) {
                // Al bekend: alleen sync naar huidige X
                v.post(() -> v.syncTo(currentX));
                return;
            }
        }
        clients.add(new WeakReference<>(v));
        v.post(() -> v.syncTo(currentX));
    }

    public void unregister(SyncHorizontalScrollView v) {
        if (v == null) return;
        Iterator<WeakReference<SyncHorizontalScrollView>> it = clients.iterator();
        while (it.hasNext()) {
            SyncHorizontalScrollView ex = it.next().get();
            if (ex == null || ex == v) it.remove();
        }
    }

    public void onClientScrolled(SyncHorizontalScrollView src, int scrollX) {
        if (broadcasting) return;
        broadcasting = true;
        try {
            currentX = scrollX;
            for (WeakReference<SyncHorizontalScrollView> ref : clients) {
                SyncHorizontalScrollView v = ref.get();
                if (v == null || v == src) continue;
                v.syncTo(scrollX);
            }
        } finally {
            broadcasting = false;
        }
    }

    private void cleanup() {
        Iterator<WeakReference<SyncHorizontalScrollView>> it = clients.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove();
        }
    }

    public int getCurrentX() { return currentX; }
}
