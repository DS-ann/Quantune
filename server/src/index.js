import { DurableObject } from "cloudflare:workers";

const MAX_USERS = 32;
const MAX_PENDING = 20;
const MAX_NAME = 32;
const MAX_CHAT = 500;
const RECONNECT_GRACE_MS = 15 * 60 * 1000;
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function varint(value) {
  let n = BigInt(value);
  const out = [];
  while (n > 127n) { out.push(Number((n & 127n) | 128n)); n >>= 7n; }
  out.push(Number(n));
  return Uint8Array.from(out);
}
function concat(parts) {
  const size = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(size); let offset = 0;
  for (const p of parts) { out.set(p, offset); offset += p.length; }
  return out;
}
function fieldBytes(number, bytes) {
  return concat([varint((number << 3) | 2), varint(bytes.length), bytes]);
}
function fieldString(number, value) {
  return value ? fieldBytes(number, new TextEncoder().encode(String(value))) : new Uint8Array();
}
function fieldInt(number, value) {
  const n = BigInt(value || 0);
  return n === 0n ? new Uint8Array() : concat([varint(number << 3), varint(n)]);
}
function fieldSInt(number, value) {
  const n = BigInt(Math.trunc(Number(value) || 0));
  const encoded = (n << 1n) ^ (n >> 63n);
  return concat([varint(number << 3), varint(encoded)]);
}
function fieldBool(number, value) { return value ? fieldInt(number, 1) : new Uint8Array(); }
function fieldFloat(number, value) {
  if (!value) return new Uint8Array();
  const b = new Uint8Array(4); new DataView(b.buffer).setFloat32(0, value, true);
  return concat([varint((number << 3) | 5), b]);
}
function readVarint(bytes, state) {
  let result = 0n, shift = 0n;
  while (state.i < bytes.length && shift < 70n) {
    const b = bytes[state.i++]; result |= BigInt(b & 127) << shift;
    if (!(b & 128)) return result;
    shift += 7n;
  }
  throw new Error("Malformed protobuf varint");
}
function decodeFields(bytes) {
  const fields = []; const s = { i: 0 };
  while (s.i < bytes.length) {
    const tag = Number(readVarint(bytes, s)); if (!tag) break;
    const number = tag >>> 3, wire = tag & 7;
    if (wire === 0) fields.push({ n: number, w: wire, v: readVarint(bytes, s) });
    else if (wire === 2) {
      const len = Number(readVarint(bytes, s));
      if (len < 0 || s.i + len > bytes.length) throw new Error("Invalid protobuf length");
      fields.push({ n: number, w: wire, b: bytes.slice(s.i, s.i + len) }); s.i += len;
    } else if (wire === 1) s.i += 8;
    else if (wire === 5) s.i += 4;
    else throw new Error("Unsupported protobuf wire type");
    if (s.i > bytes.length) throw new Error("Truncated protobuf field");
  }
  return fields;
}
const one = (f, n) => f.find(x => x.n === n);
const str = (f, n, fallback = "") => { const x = one(f,n); return x?.b ? new TextDecoder().decode(x.b) : fallback; };
const num = (f, n, fallback = 0) => { const x = one(f,n); return x ? Number(x.v) : fallback; };
const bool = (f, n) => num(f,n) !== 0;
const bytes = (f,n) => f.filter(x => x.n === n && x.b).map(x => x.b);
async function decodeEnvelope(data) {
  const f = decodeFields(new Uint8Array(data));
  let payload = one(f,2)?.b || new Uint8Array();
  if (bool(f,3) && payload.length) {
    const stream = new Blob([payload]).stream().pipeThrough(new DecompressionStream("gzip"));
    payload = new Uint8Array(await new Response(stream).arrayBuffer());
    if (payload.length > 2 * 1024 * 1024) throw new Error("Payload too large");
  }
  return { type: str(f,1), payload };
}
function envelope(type, payload = new Uint8Array()) {
  return concat([fieldString(1,type), fieldBytes(2,payload)]);
}
function encodeTrack(t) {
  if (!t) return new Uint8Array();
  return concat([fieldString(1,t.id),fieldString(2,t.title),fieldString(3,t.artist),
    fieldString(4,t.album),fieldInt(5,t.durationMs),fieldString(6,t.thumbnail)]);
}
function decodeTrack(b) {
  if (!b) return null;
  const f=decodeFields(b), id=str(f,1);
  if (!id) return null;
  return {id,title:str(f,2),artist:str(f,3),album:str(f,4),durationMs:num(f,5),thumbnail:str(f,6)};
}
function encodeUser(u, hostId) {
  return concat([fieldString(1,u.id),fieldString(2,u.name),fieldBool(3,u.id===hostId),fieldBool(4,u.connected)]);
}
function encodeState(s) {
  const parts=[fieldString(1,s.code),fieldString(2,s.hostId)];
  for (const u of s.users) parts.push(fieldBytes(3,encodeUser(u,s.hostId)));
  if (s.track) parts.push(fieldBytes(4,encodeTrack(s.track)));
  parts.push(fieldBool(5,s.playing),fieldInt(6,s.position),fieldInt(7,s.lastUpdate),
    fieldFloat(8,1));
  for (const t of s.queue) parts.push(fieldBytes(9,encodeTrack(t)));
  parts.push(fieldInt(10,s.revision),fieldString(11,s.queueTitle || ""));
  return concat(parts);
}
function syncState(s) {
  const parts=[];
  if (s.track) parts.push(fieldBytes(1,encodeTrack(s.track)));
  parts.push(fieldBool(2,s.playing),fieldInt(3,s.position),fieldInt(4,s.lastUpdate));
  for (const t of s.queue) parts.push(fieldBytes(5,encodeTrack(t)));
  parts.push(fieldFloat(6,1),fieldInt(7,s.revision),fieldString(8,s.queueTitle || ""));
  return concat(parts);
}
function livePosition(s) {
  if (!s.playing) return Math.max(0,s.position || 0);
  const p=(s.position || 0)+Math.max(0,Date.now()-(s.lastUpdate || Date.now()));
  return s.track?.durationMs ? Math.min(p,s.track.durationMs) : p;
}
function crc32Text(value) {
  let crc = 0xffffffff;
  for (const byte of new TextEncoder().encode(value)) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function playbackStateHash(s) {
  let value = `${s.track?.id || ""}\\n`;
  for (const track of s.queue || []) value += `${track.id}\\n`;
  return crc32Text(value);
}
function makeId() { return crypto.randomUUID(); }
function makeToken(code) { return code + "." + Array.from(crypto.getRandomValues(new Uint8Array(24)),b=>b.toString(16).padStart(2,"0")).join(""); }
function clean(s,max) { return String(s || "").replace(/[\u0000-\u001f\u007f]/g,"").trim().slice(0,max); }
function makeCode() {
  const a=crypto.getRandomValues(new Uint8Array(6));
  return Array.from(a,b=>CODE_CHARS[b % CODE_CHARS.length]).join("");
}
function errorPayload(code,message) { return concat([fieldString(1,code),fieldString(2,message)]); }

export class Connection extends DurableObject {
  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected WebSocket", {status:426});
    const pair=new WebSocketPair(); const [client,server]=Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({roomCode:null,userId:null});
    return new Response(null,{status:101,webSocket:client});
  }
  async send(data) {
    for (const ws of this.ctx.getWebSockets()) {
      if (ws.readyState===WebSocket.OPEN) ws.send(data);
    }
  }
  async setContext(roomCode,userId) {
    for (const ws of this.ctx.getWebSockets()) ws.serializeAttachment({roomCode,userId});
  }
  async webSocketMessage(ws,message) {
    try {
      if (typeof message==="string") throw new Error("Binary protobuf required");
      const decoded=await decodeEnvelope(message);
      const id=this.ctx.id.toString();
      const att=ws.deserializeAttachment() || {};
      if (decoded.type==="create_room") {
        let created=false;
        for(let i=0;i<5&&!created;i++) {
          const code=makeCode();
          created=await this.env.ROOMS.getByName(code).createRoom(id,decoded.payload,code);
        }
        if(!created) await this.send(envelope("error",errorPayload("room_creation_failed","Could not allocate a room. Retry.")));
        return;
      }
      if (decoded.type==="join_room") {
        const f=decodeFields(decoded.payload), code=clean(str(f,1).toUpperCase(),12);
        if(!code) { await this.send(envelope("error",errorPayload("missing_room_code","Room code is required."))); return; }
        await this.env.ROOMS.getByName(code).joinRoom(id,decoded.payload);
        return;
      }
      if (decoded.type==="reconnect") {
        const token=str(decodeFields(decoded.payload),1);
        const code=token.split(".")[0];
        if(!code) { await this.send(envelope("error",errorPayload("missing_session_token","Session token is required."))); return; }
        await this.env.ROOMS.getByName(code).reconnect(id,token);
        return;
      }
      if (!att.roomCode) {
        await this.send(envelope("error",errorPayload("not_in_room","You are not in a room.")));
        return;
      }
      await this.env.ROOMS.getByName(att.roomCode).handleMessage(id,att.userId,decoded.type,decoded.payload);
    } catch (e) {
      await this.send(envelope("error",errorPayload("invalid_message",clean(e?.message || "Invalid message",160))));
    }
  }
  async webSocketClose(ws) {
    const att=ws.deserializeAttachment() || {};
    if(att.roomCode) {
      try { await this.env.ROOMS.getByName(att.roomCode).disconnected(this.ctx.id.toString(),att.userId); } catch {}
    }
  }
  async webSocketError(ws) { try { ws.close(1011,"Connection error"); } catch {} }
}

export class Room extends DurableObject {
  constructor(ctx,env) {
    super(ctx,env);
    this.stateData=null;
    this.ctx.blockConcurrencyWhile(async()=>{ this.stateData=await this.ctx.storage.get("room") || null; });
  }
  async save() { await this.ctx.storage.put("room",this.stateData); }
  async send(id,type,payload) {
    if(!id) return;
    try {
      const stub=this.env.CONNECTIONS.get(this.env.CONNECTIONS.idFromString(id));
      await stub.send(envelope(type,payload));
    } catch {}
  }
  async setContext(id,userId) {
    try {
      const stub=this.env.CONNECTIONS.get(this.env.CONNECTIONS.idFromString(id));
      await stub.setContext(this.stateData.code,userId);
    } catch {}
  }
  async broadcast(type,payload,exceptId=null) {
    const ids=this.stateData?.users.filter(u=>u.connected && u.connectionId!==exceptId).map(u=>u.connectionId) || [];
    await Promise.all(ids.map(id=>this.send(id,type,payload)));
  }
  async fail(id,code,message) { await this.send(id,"error",errorPayload(code,message)); }
  async createRoom(connectionId,payload,assignedCode) {
    if(this.stateData) return false;
    const f=decodeFields(payload), username=clean(str(f,1),MAX_NAME);
    if(!username) { await this.fail(connectionId,"missing_username","Username is required."); return true; }
    if(!assignedCode) { await this.fail(connectionId,"room_code_missing","Room allocation failed."); return true; }
    const userId=makeId(), token=makeToken(assignedCode), now=Date.now();
    this.stateData={code:assignedCode,hostId:userId,users:[{id:userId,name:username,connected:true,connectionId}],
      pending:[],sessions:[{token,userId,name:username,isHost:true,disconnectedAt:0}],
      track:null,playing:false,position:0,lastUpdate:now,queue:[],queueTitle:"",revision:0};
    await this.save(); await this.setContext(connectionId,userId);
    await this.send(connectionId,"room_created",concat([fieldString(1,assignedCode),fieldString(2,userId),fieldString(3,token)]));
    return true;
  }
  async joinRoom(connectionId,payload) {
    const f=decodeFields(payload), code=clean(str(f,1).toUpperCase(),12), username=clean(str(f,2),MAX_NAME);
    if(!this.stateData) { await this.fail(connectionId,"room_not_found","Room not found."); return; }
    const s=this.stateData;
    if(!username) { await this.fail(connectionId,"missing_username","Username is required."); return; }
    if(s.users.length>=MAX_USERS || s.pending.length>=MAX_PENDING) { await this.fail(connectionId,"room_full","This room is full."); return; }
    if(s.pending.some(p=>p.connectionId===connectionId) || s.users.some(u=>u.connectionId===connectionId)) {
      await this.fail(connectionId,"already_pending","You already have a room request."); return;
    }
    const userId=makeId();
    s.pending.push({id:userId,name:username,connectionId,createdAt:Date.now()});
    await this.save(); await this.setContext(connectionId,userId);
    const host=s.users.find(u=>u.id===s.hostId && u.connected);
    if(host) await this.send(host.connectionId,"join_request",concat([fieldString(1,userId),fieldString(2,username)]));
  }
  async reconnect(connectionId,token) {
    const s=this.stateData;
    const session=s?.sessions.find(x=>x.token===token);
    if(!s || !session) { await this.fail(connectionId,"session_not_found","Session not found."); return; }
    if(session.disconnectedAt && Date.now()-session.disconnectedAt>RECONNECT_GRACE_MS) {
      await this.fail(connectionId,"session_expired","The reconnect session has expired."); return;
    }
    const user=s.users.find(u=>u.id===session.userId);
    if(!user) { await this.fail(connectionId,"session_not_found","Room membership no longer exists."); return; }
    const previousConnection=user.connectionId;
    user.connected=true; user.connectionId=connectionId; session.disconnectedAt=0;
    if(user.id===s.hostId) await this.ctx.storage.deleteAlarm();
    await this.save(); await this.setContext(connectionId,user.id);
    const isHost=user.id===s.hostId;
    await this.send(connectionId,"reconnected",concat([
      fieldString(1,s.code),fieldString(2,user.id),fieldBytes(3,encodeState(s)),fieldBool(4,isHost)
    ]));
    await this.broadcast("user_reconnected",concat([fieldString(1,user.id),fieldString(2,user.name)]),connectionId);
    if(previousConnection && previousConnection!==connectionId) {
      try { const old=this.env.CONNECTIONS.get(this.env.CONNECTIONS.idFromString(previousConnection)); await old.send(envelope("kicked",fieldString(1,"Session reconnected on another device."))); } catch {}
    }
  }
  async handleMessage(connectionId,userId,type,payload) {
    const s=this.stateData;
    if(!s) return this.fail(connectionId,"room_not_found","Room no longer exists.");
    const user=s.users.find(u=>u.id===userId && u.connectionId===connectionId);
    const pending=s.pending.find(p=>p.id===userId && p.connectionId===connectionId);
    if(type==="leave_room") {
      if(pending) { s.pending=s.pending.filter(p=>p!==pending); await this.save(); await this.setContext(connectionId,null); return; }
      if(user) return this.leave(connectionId,user);
      return;
    }
    if(type==="approve_join") {
      if(!user || user.id!==s.hostId) return this.fail(connectionId,"not_host","Only the host can approve requests.");
      const targetId=str(decodeFields(payload),1), request=s.pending.find(p=>p.id===targetId);
      if(!request) return this.fail(connectionId,"join_request_not_found","Join request not found.");
      if(s.users.length>=MAX_USERS) return this.fail(connectionId,"room_full","This room is full.");
      const token=makeToken(s.code), now=Date.now();
      const guest={id:request.id,name:request.name,connected:true,connectionId:request.connectionId};
      s.users.push(guest); s.sessions.push({token,userId:guest.id,name:guest.name,isHost:false,disconnectedAt:0});
      s.pending=s.pending.filter(p=>p.id!==targetId); await this.save();
      await this.setContext(guest.connectionId,guest.id);
      await this.send(guest.connectionId,"join_approved",concat([
        fieldString(1,s.code),fieldString(2,guest.id),fieldString(3,token),fieldBytes(4,encodeState(s))
      ]));
      await this.broadcast("user_joined",concat([fieldString(1,guest.id),fieldString(2,guest.name)]),guest.connectionId);
      return;
    }
    if(type==="reject_join") {
      if(!user || user.id!==s.hostId) return this.fail(connectionId,"not_host","Only the host can reject requests.");
      const f=decodeFields(payload), targetId=str(f,1), reason=clean(str(f,2),200)||"Join request rejected by host";
      const request=s.pending.find(p=>p.id===targetId); if(!request) return this.fail(connectionId,"join_request_not_found","Join request not found.");
      s.pending=s.pending.filter(p=>p!==request); await this.save();
      await this.send(request.connectionId,"join_rejected",fieldString(1,reason));
      await this.setContext(request.connectionId,null); return;
    }
    if(type==="ping") {
      const f=decodeFields(payload), receive=Date.now();
      const response=[fieldInt(1,num(f,1)),fieldInt(2,receive),fieldInt(3,Date.now()),fieldInt(4,num(f,2))];
      if(user && user.id!==s.hostId && num(f,3)===1 && s.track) {
        const guestTrackId=str(f,4);
        const guestPosition=Math.max(0,num(f,5));
        const guestAt=num(f,6,receive);
        const guestPlaying=bool(f,9);
        const guestHashValid=num(f,7)===1;
        const trackMatches=guestTrackId===s.track.id;
        const queueMatches=!guestHashValid || num(f,8)===playbackStateHash(s);
        const stateMismatch=!trackMatches || !queueMatches || guestPlaying!==s.playing;
        const ageMs=receive-guestAt;
        const canMeasureDrift=trackMatches && guestPlaying===s.playing && ageMs>=-5000 && ageMs<=30000;
        const guestPositionAtReceive=guestPosition+(guestPlaying?Math.max(0,ageMs):0);
        const driftMs=canMeasureDrift?Math.round(guestPositionAtReceive-livePosition(s)):0;
        response.push(fieldBool(5,canMeasureDrift),fieldSInt(6,driftMs),fieldBool(7,stateMismatch));
      }
      await this.send(connectionId,"pong",concat(response));
      return;
    }
    if(type==="request_sync") {
      if(!user) return this.fail(connectionId,"not_in_room","You are not an active room member.");
      await this.send(connectionId,"sync_state",syncState(s)); return;
    }
    if(type==="playback_action") {
      if(!user || user.id!==s.hostId) return this.fail(connectionId,"not_host","Only the host can control playback.");
      const f=decodeFields(payload), action=str(f,1), trackField=one(f,4);
      if(!action) return this.fail(connectionId,"missing_action","Action is required.");
      const now=Date.now();
      if(trackField?.b) s.track=decodeTrack(trackField.b);
      const queueFields=bytes(f,6), hasQueue=queueFields.length>0 || f.some(x=>x.n===12 && x.v!==0n) || action==="sync_queue";
      if(hasQueue) s.queue=queueFields.map(decodeTrack).filter(Boolean);
      if(action==="play") s.playing=true;
      else if(action==="pause") s.playing=false;
      else if(action==="change_track") s.playing=false;
      else if(action==="seek") s.position=num(f,3);
      else if(action==="sync_queue" || action==="queue_clear") s.queue=queueFields.map(decodeTrack).filter(Boolean);
      if(action==="play" || action==="pause" || action==="seek" || action==="change_track") s.position=num(f,3);
      if(str(f,7)) s.queueTitle=clean(str(f,7),120);
      s.lastUpdate=now; s.revision++;
      await this.save();
      const out=[fieldString(1,action),fieldString(2,str(f,2)),fieldInt(3,s.position)];
      if(s.track) out.push(fieldBytes(4,encodeTrack(s.track)));
      if(hasQueue) for(const t of s.queue) out.push(fieldBytes(6,encodeTrack(t)));
      out.push(fieldString(7,s.queueTitle),fieldInt(9,now));
      await this.broadcast("sync_playback",concat(out)); return;
    }
    if(type==="chat") {
      if(!user) return this.fail(connectionId,"not_in_room","You are not an active room member.");
      const message=clean(str(decodeFields(payload),1),MAX_CHAT);
      if(!message) return;
      const out=concat([fieldString(1,makeId()),fieldString(2,user.id),fieldString(3,user.name),
        fieldString(4,message),fieldInt(5,Date.now())]);
      await this.broadcast("chat_message",out); return;
    }
    if(type==="kick_user") {
      if(!user || user.id!==s.hostId) return this.fail(connectionId,"not_host","Only the host can remove listeners.");
      const f=decodeFields(payload), targetId=str(f,1), target=s.users.find(u=>u.id===targetId);
      if(!target || target.id===s.hostId) return this.fail(connectionId,"user_not_found","Listener not found.");
      await this.send(target.connectionId,"kicked",fieldString(1,clean(str(f,2),200)||"Removed by host."));
      s.users=s.users.filter(u=>u.id!==targetId); s.sessions=s.sessions.filter(x=>x.userId!==targetId);
      await this.save();
      await this.broadcast("user_left",concat([fieldString(1,target.id),fieldString(2,target.name)])); return;
    }
    return this.fail(connectionId,"unknown_message_type","Unsupported message type: "+type);
  }
  async leave(connectionId,user) {
    const s=this.stateData;
    if(user.id===s.hostId) {
      for(const u of s.users) if(u.connected && u.connectionId!==connectionId)
        await this.send(u.connectionId,"kicked",fieldString(1,"The host closed the room."));
      for(const p of s.pending) await this.send(p.connectionId,"kicked",fieldString(1,"The host closed the room."));
      this.stateData=null; await this.ctx.storage.deleteAll(); await this.ctx.storage.deleteAlarm();
      await this.setContext(connectionId,null); return;
    }
    s.users=s.users.filter(u=>u.id!==user.id); s.sessions=s.sessions.filter(x=>x.userId!==user.id);
    await this.save(); await this.setContext(connectionId,null);
    await this.broadcast("user_left",concat([fieldString(1,user.id),fieldString(2,user.name)]));
  }
  async disconnected(connectionId,userId) {
    const s=this.stateData; if(!s) return;
    const pending=s.pending.find(p=>p.connectionId===connectionId);
    if(pending) { s.pending=s.pending.filter(p=>p!==pending); await this.save(); return; }
    const user=s.users.find(u=>u.id===userId && u.connectionId===connectionId);
    if(!user) return;
    user.connected=false;
    const session=s.sessions.find(x=>x.userId===user.id);
    if(session) session.disconnectedAt=Date.now();
    await this.save();
    if(user.id===s.hostId) await this.ctx.storage.setAlarm(Date.now()+RECONNECT_GRACE_MS);
    else await this.broadcast("user_disconnected",concat([fieldString(1,user.id),fieldString(2,user.name)]));
  }
  async alarm() {
    const s=this.stateData; if(!s) return;
    const now=Date.now();
    const host=s.users.find(u=>u.id===s.hostId);
    if(host && !host.connected) {
      const hs=s.sessions.find(x=>x.userId===host.id);
      if(hs?.disconnectedAt && now-hs.disconnectedAt>=RECONNECT_GRACE_MS) {
        for(const u of s.users) if(u.connected) await this.send(u.connectionId,"kicked",fieldString(1,"The host did not reconnect. Room closed."));
        this.stateData=null; await this.ctx.storage.deleteAll(); return;
      }
      await this.ctx.storage.setAlarm((hs?.disconnectedAt || now)+RECONNECT_GRACE_MS); return;
    }
    const expired=s.sessions.filter(x=>x.disconnectedAt && now-x.disconnectedAt>=RECONNECT_GRACE_MS);
    for(const session of expired) {
      const u=s.users.find(x=>x.id===session.userId);
      if(u) { s.users=s.users.filter(x=>x.id!==u.id); await this.broadcast("user_left",concat([fieldString(1,u.id),fieldString(2,u.name)])); }
      s.sessions=s.sessions.filter(x=>x.userId!==session.userId);
    }
    await this.save();
    const next=s.sessions.filter(x=>x.disconnectedAt).map(x=>x.disconnectedAt+RECONNECT_GRACE_MS).sort((a,b)=>a-b)[0];
    if(next) await this.ctx.storage.setAlarm(next);
  }
}

export default {
  async fetch(request,env) {
    const url=new URL(request.url);
    if(url.pathname==="/health") return Response.json({ok:true,service:"Quantune Listen Together",protocol:"protobuf-websocket",version:"1.0.0"});
    if(url.pathname!=="/ws") return new Response("Not found",{status:404});
    if(request.headers.get("Upgrade")!=="websocket") return new Response("Expected WebSocket",{status:426});
    const id=crypto.randomUUID(), stub=env.CONNECTIONS.getByName(id);
    return stub.fetch(request);
  }
};
