package it.vintedaffari.app;
/** Same calibrated anchors and weights as bgg-quality-composite-v4.js. */
public final class QualityComposite {
 private static final double[][] R={{1,100},{10,98},{25,96},{50,94},{100,92},{250,88},{500,83},{1000,76},{2000,68},{5000,57},{10000,47},{20000,37},{30000,30}};
 private static final double[][] G={{5,20},{5.5,30},{6,45},{6.5,60},{6.8,68},{7,73},{7.2,78},{7.5,85},{7.8,92},{8,96},{8.4,100}};
 private static final double[][] A={{5,20},{5.5,30},{6,40},{6.5,50},{7,62},{7.5,75},{8,87},{8.5,96},{9,100}};
 private static final double[][] V={{30,25},{100,35},{300,45},{1000,58},{3000,70},{10000,82},{30000,92},{100000,100},{200000,100}};
 private static double interpolate(double x,double[][] anchors,boolean log){if(x<=anchors[0][0])return anchors[0][1];int n=anchors.length;if(x>=anchors[n-1][0])return anchors[n-1][1];for(int i=1;i<n;i++)if(x<=anchors[i][0]){double lo=anchors[i-1][0],hi=anchors[i][0];double t=log?(Math.log10(x)-Math.log10(lo))/(Math.log10(hi)-Math.log10(lo)):(x-lo)/(hi-lo);return anchors[i-1][1]+t*(anchors[i][1]-anchors[i-1][1]);}return 0;}
 public static Integer score(Integer rank,Double geek,Double average,Integer votes){if(rank==null&&geek==null&&average==null&&votes==null)return null;double sum=interpolate(Math.max(30,votes==null?0:votes),V,true)*.15,weights=.15;boolean ranked=rank!=null&&rank>0;if(ranked){sum+=interpolate(rank,R,true)*.55;weights+=.55;}if(geek!=null){sum+=interpolate(geek,G,false)*.20;weights+=.20;}if(average!=null){sum+=interpolate(average,A,false)*.10;weights+=.10;}double score=sum/weights;if(!ranked)score=Math.min(69,score);return (int)Math.round(Math.max(0,Math.min(100,score)));}
}
