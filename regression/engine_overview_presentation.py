"""Exercise production status/motion policy without Android or network."""
from pathlib import Path
import subprocess, tempfile

root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
main=(src/'MainActivity.java').read_text()
pipeline=main.split('private View enginePipelineCard(')[1].split('private void animateEngineCount(')[0]
overview=main.split('private void renderEngineOverview(')[1].split('private String engineScopeLabel(')[0]
assert 'engineIntakeCard(snapshot)' not in pipeline, 'global queue cannot inherit scroll scope'
assert 'engineIntakeCard(snapshot)' in overview and 'installEngineHistoryPull()' in overview
motion=main.split('private void animateEngineCount(')[1].split('private View engineCurrentRunHero(')[0]
assert 'ofInt' not in motion and 'current-old' not in motion, 'stock changes must not appear as throughput'

harness=r'''
package it.vintedaffari.app;
public class EngineOverviewPresentationRegression {
 static void eq(String want,String got){if(!want.equals(got))throw new AssertionError(got+" expected "+want);}
 static String status(boolean scope,boolean settled,int mask,int bgg,int vinted,boolean bp,boolean vp,long wait){return EngineOverviewPresentation.status(scope,settled,mask,bgg,vinted,bp,vp,wait,100);}
 public static void main(String[] args){
  eq("In attesa di nuovi annunci",status(false,false,0,0,0,false,false,0));
  eq("Scroll elaborato",status(true,true,0,0,0,false,false,0));
  eq("Elaborazione in corso",status(true,false,1,1,0,false,false,0));
  eq("Sto completando i dati BGG",status(true,false,2,1,0,false,false,0));
  eq("Sto verificando gli annunci Vinted",status(true,false,8,0,1,false,false,0));
  eq("Elaborazione in pausa",status(true,false,10,1,1,true,true,0));
  eq("Verifiche Vinted in pausa",status(true,false,8,0,1,false,true,0));
  eq("Dati BGG in pausa",status(true,false,2,1,0,true,false,0));
  eq("In attesa del prossimo controllo Vinted",status(true,false,8,0,1,false,false,200));
  eq("In attesa di elaborazione",status(true,false,0,1,1,false,false,0));
  // A blocked remote lane does not prevent actual local work being shown.
  eq("Elaborazione in corso",status(true,false,9,0,1,false,true,0));
  for(int phase=0;phase<5;phase++)for(int mask=0;mask<32;mask++){
   boolean active=EngineOverviewPresentation.activePhase(mask,phase,false,false,0,100);
   if(active!=(phase<4&&(mask&(1<<phase))!=0))throw new AssertionError("inactive phase animated");
  }
  if(EngineOverviewPresentation.activePhase(8,3,false,false,200,100))throw new AssertionError("wait animated");
  if(EngineOverviewPresentation.activePhase(8,3,false,true,0,100))throw new AssertionError("Vinted pause animated");
  if(EngineOverviewPresentation.activePhase(2,1,true,false,0,100))throw new AssertionError("BGG pause animated");
  eq("Scroll elaborato",status(true,true,16,0,0,false,false,0));
  eq("Da riconoscere",EngineOverviewPresentation.phaseLabel(0));eq("Pronti",EngineOverviewPresentation.phaseLabel(4));
  // An incomplete price comparison has stock, but no executable queue or animation.
  eq("dati incompleti",EngineOverviewPresentation.phaseState(3,5,0,false,false,false,0,100));
  eq("in coda",EngineOverviewPresentation.phaseState(3,5,2,false,false,false,0,100));
  eq("in pausa",EngineOverviewPresentation.phaseState(3,5,2,false,false,true,0,100));
  eq("dati incompleti",EngineOverviewPresentation.phaseState(3,5,0,false,false,true,0,100));
  // A PROCESSING lease remains work when pause/pacing suppresses its animation.
  eq("in pausa",EngineOverviewPresentation.phaseState(3,1,0,true,false,true,0,100));
  eq("in attesa",EngineOverviewPresentation.phaseState(3,1,0,true,false,false,200,100));
  eq("in pausa",EngineOverviewPresentation.phaseState(1,1,0,true,true,false,0,100));
  if(!EngineOverviewPresentation.contentSettled(true,new int[]{0,0,0,0,3}))throw new AssertionError("complete scroll not settled");
  if(EngineOverviewPresentation.contentSettled(true,new int[]{1,0,0,0,0}))throw new AssertionError("pending local analysis hidden by settled run");
  if(EngineOverviewPresentation.contentSettled(false,new int[5]))throw new AssertionError("unmaterialised work marked done");
  System.out.println("PASS production overview status, mixed-lane pause and 160 activity masks");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'EngineOverviewPresentationRegression.java';p.write_text(harness)
 subprocess.run(['javac','-d',tmp,str(src/'EngineOverviewPresentation.java'),str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.EngineOverviewPresentationRegression'],check=True)
