package it.vintedaffari.app;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only presentation of a fresh radar heartbeat, scoped to captured identities. */
final class EngineLocalActivity {
    private EngineLocalActivity() {}
    static int activeCount(long at,long now,long value,String detail,List<String> scope) {
        if(at<=0||now<at||now-at>10_000L||value<=0||detail==null||!detail.startsWith("RUNNING\n"))return 0;
        Set<String> active=new HashSet<>();
        for(String signature:detail.substring(8).split("\n"))if(scope.contains(signature))active.add(signature);
        return (int)Math.min(value,active.size());
    }
}
