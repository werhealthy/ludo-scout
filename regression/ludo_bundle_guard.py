from pathlib import Path
main=Path('app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
brain=Path('app/src/main/java/it/vintedaffari/app/LocalScoutBrain.java').read_text()
checks={
 'bundle quality floor':'if(score<6.0)return false' in main,
 'foreign dependent excluded':'isForeignLanguage(lc)&&lc.contains("DEP")' in main,
 'bundle quality weighted':'bundleQuality(gb)*12' in main,
 'ludo bundle section removed':'Bundle verificati' not in main,
 'visual advice images':'dealArtworkView(d,dp(320),dp(176))' in main,
 'library insights':'renderLibraryInsights(snap,library)' in main,
 'personal signal modest':'double personal=affinity(d,profile)' in brain,
 'BGG dominates':'q*.45+deal*.29+rating*3.5' in brain,
 'discovery label':'return"SCOPERTA"' in brain,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('FAIL: '+', '.join(failed))
print('PASS:',len(checks),'smart bundle/Ludo guards')
