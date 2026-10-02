package it.vintedaffari.app;
/** Local navigation state only. Collection and preference data keep their existing owners. */
final class LudoRoomState {
 static final String EXPLORE="explore",HOME="home";
 private String room;private int exploreY,homeY;
 LudoRoomState(String saved,int explore,int home){room=normalize(saved);exploreY=Math.max(0,explore);homeY=Math.max(0,home);}
 static String normalize(String value){return HOME.equals(value)?HOME:EXPLORE;}
 String room(){return room;}boolean isHome(){return HOME.equals(room);}
 int position(String value){return HOME.equals(normalize(value))?homeY:exploreY;}
 void recordScroll(int y){if(isHome())homeY=Math.max(0,y);else exploreY=Math.max(0,y);}
 int switchTo(String next,int currentY){recordScroll(currentY);room=normalize(next);return position(room);}
 static String swipeTarget(String current,int direction){return direction>0?HOME:direction<0?EXPLORE:normalize(current);}
}
