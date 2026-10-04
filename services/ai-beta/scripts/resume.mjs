import {createHash} from 'node:crypto';
export function buildResume(deviceDigest,probeToken){
 if(!/^[a-f0-9]{64}$/.test(deviceDigest||'')||typeof probeToken!=='string'||!probeToken)throw Error('Resume prerequisites missing');
 const phone={'francesco-phone-1':deviceDigest};
 return {auth:{DEVICE_DIGESTS:JSON.stringify({...phone,'provision-check':createHash('sha256').update(probeToken).digest('hex')})},cleanup:{DEVICE_DIGESTS:JSON.stringify(phone)}};
}
export function verifyBudget(status,month,enabled){
 const b=status?.budget;
 if(status?.enabled!==enabled||!b||b.month!==month||!Number.isSafeInteger(b.calls_reserved)||b.calls_reserved<0||b.calls_reserved>100||!Number.isSafeInteger(b.reserved_micro)||b.reserved_micro<b.calls_reserved*10000||b.reserved_micro>1000000||b.reserved_eur!==(b.reserved_micro/1000000).toFixed(2))throw Error('Current accounting invalid');
 return b;
}
export function verifyUnchanged(status,baseline,month){
 const b=verifyBudget(status,month,true);
 if(!baseline||b.month!==baseline.month||b.calls_reserved!==baseline.calls_reserved||b.reserved_micro!==baseline.reserved_micro||b.reserved_eur!==baseline.reserved_eur)throw Error('Current accounting changed');
}
