package it.vintedaffari.app;
import java.util.*;

/** Stable contract for the current rule engine and a future compact on-device model. */
public interface LocalIntelligenceBackend {
    LocalScoutBrain.Snapshot analyze(List<DealRecord> deals, BundleDatabase bundles, List<LibraryGame> library);
    String id();
    final class Rules implements LocalIntelligenceBackend{
        public LocalScoutBrain.Snapshot analyze(List<DealRecord>d,BundleDatabase b,List<LibraryGame>l){return LocalScoutBrain.analyze(d,b,l);}public String id(){return"rules-v5.3";}
    }
}
