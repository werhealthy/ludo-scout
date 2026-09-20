const fs=require('fs'),path=require('path'),vm=require('vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/engine/bgg-quality-composite-v4.js'),'utf8');
const context={VintedLocalCatalog:{games:[]}};vm.createContext(context);vm.runInContext(source,context);
const lines=[];for(let i=0;i<120;i++){const rank=i%9===0?null:1+(i*271)%30000,geek=5+(i%35)/10,average=5+(i%41)/10,votes=(i*1777)%210000;const score=context.VintedQualityComposite.score({bggRank:rank,geekRating:geek,averageRating:average,ratingCount:votes}).score;lines.push([rank===null?'null':rank,geek,average,votes,score].join('\t'));}
fs.writeFileSync(path.join(__dirname,'quality-fixtures.tsv'),lines.join('\n')+'\n');
