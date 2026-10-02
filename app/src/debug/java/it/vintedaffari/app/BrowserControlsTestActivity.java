package it.vintedaffari.app;
import android.app.Activity;
import android.os.Bundle;
/** Debug-only native surface: never creates a WebView or fetches marketplace pages. */
public class BrowserControlsTestActivity extends Activity {
 public VintedBrowserControls controls;public int navigationCount;public String navigated;
 public void onCreate(Bundle state){super.onCreate(state);controls=new VintedBrowserControls(this,new VintedBrowserControls.Listener(){public void onNavigate(String url){navigationCount++;navigated=url;}public void onCapture(){}public void onToggle(){}public void onOpenEngine(long id){}public void onClose(){}public void onMenu(){}});setContentView(controls.view());VintedBrowserControls.State model=new VintedBrowserControls.State();model.permittedURL="https://www.vinted.it/catalog/4881-board-games?brand_ids%5B%5D=12&price_to=30&page=2";model.page=2;model.maxCents=3000;model.capture=true;model.supported=true;model.captureId=1;controls.render(model);}
}
