package it.vintedaffari.app;
/** Consumes a count change once per fresh snapshot, within the same observation scroll. */
final class EngineMotionState {
    private String scope;private long token=Long.MIN_VALUE;private int[] counts;
    boolean initialized(){return counts!=null;}
    void restore(String scope,int[] counts){this.scope=scope;this.counts=counts.clone();}
    int[] consume(String scope,long token,int[] next){
        int[] before=counts!=null&&scope.equals(this.scope)&&token!=this.token?counts.clone():next.clone();
        this.scope=scope;this.token=token;this.counts=next.clone();return before;
    }
}
