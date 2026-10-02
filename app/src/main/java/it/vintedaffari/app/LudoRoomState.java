package it.vintedaffari.app;
/** Local room navigation; persistent collections retain their existing owners. */
final class LudoRoomState {
 static final String EXPLORE="explore",HUNTS="hunts",HOME="home";
 private String room;private int exploreY,huntsY,homeY;private boolean huntsRestorePending;
 LudoRoomState(String saved,int explore,int home){this(saved,explore,0,home);}
 LudoRoomState(String saved,int explore,int hunts,int home){room=normalize(saved);exploreY=Math.max(0,explore);huntsY=Math.max(0,hunts);homeY=Math.max(0,home);}
 static String normalize(String value){return HOME.equals(value)?HOME:HUNTS.equals(value)?HUNTS:EXPLORE;}
 String room(){return room;}boolean isHome(){return HOME.equals(room);}
 int position(String value){String key=normalize(value);return HOME.equals(key)?homeY:HUNTS.equals(key)?huntsY:exploreY;}
 void beginHuntsRestore(){huntsRestorePending=true;}
 void finishHuntsRestore(){huntsRestorePending=false;}
 void recordScroll(int y){if(huntsRestorePending&&HUNTS.equals(room))return;int value=Math.max(0,y);if(isHome())homeY=value;else if(HUNTS.equals(room))huntsY=value;else exploreY=value;}
 int switchTo(String next,int currentY){recordScroll(currentY);room=normalize(next);return position(room);}
 static String swipeTarget(String current,int direction){String key=normalize(current);if(direction>0)return EXPLORE.equals(key)?HUNTS:HOME;if(direction<0)return HOME.equals(key)?HUNTS:EXPLORE;return key;}
}
