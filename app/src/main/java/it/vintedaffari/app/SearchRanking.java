package it.vintedaffari.app;
import java.text.Normalizer;import java.util.*;
/** Token-aware typo tolerance: a short base title cannot outrank an exact expansion. */
public final class SearchRanking {
 public static String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 public static int score(String query,String title){String q=normalize(query),t=normalize(title);if(q.isEmpty()||t.isEmpty())return 0;if(q.equals(t))return 1000;String[] wanted=q.split(" +"),words=t.split(" +");int sum=0;for(String w:wanted){int best=0;for(String v:words){if(w.equals(v))best=Math.max(best,100);else if(w.length()>=3&&v.startsWith(w))best=Math.max(best,80);else if(w.length()>=4&&Math.abs(w.length()-v.length())<=2){int d=distance(w,v);if(d<=Math.max(1,w.length()/5))best=Math.max(best,70-d*10);}}sum+=best;}return sum/wanted.length*8-Math.abs(words.length-wanted.length)*8;}
 private static int distance(String a,String b){int[] p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int[] n=new int[p.length];n[0]=i;for(int j=1;j<n.length;j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()];}
}
