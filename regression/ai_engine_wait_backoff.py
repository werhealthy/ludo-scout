#!/usr/bin/env python3
"""Exercise the production AI retry-deadline calculation on the host JVM."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'app/src/main/java/it/vintedaffari/app/AiEngineBackoff.java'
probe='''package it.vintedaffari.app;
public final class AiEngineBackoffProbe {
 public static void main(String[] args){
  long now=1_000_000L,deadline=now+240_000L;
  if(AiEngineBackoff.nextAttemptAt("WAIT",now,deadline,false,900_000L)!=deadline)throw new AssertionError("WAIT must use the journal deadline");
  if(AiEngineBackoff.nextAttemptAt("WAIT",now,now-1,false,900_000L)!=now+10_000L)throw new AssertionError("expired WAIT must retry promptly");
  if(AiEngineBackoff.nextAttemptAt("CHECKED",now,deadline,false,900_000L)!=now+900_000L)throw new AssertionError("completed pass keeps normal backoff");
  if(AiEngineBackoff.nextAttemptAt("CHECKED",now,deadline,true,900_000L)!=now+10_000L)throw new AssertionError("pending batch continues promptly");
  System.out.println("PASS AI WAIT honors persisted retry deadline (4 checks)");
 }
}'''
with tempfile.TemporaryDirectory(prefix='ludo-ai-wait-test-') as tmp:
    probe_path=Path(tmp)/'AiEngineBackoffProbe.java'
    probe_path.write_text(probe,encoding='utf-8')
    subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,str(source),str(probe_path)],check=True)
    subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.AiEngineBackoffProbe'],check=True)
