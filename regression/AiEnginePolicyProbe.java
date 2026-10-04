package it.vintedaffari.app;
/** Runs the production policy with the local JVM; no Android or remote service. */
public final class AiEnginePolicyProbe {
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  Class<?> policy;
  try{policy=Class.forName("it.vintedaffari.app.AiEnginePolicy");}
  catch(ClassNotFoundException missing){throw new AssertionError("Automatic AI policy is missing");}
  java.lang.reflect.Method hold=policy.getDeclaredMethod("hold",String.class,String.class);
  check((Boolean)hold.invoke(null,"BASE_GAME","NON_GAME"),"AI disagreement must hold automatic trust");
  check((Boolean)hold.invoke(null,"UNCERTAIN","NON_GAME"),"uncertain saved identity must remain untrusted");
  check((Boolean)hold.invoke(null,"BASE_GAME","BUNDLE"),"bundle conflict must hold");
  check(!(Boolean)hold.invoke(null,"BASE_GAME","BASE_GAME"),"agreement must preserve existing local gates");
  check(!(Boolean)hold.invoke(null,"BASE_GAME","UNKNOWN"),"abstention must not invent a negative verdict");
  check(!(Boolean)hold.invoke(null,"ACCESSORY","BASE_GAME"),"AI must not restore local negatives");
  check(!(Boolean)hold.invoke(null,"COMPONENTS","ACCESSORY_COMPONENT"),"compatible subtype must remain unchanged");
  java.lang.reflect.Method fresh=policy.getDeclaredMethod("fresh",long.class,long.class);
  check((Boolean)fresh.invoke(null,1000L,1001L),"fresh cache rejected");
  check(!(Boolean)fresh.invoke(null,1000L,1000L+7L*86400000),"expired cache reused");
  check(!(Boolean)fresh.invoke(null,1001L,1000L),"future cache reused");
  System.out.println("AI engine policy: 10 checks passed");
 }
}
