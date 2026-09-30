package it.vintedaffari.app;

import java.util.ArrayList;
import java.util.List;

/** Emits actual production predicates for the SQLite fixture; no Android dependency. */
public final class DiscoverCategorySqlFixture {
    public static void main(String[] args) {
        List<String> invalid=new ArrayList<>();
        if(!DiscoverCategories.appendFilter(-1,invalid).isEmpty()||!DiscoverCategories.appendFilter(99,invalid).isEmpty()||!invalid.isEmpty())throw new AssertionError("Reset must remove category predicate and bindings");
        String[] labels=DiscoverCategories.labels();
        for(int i=0;i<labels.length;i++) {
            List<String> bindings=new ArrayList<>();
            String sql=DiscoverCategories.appendFilter(i,bindings);
            List<String> legacy=new ArrayList<>();
            if(!sql.equals(DiscoverCategories.appendFilter(DiscoverCategories.query(i),legacy))||!bindings.equals(legacy))throw new AssertionError("Typed and legacy category differ");
            System.out.print(labels[i]+"\t"+sql);
            for(String binding:bindings)System.out.print("\t"+binding);
            System.out.println();
        }
    }
}
