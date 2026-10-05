"""Guard API28 source contracts and execute the production URL encoder on Java."""
from pathlib import Path
import re, shutil, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
resolver=(src/'VintedLinkResolver.java').read_text(encoding='utf-8')
main=(src/'MainActivity.java').read_text(encoding='utf-8')
radar=(src/'VintedAccessibilityService.java').read_text(encoding='utf-8')
fade=(src/'FadeArtwork.java').read_text(encoding='utf-8')
checks={
 'minSdk28':'minSdk 28' in (root/'app/build.gradle').read_text(encoding='utf-8'),
 'URL encoder API28':'URLEncoder.encode(s==null?"":s, StandardCharsets.UTF_8.name())' in resolver,
 'Main receiver private on all SDKs':'ContextCompat.registerReceiver(this,receiver,f,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)' in main,
 'Radar receiver private on all SDKs':'ContextCompat.registerReceiver(this,retryReceiver,retryFilter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)' in radar,
 'Zoom compat widget':'ZoomImageView extends androidx.appcompat.widget.AppCompatImageView' in main,
 'Fade compat widget':'FadeArtwork extends androidx.appcompat.widget.AppCompatImageView' in fade,
}
bad=[name for name,passed in checks.items() if not passed]
assert not bad, 'Missing compatibility fixes: '+', '.join(bad)
method=re.search(r'    private static String enc\(String s\)\{(.*?)\n    \}',resolver,re.S).group(0)
probe='''import java.net.URLEncoder;import java.nio.charset.StandardCharsets;
public class EncoderProbe {
METHOD
public static void main(String[] args){
String[][] cases={{null,""},{"",""},{"123","123"},{"A B+C&/","A+B%2BC%26%2F"},{"città €","citt%C3%A0+%E2%82%AC"},{"🎲","%F0%9F%8E%B2"}};
for(String[] c:cases)if(!enc(c[0]).equals(c[1]))throw new AssertionError(c[0]);
System.out.println("PASS production encoder: null, ASCII, separators, accented text, euro, emoji");
}}
'''.replace('METHOD',method)
with tempfile.TemporaryDirectory() as td:
 p=Path(td)/'EncoderProbe.java';p.write_text(probe,encoding='utf-8')
 compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run(compiler+['-encoding','UTF-8','-d',td,str(p)],check=True)
 subprocess.run(['java','-cp',td,'EncoderProbe'],check=True)
print('PASS all five Android compatibility source contracts')
