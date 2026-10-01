"""Execute real LibraryDatabase Java methods, then apply emitted SQL to SQLite fixtures."""
from pathlib import Path
import sqlite3, subprocess, tempfile, re
root=Path(__file__).resolve().parents[1]
dbsrc=(root/"app/src/main/java/it/vintedaffari/app/LibraryDatabase.java").read_text()
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
def method(src,signature):
    start=src.index(signature);brace=src.index("{",start);depth=0
    for i in range(brace,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return src[start:i+1]
    raise ValueError(signature)
methods="\n".join(method(dbsrc,s).replace("@Override ","") for s in ["public void onCreate(","public void onUpgrade(","public synchronized void setPersonalRating(","public synchronized void markSold(String bggId,String reason)","public synchronized void restoreOwned("])
if "markSold(String bggId,String reason,Integer" in dbsrc:
    methods+="\n"+method(dbsrc,"public synchronized void markSold(String bggId,String reason,Integer")
if "setSalePrice(" in dbsrc:
    methods+="\n"+method(dbsrc,"public synchronized boolean setSalePrice(")
sale_call='n.markSold("a","Non lo giocavo",1234);' if "markSold(String bggId,String reason,Integer" in dbsrc else 'n.markSold("a","Non lo giocavo");'
update_call='n.setSalePrice("a",2468);' if "setSalePrice(" in dbsrc else 'n.markSold("a","Non lo giocavo");'
if "private static String libraryPersonalRatingLabel(" in ui:
    label=method(ui,"private static String libraryPersonalRatingLabel(")+"\n"+method(ui,"private static Integer librarySalePrice(")
else:
    expr=re.search(r'pc.addView\(text\((g.personalRating==null\?[^;]+?),17,g.personalRating==null\?',ui).group(1)
    label="private static String libraryPersonalRatingLabel(Integer value){LibraryGame g=new LibraryGame();g.personalRating=value;return "+expr+";}"
harness=r"""
import java.util.*;
class LibraryGame { Integer personalRating; }
class ContentValues extends LinkedHashMap<String,Object> {void putNull(String key){put(key,null);}}
class SQLiteDatabase {
 void execSQL(String sql){System.out.println(sql);}
 String quoted(Object v){return v==null?"NULL":v instanceof Number?v.toString():"'"+v.toString().replace("'","''")+"'";}
 int update(String table,ContentValues values,String where,String[] args){
  StringJoiner pairs=new StringJoiner(",");for(Map.Entry<String,Object> e:values.entrySet())pairs.add(e.getKey()+"="+quoted(e.getValue()));
  for(String arg:args)where=where.replaceFirst("\\?",java.util.regex.Matcher.quoteReplacement(quoted(arg)));
  execSQL("UPDATE "+table+" SET "+pairs+" WHERE "+where);return 1;
 }
}
public class LibraryDataRegression {
 SQLiteDatabase db=new SQLiteDatabase();SQLiteDatabase getWritableDatabase(){return db;}
 __METHODS__
 __LABEL__
 public static void main(String[] args){
  LibraryDataRegression n=new LibraryDataRegression();
  if(args[0].equals("schema")){int version=Integer.parseInt(args[1]);if(version==0)n.onCreate(n.db);else n.onUpgrade(n.db,version,8);}
  else if(args[0].equals("rating"))n.setPersonalRating("a",args[1].equals("null")?null:Integer.valueOf(args[1]));
  else if(args[0].equals("sold")){__SALE__}
  else if(args[0].equals("sale-price")){__UPDATE__}
  else if(args[0].equals("restore"))n.restoreOwned("a");
  else if(args[0].equals("parse")){try{System.out.println(librarySalePrice(args[1]));}catch(IllegalArgumentException error){System.out.println("INVALID");}}
  else if(args[0].equals("label"))System.out.println(libraryPersonalRatingLabel(args[1].equals("null")?null:Integer.valueOf(args[1])));
 }
}
""".replace("__METHODS__",methods).replace("__LABEL__",label).replace("__SALE__",sale_call).replace("__UPDATE__",update_call)
OLD_SCHEMA="CREATE TABLE library_games(id INTEGER PRIMARY KEY AUTOINCREMENT,bgg_id TEXT UNIQUE,name TEXT,image_url TEXT,source TEXT,paid_cents INTEGER,shipping_cents INTEGER,rating REAL,weight REAL,playtime INTEGER,acquired_at INTEGER,added_at INTEGER,edition_id TEXT,edition_label TEXT,fee_cents INTEGER,fee_estimated INTEGER NOT NULL DEFAULT 0,bundle_purchase INTEGER NOT NULL DEFAULT 0,bundle_label TEXT,bundle_size INTEGER,geek_rating REAL,bgg_rank INTEGER,voters INTEGER,quality_score INTEGER,min_players INTEGER,max_players INTEGER,categories TEXT,personal_rating INTEGER,bundle_group_id TEXT,bundle_total_cents INTEGER,collection_state TEXT NOT NULL DEFAULT 'owned',sold_reason TEXT,sold_at INTEGER)"
columns_by_version={
2:["acquired_at"],3:["edition_id","edition_label","fee_cents","fee_estimated"],
4:["bundle_purchase","bundle_label","bundle_size"],
5:["geek_rating","bgg_rank","voters","quality_score","min_players","max_players","categories","personal_rating"],
6:["bundle_group_id","bundle_total_cents"],7:["collection_state","sold_reason","sold_at"]}
def legacy(version):
    connection=sqlite3.connect(":memory:")
    definitions=OLD_SCHEMA[OLD_SCHEMA.index("(")+1:-1].split(",")
    excluded={name for since,names in columns_by_version.items() if since>version for name in names}
    definitions=[d for d in definitions if d.split()[0] not in excluded]
    connection.execute("CREATE TABLE library_games("+",".join(definitions)+")")
    connection.execute("INSERT INTO library_games(bgg_id,name,paid_cents) VALUES('a','Azul',3456),('b','Catan',7777)")
    if version>=5:connection.execute("UPDATE library_games SET personal_rating=7 WHERE bgg_id='a'")
    if version>=7:connection.execute("UPDATE library_games SET collection_state='sold',sold_at=123,sold_reason='Altro' WHERE bgg_id='a'")
    return connection
failures=[]
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp);(p/"LibraryDataRegression.java").write_text(harness)
    subprocess.run(["javac","-d",temp,str(p/"LibraryDataRegression.java")],check=True)
    def run(*args):return subprocess.run(["java","-cp",temp,"LibraryDataRegression",*map(str,args)],text=True,capture_output=True,check=True).stdout.strip().splitlines()
    def apply(connection,*args):
        for sql in run(*args):connection.execute(sql)
    def test(name,fn):
        try:fn();print("PASS "+name)
        except (AssertionError,sqlite3.Error) as error:failures.append(name);print("FAIL "+name+": "+str(error))
    def migration(version):
        c=legacy(version);apply(c,"schema",version)
        assert c.execute("SELECT paid_cents,sale_price_cents FROM library_games WHERE bgg_id='a'").fetchone()==(3456,None)
        assert c.execute("SELECT COUNT(*) FROM library_games").fetchone()==(2,)
        if version>=5:assert c.execute("SELECT personal_rating FROM library_games WHERE bgg_id='a'").fetchone()==(7,)
        if version>=7:assert c.execute("SELECT collection_state,sold_at,sold_reason FROM library_games WHERE bgg_id='a'").fetchone()==("sold",123,"Altro")
    for version in range(1,8):test("preserve version"+str(version)+" data with unknown sale price",lambda v=version:migration(v))
    def fresh():
        c=sqlite3.connect(":memory:");apply(c,"schema",0)
        c.execute("INSERT INTO library_games(bgg_id,name) VALUES('a','Azul')")
        assert c.execute("SELECT sale_price_cents FROM library_games").fetchone()==(None,)
    test("fresh schema leaves sale price unknown",fresh)
    # Test write SQL against an independent fixture, so missing column cannot mask rating/write errors.
    def current():
        c=legacy(7);c.execute("ALTER TABLE library_games ADD COLUMN sale_price_cents INTEGER");return c
    for value,want in [(0,0),(7,7),(10,10),("null",None)]:
        def rating(value=value,want=want):
            c=current();apply(c,"rating",value);assert c.execute("SELECT personal_rating FROM library_games WHERE bgg_id='a'").fetchone()==(want,)
        test("rating "+str(value)+" remains distinct and exact",rating)
    def sale():
        c=current();apply(c,"sold");assert c.execute("SELECT collection_state,sale_price_cents,paid_cents,personal_rating FROM library_games WHERE bgg_id='a'").fetchone()==("sold",1234,3456,7)
    test("sale stores actual proceeds without altering purchase or rating",sale)
    def historical():
        c=current();apply(c,"sale-price");assert c.execute("SELECT sale_price_cents,sold_at,sold_reason FROM library_games WHERE bgg_id='a'").fetchone()==(2468,123,"Altro")
    test("historical sale-price backfill preserves sale date and reason",historical)
    def restore():
        c=current();c.execute("UPDATE library_games SET sale_price_cents=2468 WHERE bgg_id='a'");apply(c,"restore")
        assert c.execute("SELECT collection_state,sold_at,sold_reason,sale_price_cents,personal_rating FROM library_games WHERE bgg_id='a'").fetchone()==("owned",None,None,None,7)
    test("restore clears sale fields and preserves taste",restore)
    for value,want in [(7,"3,5"),(10,"5,0"),(0,"0,0")]:
        test("display legacy "+str(value)+" as exact five-star value",lambda v=value,w=want:None if w in run("label",v)[0] and "/10" not in run("label",v)[0] else (_ for _ in ()).throw(AssertionError("incorrect five-star conversion")))
    def owned_guard():
        c=current();c.execute("UPDATE library_games SET collection_state='owned',sold_at=NULL,sold_reason=NULL WHERE bgg_id='a'");apply(c,"sale-price")
        assert c.execute("SELECT collection_state,sale_price_cents,sold_at FROM library_games WHERE bgg_id='a'").fetchone()==("owned",None,None)
    test("sale-price backfill cannot change owned game",owned_guard)
    for raw,want in [("","null"),("  ","null"),("0","0"),("0,00","0"),("12,34","1234"),("12.34","1234"),("7,5","750"),("21474836,47","2147483647"),("-1","INVALID"),("12,345","INVALID"),("NaN","INVALID"),("21474836,48","INVALID")]:
        test("sale input "+repr(raw),lambda v=raw,w=want:None if run("parse",v)==[w] else (_ for _ in ()).throw(AssertionError("incorrect cents/input validation")))
if failures:raise SystemExit("Library data regressions failed: "+", ".join(failures))
