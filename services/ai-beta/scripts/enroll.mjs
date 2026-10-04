import {randomBytes,createHash} from 'node:crypto';
import {writeFileSync} from 'node:fs';
// Exclusive private output: token is never printed or put in shell arguments.
const [id,out]=process.argv.slice(2);
if(!/^[a-zA-Z0-9_-]{1,64}$/.test(id||'')||!out)throw Error('Usage: node scripts/enroll.mjs DEVICE_ID PRIVATE_OUTPUT.json');
const token=randomBytes(32).toString('base64url');
writeFileSync(out,JSON.stringify({device_id:id,token,device_digest:createHash('sha256').update(token).digest('hex')},null,2)+'\n',{flag:'wx',mode:0o600});
