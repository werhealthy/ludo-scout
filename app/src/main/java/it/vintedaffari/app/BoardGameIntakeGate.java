package it.vintedaffari.app;

import java.text.Normalizer;
import java.util.*;

/**
 * Decides whether an unresolved Vinted observation is worth a human BGG fact-check.
 *
 * The important product rule is asymmetrical:
 * - confirmed/plausible board-game evidence may reach review;
 * - generic marketplace noise must never use the human review queue as a catch-all.
 *
 * Auto-filtered rows are quarantined, not physically deleted, so the decision is reversible.
 */
public final class BoardGameIntakeGate {
    public enum Action { ACCEPT, REVIEW, QUARANTINE }

    public static final class Decision {
        public final Action action;
        public final String reason;
        Decision(Action action,String reason){this.action=action;this.reason=reason;}
    }

    private static final Set<String> FILLER = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "il","lo","la","i","gli","le","un","uno","una","the","a","an","of","and","e","di","da","del","della","dei","delle",
            "gioco","giochi","tavolo","societa","board","game","games","boardgame","tabletop",
            "edizione","edition","versione","version","italiano","italiana","italian","nuovo","nuova","usato","usata",
            "completo","completa","originale","original","vendo","set","lotto"
    )));

    private static final String[] BOARD_GAME_CUES = {
            "gioco da tavolo","giochi da tavolo","gioco di societa","giochi di societa","board game","boardgame",
            "tabletop game","espansione","expansion","deckbuilding","deck building","worker placement","gioco di carte"
    };

    private static final String[] STRONG_NON_GAME = {
            "scarpe","sneakers","stivali","sandali","ciabatte","occhiali da sole","sunglasses",
            "maglietta","t shirt","tshirt","felpa","pantaloni","jeans","giacca","cappotto","vestito","abito",
            "borsa","borsetta","zaino","portafoglio","cintura","profumo","orologio","collana","orecchini","bracciale",
            "libro","libri","romanzo","romanzi","fumetto","fumetti","manga","dvd","blu ray","bluray","vinile","compact disc","audio cd","cd musicale",
            "funko","action figure","figurina","figurine","photocard","peluche","pupazzo","bambola","bambole","modellino","modellini","statuetta","statuette",
            "warhammer",
            "videogioco","videogame","video game","playstation 5","playstation 4","playstation 3","ps5","ps4","ps3",
            "xbox one","xbox series","xbox 360","nintendo switch","switch lite","nintendo 3ds","nintendo ds","wii u",
            "isbn","paperback","hardcover","copertina rigida","copertina flessibile","pagine","editore","autore",
            "biografia","saggio","enciclopedia","rivista","magazine","literature","world literature","letteratura",
            "hi hat","hihat","cymbal","cymbals","piatto batteria","piatti batteria","drum cymbal","crash cymbal","ride cymbal","musicassette","audiocassetta","music cd"
    };

    private BoardGameIntakeGate(){}

    /** Titles explicitly reported by the product owner as cross-category collisions. These are not
     * deleted blindly: they simply require a second positive board-game signal before an automatic
     * BGG association is allowed. Future collisions are learned from repeated user exclusions. */
    private static final Set<String> SEEDED_COLLISION_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "watergate"
    )));

    public static boolean seededCollisionTitle(String title){return SEEDED_COLLISION_TITLES.contains(norm(title));}

    /** Guard even a nominally exact BGG match when the marketplace title is known to collide with
     * books/media/collectibles. Exact string equality is identity evidence, not product-type proof. */
    public static Decision matchedAnalysis(VintedCard card,ListingClassifier.Result product,GameAnalysis analysis,boolean learnedCollisionRisk){
        String title=card==null?"":card.title,raw=card==null?"":card.rawDescription;
        if(isStrongNonGameText(title,raw))return new Decision(Action.QUARANTINE,"Segnali forti di categoria non gioco da tavolo");
        // BGG title equality identifies a candidate, not the marketplace product. Only an
        // independently positive marketplace classification can publish automatically.
        if(product==null||!product.hasPositiveBoardGameEvidence())
            return new Decision(Action.QUARANTINE,"Il titolo coincide con BGG ma manca una prova indipendente che l'oggetto sia un gioco da tavolo");
        boolean risky=seededCollisionTitle(title)||learnedCollisionRisk;
        if(!risky)return new Decision(Action.ACCEPT,"Match BGG con contesto marketplace non a rischio");
        String publisher=analysis==null?"":analysis.productPublisher;
        String brand=card==null?"":card.brand;
        String context=title+" "+raw+" "+brand+" "+publisher;
        if(hasStrongBoardGameCue(context))return new Decision(Action.ACCEPT,"Titolo collision-prone ma con segnale esplicito da gioco da tavolo");
        String n=norm(context);
        // Common board-game publishers/components provide a positive second signal without forcing
        // every seller to write 'gioco da tavolo' in the title.
        for(String cue:new String[]{"matagot","asmodee","ravensburger","giochi uniti","cranio creations","devir","goliath games","kosmos","lookout games","stegmaier"})
            if(containsPhrase(n,norm(cue)))return new Decision(Action.ACCEPT,"Titolo collision-prone ma publisher ludico riconoscibile");
        return new Decision(Action.QUARANTINE,"Titolo condiviso con prodotti non ludici: manca un secondo indizio da gioco da tavolo");
    }

    /** Live Accessibility/JS decision after the local catalog matcher has had a chance to identify it. */
    public static Decision afterAnalysis(VintedCard card,ListingClassifier.Result product,GameAnalysis analysis){
        String title=card==null?"":card.title;
        String raw=card==null?"":card.rawDescription;
        if(isStrongNonGameText(title,raw))return new Decision(Action.QUARANTINE,"Segnali forti di categoria non gioco da tavolo");
        if(product==null||!product.hasPositiveBoardGameEvidence())
            return new Decision(Action.QUARANTINE,"Nessuna prova positiva di prodotto gioco da tavolo");
        if(analysis==null)return new Decision(Action.QUARANTINE,"Nessuna evidenza BGG disponibile");
        if("excluded".equals(analysis.status))return new Decision(Action.QUARANTINE,"Il matcher locale classifica l'articolo come accessorio/lotto");
        double score=analysis.matchConfidence==null?0.0:analysis.matchConfidence;
        // Human review is reserved for a narrow ambiguity band. Merely writing "gioco da tavolo"
        // is not a reason to ask the user to do Ludo's matching work.
        if(score>=78.0 && !empty(analysis.candidateName) && plausibleOverlap(title,analysis.candidateName))
            return new Decision(Action.REVIEW,"Candidato BGG molto plausibile ma non abbastanza sicuro ("+Math.round(score)+")");
        return new Decision(Action.QUARANTINE,"Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check");
    }

    /** Background matcher decision for unresolved provisional games. */
    public static Decision unresolvedTitle(String title,List<BggSearchClient.Game> candidates,boolean ambiguousExact){
        if(isStrongNonGameText(title,title))return new Decision(Action.QUARANTINE,"Titolo con segnali forti di categoria non gioco");
        if(ambiguousExact)return new Decision(Action.REVIEW,"Più giochi BGG condividono esattamente questo nome/alias");
        if(candidates!=null){
            int n=Math.min(3,candidates.size());
            for(int i=0;i<n;i++){
                BggSearchClient.Game g=candidates.get(i);if(g==null)continue;
                if(g.searchScore>=820 && plausibleGame(title,g))return new Decision(Action.REVIEW,"Candidato BGG forte ma ancora ambiguo");
            }
        }
        return new Decision(Action.QUARANTINE,"Nessuna evidenza sufficiente che l'articolo sia un gioco BGG");
    }

    public static boolean isStrongNonGameText(String title,String raw){
        String t=norm(title),r=norm(raw),all=(t+" "+r).trim();
        for(String x:STRONG_NON_GAME)if(containsPhrase(all,norm(x)))return true;
        // Vinted clothing/shoe listings expose size semantics in the same accessibility card.
        String low=(raw==null?"":raw).toLowerCase(Locale.ROOT);
        if(low.contains("taglia:")||low.contains("size:")||low.matches(".*\\b(?:it|eu|uk|us)\\s*\\d{2}\\b.*"))return true;
        // Bare CD is strong only as a token; avoids matching words that merely contain "cd".
        return containsToken(t,"cd");
    }

    public static boolean hasStrongBoardGameCue(String text){
        String n=norm(text);for(String x:BOARD_GAME_CUES)if(containsPhrase(n,norm(x)))return true;return false;
    }

    private static boolean plausibleGame(String query,BggSearchClient.Game game){
        if(game==null)return false;if(plausibleOverlap(query,game.name))return true;
        for(String a:game.aliases)if(plausibleOverlap(query,a))return true;return false;
    }

    /** Deliberately lexical: a high fuzzy score alone is not evidence enough for human review. */
    public static boolean plausibleOverlap(String query,String candidate){
        String q=norm(query),c=norm(candidate);if(q.isEmpty()||c.isEmpty())return false;
        if(containsPhrase(q,c)||containsPhrase(c,q))return significant(c).size()>0;
        List<String> qtok=significant(q),ctok=significant(c);if(qtok.isEmpty()||ctok.isEmpty())return false;
        int exact=0;boolean longExact=false;boolean fuzzyLong=false;
        for(String a:qtok){
            for(String b:ctok){
                if(a.equals(b)){exact++;if(a.length()>=6)longExact=true;break;}
                if(a.length()>=6&&b.length()>=6&&a.charAt(0)==b.charAt(0)&&Math.abs(a.length()-b.length())<=1&&editDistanceAtMost(a,b,1)){fuzzyLong=true;}
            }
        }
        if(exact>=2)return true;
        if(exact>=1&&longExact&&(qtok.size()<=2||ctok.size()<=2))return true;
        return fuzzyLong&&(qtok.size()==1||ctok.size()==1);
    }

    private static List<String> significant(String normalized){
        List<String> out=new ArrayList<>();for(String w:normalized.split(" +"))if(w.length()>=3&&!FILLER.contains(w))out.add(w);return out;
    }
    private static boolean editDistanceAtMost(String a,String b,int max){
        if(Math.abs(a.length()-b.length())>max)return false;int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){cur[0]=i;int row=cur[0];for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;cur[j]=Math.min(Math.min(prev[j]+1,cur[j-1]+1),prev[j-1]+cost);row=Math.min(row,cur[j]);}if(row>max)return false;int[] tmp=prev;prev=cur;cur=tmp;}return prev[b.length()]<=max;
    }
    private static boolean containsToken(String text,String token){return (" "+text+" ").contains(" "+token+" ");}
    private static boolean containsPhrase(String text,String phrase){return (" "+text+" ").contains(" "+phrase+" ");}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String norm(String s){String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
}
