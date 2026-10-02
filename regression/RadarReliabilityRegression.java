package it.vintedaffari.app;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RadarReliabilityRegression {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        // A stale preferences snapshot must not roll back timestamp/value pairs on restart.
        File freshDir=Files.createTempDirectory("radar-fresh-test").toFile(),freshFile=new File(freshDir,"intake.properties");
        Map<String,Long> freshness=new HashMap<>();freshness.put("eventAt",100L);freshness.put("eventType",32L);freshness.put("scanAt",110L);freshness.put("lastCardsParsed",4L);freshness.put("localAnalysisLastBatchAt",120L);freshness.put("localAnalysisLastBatchSize",1L);
        RadarIntakeCounters fresh=new RadarIntakeCounters();fresh.initialize(freshFile,freshness,200);fresh.persist(freshFile);
        freshness.put("eventAt",90L);freshness.put("eventType",2048L);freshness.put("localAnalysisLastBatchAt",80L);freshness.put("localAnalysisLastBatchSize",6L);
        RadarIntakeCounters freshRestart=new RadarIntakeCounters();freshRestart.initialize(freshFile,freshness,300);
        check(freshRestart.get("eventAt")==100&&freshRestart.get("eventType")==32,"restart lost event timestamp/type pair");
        check(freshRestart.get("localAnalysisLastBatchAt")==120&&freshRestart.get("localAnalysisLastBatchSize")==1,"restart lost analysis timestamp/size pair");
        check(freshRestart.get("scanAt")==110&&freshRestart.get("lastCardsParsed")==4,"restart lost scan timestamp/card count pair");
        freshRestart.recordEvent(130,64);freshRestart.recordEvent(125,2048);
        check(freshRestart.get("vintedEvents")==2,"quiet event tail not counted in owner memory");
        freshRestart.recordScan(140,0);freshRestart.recordAnalysis(150,5);freshRestart.persist(freshFile);
        RadarIntakeCounters liveReload=new RadarIntakeCounters();liveReload.initialize(freshFile,freshness,400);
        check(liveReload.get("eventAt")==130&&liveReload.get("eventType")==64,"older callback overwrote event pair");
        check(liveReload.get("scanAt")==140&&liveReload.get("lastCardsParsed")==0,"empty scan not preserved");
        check(liveReload.get("localAnalysisLastBatchAt")==150&&liveReload.get("localAnalysisLastBatchSize")==5,"live analysis pair not persisted");
        check(liveReload.diagnosticPayload().contains("analysisSource=live-owner"),"live provenance lost");
        RadarIntakeCounters initializing=new RadarIntakeCounters();initializing.recordEvent(1000,16);initializing.initialize(freshFile,freshness,1100);
        check(initializing.get("eventAt")==1000&&initializing.get("eventType")==16,"initialization rolled back a live callback");
        File dir=Files.createTempDirectory("radar-test").toFile(),file=new File(dir,"intake.properties");
        Map<String,Long> seed=new HashMap<>();seed.put("cardsParsedTotal",14248L);
        RadarIntakeCounters first=new RadarIntakeCounters();first.add("cardsParsedTotal",3);first.initialize(file,seed,1000);check(first.get("cardsParsedTotal")==14251,"events during initialization lost");first.persist(file);
        seed.put("cardsParsedTotal",14019L);RadarIntakeCounters restart=new RadarIntakeCounters();restart.add("cardsParsedTotal",7);restart.initialize(file,seed,2000);check(restart.get("cardsParsedTotal")==14258,"stale prefs rolled back counters");check(restart.metadata().contains("counterEpoch=1000"),"epoch lost");restart.persist(file);
        seed.put("cardsParsedTotal",15000L);RadarIntakeCounters higher=new RadarIntakeCounters();higher.initialize(file,seed,3000);check(higher.get("cardsParsedTotal")==14258,"stale higher migration seed replaced owner file");
        higher.add("analysisCommitted",2);check(higher.get("analysesStored")==0&&higher.get("analysisCommitted")==2,"attempts confused with commits");
        Files.writeString(file.toPath(),"cardsParsedTotal=bad\nepoch=bad\n");RadarIntakeCounters corrupt=new RadarIntakeCounters();corrupt.initialize(file,seed,4000);check(corrupt.get("cardsParsedTotal")==15000,"corrupt file rejected valid seed");
        RadarPersistence lane=new RadarPersistence();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicInteger order=new AtomicInteger();String caller=Thread.currentThread().getName();
        check(lane.submit(()->{check(!Thread.currentThread().getName().equals(caller),"persist on caller");entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}check(order.incrementAndGet()==1,"ordering");}),"first rejected");
        check(entered.await(3,TimeUnit.SECONDS),"not started");check(lane.submit(()->check(order.incrementAndGet()==2,"second order")),"second rejected");lane.close(()->{check(order.incrementAndGet()==3,"cleanup raced writes");finished.countDown();});
        check(!lane.submit(()->{}),"work accepted after destroy");check(order.get()==0,"close blocked/drained on caller");release.countDown();check(finished.await(3,TimeUnit.SECONDS),"drain failed");lane.close(()->{throw new AssertionError("double cleanup");});
        System.out.println("PASS counters restart/migration/corruption and real worker FIFO/nonblocking/drain lifecycle");
        RadarIntakeCounters finalCounters=new RadarIntakeCounters();finalCounters.initialize(new File(dir,"final.properties"),new HashMap<>(),5000);
        RadarPersistence finalLane=new RadarPersistence();CountDownLatch drained=new CountDownLatch(1);
        finalLane.submit(()->finalCounters.add("analysisCommitted",8));
        finalLane.close(()->{finalCounters.persist(new File(dir,"final.properties"));drained.countDown();});
        check(drained.await(3,TimeUnit.SECONDS),"final flush did not finish");
        RadarIntakeCounters restored=new RadarIntakeCounters();restored.initialize(new File(dir,"final.properties"),new HashMap<>(),6000);
        check(restored.get("analysisCommitted")==8,"destroy lost accepted work counters");
        // A replacement service shares the process owner while the old instance still drains.
        RadarIntakeCounters owner=new RadarIntakeCounters();File shared=new File(dir,"overlap.properties");owner.initialize(shared,new HashMap<>(),7000);
        owner.add("cardsParsedTotal",4);Map<String,Long> stale=new HashMap<>();stale.put("cardsParsedTotal",999L);
        owner.initialize(shared,stale,8000);check(owner.get("cardsParsedTotal")==4,"recreation re-seeded an initialized owner");
        Thread old=new Thread(()->{for(int i=0;i<25;i++){owner.add("cardsParsedTotal",1);owner.persist(shared);}});
        Thread replacement=new Thread(()->{for(int i=0;i<25;i++){owner.add("cardsParsedTotal",1);owner.persist(shared);}});
        old.start();replacement.start();old.join();replacement.join();
        RadarIntakeCounters overlapReload=new RadarIntakeCounters();overlapReload.initialize(shared,new HashMap<>(),9000);
        check(overlapReload.get("cardsParsedTotal")==54&&!owner.metadata().contains("counterError=IOException"),"overlap overwrite/tmp rename lost increments");
    }
}
