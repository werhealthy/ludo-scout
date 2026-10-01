package it.vintedaffari.app;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
public final class DbContentionTraceRegression {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("db-trace-test").toFile();
        Files.createDirectories(new File(dir,"db_contention").toPath());
        Files.writeString(new File(dir,"db_contention/radar.txt").toPath(),"previous-pid=99");
        DbContentionTrace.configure(dir,"it.vintedaffari.app:radar","test-build",123);
        DbContentionTrace.Scope active=DbContentionTrace.start("claim");
        active.phase("ACQUIRE_WRITER");
        String snapshot=DbContentionTrace.snapshot();
        require(snapshot.contains("op=claim")&&snapshot.contains("phase=ACQUIRE_WRITER"),"active wait is missing");
        require(snapshot.contains("pid=123")&&snapshot.contains("version=test-build"),"provenance missing");
        Thread.sleep(15);active.phase("TRANSACTION");active.phase("COMMIT");active.close();active.close();
        snapshot=DbContentionTrace.snapshot();
        require(!snapshot.contains("maxBeginMs=0;maxBodyMs=0;maxCommitMs=0;maxHelperCallMs=0"),"acquisition time lost");
        require(snapshot.contains("op=claim;count=1;failures=0"),"close must count once");
        DbContentionTrace.Scope failed=DbContentionTrace.start("busy");failed.phase("ACQUIRE_WRITER");
        failed.failed(new IllegalStateException("secret title must not be logged"));failed.close();
        snapshot=DbContentionTrace.snapshot();
        require(snapshot.contains("op=busy;count=1;failures=1"),"failed acquisition lost");
        require(snapshot.contains("IllegalStateException")&&!snapshot.contains("secret title"),"only error class may be logged");
        Object monitor=new Object();CountDownLatch waiting=new CountDownLatch(1);
        Thread worker=new Thread(()->{try(DbContentionTrace.Scope scope=DbContentionTrace.start("monitor")){
            scope.phase("HELPER_CALL");waiting.countDown();synchronized(monitor){} }},"blocked-worker");
        synchronized(monitor){worker.start();waiting.await();
            long until=System.nanoTime()+2_000_000_000L;
            while(worker.getState()!=Thread.State.BLOCKED&&System.nanoTime()<until)Thread.yield();
            snapshot=DbContentionTrace.snapshot();
            require(snapshot.contains("thread=blocked-worker")&&snapshot.contains("state=BLOCKED"),"monitor wait not observable");
        }worker.join(2000);require(!worker.isAlive(),"recorder must not keep worker blocked");
        for(int i=0;i<500;i++){try(DbContentionTrace.Scope s=DbContentionTrace.start("bulk"+i)){} }
        require(DbContentionTrace.snapshot().length()<=32768,"output must be bounded");
        long until=System.nanoTime()+7_000_000_000L;
        while((!DbContentionTrace.readSummaries(dir).contains("op=busy"))&&System.nanoTime()<until)Thread.sleep(25);
        require(DbContentionTrace.readSummaries(dir).contains("previous-pid=99"),"previous process evidence must survive restart");
        require(DbContentionTrace.readSummaries(dir).contains("op=busy"),"async file export missing");
        File[] files=new File(dir,"db_contention").listFiles();require(files!=null&&files.length<=3,"bounded per-process files");
        System.out.println("PASS active wait, provenance, idempotent close, failed acquisition, redaction, monitor wait, bounded output, async export");
    }
}
