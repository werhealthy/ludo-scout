#!/usr/bin/env python3
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/vintedaffari/app/FeaturedBoxGeometry.java"
java='''package it.vintedaffari.app;
public class PreviewGeometryRegression {
 public static void main(String[] args){
 int cases=0;
 for(int w:new int[]{68,120,180,300})for(int h:new int[]{68,160,240})for(int cw:new int[]{15,50,100,300,600}){
 FeaturedBoxGeometry g=new FeaturedBoxGeometry();
 if(!g.update(w,h,cw,100,1))throw new AssertionError("valid artwork rejected");
 float left=Float.MAX_VALUE,right=-Float.MAX_VALUE;
 for(float[] p:new float[][]{g.front,g.top,g.side})for(int i=0;i<8;i+=2){if(p[i]<0||p[i]>w||p[i+1]<0||p[i+1]>h)throw new AssertionError("clipped box");left=Math.min(left,p[i]);right=Math.max(right,p[i]);}
 if(Math.abs((left+right)/2-w/2f)>.01f)throw new AssertionError("complete box off-center: "+w+"x"+h);
 if(Math.abs(g.width/g.height-cw/100f)>.001f)throw new AssertionError("cover aspect changed");
 cases++;
 }
 if(new FeaturedBoxGeometry().update(0,100,100,100,1))throw new AssertionError("invalid bounds accepted");
 System.out.println("PASS "+cases+" complete-box geometry cases");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"PreviewGeometryRegression.java";p.write_text(java)
 subprocess.run(["javac","-d",tmp,str(source),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.PreviewGeometryRegression"],check=True)
