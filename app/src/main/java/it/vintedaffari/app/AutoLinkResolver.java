package it.vintedaffari.app;

import android.content.Context;

/** Zero-cost resolver chain. No Google/Brave/Serper keys. */
public final class AutoLinkResolver {
    public interface Callback{
        void onResolved(VintedLinkResolver.Result r);
        void onUnresolved(String signature,String reason);
        default void onProgress(String signature,int progress,String stage){}
        default void onCandidates(String signature,java.util.List<VintedLinkResolver.CandidateOption> candidates){}
    }
    private final VintedLinkResolver direct;
    public AutoLinkResolver(Context c){direct=new VintedLinkResolver(c);}
    public long nextAllowedAt(DealRecord d){return direct.nextAllowedAt(d);}
    public void resolve(DealRecord d,Callback cb){
        direct.resolve(d,new VintedLinkResolver.Callback(){
            @Override public void onResolved(VintedLinkResolver.Result r){if(cb!=null)cb.onResolved(r);}
            @Override public void onUnresolved(String signature,String reason){if(cb!=null)cb.onUnresolved(signature,reason);}
            @Override public void onProgress(String signature,int progress,String stage){if(cb!=null)cb.onProgress(signature,progress,stage);}
            @Override public void onCandidates(String signature,java.util.List<VintedLinkResolver.CandidateOption> candidates){if(cb!=null)cb.onCandidates(signature,candidates);}
        });
    }
}
