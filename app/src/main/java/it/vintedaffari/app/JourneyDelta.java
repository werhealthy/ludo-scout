package it.vintedaffari.app;
import java.util.Map;
import java.util.Set;

/** Gate changes for only the displayed exploration's observed listings. */
final class JourneyDelta {
    private JourneyDelta(){}
    static int mask(int[] gates){int mask=0;for(int i=0;i<5;i++)if(gates[i]>0)mask|=1<<i;return mask;}
    static int[] delta(Map<String,Integer> before,Map<String,Integer> after,Set<String> run){
        int[] delta=new int[5];for(String id:run){if(!after.containsKey(id))continue;int a=after.get(id),b=before.getOrDefault(id,0);for(int i=0;i<5;i++)delta[i]+=((a>>i)&1)-((b>>i)&1);}return delta;
    }
}
