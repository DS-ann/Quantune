# Quantune Listen Together Server

Cloudflare Workers + SQLite-backed Durable Objects implementation for Quantune's Internet Listen Together relay.

## Deployment

Worker name: `quantune-listen-server`
WebSocket endpoint: `wss://quantune-listen-server.<workers-subdomain>.workers.dev/ws`
Health endpoint: `https://quantune-listen-server.<workers-subdomain>.workers.dev/health`

The Worker uses one Connection Durable Object per socket and one Room Durable Object per room. Room state and reconnect sessions are stored in Durable Object SQLite-backed storage. WebSocket connections use the Hibernation API.

## Protocol

Binary Protobuf envelope compatible with the room/create/join/approval/reconnect/playback/ping messages used by Quantune. The `chat` / `chat_message` extension is implemented here and requires a corresponding Quantune client update before it appears in the app.

Protocol field layout is based on the published Metrolist Listen Together schema:
https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/proto/listentogether.proto

## Important limitations

- This is an independent relay, not an end-to-end encrypted media service. It relays metadata and playback state, not audio.
- The first deployment is a compatibility implementation. It must be tested with two Quantune clients before replacing the app's default relay.
- Cloudflare free-plan quotas apply. This service does not guarantee unlimited rooms or uptime.
