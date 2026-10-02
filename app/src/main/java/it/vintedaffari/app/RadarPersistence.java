package it.vintedaffari.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single persistence lane. Close drains accepted work and closes resources on that same lane. */
final class RadarPersistence {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"LudoRadarPersistence"));
    private boolean closed;
    synchronized boolean submit(Runnable work){
        if(closed)return false;
        worker.execute(work);return true;
    }
    synchronized void close(Runnable cleanup){
        if(closed)return;
        closed=true;worker.execute(cleanup);worker.shutdown();
    }
}
