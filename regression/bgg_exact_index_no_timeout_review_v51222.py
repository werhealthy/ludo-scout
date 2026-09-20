#!/usr/bin/env python3
from pathlib import Path
import bisect

ROOT=Path(__file__).resolve().parents[1]
bgg=(ROOT/"app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Model the compact exact index: normalized-key hash in high 32 bits, game index in low 32.
def java_hash(s):
    h=0
    for ch in s:
        h=(31*h+ord(ch))&0xffffffff
    return h if h<0x80000000 else h-0x100000000

def pack(s,i):
    h=java_hash(s)
    x=((h & 0xffffffff)<<32)|(i&0xffffffff)
    return x-(1<<64) if x&(1<<63) else x

games=[("fyfe",["fyfe board game"]),("azul",["azul gioco"]),("watergate",[])]
idx=sorted(pack(name,i) for i,(name,aliases) in enumerate(games) for name in [name,*aliases])

def candidates(q):
    h=java_hash(q)
    base=((h & 0xffffffff)<<32)
    if base&(1<<63): base-=1<<64
    p=bisect.bisect_left(idx,base)
    out=[]
    while p<len(idx):
        raw=idx[p] & ((1<<64)-1)
        hh=(raw>>32)&0xffffffff
        signed=hh if hh<0x80000000 else hh-0x100000000
        if signed!=h: break
        gi=raw&0xffffffff
        name,aliases=games[gi]
        if q==name or q in aliases: out.append(gi)
        p+=1
    return out

assert candidates("fyfe")==[0]
assert candidates("azul gioco")==[1]
assert candidates("not a game")==[]

checks=[
 ("exact lookup uses compact primitive hash index",
  "localExactHashIndex" in bgg and "packExactHash" in bgg and "Arrays.binarySearch(index,base)" in bgg),
 ("exact lookup no longer full-scans 31k games",
  "for(Game g:catalog)" not in bgg[bgg.index("public List<Game> localExactCandidates"):bgg.index("/** Builds only the shared queue-process catalog")]),
 ("cold index build is outside fuzzy query budget",
  "ensureCatalogIndex();List<Game> catalog=localCatalogIndex" in bgg and "long started=android.os.SystemClock.elapsedRealtime();" in bgg),
 ("technical BGG timeout does not create review",
  "else if(searchTimedOut)" in runner and "market.autoQuarantineGame" in runner and
  'markBggMatchReview(g.id,TextUtils.isEmpty(reviewReason)?"Ricerca BGG locale troppo lenta"' not in runner),
 ("technical matcher exception does not create review",
  "Errore tecnico match locale" in runner and "bgg_match_technical_drop" in runner),
 ("5.12.21 timeout reviews are reopened once",
  "reopenTechnicalBggReviewsForExactIndex" in market and "bgg_exact_index_v4_cutover" in market and
  "Riaperto da indice BGG esatto 5.12.22" in market),
 ("review diagnostics distinguish technical and real review",
  "currentReviewBreakdown" in market and "reviewBreakdown={" in radar),
 ("classifier block diagnostics are timestamped by build",
  "lastClassifierBlockAt" in radar and "lastClassifierBlockBuild" in radar),
 ("version lineage preserved",
  "versionName '5.12." in build and "versionCode ciVersionCode ? (1000000 + ciVersionCode.toInteger())" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.22 regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} exact-index guards")
