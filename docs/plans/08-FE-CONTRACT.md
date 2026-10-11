# DHOPM — Đề xuất Hợp đồng Frontend ↔ Backend (FE Contact)

> **Bản chất tài liệu:** đây là **hợp đồng giao thức (protocol contract)** giữa **Backend (BE)** — project `JVNC` (`Tson-dev/JVNC`) — và **các client Frontend (FE)**. Đây là **ranh giới duy nhất** được phép vượt qua để FE lấy kết quả từ BE; FE **không** import `dhopm.*`, **không** phụ thuộc jar backend (D7 / F1 / F2).
>
> **Đối tượng đọc:** tác giả FE (Tâm — `2312441-sudo/javanc`), tác giả BE (Sơn), và người viết app G6.
>
> **Đọc kèm:** `DECISIONS.md` (D6, D7, D20, D27, D28, D34, D38, D41) · `plans/00-OVERALL-PLAN.md` §2 & §4.3 · `plans/05-BACKEND-CLIAPI-PLAN.md` (chiến lược) · `phases/P5-G5.md` (kế hoạch G5).

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-008 |
| **Version** | 0.1 (Draft — chờ chốt transport `D34`) |
| **Contract version** | `protocol 1` (mỗi request ghi `"v":1`) |
| **Trạng thái** | 🕔 Draft — chưa code; dùng làm **đặc tả bàn giao** cho FE |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Tạo mới: chốt danh sách client, ràng buộc, mô hình dữ liệu, bộ lệnh + input/output, mã lỗi, ví dụ, tiêu chí test |

---

## 1. Mục đích & phạm vi

- Định nghĩa **API contact** (bộ lệnh + schema JSON) để FE giao việc và lấy kết quả từ BE.
- Liệt kê **các client (FE)** tương tác với BE và vai trò từng client.
- Ghi rõ **ràng buộc** (bất biến hợp đồng) và **input/output** từng lệnh.
- **Không** mô tả cài đặt nội bộ thuật toán (không đổ interface Java ra ngoài).

**Ngoài phạm vi:** giao diện/UX của FE; chi tiết tối ưu V3/V4; quản lý tài nguyên cấp OS.

---

## 2. Vai trò & các client (FE) tương tác

### 2.1 Sơ đồ

```
┌─────────────── Các client (FE / tool) ────────────────┐
│  C1 JavaFX Visualizer (javanc)   C2 dhopm-app (G6)     │
│  C3 Automation/CLI + CI          C4 (nội bộ) brush      │
└───────────────┬────────────────────────────────────────┘
                │  JSON theo HỢP ĐỒNG NÀY (không share class Java)
                ▼
      ┌───────────────────────────────────────────┐
      │  Manager CLI/API  (dhopm-cli) — D6/D7      │  ← ĐIỂM VÀO DUY NHẤT
      │  VersionRegistry · JobManager ·            │
      │  ResultAggregator · SessionManager ·       │
      │  ParameterEcho · CommandDispatcher         │
      └───────────────┬───────────────────────────┘
                      │ chỉ dùng API dhopm-common (in-process)
                      ▼
      [ V1 ]  [ V2 ]  [ V3* ]  [ V4* ]     (* khi hoàn thiện)
```

### 2.2 Danh sách client

| ID | Client | Repo / vị trí | Vai trò | Có phải FE không |
|---|---|---|---|---|
| **C1** | **JavaFX Visualizer** (`javanc`) | `2312441-sudo/javanc` (clone `D:\JVNC\JVNC Tam\javanc`) | Chạy mining, hiển thị DHO-List/cây DFS/biểu đồ DO, mô phỏng stream | ✅ FE chính |
| **C2** | **`dhopm-app`** (debug/compare app) | module trong repo BE (G6) | Chọn ≥1 engine, so sánh, debug — nhưng nằm **cùng repo** BE | ⚠️ client nội bộ (vẫn dùng hợp đồng này) |
| **C3** | **Automation/CLI + CI** | `dhopm-bench`, script benchmark, GitHub Actions | Chạy lô, thu CSV/JSON, test hồi quy | ❌ tool (client hợp đồng) |
| **C4** | `TamSimulationBridge` (FE) | trong `javanc` | Engine demo nội bộ để animation step-by-step | ❌ **không** phải client BE — giữ nguyên |

> **Quy tắc chốt:** C1/C2/C3 nói chuyện với BE **chỉ** qua hợp đồng này. `TamSimulationBridge` (C4) là demo nội bộ, **không** gọi BE — giữ nguyên.

---

## 3. Ràng buộc hợp đồng (bắt buộc)

| # | Ràng buộc | Nguồn |
|---|---|---|
| R1 | FE **không** import `dhopm.*`, **không** Maven-dependency vào module BE, **không** biết `TsonV1Bridge`/`MiningEngine` | D7 / F1 |
| R2 | Giao tiếp **chỉ** qua Manager; hợp đồng = **đặc tả này**, không share class Java | D7 / F2 |
| R3 | **Mọi request khai phá phải khai báo tường minh `partial` (∂), `f`, `minOcc`**; thiếu ⇒ `MISSING_PARAMETER`. Manager **không** lấy mặc định thư viện thay FE | D28 / D41 |
| R4 | Giá trị số thực truyền **double đầy đủ** (không làm tròn khi truyền; chỉ làm tròn khi hiển thị) | C4 |
| R5 | Pattern là **tập item**; so sánh/hiển thị theo `canonicalKey` (item sắp tự nhiên, nối `,`) | C6 |
| R6 | `minSup = ∂ × N_eff`, `N_eff = min(TL, W)` — **2 pha**; response trả `minSup` **đã dùng** | C9 / D11 |
| R7 | Danh tính kết quả = `(∂, f, minOcc, TL)`. **So sánh nhiều version bắt buộc cùng bộ tham số**; khác ⇒ `MISMATCHED_WINDOW_CONFIG`, **không gộp** | C12 / D20 |
| R8 | `window.size = null` ⇔ `W = ∞` (chế độ `minOcc = 0` hoặc `f = 1`) | D10 |
| R9 | **Không** quyết định `D45`/transport ở tầng FE: nếu transport đổi, **chỉ** đổi kênh, không đổi schema | kế hoạch G5 |
| R10 | Protocol **chỉ thêm** field/cmd khi nâng version; **không bỏ** field cũ (backward compatible) | plan `05` §12 |
| R11 | Kết quả phải **deterministic**: cùng request ⇒ cùng kết quả (bất kể worker) | INV-E / C5 |
| R12 | Manager là **client của API `dhopm-common`**; hot path thuật toán **không đổi** | D6 / P5 |

---

## 4. Mô hình dữ liệu chung (wire model)

Tất cả số thực là JSON number (double). TID là số nguyên dương tăng nghiêm ngặt (INV-A).

### 4.1 Transaction

```jsonc
{ "tid": 7, "items": ["A","E"] }
```

- `items`: mảng chuỗi; trùng → chuẩn hoá về tập phân biệt; rỗng/độ dài 0 → **loại từ khi nhập**.

### 4.2 Config (tham số khai phá)

| Field | Kiểu | Miền | Ghi chú |
|---|---|---|---|
| `partial` | number | `[0,1]` | `∂` — tỉ lệ ngưỡng trên `N_eff` |
| `f` | number | `(0,1]` | hệ số suy giảm |
| `minOcc` | number | `[0,1)` | ngưỡng cửa sổ; `0` ⇒ paper |
| `epsilon` | number | `>0` | sai số so sánh, mặc định `1e-9` |
| `workers` | int | `≥1` | mặc định = số CPU (D4) |

### 4.3 Pattern (kết quả)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `items` | string[] | thứ tự theo `canonicalKey` khi hiển thị |
| `key` | string | `canonicalKey` = items sắp tự nhiên nối `,` |
| `do` | number | `dampedOccupancy` — giá trị DO |
| `support` | int | = `tids.length` (số transaction **còn sống** chứa X) |
| `tids` | int[] | TID tăng dần (có thể rỗng nếu không cần) |

### 4.4 WindowInfo (trạng thái cửa sổ)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `size` | long \| null | `W(f,minOcc)`; **`null` = ∞** |
| `effective` | long | `N_eff = min(TL, W)` |
| `minSup` | number | ngưỡng hiệu dụng tại `mineNow` |
| `maxDO` | number | `Z(f,TL)` — trần DO |
| `evictions` | long | tổng số transaction bị evict (tích luỹ) |
| `liveEntries` | long | tổng entry sống tại thời điểm mine |
| `deadEntries` | long | tổng entry chết (prefix) |

### 4.5 Issue (cảnh báo/lỗi cấu hình)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `severity` | `"ERROR"` \| `"WARNING"` | |
| `code` | string | **mã ổn định** (mục 7) |
| `message` | string | thông điệp **có số liệu** (để hiển thị) |

### 4.6 Envelope response

```jsonc
{ "id": 1, "v": 1, "ok": true,  ... }                       // thành công
{ "id": 1, "v": 1, "ok": false, "code": "...", "message": "..." }  // lỗi
```

---

## 5. Bộ lệnh & input/output

Bộ lệnh = **đúng bộ lệnh chuẩn D27** + quản trị + phiên. Tất cả nhận `(∂, f, minOcc)` **bắt buộc** (trừ `info`/`version`/`window`/`validate` khi không có dataset — xem ghi chú).

### 5.1 Bảng tổng

| Lệnh | Mục đích | Bắt buộc | Trả về |
|---|---|---|---|
| `mine` | chạy khai phá, trả tập DOP | `(∂,f,minOcc)` + (`dataset` hoặc `session`) | `result[]` + `window` + `jobs[]` |
| `detail` | debug chi tiết (node/entry/roots, top-N theo DO) | như `mine` | `mine` + `stats` |
| `stream` | log thời gian thực (load + progress ticks) | như `mine` | dòng `progress` rồi `result` |
| `golden` | chạy TestKit TC1–TC18 | — | `cases[]` (pass/fail) |
| `inspect` | thống kê dataset/config, **không** mining | `dataset` | `dataset` + `config` |
| `window` | tra cứu cửa sổ (**không** cần dataset) | `f`(,`minOcc`) | `window`, `maxDOAsymptotic`, `maxPartialAsymptotic` |
| `validate` | kiểm tra cấu hình trước khi chạy | `(∂,f,minOcc)` | `issues[]` |
| `sweep` | quét tổ hợp `(∂,f,minOcc)` | `dataset` + danh sách | bảng `rows[]` |
| `info` | manifest **tĩnh** của Manager | — | `commands[]`, `versions[]`, `protocol` |
| `version` | phiên bản Manager/JDK | — | `manager`, `jdk`, `os` |
| `session.create` | tạo phiên stream | `(∂,f,minOcc)` | `session` |
| `feed` | nạp transaction vào phiên | `session` + `transactions[]` | `loaded`, `lastTid`, `window` |
| `session.mine` | mine trên phiên | `session` (+ `versions`) | như `mine` |

### 5.2 `mine` — input

```jsonc
{
  "id": 1, "v": 1, "cmd": "mine",
  "dataset": "retail.dat", "format": "fimi",
  "partial": 0.06, "f": 0.9, "minOcc": 1e-6, "epsilon": 1e-9,
  "workers": 8, "parts": 5, "limit": 0,
  "versions": ["v1","v2"]
}
```

| Field | Ý nghĩa |
|---|---|
| `dataset` + `format` | nguồn dữ liệu (`fimi` \| `text`); hoặc dùng `session` thay thế |
| `partial`/`f`/`minOcc`/`epsilon` | bắt buộc (R3) |
| `workers` | số worker (tùy chọn) |
| `parts` | chia dataset thành N phần incremental (tùy chọn) |
| `limit` | đọc tối đa N transaction đầu (0 = hết) |
| `versions` | danh sách version cần chạy (≥1) |

### 5.3 `mine` — output

```jsonc
{
  "id": 1, "v": 1, "ok": true,
  "config": {"partial":0.06,"f":0.9,"minOcc":1e-6,"epsilon":1e-9,"workers":8},
  "totalTransactions": 88162, "lastTid": 88162,
  "minSup": 9.18, "maxDO": 10.0,
  "window": {"size":153,"effective":153,"minSup":9.18,"maxDO":10.0,
             "evictions":87981,"liveEntries":12045,"deadEntries":0},
  "issues": [ {"severity":"WARNING","code":"INFEASIBLE_PARTIAL","message":"..."} ],
  "jobs": [
    {"version":"v1","engine":"v1-standard","total_ms":91234,"patterns":0,
     "patterns_list":[]},
    {"version":"v2","engine":"v2-minocc","total_ms":1800,"patterns":0,
     "patterns_list":[]}
  ],
  "perVersion": {
    "v1": {"patterns":[],"window":{"size":null,"effective":88162,"minSup":5289.72,"maxDO":10.0}},
    "v2": {"patterns":[],"window":{"size":153,"effective":153,"minSup":9.18,"maxDO":10.0}}
  }
}
```

- **Một version** ⇒ có thể đặt `patterns` trực tiếp; **nhiều version** ⇒ dùng `perVersion` + `jobs`.
- Mỗi phần tử `patterns_list` (hoặc `patterns`) theo mô hình 4.3, **`do` double đầy đủ**.
- `window` ở mức top = cửa sổ của job **đầu tiên** (tiện hiển thị); số liệu theo từng version nằm trong `perVersion`.

### 5.4 `detail` — bổ sung

```jsonc
{"id":2,"v":1,"ok":true,"...":"như mine","stats":{
  "nodes":[{"item":"F","support":7,"do":1.2553,"head":0,"entries":7}],
  "roots":6,"globalEntries":120,"topN":5, "phaseMs":{"g0":1,"g1":3,"g2":4,"g3":9}
}}
```

### 5.5 `stream` — progress rồi result

```jsonc
{"id":3,"v":1,"event":"progress","version":"v2","phase":"MINING",
 "done":3,"total":6,"patterns":12,"elapsedMs":420}
{"id":3,"v":1,"event":"window","version":"v2",
 "window":{"size":153,"effective":153,"evictions":20,"liveEntries":900,"deadEntries":10}}
{"id":3,"v":1,"ok":true,"jobs":[{"version":"v2","total_ms":1800,"patterns":42}],
 "patterns":[{"items":["A","E"],"key":"A,E","do":1.2601,"support":3,"tids":[2,4,7]}]}
```

### 5.6 `window` (không cần dataset)

```jsonc
{"id":6,"v":1,"cmd":"window","f":0.9,"minOcc":1e-6}
```
```jsonc
{"id":6,"v":1,"ok":true,
 "window":{"size":153},
 "maxDOAsymptotic":10.0,
 "maxPartialAsymptotic":0.0654,
 "maxDOExact":null, "maxPartialExact":null}
```

> ⚠️ **Bắt buộc ghi hậu tố** (D38): không có `TL` ⇒ **chỉ** trả `…Asymptotic`. Khi request **có** `TL` (hoặc gọi sau khi nạp xong) ⇒ Manager **phải** trả thêm `maxDOExact`, `maxPartialExact` bằng công thức chính xác `Z(f,TL)/N_eff`. **Validator chặn tham số dùng giá trị chính xác**, không dùng xấp xỉ.

### 5.7 `validate`

```jsonc
{"id":5,"v":1,"cmd":"validate","partial":1.0,"f":0.9,"minOcc":1e-3}
```
```jsonc
{"id":5,"v":1,"ok":true,
 "issues":[{"severity":"WARNING","code":"INFEASIBLE_PARTIAL",
   "message":"minSup = ∂×N_eff = 88.0 > Z(f,TL) = 10.0 ⇒ the DOP set is provably EMPTY; reduce ∂ or increase f (exact feasible bound: ∂ ≤ 0.1136)"}],
 "maxPartialExact":0.1136,"maxPartialAsymptotic":0.1136}
```

### 5.8 `sweep`

```jsonc
{"id":7,"v":1,"cmd":"sweep","dataset":"mushroom.dat","f":0.9,
 "partials":[0.02,0.03,0.06],"minOccs":[1e-6],"versions":["v2"]}
```
```jsonc
{"id":7,"v":1,"ok":true,"rows":[
 {"partial":0.02,"f":0.9,"minOcc":1e-6,"minSup":3.06,"patterns":12,"total_ms":35,"issue":null},
 {"partial":0.06,"f":0.9,"minOcc":1e-6,"minSup":9.18,"patterns":0,"total_ms":1200,"issue":"INFEASIBLE_PARTIAL"}
]}
```

### 5.9 Quản trị: `info` / `version`

```jsonc
{"id":8,"v":1,"cmd":"info"}
```
```jsonc
{"id":8,"v":1,"ok":true,
 "protocol":{"current":1,"supported":[1]},
 "manager":{"name":"dhopm-cli","maxWindow":10000000},
 "commands":["mine","detail","stream","golden","inspect","window","validate","sweep","info","version","session.create","feed","session.mine"],
 "versions":[
   {"id":"v1","engine":"v1-standard","minOccDefault":0,"status":"ready"},
   {"id":"v2","engine":"v2-minocc","minOccDefault":1e-6,"status":"ready"},
   {"id":"v3","engine":"v3-optimized","status":"unavailable"},
   {"id":"v4","engine":"v4-extreme","status":"unavailable"}],
 "formats":["fimi","text","zip"]}
```

> `info` trả **manifest tĩnh**; **không** dò trạng thái runtime nội bộ engine (P1). Version chưa hoàn thiện ⇒ `status:"unavailable"` (không crash).

### 5.10 Phiên: `session.create` → `feed` → `session.mine`

```jsonc
{"id":9,"v":1,"cmd":"session.create","config":{"partial":0.15,"f":0.9,"minOcc":0}}
→ {"id":9,"v":1,"ok":true,"session":"s1"}
{"id":10,"v":1,"cmd":"feed","session":"s1","transactions":[{"tid":1,"items":["A","E"]}]}
→ {"id":10,"v":1,"ok":true,"loaded":1,"lastTid":1,"window":{"size":null,"effective":1}}
{"id":11,"v":1,"cmd":"session.mine","session":"s1","versions":["v2"],"minOcc":1e-6}
→ {"id":11,"v":1,"ok":true,"jobs":[{"version":"v2","total_ms":3,"patterns":1}],
   "patterns":[{"items":["A","E"],"key":"A,E","do":1.0,"support":1,"tids":[1]}]}
```

---

## 6. Giao thức vận chuyển & phiên

| Chế độ | Cách dùng | Khi nào | Trạng thái |
|---|---|---|---|
| **One-shot** | `Manager <command> --json`, đọc/ghi **1 dòng JSON** qua **stdio** | FE gọi 1 lần, không giữ trạng thái; test/automation | ✅ mặc định |
| **Serve (JSONL)** | tiến trình `serve`; mỗi dòng = 1 request/response; `id` đồng bộ | Nhiều request liên tiếp, phiên stream | ✅ mặc định (stdio) |
| ~~Daemon TCP~~ | mở cổng local | (chỉ khi `D34` = FE chạy **máy khác**) | ⛔ đã bác bỏ ở `DECISIONS.md` §6; **xét lại nếu D34 đổi** |

**Nguyên tắc kênh:**
- **`id`** là khoá đồng bộ giữa request → progress → response (bắt buộc trong `serve`).
- **Progress/window event** là dòng JSON có `event`; FE tự tiêu thụ, có thể bỏ qua.
- Một **session** xử lý **tuần tự** (khoá); nhiều session chạy song song.
- Không auth (LAN nội bộ — rủi ro chấp nhận, ghi rõ nếu mở TCP).

---

## 7. Mã lỗi & cảnh báo (ổn định)

### 7.1 Lỗi request (làm request thất bại)

| Code | Nghĩa |
|---|---|
| `UNKNOWN_COMMAND` | `cmd` không thuộc bộ lệnh |
| `UNKNOWN_VERSION` | `versions[]` chứa version không tồn tại / chưa hỗ trợ |
| `MISSING_PARAMETER` | thiếu `partial`/`f`/`minOcc` (hoặc `dataset`/`session` khi cần) |
| `INVALID_PARAMETER` | giá trị ngoài miền (`∂∉[0,1]`, `f∉(0,1]`, `minOcc∉[0,1)`, `ε≤0`, `workers<1`) |
| `MIN_OCC_TOO_LARGE` | `minOcc ≥ 1/(1−f)` ⇒ `W=0` (lỗi cấu hình, D30) |
| `WINDOW_TOO_LARGE` | `W > maxWindow` (10⁷) — không cấp phát nổi |
| `MISMATCHED_WINDOW_CONFIG` | so nhiều version với `(∂,f,minOcc)` khác nhau (R7) |
| `DATASET_NOT_FOUND` | file dataset không tồn tại |
| `UNSUPPORTED_FORMAT` | format/extension ngoài `fimi`/`text`/`.zip` (D43) |
| `SESSION_NOT_FOUND` | `session` không tồn tại |
| `NOT_IMPLEMENTED` | version/feature chưa có (vd V3/V4 khi chưa xong) |
| `INTERNAL_ERROR` | lỗi không lường trước (kèm `message`) |

### 7.2 Cảnh báo (request vẫn `ok`, kèm `issues[]`)

| Code | Nghĩa |
|---|---|
| `PARTIAL_ZERO` | `∂=0` ⇒ mọi pattern là DOP ⇒ output bùng nổ (D31) |
| `INFEASIBLE_PARTIAL` | `∂×N_eff > Z(f,TL)` ⇒ kết quả **chắc chắn rỗng**; trả ∅ O(1) + cảnh báo có số liệu (D19/D38) |

---

## 8. Tích hợp phía FE (BackendClient)

FE chỉ cần **một** lớp client thay cho `TsonV1Bridge`:

```
FE (javanc)                          BE (JVNC)
────────────                         ─────────────
interface BridgeEngine  ◄── giữ nguyên DIP
   ▲
BackendClient implements BridgeEngine
   │  serialize request theo hợp đồng này
   │  (chạy Manager one-shot hoặc gửi JSONL tới serve)
   └──────────────────────────────► dhopm-cli (Manager) ──► engines
```

| Việc | FE phải làm |
|---|---|
| Thay `TsonV1Bridge` | bằng `BackendClient` (gửi JSON theo hợp đồng) |
| Giữ `BridgeEngine` | interface DIP giữ nguyên — không đổi kiến trúc FE |
| Giữ `TamSimulationBridge` | engine demo animation nội bộ — không đụng |
| Bỏ Maven-dependency | **không** import `dhopm.*`, không cần `mvn install` jar backend |
| Hiển thị cửa sổ | đọc `window` (`size=null` ⇒ chế độ paper, vẽ nhãn "W=∞") |
| Hiển thị cảnh báo | đọc `issues[]` + mã lỗi, hiển thị `message` có số liệu |

---

## 9. Ví dụ end-to-end (chạy G2 threshold mushroom)

```jsonc
// 1) FE hỏi cấu hình trước (không cần dataset)
{"id":1,"v":1,"cmd":"window","f":0.9,"minOcc":1e-6}
// ← {"id":1,"v":1,"ok":true,"window":{"size":153},"maxPartialAsymptotic":0.0654}

// 2) FE chạy đối chiếu V1 vs V2 cùng (∂,f,minOcc)
{"id":2,"v":1,"cmd":"mine","dataset":"mushroom.dat","format":"fimi",
 "partial":0.06,"f":0.9,"minOcc":1e-6,"versions":["v1","v2"]}
// ← {"id":2,"v":1,"ok":true,
//     "jobs":[{"version":"v1","total_ms":301000,"patterns":0},
//             {"version":"v2","total_ms":4,"patterns":0}],
//     "issues":[{"severity":"WARNING","code":"INFEASIBLE_PARTIAL",
//                "message":"minSup = 9.18 > Z = 10 ... "}]}
//   ⇒ FE hiển thị: V2 trả ∅ tức thì; V1 chạy 301 s mới ra ∅ ⇒ thấy giá trị cửa sổ.
```

---

## 10. Tiêu chí chấp nhận (test hợp đồng)

- [ ] Schema mỗi lệnh có **request/response JSON hợp lệ**; `id` khớp.
- [ ] `mine --json` cho **cùng kết quả** với CLI văn bản cũ (so `inspect`), cho V1 & V2.
- [ ] `window.size` khớp `WindowMathTest` + bảng `plans/00` §2.3.1.
- [ ] Thiếu `(∂,f,minOcc)` ⇒ `MISSING_PARAMETER`; `minOcc ≥ 1/(1−f)` ⇒ `MIN_OCC_TOO_LARGE`; multi-version lệch tham số ⇒ `MISMATCHED_WINDOW_CONFIG`.
- [ ] `window` **không có `TL`** ⇒ chỉ `…Asymptotic` + `…Exact=null`; có `TL` ⇒ trả `…Exact` (D38).
- [ ] `versions:["v3"]` (chưa xong) ⇒ `UNKNOWN_VERSION`/`NOT_IMPLEMENTED`, **không crash**.
- [ ] Stream lớn có `progress` event, không đóng băng.
- [ ] FE (`javanc`) chạy **chỉ** với `BackendClient`; `grep -R "dhopm."` trong FE = 0 kết quả.
- [ ] Cùng request + khác `workers` ⇒ kết quả **bit-for-bit** (R11).

---

## 11. Phụ lục — Ánh xạ lệnh ↔ API `dhopm-common`

| Lệnh | Dùng API |
|---|---|
| `mine`/`detail`/`session.mine` | `Engine.loadBatch` + `Engine.mineNow` → `MineResult`, `Pattern` |
| `stream` progress | `ProgressAwareEngine` + `MiningProgressListener` (`MiningProgress`) |
| `stream` window / `window` | `WindowAwareEngine` + `WindowListener` (`WindowInfo`), `WindowMath` |
| `validate` | `ParameterValidator.validate(config[, tl])` (`Issue`, `Severity`) |
| `inspect` | `TransactionSource` / `FimiTransactionReader` / `TextTransactionReader` / `ZipDataset` |
| `golden` | `GoldenRunner` + `GoldenCases` (TestKit) |
| `mine` timing | `TimedEngine` / `TimingRecorder`, `PhaseAwareEngine` (`Phase`) |
| `mine` workers | `MiningConfig.workers` + `WorkerPool` |

---

*Kết thúc hợp đồng FE. Thay đổi hợp đồng phải: (1) tăng `contract version`, (2) chỉ **thêm** field/cmd, (3) ghi quyết định vào `DECISIONS.md`. Ranh giới bắt buộc: FE **không** import `dhopm.*`.*
