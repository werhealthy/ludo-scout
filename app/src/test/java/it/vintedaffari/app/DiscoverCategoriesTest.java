package it.vintedaffari.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoverCategoriesTest {
    @Test public void strategyIncludesEveryMappedCategoryAndNeverSearchesTitles() {
        List<String> args=new ArrayList<>();
        String sql=DiscoverCategories.appendFilter("Categoria: Strategia",args);
        assertFalse(sql.contains("canonical_name"));
        assertEquals(6,args.size());
        assertTrue(args.contains(" · economic · "));
        assertTrue(args.contains(" · trains · "));
        assertTrue(args.contains(" · industry / manufacturing · "));
    }
    @Test public void categoryQueryIsDistinctFromOrdinaryTextSearch() {
        List<String> args=new ArrayList<>();
        assertEquals("",DiscoverCategories.appendFilter("Fantasy",args));
        assertEquals("",DiscoverCategories.appendFilter("Categoria: sconosciuta",args));
        assertTrue(args.isEmpty());
        assertEquals("Sky Team",DiscoverCategories.searchText("Sky Team"));
        assertEquals("",DiscoverCategories.searchText("Categoria: Sci-Fi"));
    }
    @Test public void clusterSupportsOrAndOverlappingMembership() {
        List<String> args=new ArrayList<>();
        String sql=DiscoverCategories.appendFilter("Categoria: Famiglia",args);
        assertTrue(sql.contains(" OR "));
        assertTrue(args.contains(" · dice · "));
        assertTrue(args.contains(" · educational · "));
        args.clear();
        DiscoverCategories.appendFilter("Categoria: Mistero",args);
        assertTrue(args.contains(" · murder / mystery · "));
        assertTrue(args.contains(" · spies / secret agents · "));
        args.clear();
        DiscoverCategories.appendFilter("Categoria: Party",args);
        assertTrue(args.contains(" · real-time · "));
        assertTrue(args.contains(" · deduction · "));
    }
}
