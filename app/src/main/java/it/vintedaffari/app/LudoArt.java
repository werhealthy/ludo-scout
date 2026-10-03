package it.vintedaffari.app;
/** Single mapping for the supplied hand-drawn states. */
final class LudoArt {
 private LudoArt(){}
 static int image(LudoPetMood mood){
  switch(mood){
   case GREETING:return R.drawable.ludo_hello;
   case SEARCHING:return R.drawable.ludo_deal_search;
   case FOUND:return R.drawable.ludo_treasure_reward;
   case CONFUSED:return R.drawable.ludo_no_results;
   case SLEEPING:return R.drawable.ludo_sleeping;
   default:return R.drawable.ludo_idle;
  }
 }
}
