package it.vintedaffari.app;
import android.text.TextUtils;import android.content.*;import org.w3c.dom.*;import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import javax.xml.parsers.*;
public final class BggSearchClient {
 public static final class Edition{public String id,name,imageUrl,publisher,languages;public Integer year;public String label(){return (name==null?"Edizione":name)+(year==null?"":" · "+year)+(TextUtils.isEmpty(publisher)?"":" · "+publisher)+(TextUtils.isEmpty(languages)?"":" · "+languages);}}
 public static final class Game {public String id,name,imageUrl,type,categories,editionName,editionId;public Integer year,playtime,minPlayers,maxPlayers,rank,voters,qualityScore,marketUsedMedianCents,marketUsedMinCents,marketUsedCount;public Double rating,geekRating,weight;public boolean local;public String notice;public int searchScore;public final List<String> aliases=new ArrayList<>();public final List<Edition> editions=new ArrayList<>();@Override public String toString(){return name+(year==null?"":" ("+year+")")+("boardgameexpansion".equals(type)?" · Espansione":"");}}
 public interface Callback{void ok(List<Game> games);void error(String e);}
 private static final long QUEUE_SEARCH_BUDGET_MS=2_500L;
 private final ExecutorService exec=Executors.newSingleThreadExecutor();private final ExecutorService directExec=Executors.newSingleThreadExecutor();private final Context context;private volatile Map<String,Integer> localPriceRefs,localNewPriceRefs;private volatile Map<String,Game> localByIdIndex;private volatile List<Game> localCatalogIndex;private volatile long[] localExactHashIndex,localTokenHashIndex;private volatile long localCatalogLoadMs,localTokenIndexLoadMs;private volatile int localExactScans,localExactCacheHits,localExactTimeouts,localFuzzyTimeouts,localFuzzyCandidatesScanned;private volatile boolean queueSearchTimedOut=false;
 private final Map<String,List<Game>> fastCache=Collections.synchronizedMap(new LinkedHashMap<String,List<Game>>(32,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<Game>> eldest){return size()>24;}});
 private final Map<String,List<Game>> queueFuzzyCache=Collections.synchronizedMap(new LinkedHashMap<String,List<Game>>(40,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<Game>> eldest){return size()>32;}});
 private final Map<String,List<Game>> queueExactCache=Collections.synchronizedMap(new LinkedHashMap<String,List<Game>>(72,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<Game>> eldest){return size()>64;}});
 public BggSearchClient(Context c){context=c.getApplicationContext();}
 public void warmup(){exec.execute(()->{try{priceRefs();}catch(Exception ignored){}});}
 public void shutdown(){fastCache.clear();queueFuzzyCache.clear();queueExactCache.clear();localByIdIndex=null;localCatalogIndex=null;localExactHashIndex=null;localTokenHashIndex=null;exec.shutdownNow();directExec.shutdownNow();}
 public void resetQueueSearchBudget(){queueSearchTimedOut=false;}
 public boolean queueSearchTimedOut(){return queueSearchTimedOut;}
 public boolean configured(){return !TextUtils.isEmpty(BuildConfig.BGG_TOKEN)&&!"PASTE_YOUR_BGG_TOKEN_HERE".equals(BuildConfig.BGG_TOKEN);}

 // These compact indexes are fixed application resources, not dynamic assets. Keeping them in
 // res/raw gives us a compile-time R reference: a missing file can no longer silently ship and
 // become a runtime AssetManager path failure. GZIPInputStream consumes the raw gzip bytes.
 private InputStream openSearchIndex()throws IOException{
  try{return context.getResources().openRawResource(R.raw.bgg_search_index_gz);}
  catch(android.content.res.Resources.NotFoundException e){throw new FileNotFoundException("Indice locale BGG non incluso nell'app");}
 }
 private InputStream openPriceIndex()throws IOException{
  try{return context.getResources().openRawResource(R.raw.bgg_used_price_index_gz);}
  catch(android.content.res.Resources.NotFoundException e){throw new FileNotFoundException("Indice prezzi usato BGG non incluso nell'app");}
 }
 private InputStream openNewPriceIndex()throws IOException{
  try{return context.getResources().openRawResource(R.raw.bgg_price_index_gz);}
  catch(android.content.res.Resources.NotFoundException e){throw new FileNotFoundException("Indice prezzi nuovi BGG non incluso nell'app");}
 }

 // Keep the 31k-game local search catalog on disk. The previous implementation parsed the
 // 18 MB catalog-data.js into a retained JSONArray in every Activity process, duplicating the
 // hidden JS engine's catalog and consuming a large fraction of the Android heap.
 private List<Game> localSearch(String query)throws Exception{
  List<Game> out=new ArrayList<>();Map<String,Integer> scores=new HashMap<>();BggManualSearchRanking.Query rankedQuery=BggManualSearchRanking.prepare(query);
  try(BufferedReader reader=new BufferedReader(new InputStreamReader(new java.util.zip.GZIPInputStream(openSearchIndex()),java.nio.charset.StandardCharsets.UTF_8),64*1024)){
   String line;while((line=reader.readLine())!=null){String[] c=line.split("\t",-1);if(c.length<8)continue;int score=BggManualSearchRanking.score(rankedQuery,c[1],false);if(!c[7].isEmpty())for(String alias:c[7].split("\u001f"))score=Math.max(score,BggManualSearchRanking.score(rankedQuery,alias,true));if(score<320)continue;Game g=fromIndex(c);if(TextUtils.isEmpty(g.id))continue;score+=BggManualSearchRanking.popularityBoost(g.rank,g.voters);g.searchScore=score;out.add(g);scores.put(g.id,score);}
  }
  out.sort((a,b)->{int c=Integer.compare(scores.get(b.id),scores.get(a.id));if(c!=0)return c;int ar=a.rank==null||a.rank<=0?Integer.MAX_VALUE:a.rank,br=b.rank==null||b.rank<=0?Integer.MAX_VALUE:b.rank;return Integer.compare(ar,br);});return new ArrayList<>(out.subList(0,Math.min(16,out.size())));
 }
 public List<Game> localCandidates(String query){try{return localSearch(query);}catch(Exception e){return Collections.emptyList();}}
 /** Queue-process fuzzy search. Unlike localCandidates(), which deliberately streams from disk so
  * short-lived UI/manual clients do not retain the full catalog, the durable queue client already
  * owns the exact/id indexes. Reuse those parsed Game objects and keep only the top 16 scores. */
 public List<Game> localCandidatesIndexed(String query){
  String key=normalize(query);if(key.isEmpty())return Collections.emptyList();
  List<Game> cached=queueFuzzyCache.get(key);if(cached!=null)return copySearchResults(cached);
  try{
   ensureCatalogIndex();ensureTokenIndex();List<Game> catalog=localCatalogIndex;long[] tokens=localTokenHashIndex;
   if(catalog==null||catalog.isEmpty()||tokens==null)return Collections.emptyList();
   // Candidate generation is sublinear: use the rarest query tokens to build a bounded pool,
   // then run the existing conservative ranking only on that pool.
   ArrayList<int[]> ranges=new ArrayList<>();HashSet<Integer> queryHashes=new HashSet<>();
   for(String token:key.split(" ")){if(token.length()<3)continue;int h=token.hashCode();if(!queryHashes.add(h))continue;int[] r=hashRange(tokens,h);if(r[1]>r[0])ranges.add(r);}
   ranges.sort(Comparator.comparingInt(r->r[1]-r[0]));
   LinkedHashSet<Integer> candidateIds=new LinkedHashSet<>();int usedRanges=0;
   for(int[] r:ranges){
    for(int p=r[0];p<r[1]&&candidateIds.size()<4000;p++){int gi=(int)(tokens[p]&0xffffffffL);if(gi>=0&&gi<catalog.size())candidateIds.add(gi);}
    if(++usedRanges>=3||candidateIds.size()>=4000)break;
   }
   if(candidateIds.isEmpty())return Collections.emptyList();
   long started=android.os.SystemClock.elapsedRealtime();
   BggManualSearchRanking.Query rankedQuery=BggManualSearchRanking.prepare(query);
   PriorityQueue<RankedLocal> top=new PriorityQueue<>(16,(a,b)->{int cc=Integer.compare(a.score,b.score);if(cc!=0)return cc;return Integer.compare(rankValue(b.game),rankValue(a.game));});
   int scanned=0;
   for(Integer gi:candidateIds){
    if(gi==null)continue;if((++scanned&63)==0&&android.os.SystemClock.elapsedRealtime()-started>QUEUE_SEARCH_BUDGET_MS){queueSearchTimedOut=true;localFuzzyTimeouts++;localFuzzyCandidatesScanned+=scanned;return Collections.emptyList();}
    Game g=catalog.get(gi);int score=BggManualSearchRanking.score(rankedQuery,g.name,false);
    for(String alias:g.aliases)score=Math.max(score,BggManualSearchRanking.score(rankedQuery,alias,true));
    if(score<320)continue;score+=BggManualSearchRanking.popularityBoost(g.rank,g.voters);
    RankedLocal rr=new RankedLocal(g,score);
    if(top.size()<16)top.add(rr);else if(compareRankedBestFirst(rr,top.peek())<0){top.poll();top.add(rr);}
   }
   localFuzzyCandidatesScanned+=scanned;
   ArrayList<RankedLocal> ranked=new ArrayList<>(top);ranked.sort(BggSearchClient::compareRankedBestFirst);
   ArrayList<Game> out=new ArrayList<>(ranked.size());for(RankedLocal rr:ranked){Game g=copySearchGame(rr.game);g.searchScore=rr.score;out.add(g);}
   queueFuzzyCache.put(key,copySearchResults(out));return out;
  }catch(Exception e){return Collections.emptyList();}
 }
 /** Exact/alias lookup for the long-lived background matcher. Exact results are cached
  * lazily instead of retaining a global alias->games HashMap. This keeps cold-start CPU/heap bounded
  * while preserving one catalog parse and O(1) id lookup. */
 public List<Game> localExactCandidates(String query){
  String key=normalize(query);if(key.isEmpty())return Collections.emptyList();
  List<Game> cached=queueExactCache.get(key);if(cached!=null){localExactCacheHits++;return copySearchResults(cached);}
  try{
   // The old implementation normalized every title/alias across all 31k games on every lookup.
   // On Pixel that exceeded the 2.5s safety budget on every exact scan and manufactured BGG review.
   // Build one compact primitive hash index with the catalog instead. Hash collisions are always
   // verified against the original strings before a candidate is returned.
   ensureCatalogIndex();List<Game> catalog=localCatalogIndex;long[] index=localExactHashIndex;
   if(catalog==null||index==null)return Collections.emptyList();
   ArrayList<Game> hit=new ArrayList<>();HashSet<Integer> seen=new HashSet<>();localExactScans++;
   int hash=key.hashCode();long base=((long)hash)<<32;int pos=Arrays.binarySearch(index,base);if(pos<0)pos=-pos-1;else while(pos>0&&(int)(index[pos-1]>>32)==hash)pos--;
   while(pos<index.length&&(int)(index[pos]>>32)==hash){
    int gameIndex=(int)(index[pos]&0xffffffffL);pos++;
    if(gameIndex<0||gameIndex>=catalog.size()||!seen.add(gameIndex))continue;
    Game g=catalog.get(gameIndex);boolean match=key.equals(normalize(g.name));
    if(!match)for(String alias:g.aliases)if(key.equals(normalize(alias))){match=true;break;}
    if(match)hit.add(copySearchGame(g));
   }
   queueExactCache.put(key,copySearchResults(hit));return hit;
  }catch(Exception e){return Collections.emptyList();}
 }
 /** Builds only the shared queue-process catalog and BGG-id index in one gzip pass. Exact alias
  * lookup is lazy/cached, avoiding the large all-alias HashMap that dominated cold starts. */
 private void ensureCatalogIndex()throws Exception{
  if(localByIdIndex!=null&&localCatalogIndex!=null&&localExactHashIndex!=null)return;
  synchronized(this){
   if(localByIdIndex!=null&&localCatalogIndex!=null&&localExactHashIndex!=null)return;
   long started=android.os.SystemClock.elapsedRealtime();Map<String,Game> byId=new HashMap<>(40000);ArrayList<Game> catalog=new ArrayList<>(32000);
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(new java.util.zip.GZIPInputStream(openSearchIndex()),java.nio.charset.StandardCharsets.UTF_8),64*1024)){
    String line;while((line=reader.readLine())!=null){String[] c=line.split("\t",-1);if(c.length<8)continue;Game g=fromIndex(c);if(TextUtils.isEmpty(g.id)||TextUtils.isEmpty(g.name))continue;byId.put(g.id,g);catalog.add(g);}
   }
   int exactEntries=0;for(Game g:catalog)exactEntries+=1+g.aliases.size();
   long[] exact=new long[exactEntries];int at=0;
   for(int i=0;i<catalog.size();i++){
    Game g=catalog.get(i);String primary=normalize(g.name);if(!primary.isEmpty())exact[at++]=packExactHash(primary,i);
    for(String alias:g.aliases){String n=normalize(alias);if(!n.isEmpty())exact[at++]=packExactHash(n,i);}
   }
   if(at<exact.length)exact=Arrays.copyOf(exact,at);Arrays.sort(exact);
   localCatalogIndex=Collections.unmodifiableList(catalog);localByIdIndex=Collections.unmodifiableMap(byId);localExactHashIndex=exact;localCatalogLoadMs=android.os.SystemClock.elapsedRealtime()-started;
  }
 }
 private static long packExactHash(String normalized,int gameIndex){return (((long)normalized.hashCode())<<32)|(gameIndex&0xffffffffL);}
 private void ensureTokenIndex(){
  if(localTokenHashIndex!=null)return;
  synchronized(this){
   if(localTokenHashIndex!=null)return;
   long started=android.os.SystemClock.elapsedRealtime();List<Game> catalog=localCatalogIndex;if(catalog==null){localTokenHashIndex=new long[0];return;}
   LongBuilder out=new LongBuilder(Math.max(65536,catalog.size()*4));
   for(int i=0;i<catalog.size();i++){
    Game g=catalog.get(i);HashSet<Integer> seenHashes=new HashSet<>();
    addTokenHashes(out,seenHashes,normalize(g.name),i);
    for(String alias:g.aliases)addTokenHashes(out,seenHashes,normalize(alias),i);
   }
   long[] built=out.toArray();Arrays.sort(built);localTokenHashIndex=built;localTokenIndexLoadMs=android.os.SystemClock.elapsedRealtime()-started;
  }
 }
 private static void addTokenHashes(LongBuilder out,HashSet<Integer> seen,String normalized,int gameIndex){
  if(TextUtils.isEmpty(normalized))return;for(String token:normalized.split(" ")){if(token.length()<3)continue;int h=token.hashCode();if(seen.add(h))out.add((((long)h)<<32)|(gameIndex&0xffffffffL));}
 }
 private static int[] hashRange(long[] index,int hash){
  long base=((long)hash)<<32;int start=Arrays.binarySearch(index,base);if(start<0)start=-start-1;else while(start>0&&(int)(index[start-1]>>32)==hash)start--;
  int end=start;while(end<index.length&&(int)(index[end]>>32)==hash)end++;return new int[]{start,end};
 }
 private static final class LongBuilder{
  private long[] data;private int size;LongBuilder(int capacity){data=new long[Math.max(16,capacity)];}
  void add(long v){if(size>=data.length)data=Arrays.copyOf(data,data.length+(data.length>>1)+1);data[size++]=v;}
  long[] toArray(){return Arrays.copyOf(data,size);}
 }
 public String localIndexSummary(){List<Game> c=localCatalogIndex;long[] x=localExactHashIndex,t=localTokenHashIndex;return "build=bgg-local-index-v5;loaded="+(c!=null)+";games="+(c==null?0:c.size())+";exactEntries="+(x==null?0:x.length)+";tokenEntries="+(t==null?0:t.length)+";loadMs="+localCatalogLoadMs+";tokenLoadMs="+localTokenIndexLoadMs+";exactScans="+localExactScans+";exactCacheHits="+localExactCacheHits+";exactTimeouts="+localExactTimeouts+";fuzzyTimeouts="+localFuzzyTimeouts+";fuzzyCandidatesScanned="+localFuzzyCandidatesScanned+";searchBudgetMs="+QUEUE_SEARCH_BUDGET_MS+";exactCacheSize="+queueExactCache.size()+";fuzzyCacheSize="+queueFuzzyCache.size();}
 private static final class RankedLocal{final Game game;final int score;RankedLocal(Game game,int score){this.game=game;this.score=score;}}
 private static int rankValue(Game g){return g==null||g.rank==null||g.rank<=0?Integer.MAX_VALUE:g.rank;}
 private static int compareRankedBestFirst(RankedLocal a,RankedLocal b){int c=Integer.compare(b.score,a.score);if(c!=0)return c;return Integer.compare(rankValue(a.game),rankValue(b.game));}
 private static Game copySearchGame(Game x){Game g=new Game();if(x==null)return g;g.id=x.id;g.name=x.name;g.imageUrl=x.imageUrl;g.type=x.type;g.categories=x.categories;g.editionName=x.editionName;g.editionId=x.editionId;g.year=x.year;g.playtime=x.playtime;g.minPlayers=x.minPlayers;g.maxPlayers=x.maxPlayers;g.rank=x.rank;g.voters=x.voters;g.qualityScore=x.qualityScore;g.marketUsedMedianCents=x.marketUsedMedianCents;g.marketUsedMinCents=x.marketUsedMinCents;g.marketUsedCount=x.marketUsedCount;g.rating=x.rating;g.geekRating=x.geekRating;g.weight=x.weight;g.local=x.local;g.notice=x.notice;g.searchScore=x.searchScore;g.aliases.addAll(x.aliases);g.editions.addAll(x.editions);return g;}
 private static List<Game> copySearchResults(List<Game> src){ArrayList<Game> out=new ArrayList<>(src==null?0:src.size());if(src!=null)for(Game g:src)out.add(copySearchGame(g));return out;}
 private static String normalize(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
 private static Game fromIndex(String[] c){Game g=new Game();g.local=true;g.id=c[0];g.name=c[1];g.year=parseInt(c[2]);g.rank=parseInt(c[3]);g.voters=parseInt(c[4]);g.rating=parseDouble(c[5]);g.geekRating=parseDouble(c[6]);g.qualityScore=QualityComposite.score(g.rank,g.geekRating,g.rating,g.voters);g.type="boardgame";if(c.length>7&&!c[7].isEmpty())for(String alias:c[7].split("\u001f"))if(!TextUtils.isEmpty(alias)&&!g.aliases.contains(alias))g.aliases.add(alias);return g;}
 private static Integer parseInt(String s){try{return TextUtils.isEmpty(s)?null:Integer.valueOf(s);}catch(Exception e){return null;}}
 private static Double parseDouble(String s){try{return TextUtils.isEmpty(s)?null:Double.valueOf(s);}catch(Exception e){return null;}}

 public Game localById(String bggId){if(TextUtils.isEmpty(bggId))return null;try{Map<String,Game> byId=localByIdIndex;if(byId==null){ensureCatalogIndex();byId=localByIdIndex;}return byId==null?null:byId.get(bggId);}catch(Exception ignored){return null;}}
 public Integer localMarketReferenceCents(String bggId){if(TextUtils.isEmpty(bggId))return null;try{return priceRefs().get(bggId);}catch(Exception ignored){return null;}}
 public Integer localNewMarketCents(String bggId){if(TextUtils.isEmpty(bggId))return null;try{return newPriceRefs().get(bggId);}catch(Exception ignored){return null;}}
 private Map<String,Integer> priceRefs()throws Exception{Map<String,Integer> ready=localPriceRefs;if(ready!=null)return ready;Map<String,Integer> map=new HashMap<>();try(BufferedReader reader=new BufferedReader(new InputStreamReader(new java.util.zip.GZIPInputStream(openPriceIndex()),java.nio.charset.StandardCharsets.UTF_8),32*1024)){String line;while((line=reader.readLine())!=null){String[] c=line.split("\t",-1);if(c.length<2)continue;try{int low=Integer.parseInt(c[1]);if(low>0)map.put(c[0],low);}catch(Exception ignored){}}}localPriceRefs=Collections.unmodifiableMap(map);return localPriceRefs;}
 private Map<String,Integer> newPriceRefs()throws Exception{Map<String,Integer> ready=localNewPriceRefs;if(ready!=null)return ready;Map<String,Integer> map=new HashMap<>();try(BufferedReader reader=new BufferedReader(new InputStreamReader(new java.util.zip.GZIPInputStream(openNewPriceIndex()),java.nio.charset.StandardCharsets.UTF_8),32*1024)){String line;while((line=reader.readLine())!=null){String[] c=line.split("\t",-1);if(c.length<2)continue;try{int cents=Integer.parseInt(c[1]);if(cents>0)map.put(c[0],cents);}catch(Exception ignored){}}}localNewPriceRefs=Collections.unmodifiableMap(map);return localNewPriceRefs;}
 public void detailsMany(List<String> ids,Callback cb){exec.execute(()->{try{if(ids==null||ids.isEmpty()){cb.ok(Collections.emptyList());return;}if(!configured())throw new IOException("Aggiornamento BGG non configurato; restano i dati locali.");StringJoiner join=new StringJoiner(",");int count=0;for(String id:ids){if(TextUtils.isEmpty(id))continue;join.add(id.trim());if(++count>=20)break;}if(count==0){cb.ok(Collections.emptyList());return;}List<Game> games=parse(get("thing?id="+join.toString()+"&stats=1&marketplace=1"),true);cb.ok(games);}catch(Exception e){cb.error(e.getMessage());}});}
 private static int manualSearchScore(BggManualSearchRanking.Query q,Game g){if(g==null)return 0;int score=BggManualSearchRanking.score(q,g.name,false);for(String alias:g.aliases)score=Math.max(score,BggManualSearchRanking.score(q,alias,true));return score+BggManualSearchRanking.popularityBoost(g.rank,g.voters);}
 public void searchFast(String query,Callback cb){exec.execute(()->{try{String key=normalize(query);List<Game> cached=fastCache.get(key);if(cached!=null){cb.ok(new ArrayList<>(cached));return;}List<Game> local=localSearch(query);int localBest=local.isEmpty()?0:local.get(0).searchScore;if(!configured()){if(local.isEmpty())cb.error("Nessun risultato locale. La ricerca online richiede BGG_TOKEN.");else{fastCache.put(key,new ArrayList<>(local));cb.ok(local);}return;}if(localBest>=1650){fastCache.put(key,new ArrayList<>(local));cb.ok(local);return;}try{List<Game> remote=parse(get("search?type=boardgame,boardgameexpansion&query="+URLEncoder.encode(query,"UTF-8")),false);BggManualSearchRanking.Query rq=BggManualSearchRanking.prepare(query);for(Game g:remote)g.searchScore=manualSearchScore(rq,g);Map<String,Game> merged=new LinkedHashMap<>();for(Game g:remote)merged.put(g.id,g);for(Game g:local){Game existing=merged.get(g.id);if(existing==null)merged.put(g.id,g);else{existing.searchScore=Math.max(existing.searchScore,g.searchScore);for(String a:g.aliases)if(!existing.aliases.contains(a))existing.aliases.add(a);}}List<Game> games=new ArrayList<>(merged.values());for(Game g:games)g.searchScore=Math.max(g.searchScore,manualSearchScore(rq,g));games.sort((a,b)->Integer.compare(b.searchScore,a.searchScore));if(games.size()>16)games=new ArrayList<>(games.subList(0,16));if(!games.isEmpty()){StringJoiner ids=new StringJoiner(",");for(Game g:games)ids.add(g.id);try{List<Game> detailed=parse(get("thing?id="+ids+"&stats=1"),true);Map<String,Integer> order=new HashMap<>();Map<String,Integer> scores=new HashMap<>();for(int i=0;i<games.size();i++){order.put(games.get(i).id,i);scores.put(games.get(i).id,games.get(i).searchScore);}for(Game g:detailed)g.searchScore=Math.max(scores.getOrDefault(g.id,0),manualSearchScore(rq,g));detailed.sort(Comparator.comparingInt(g->order.getOrDefault(g.id,Integer.MAX_VALUE)));games=detailed;}catch(Exception ignored){}}fastCache.put(key,new ArrayList<>(games));cb.ok(games);}catch(Exception remote){if(!local.isEmpty()){for(Game g:local)g.notice="Ricerca online non riuscita: "+remote.getMessage();fastCache.put(key,new ArrayList<>(local));cb.ok(local);}else cb.error(remote.getMessage());}}catch(Exception e){cb.error(e.getMessage());}});}
 public void search(String query,Callback cb){searchFast(query,cb);}
 public void details(String id,boolean versions,Callback cb){exec.execute(()->{Game local=localById(id);try{if(!configured()){if(local!=null){local.notice="Scheda caricata dal catalogo locale";cb.ok(Collections.singletonList(local));return;}throw new IOException("Gioco non presente nel catalogo locale e BGG online non configurato.");}List<Game> games=parse(get("thing?id="+URLEncoder.encode(id,"UTF-8")+"&stats=1&marketplace=1"+(versions?"&versions=1":"")),true);if(games.isEmpty())throw new IOException("BGG non ha restituito il gioco richiesto");cb.ok(games);}catch(Exception e){if(local!=null){local.notice="Dati locali: aggiornamento online non riuscito";cb.ok(Collections.singletonList(local));}else cb.error(e.getMessage());}});}
 /** Direct ID/link resolution uses its own worker so a human correction is never blocked behind
  * a slower fuzzy-name search already in progress. BggRateLimiter still serializes real API starts. */
 public void detailsDirect(String id,boolean versions,Callback cb){directExec.execute(()->{Game local=localById(id);try{if(!configured()){if(local!=null){local.notice="Scheda caricata dal catalogo locale";cb.ok(Collections.singletonList(local));return;}throw new IOException("Gioco non presente nel catalogo locale e BGG online non configurato.");}List<Game> games=parse(get("thing?id="+URLEncoder.encode(id,"UTF-8")+"&stats=1&marketplace=1"+(versions?"&versions=1":"")),true);if(games.isEmpty())throw new IOException("BGG non ha restituito il gioco richiesto");cb.ok(games);}catch(Exception e){if(local!=null){local.notice="Dati locali: aggiornamento online non riuscito";cb.ok(Collections.singletonList(local));}else cb.error(e.getMessage());}});}
 private Document get(String path)throws Exception {BggRateLimiter.acquire(context);HttpURLConnection c=(HttpURLConnection)new URL("https://boardgamegeek.com/xmlapi2/"+path).openConnection();try{c.setConnectTimeout(9000);c.setReadTimeout(16000);if(BuildConfig.BGG_TOKEN!=null&&!BuildConfig.BGG_TOKEN.isEmpty()&&!"PASTE_YOUR_BGG_TOKEN_HERE".equals(BuildConfig.BGG_TOKEN))c.setRequestProperty("Authorization","Bearer "+BuildConfig.BGG_TOKEN);c.setRequestProperty("User-Agent","LudoScout/5.11 Android");int status=c.getResponseCode();if(status!=200)throw new IOException(status==202?"BGG sta preparando i dati: riprova tra poco.":"BGG HTTP "+status);try(InputStream in=c.getInputStream()){return SafeXml.parse(in);}}finally{c.disconnect();}}
 private List<Game> parse(Document d,boolean detail){List<Game> out=new ArrayList<>();Element root=d.getDocumentElement();for(Element item:children(root,"item")){Game g=new Game();g.id=item.getAttribute("id");g.type=item.getAttribute("type");for(Element n:children(item,"name")){String value=n.getAttribute("value");String nt=n.getAttribute("type");if("primary".equals(nt)||g.name==null)g.name=value;if("alternate".equals(nt)&&!TextUtils.isEmpty(value)&&!g.aliases.contains(value))g.aliases.add(value);}g.year=intValue(item,"yearpublished");if(detail){g.imageUrl=text(item,"image");g.playtime=intValue(item,"playingtime");g.minPlayers=intValue(item,"minplayers");g.maxPlayers=intValue(item,"maxplayers");g.rating=doubleValue(item,"average");g.geekRating=doubleValue(item,"bayesaverage");g.voters=intValue(item,"usersrated");g.weight=doubleValue(item,"averageweight");NodeList ranks=item.getElementsByTagName("rank");for(int k=0;k<ranks.getLength();k++){Element rank=(Element)ranks.item(k);if("boardgame".equals(rank.getAttribute("name")))try{g.rank=Integer.valueOf(rank.getAttribute("value"));}catch(Exception ignored){}}List<String> cats=new ArrayList<>();for(Element link:children(item,"link"))if("boardgamecategory".equals(link.getAttribute("type")))cats.add(link.getAttribute("value"));g.categories=TextUtils.join(" · ",cats);g.qualityScore=QualityComposite.score(g.rank,g.geekRating,g.rating,g.voters);applyMarketplace(g,item);for(Element vs:children(item,"versions"))for(Element v:children(vs,"item")){Edition e=new Edition();e.id=v.getAttribute("id");for(Element n:children(v,"name"))if(e.name==null||"primary".equals(n.getAttribute("type")))e.name=n.getAttribute("value");e.year=intValue(v,"yearpublished");e.imageUrl=text(v,"image");List<String> publishers=new ArrayList<>(),languages=new ArrayList<>();for(Element link:children(v,"link")){if("boardgamepublisher".equals(link.getAttribute("type")))publishers.add(link.getAttribute("value"));if("language".equals(link.getAttribute("type")))languages.add(link.getAttribute("value"));}e.publisher=TextUtils.join(", ",publishers);e.languages=TextUtils.join(", ",languages);g.editions.add(e);}}if(!TextUtils.isEmpty(g.id)&&!TextUtils.isEmpty(g.name))out.add(g);}return out;}
 private static void applyMarketplace(Game g,Element item){List<Integer> used=new ArrayList<>();NodeList nodes=item.getElementsByTagName("listing");for(int i=0;i<nodes.getLength();i++){if(!(nodes.item(i) instanceof Element))continue;Element listing=(Element)nodes.item(i);List<Element> prices=children(listing,"price");if(prices.isEmpty())continue;Element price=prices.get(0);String currency=price.getAttribute("currency");if(!TextUtils.isEmpty(currency)&&!"EUR".equalsIgnoreCase(currency))continue;double eur;try{eur=Double.parseDouble(price.getAttribute("value"));}catch(Exception ignored){continue;}if(eur<=0||eur>10000)continue;List<Element> conditions=children(listing,"condition");String condition=conditions.isEmpty()?"":conditions.get(0).getAttribute("value");if("new".equalsIgnoreCase(condition))continue;used.add((int)Math.round(eur*100.0));}if(used.isEmpty())return;Collections.sort(used);g.marketUsedCount=used.size();g.marketUsedMinCents=used.get(0);int n=used.size();g.marketUsedMedianCents=n%2==1?used.get(n/2):(used.get(n/2-1)+used.get(n/2))/2;}
 private static List<Element> children(Element e,String tag){List<Element> out=new ArrayList<>();NodeList nodes=e.getChildNodes();for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element&&tag.equals(nodes.item(i).getNodeName()))out.add((Element)nodes.item(i));return out;}
 private static String text(Element e,String tag){List<Element> n=children(e,tag);return n.isEmpty()?"":n.get(0).getTextContent();}
 private static Integer intValue(Element e,String tag){NodeList n=e.getElementsByTagName(tag);if(n.getLength()==0)return null;try{return Integer.valueOf(((Element)n.item(0)).getAttribute("value"));}catch(Exception x){return null;}}
 private static Double doubleValue(Element e,String tag){NodeList n=e.getElementsByTagName(tag);if(n.getLength()==0)return null;try{return Double.valueOf(((Element)n.item(0)).getAttribute("value"));}catch(Exception x){return null;}}
}
