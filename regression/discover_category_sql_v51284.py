#!/usr/bin/env python3
"""Execute production-generated cluster predicates on hand-checked SQLite rows."""
from pathlib import Path
import sqlite3
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as classes:
    subprocess.run(["javac","-encoding","UTF-8","-d",classes,
        str(ROOT/"app/src/main/java/it/vintedaffari/app/DiscoverCategories.java"),
        str(ROOT/"regression/DiscoverCategorySqlFixture.java")],check=True)
    output=subprocess.check_output(["java","-cp",classes,
        "it.vintedaffari.app.DiscoverCategorySqlFixture"],text=True)

db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE games(id INTEGER,canonical_name TEXT,categories TEXT,database_visible INTEGER,bgg_id TEXT,match_state TEXT)")
db.executemany("INSERT INTO games VALUES(?,?,?,?,?,?)",[
    (1,"Train title","Economic",1,"1","MATCHED"),
    (2,"Cards title","Trains · City Building",1,"2","MATCHED"),
    (3,"Fantasy title","Science Fiction · Space Exploration",1,"3","MATCHED"),
    (4,"Mystery title","Exploration · Adventure",1,"4","MATCHED"),
    (5,"Party title","Animals · Dice",1,"5","MATCHED"),
    (6,"Family title","Deduction · Real-time",1,"6","MATCHED"),
    (7,"Strategy title","Spies / Secret Agents · Horror",1,"7","MATCHED"),
    (8,"Game title","Card Game · Collectible Components",1,"8","MATCHED"),
    (9,"Game title","World War II · Napoleonic",1,"9","MATCHED"),
    (10,"Hidden family","Children's Game",0,"10","MATCHED"),
    (11,"Unverified train","Trains",1,"11","BGG_MATCH_REVIEW"),
    (12,"Missing categories","",1,"12","MATCHED"),
    (13,"Missing metadata",None,1,"13","MATCHED"),
    (14,"Missing identity","Economic",1,None,"MATCHED"),
])
expected={"Strategia":[1,2],"Famiglia":[5],"Party":[6],"Fantasy":[4],
          "Carte":[8],"Sci-Fi":[3],"Mistero":[7],"Guerra":[9]}
seen=set()
for line in output.splitlines():
    label,predicate,*bindings=line.split("\t")
    base=" FROM games g WHERE g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'"+predicate
    rows=[r[0] for r in db.execute("SELECT g.id"+base+" ORDER BY g.id",bindings)]
    count=db.execute("SELECT COUNT(*)"+base,bindings).fetchone()[0]
    assert rows==expected[label],(label,rows,expected[label])
    assert count==len(rows),(label,count,rows)
    page=[r[0] for r in db.execute("SELECT g.id"+base+" ORDER BY g.id LIMIT 1 OFFSET 1",bindings)]
    assert page==expected[label][1:2],(label,page)
    for term in ["title","Train","absent"]:
        filtered=base+" AND g.canonical_name LIKE ?"
        params=bindings+["%"+term+"%"]
        matches=[r[0] for r in db.execute("SELECT g.id"+filtered+" ORDER BY g.id",params)]
        assert matches==[i for i in rows if term.lower() in db.execute("SELECT canonical_name FROM games WHERE id=?",(i,)).fetchone()[0].lower()]
        assert db.execute("SELECT COUNT(*)"+filtered,params).fetchone()[0]==len(matches)
    seen.add(label)
    print("PASS",label,"category identity, eligibility, count and pagination")
assert seen==set(expected)
print("PASS 8/8 production SQLite category fixtures")
