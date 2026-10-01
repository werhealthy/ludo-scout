"""Execute the real bounded trace recorder; catches lost active/failure records and unbounded output."""
from pathlib import Path
import subprocess, tempfile
root = Path(__file__).resolve().parents[1]
source = root/'app/src/main/java/it/vintedaffari/app/DbContentionTrace.java'
assert source.exists(), 'Missing contention trace: in-flight DB waits cannot be exported'
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac','-d',output,str(source),str(root/'regression/DbContentionTraceRegression.java')],check=True)
    subprocess.run(['java','-cp',output,'it.vintedaffari.app.DbContentionTraceRegression'],check=True)
