package it.vintedaffari.app;
import java.io.IOException;
import java.net.HttpURLConnection;
/** Cancellation owns the active connection, so leaving the editor releases its network work. */
final class CoverImportRequest {
 private volatile boolean cancelled;
 private HttpURLConnection connection;
 void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Caricamento annullato");}
 synchronized void register(HttpURLConnection value)throws IOException{if(cancelled){value.disconnect();throw new IOException("Caricamento annullato");}connection=value;check();}
 synchronized void release(HttpURLConnection value){if(connection==value)connection=null;}
 synchronized void cancel(){cancelled=true;if(connection!=null){connection.disconnect();connection=null;}}
}
