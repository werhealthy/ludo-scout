package it.vintedaffari.app;

/** Presentation only: never decides queue ownership or changes pipeline eligibility. */
final class EngineOverviewPresentation {
    private EngineOverviewPresentation() {}
    static String phaseLabel(int phase) {
        String[] labels={"Da riconoscere","Dati BGG","Da collegare","Verifica Vinted","Pronti"};
        return labels[phase];
    }
    static boolean activePhase(int mask,int phase,boolean bggPaused,boolean vintedPaused,long waitUntil,long now) {
        return (mask&(1<<phase))!=0 && !(phase==1&&bggPaused)
                && !(phase==3&&(vintedPaused||waitUntil>now));
    }
    static String status(boolean hasScope,boolean settled,int mask,int bgg,int vinted,
                         boolean bggPaused,boolean vintedPaused,long waitUntil,long now) {
        for(int phase=0;phase<5;phase++)if(activePhase(mask,phase,bggPaused,vintedPaused,waitUntil,now)) {
            if(phase==3)return "Sto verificando gli annunci Vinted";
            if(phase==1)return "Sto completando i dati BGG";
            return "Elaborazione in corso";
        }
        if(!hasScope)return "In attesa di nuovi annunci";
        if(settled)return "Scroll elaborato";
        if(bgg>0&&vinted>0&&bggPaused&&vintedPaused)return "Elaborazione in pausa";
        if(vinted>0&&vintedPaused)return "Verifiche Vinted in pausa";
        if(vinted>0&&waitUntil>now)return "In attesa del prossimo controllo Vinted";
        if(bgg>0&&bggPaused)return "Dati BGG in pausa";
        return "In attesa di elaborazione";
    }
}
