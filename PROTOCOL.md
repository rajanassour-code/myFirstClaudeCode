# Live Video Assist: wire protocol (for native SDKs)

Any client that speaks this protocol can call an operator and be co-browsed: the web pages, the iOS SDK, the Android SDK. The reference implementation in plain JavaScript is `public/native-sim.html` (customer side) and `public/cobrowse-native-viewer.js` (operator side).

## 1. Signaling (client ↔ server)

JSON messages. Two transports, same messages:

| Transport | How |
|---|---|
| **WebSocket** (preferred) | `wss://HOST/ws`. One JSON message per text frame |
| **HTTP long-polling** (fallback, e.g. cPanel shared hosting) | `POST /signal/open` → `{token}`<br>`GET /signal/poll?token=T` → JSON array of messages (waits up to 25 s, returns `[]` on timeout; HTTP **410** = session over)<br>`POST /signal/send` body `{token, msgs:[…]}`<br>`POST /signal/close` body `{token}` |

Client rule: try the WebSocket. If no server message arrives within **6 s** (or the socket errors or closes first), switch to polling. A poll session is dropped by the server if no poll arrives for 45 s.

`GET /config` → `{ iceServers: [...] }`: pass this to the WebRTC peer connection.

| Direction | Message |
|---|---|
| client → server | `{type:"hello", role:"customer", name, topic, ref}` |
| server → client | `{type:"ready", id, transport}` |
| customer → server | `{type:"request"}` join the queue · `{type:"cancel"}` leave it |
| server → customer | `{type:"waiting", position, agentsOnline}` |
| server → client | `{type:"matched", role:"callee", peerName}` (the customer is always **callee**) |
| both ways | `{type:"signal", data:{sdp:{type,sdp}}}` or `{type:"signal", data:{ice:{candidate,sdpMid,sdpMLineIndex}}}` |
| both ways | `{type:"hangup"}` · server → client `{type:"hangup", reason}` |

## 2. WebRTC

- The **operator creates the offer** and a data channel labelled **`chat`**. The customer answers and receives the channel through `ondatachannel`.
- The customer sends camera and microphone tracks. Audio and video are negotiated even if a side has no camera.
- ICE candidates are trickled through `signal` messages. Candidates that arrive before the remote description must be queued.

## 3. Data channel messages (JSON, UTF-8 text)

**Chunking:** any message longer than 16,000 characters is sent as consecutive pieces
`{kind:"chunk", id:"<random>", i:<index>, n:<count>, d:"<slice of the JSON text>"}`. The receiver joins the `d` strings in `i` order and parses the result. Senders should skip or delay a message while the channel's `bufferedAmount` exceeds about 4 MB.

### Call

| Message | Meaning |
|---|---|
| `{kind:"chat", text}` | chat message (max 2,000 chars) |
| `{kind:"share", on}` | operator → customer: operator started or stopped screen sharing (show the remote video uncropped) |
| `{kind:"caps?"}` | operator asks what the client supports |
| `{kind:"caps", cobrowse:true, native:true, platform:"ios"\|"android", v:2}` | customer → operator: send it when the channel opens **and** in reply to `caps?` |

### Native co-browse: operator → customer

| Message | Customer must… |
|---|---|
| `cb:request` | show the consent prompt; reply `cb:accepted` or `cb:declined` (if already active: reply `cb:accepted` and resend a frame) |
| `cb:stop` | stop capturing, remove all overlays (no reply) |
| `cb:pointer {nx, ny}` / `{hide:true}` | show or hide the advisor pointer at normalised coordinates |
| `cb:highlight {nx, ny}` | find the element there, draw a pulsing ring around it, reply `cb:highlighted {rect}` |
| `cb:control-request` | show the second consent prompt; reply `cb:control-granted` or `cb:control-denied` |
| `cb:control-release` | end control mode (no reply) |
| `cb:remote {action:"tap", nx, ny}` | see "Remote actions" below |
| `cb:remote {action:"set", id, value}` | set the text of field `id` (from a previous `cb:field`) |
| `cb:remote {action:"choose", id, index}` | choose option `index` of picker `id` (from a previous `cb:choices`) |
| `cb:remote {action:"scroll", nx, ny, dy}` | scroll the scrollable view under (nx, ny) by `dy` × screen height |

### Native co-browse: customer → operator

| Message | Meaning |
|---|---|
| `cb:accepted` / `cb:declined` / `cb:stopped` | consent result / customer stopped sharing |
| `cb:frame {w, h, jpg, title}` | the screen: `w`,`h` = screen size in **points** (dp on Android); `jpg` = base64 JPEG **without** the `data:` prefix, any resolution (≈ 360–540 px wide, quality ≈ 0.5–0.6 recommended). Send only when the screen changed, max ~10 per second |
| `cb:tap {nx, ny}` | the customer touched the screen (operator sees a blue ripple) |
| `cb:focus {rect}` / `{rect:null}` | the customer is editing a field / stopped editing |
| `cb:field {id, rect, value, placeholder, input}` | reply to a tap on an editable field. `input` = `text` \| `number` \| `decimal` \| `email` \| `phone` \| `multiline` |
| `cb:choices {id, rect, options:[…], selected}` | reply to a tap on a picker / spinner |
| `cb:highlighted {rect}` | reply to `cb:highlight` |
| `cb:applied {id?, rect?}` | a remote action was carried out |
| `cb:denied {action, reason}` | a remote action was refused (the operator sees `Blocked: <reason>`) |
| `cb:control-granted` / `cb:control-denied` / `cb:control-revoked` | control consent result / the customer took control back |

Coordinates: `nx, ny ∈ [0,1]` relative to the captured screen; `rect = [nx, ny, nw, nh]`.

### Remote actions (rules enforced ON THE PHONE)

On `tap`, hit-test the deepest visible view at (nx, ny), then:

1. **Refuse** (`cb:denied`) if control is not granted; if nothing is there; if the view is, or is inside, a **masked** or **no-remote** view; if it is a **secure** text field (password, PIN, one-time code, card number); or if it is a **button whose title or label suggests a commitment** (send, confirm, pay, submit, approve, save, delete, sign). That last rule is a safety net; developers should still mark such buttons as no-remote explicitly.
2. Editable text field → reply `cb:field` (do **not** focus it or show the keyboard on the phone).
3. Picker / spinner / single-choice control → reply `cb:choices`.
4. Switch / checkbox → toggle it, fire its normal value-changed events.
5. Any other tappable control (button, tab, cell, link) → activate it as a real tap would.
6. Then flash an orange outline around the view, show a short toast ("Filled in by your advisor" / "Advisor tapped “Cards”"), and reply `cb:applied {id, rect}`.

`set` and `choose` re-check the same rules for the target view.

### Masking

Applied to every frame **before** encoding, so secrets never leave the device:

- automatically: secure text entry, and fields with password / one-time-code / credit-card content types (iOS `textContentType`, Android `inputType` and autofill hints)
- explicitly: views the app marks as masked (UIKit `view.liveAssistMasked = true`, SwiftUI `.liveAssistMasked()`, Android `LiveAssist.mask(view)`, Compose `Modifier.liveAssistMasked()`)
- masked areas are painted solid dark with a 🔒
- the SDK's own overlays (floating call window, banner, pointer) are never captured
