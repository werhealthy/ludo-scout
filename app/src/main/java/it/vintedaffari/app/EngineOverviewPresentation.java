package it.vintedaffari.app;

/** Presentation only: never decides queue ownership or changes pipeline eligibility. */
final class EngineOverviewPresentation {
    private EngineOverviewPresentation() {}
    static boolean showResearchEmpty(boolean hasScope,int waitingRuns,int intakeCount) {
        return !hasScope&&waitingRuns==0&&intakeCount==0;
    }
    static String backSection(String section,boolean hasDay) {
        if("run".equals(section)&&hasDay)return "day";
        if("day".equals(section))return "history";
        return "overview";
    }
    static String phaseLabel(int phase) {
        String[] labels={"Da riconoscere","Dati BGG","Da collegare","Verifica annuncio","Pronti"};
        return labels[phase];
    }
    static boolean activePhase(int mask,int phase,boolean bggPaused,boolean vintedPaused,long waitUntil,long now) {
        return phase>=0&&phase<4&&(mask&(1<<phase))!=0 && !(phase==1&&bggPaused)
                && !(phase==3&&(vintedPaused||waitUntil>now));
    }
    static String phaseState(int phase,int total,boolean active,boolean bggPaused,boolean vintedPaused,long waitUntil,long now) {
        if(phase==4)return "disponibili";
        if(active)return "attive";
        if(total>0&&((phase==1&&bggPaused)||(phase==3&&vintedPaused)))return "in pausa";
        if(total>0&&phase==3&&waitUntil>now)return "in attesa";
        return total>0?"in coda":"nessun elemento";
    }
    static String phaseState(int phase,int total,int queued,boolean active,boolean bggPaused,boolean vintedPaused,long waitUntil,long now) {
        if(phase==4)return "disponibili";
        if(total<=0)return "nessun elemento";
        if(queued<=0&&!active)return "dati incompleti";
        if((phase==1&&bggPaused)||(phase==3&&vintedPaused))return "in pausa";
        if(phase==3&&waitUntil>now)return "in attesa";
        if(active)return "attive";
        return "in coda";
    }
    static boolean contentSettled(boolean backendSettled,int[] queued) {
        if(!backendSettled)return false;
        for(int phase=0;phase<4;phase++)if(queued[phase]>0)return false;
        return true;
    }
    static boolean motionAllowed(boolean active,boolean resumed,boolean focused,boolean overview,boolean attached,boolean shown,boolean enabled) {
        return active&&resumed&&focused&&overview&&attached&&shown&&enabled;
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
