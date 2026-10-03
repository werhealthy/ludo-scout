"""Integration contract: fixed action is outside scroll; detailed counts remain accessible."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
nav=ui.split('private void renderNav(){',1)[1].split('private View navItem(',1)[0]
assert '"Bundle","bundles"' not in nav, 'Bundle still occupies main navigation'
assert 'mainScrollStage.addView(footer' in ui, 'Search must live outside scrolling content'
assert 'lp.bottomMargin=footer.getHeight()' in ui, 'Scroll viewport must reserve measured footer height'
assert 'showLudoJourneyDetails(snapshot)' in ui, 'Detailed results must remain accessible'
print('PASS fixed-action containment, measured viewport reservation, catalog-only Bundle, progressive results')
