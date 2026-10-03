package it.vintedaffari.app;
/** Visual reactions only: never changes the pipeline or its data. */
enum LudoPetMood {
 IDLE, GREETING, SEARCHING, FOUND, CONFUSED, SLEEPING;
 static LudoPetMood forJourney(boolean working,boolean paused,boolean finished){
  if(paused)return SLEEPING;
  if(working)return SEARCHING;
  return finished?FOUND:IDLE;
 }
}
