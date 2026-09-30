package it.vintedaffari.app;

import java.util.List;
import java.util.Locale;

/** Home clusters filter BGG categories only, without changing game eligibility. */
final class DiscoverCategories {
    private DiscoverCategories() {}
    private static final String PREFIX="Categoria: ";
    private static final String[] LABELS={"Strategia","Famiglia","Party","Fantasy","Carte","Sci-Fi","Mistero","Guerra"};
    private static final String[][] BGG={
        {"Economic","City Building","Civilization","Industry / Manufacturing","Territory Building","Trains"},
        {"Children's Game","Animals","Educational","Puzzle","Dice","Action / Dexterity"},
        {"Party Game","Bluffing","Deduction","Humor","Negotiation","Real-time"},
        {"Fantasy","Adventure","Mythology","Exploration"},
        {"Card Game","Collectible Components"},
        {"Science Fiction","Space Exploration"},
        {"Murder / Mystery","Horror","Spies / Secret Agents","Zombies"},
        {"Wargame","World War I","World War II","Napoleonic","American Civil War","Vietnam War","Modern Warfare"}
    };
    static String[] labels(){return LABELS.clone();}
    static String query(int index){return PREFIX+LABELS[Math.max(0,Math.min(index,LABELS.length-1))];}
    private static int index(String query){
        if(query==null||!query.startsWith(PREFIX))return -1;
        for(int i=0;i<LABELS.length;i++)if(query.substring(PREFIX.length()).equals(LABELS[i]))return i;
        return -1;
    }
    static String searchText(String query){return index(query)>=0?"":query;}
    static String appendFilter(String query,List<String> args){
        int index=index(query);if(index<0)return "";
        StringBuilder sql=new StringBuilder(" AND (");
        for(int i=0;i<BGG[index].length;i++){
            if(i>0)sql.append(" OR ");
            sql.append("INSTR(' · ' || LOWER(TRIM(COALESCE(g.categories,''))) || ' · ', ?) > 0");
            args.add(" · "+BGG[index][i].toLowerCase(Locale.ROOT)+" · ");
        }
        return sql.append(")").toString();
    }
}
