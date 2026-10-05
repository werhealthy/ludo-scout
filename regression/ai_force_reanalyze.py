from pathlib import Path
p=Path('app/src/main/java/it/vintedaffari/app/AiBetaRealDialog.java').read_text(encoding='utf-8')
checks={
 'explicit reanalyze button':'Rianalizza con Qwen' in p,
 'forced path bypasses display cache':'!force&&cached(saved,chosen)!=null' in p,
 'forced path clears cached AI response':all(x in p for x in ['saved.remove("real_response")','saved.remove("real_display_key")','saved.remove("real_review_key")','saved.remove("real_pending_id")']),
 'forced request identity changes':'chosen.key+"|rerun|"+System.currentTimeMillis()' in p,
 'normal analyze remains cached':'analyze.setOnClickListener(v->analyze(false))' in p,
 'forced analyze wired':'reanalyze.setOnClickListener(v->analyze(true))' in p,
}
for name,ok in checks.items(): print(('PASS ' if ok else 'FAIL ')+name)
if not all(checks.values()): raise SystemExit(1)
