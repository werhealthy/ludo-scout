package it.vintedaffari.app;

import java.util.ArrayList;
import java.util.List;

/** Emits actual production predicates for the SQLite fixture; no Android dependency. */
public final class DiscoverCategorySqlFixture {
    public static void main(String[] args) {
        String[] labels=DiscoverCategories.labels();
        for(int i=0;i<labels.length;i++) {
            List<String> bindings=new ArrayList<>();
            String sql=DiscoverCategories.appendFilter(DiscoverCategories.query(i),bindings);
            System.out.print(labels[i]+"\t"+sql);
            for(String binding:bindings)System.out.print("\t"+binding);
            System.out.println();
        }
    }
}
