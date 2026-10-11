# DHOPM — Hợp đồng Frontend ↔ Backend (FE Contact)

> **Bản chất tài liệu:** đây là **hợp đồng giao thức (protocol contract)** giữa **Backend (BE)** — project `JVNC` (`Tson-dev/JVNC`) — và **các client Frontend (FE)**. Đây là **ranh giới duy nhất** được phép vượt qua để FE lấy kết quả từ BE; FE **không** import `dhopm.*`, **không** phụ thuộc jar backend (D7 / F1 / F2).
>
> **Đối tượng đọc:** tác giả FE (Tâm — `2312441-sudo/javanc`), tác giả BE (Sơn), và người viết app G6.
>
> **Đọc kèm:** `DECISIONS.md` (D6, D7, D20, D27, D28, D38, D41, **D45–D49**) · `plans/00-OVERALL-PLAN.md` §2 & §4.3 · `plans/05-BACKEND-CLIAPI-PLAN.md` · `phases/P5-G5.md` · `plans/07-G5-PIVOT-ANALYSIS.md` · `plans/Draft FE Contact.txt` · `plans/Draft mining comment.txt`.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-008 |
| **Version** | 0.2 (Draft) |
| **Contract version** | `protocol 1` (mỗi request ghi `"v":1`) |
| **Trạng thái** | 🕔 Draft — các quyết định transport/CLI/lưu-file/config đã chốt (D45–D49); chờ code |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Tạo mới: danh sách client, ràng buộc, mô hình dữ liệu, bộ lệnh, mã lỗi. Transport mặc định **stdio**, TCP để ngỏ |
| **0.2** | Chốt theo **D45–D49**: **transport TCP `localhost:7079`** (BE `serve` luôn mở cổng, FE dò localhost→LAN + `connect`/`hello`) + giữ stdio; **CLI Hybrid** (`<dataset> <partial> <factor> <limit>` + `--minOcc` bắt buộc); `stream --step` + `--detail 0/1/2`; **lưu kết quả `isLog`/`--export`**, file `mine_<yy-MM-dd>_<NNN>.txt` (NNN reset ngày), lệnh **`fetch`**, **FE bắt buộc mirror**; **config 3 cấp** `default < JSON < args` |

---

## 1. Mục đích & phạm vi

- Định nghĩa **bộ lệnh + schema JSON** để FE giao việc và lấy kết quả từ BE.
- Liệt kê **các client (FE)**, **ràng buộc**, **mô hình dữ liệu**, **transport**, **lưu/đọc kết quả**, **mã lỗi**.
- **Không** mô tả cài đặt nội bộ thuật toán (không đổ interface Java ra ngoài).

**Ngoài phạm vi:** giao diện/UX của FE; chi tiết tối ưu V4; quản lý tài nguyên cấp OS.

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
   ┌────────────────────────────────────────────┐
   │  Manager CLI/API  (dhopm-cli) — D6/D7       │  ← ĐIỂM VÀO DUY NHẤT
   │  CommandDispatcher · VersionRegistry ·      │
   │  JobManager · ResultAggregator ·            │
   │  SessionManager · ParameterEcho ·           │
   │  ResultStore                                │
   └───────┬───────────────────────────┬─────────┘
           │ stdio JSONL (test/CI)     │ TCP :7079 (FE runtime) + stdio
           ▼                           ▼
   [ V1 ] [ V2 ] [ V3 ] [ V4* ]     BE mở cổng chờ `connect` từ FE
```

### 2.2 Danh sách client

| ID | Client | Repo / vị trí | Vai trò | Có phải FE không |
|---|---|---|---|---|
| **C1** | **JavaFX Visualizer** (`javanc`) | `2312441-sudo/javanc` | Chạy mining, hiển thị DHO-List/cây DFS/biểu đồ DO, mô phỏng stream | ✅ FE chính |
| **C2** | **`dhopm-app`** (debug/compare app) | module trong repo BE (G6) | Chọn ≥1 engine, so sánh, debug | ⚠️ client nội bộ (vẫn dùng hợp đồng này) |
| **C3** | **Automation/CLI + CI** | `dhopm-bench`, script, GitHub Actions | Chạy lô, thu CSV/JSON, test hồi quy | ❌ tool (dùng **stdio**) |
| **C4** | `TamSimulationBridge` (FE) | trong `javanc` | Engine demo nội bộ để animation step-by-step | ❌ **không** phải client BE — giữ nguyên |

> **Quy tắc chốt:** C1/C2/C3 nói chuyện với BE **chỉ** qua hợp đồng này. `TamSimulationBridge` (C4) là demo nội bộ, **không** gọi BE.

---

## 3. Ràng buộc hợp đồng (bắt buộc)

| # | Ràng buộc | Nguồn |
|---|---|---|
| R1 | FE **không** import `dhopm.*`, **không** Maven-dependency vào module BE, **không** biết `TsonV1Bridge`/`MiningEngine` | D7 / F1 |
| R2 | Giao tiếp **chỉ** qua Manager; hợp đồng = **đặc tả này**, không share class Java | D7 / F2 |
| R3 | **Mọi request khai phá phải khai báo tường minh `partial` (∂), `f`, `minOcc`**; thiếu ⇒ `MISSING_PARAMETER`. Manager **không** lấy mặc định thư viện thay FE | D28 / D41 |
| R4 | Giá trị số thực truyền **double đầy đủ** (không làm tròn khi truyền) | C4 |
| R5 | Pattern là **tập item**; so sánh/hiển thị theo `canonicalKey` (item sắp tự nhiên, nối `,`) | C6 |
| R6 | `minSup = ∂ × N_eff`, `N_eff = min(TL, W)` — **2 pha**; response trả `minSup` **đã dùng** | C9 / D11 |
| R7 | Danh tính kết quả = `(∂, f, minOcc, TL)`. **So sánh nhiều version bắt buộc cùng bộ tham số**; khác ⇒ `MISMATCHED_WINDOW_CONFIG`, **không gộp** | C12 / D20 |
| R8 | `window.size = null` ⇔ `W = ∞` (chế độ `minOcc = 0` hoặc `f = 1`) | D10 |
| R9 | **Transport chuẩn = TCP `localhost:7079`** (FE runtime) + **stdio JSONL** (test/CI). Đổi kênh **không** đổi schema | D46 |
| R10 | Protocol **chỉ thêm** field/cmd khi nâng version; **không bỏ** field cũ (backward compatible) | plan `05` §12 |
| R11 | Kết quả phải **deterministic**: cùng request ⇒ cùng kết quả (bất kể worker) | INV-E / C5 |
| R12 | Manager là **client của API `dhopm-common`**; hot path thuật toán **không đổi** | D6 / P5 |
| R13 | Mọi lệnh mining **luôn trả danh sách DOPs** (chỉ khác cách trình bày) | D47 |
| R14 | Khi **`isLog` = true** (hoặc có `--export`), BE ghi file kết quả và FE **phải lưu bản sao cục bộ** để không phụ thuộc BE | D48 |

---

## 4. Mô hình dữ liệu chung (wire model)

### 4.1 Transaction

```jsonc
{ "tid": 7, "items": ["A","E"] }
```

- `items`: mảng chuỗi; trùng → chuẩn hoá về tập phân biệt; rỗng → **loại từ khi nhập**.

### 4.2 Config (tham số khai phá — bắt buộc)

| Field | Kiểu | Miền | Ghi chú |
|---|---|---|---|
| `partial` | number | `[0,1]` | `∂` |
| `f` | number | `(0,1]` | hệ số suy giảm |
| `minOcc` | number | `[0,1)` | ngưỡng cửa sổ; `0` ⇒ paper. **Bắt buộc** |
| `epsilon` | number | `>0` | mặc định `1e-9` |
| `workers` | int | `≥1` | mặc định = số CPU (D4) |

### 4.3 Pattern (kết quả)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `items` | string[] | thứ tự theo `canonicalKey` khi hiển thị |
| `key` | string | `canonicalKey` |
| `do` | number | `dampedOccupancy` |
| `support` | int | `tids.length` (số transaction **còn sống** chứa X) |
| `tids` | int[] | TID tăng dần (có thể rỗng) |

### 4.4 WindowInfo

| Field | Kiểu | Ghi chú |
|---|---|---|
| `size` | long \| null | `W(f,minOcc)`; **`null` = ∞** |
| `effective` | long | `N_eff = min(TL, W)` |
| `minSup` | number | ngưỡng hiệu dụng tại `mineNow` |
| `maxDO` | number | `Z(f,TL)` |
| `evictions` | long | tổng evict (tích luỹ) |
| `liveEntries` | long | tổng entry sống |
| `deadEntries` | long | tổng entry chết |

### 4.5 Issue (cảnh báo/lỗi cấu hình)

```jsonc
{ "severity":"WARNING", "code":"INFEASIBLE_PARTIAL", "message":"..." }
```

### 4.6 SavedFile (kết quả lưu)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `fileID` | string | `<yy-MM-dd>_<NNN>`, ví dụ `26-10-11_001` |
| `path` | string | đường dẫn đầy đủ, ví dụ `/mine/mine_26-10-11_001.txt` |

### 4.7 Envelope response

```jsonc
{ "id": 1, "v": 1, "ok": true,  ... }                              // thành công
{ "id": 1, "v": 1, "ok": false, "code": "...", "message": "..." }  // lỗi
```

---

## 5. Transport & mô hình vận hành

### 5.1 Hai kênh

| Kênh | Cách dùng | Khi nào | Trạng thái |
|---|---|---|---|
| **TCP `localhost:7079`** | BE chạy `serve`, **luôn mở cổng chờ**; mỗi kết nối = JSONL | FE runtime (C1), app (C2) — kể cả **khác máy** | ✅ **chuẩn (D46)** |
| **stdio JSONL** | `Manager <command> --json` hoặc tiến trình `serve` over stdio | test/automation/CI (C3) | ✅ giữ nguyên |

### 5.2 Luồng khám phá & kết nối (FE → BE)

```
[FE] bấm nút "Detect BE"
   │
   ├─(1) thử connect 127.0.0.1:7079  ──thành công──┐
   │                                                │
   ├─(2) nếu (1) thất bại → quét LAN tìm cổng 7079 ─┤
   │                                                ▼
   │                     gửi {"cmd":"hello","v":1} tới địa chỉ tìm được
   │                                                │
   │   ┌── phản hồi hợp lệ (protocol khớp) ─────────┴─→ lưu địa chỉ, chính thức kết nối
   │   │
   │   └── không phản hồi / sai Contract → coi như "không tìm thấy"
   │
   └─ nếu cả localhost và LAN đều không có → FE hiển thị "Không tìm thấy BE"
```

- BE **chủ động mở** cổng 7079 khi chạy `serve` (ràng buộc phía BE — D46), **không** chờ FE khởi động.
- FE/BE **khởi động độc lập**; địa chỉ BE được **FE lưu lại** sau khi handshake thành công.

### 5.3 Handshake `hello`

```jsonc
// FE → BE
{"id":1,"v":1,"cmd":"hello"}
// BE → FE
{"id":1,"v":1,"ok":true,"protocol":1,"manager":"dhopm-cli",
 "versions":[{"id":"v1","status":"ready"},{"id":"v2","status":"ready"},
             {"id":"v3","status":"ready"},{"id":"v4","status":"pending"}],
 "commands":["mine","detail","stream","window","validate","inspect","golden",
             "sweep","info","version","config","fetch","session.create","feed","session.mine"]}
```

- Nếu `hello.protocol` trả về khác ⇒ BE/FE **không tương thích** ⇒ FE coi như **không tìm thấy**.

### 5.4 Quy tắc kênh

- **`id`** đồng bộ request → progress/event → response (bắt buộc trong `serve`/TCP).
- **Progress/window/step event** là dòng JSON có `event`; client có thể bỏ qua.
- Một **session** xử lý **tuần tự**; nhiều session song song.
- **Không auth** ở `protocol 1` (LAN nội bộ — rủi ro chấp nhận; ghi rõ nếu mở rộng).

---

## 6. Cú pháp CLI & bộ lệnh (Hybrid — D47)

### 6.1 Cú pháp chung

```
<cmd> <dataset> <partial> <factor> <limit> [flags]     ← lệnh khai phá
```

- **Positional bắt buộc (lệnh khai phá):** `<dataset> <partial> <factor> <limit>` (`limit = 0` nghĩa là đọc hết).
- **Flag bắt buộc:** `--minOcc <x>` (D28/D41).
- **Flag tùy chọn:** `--workers <n>` · `--format fimi|text|zip` · `--parts <n>` · `--export [<path>]` · `--detail 0|1|2` · `--step`.
- JSON (`serve`/TCP) dùng khoá tương ứng (`dataset/partial/f/minOcc/limit/...`), positional chỉ là hình thức CLI.

### 6.2 Bảng lệnh

| Nhóm | Lệnh | Cú pháp rút gọn | Trả về |
|---|---|---|---|
| Khai phá | `mine` | `mine <dataset> <∂> <f> <limit> --minOcc <x>` | `patterns[]` + `window` |
| Khai phá | `detail` | như `mine` | `mine` + `stats` (window/roots/entries) |
| Khai phá | `stream` | như `mine` `[--step] [--detail 0/1/2]` | dòng `event` rồi kết quả |
| Kiểm tra | `inspect` | `inspect <dataset>` | thống kê dataset (không mining) |
| Kiểm tra | `golden` | `golden` | `cases[]` (TC1–TC18) |
| Cửa sổ | `window` | `window <f> --minOcc <x> [--tl <long>]` | `window`, `maxDO*`, `maxPartial*` |
| Cửa sổ | `validate` | `validate <∂> <f> --minOcc <x> [--tl <long>]` | `issues[]` |
| Cửa sổ | `sweep` | `sweep <dataset> --f <f> --partials <list> --minOccs <list> [--versions <list>]` | `rows[]` |
| Quản trị | `info` / `version` | `info` / `version` | manifest tĩnh / phiên bản |
| Quản trị | `config` | `config get <k>` · `config set <k> <v>` · `config list` | giá trị config |
| File | `fetch` | `fetch <fileID>` | nội dung file |
| Phiên | `session.create` / `feed` / `mine` | (JSON) | session + kết quả |
| Vận hành | `serve` | `serve` | mở cổng 7079 (TCP) + stdio |

### 6.3 Kết quả mining luôn có DOPs (D47)

- `mine` trả **danh sách DOPs** (kèm `window`); **không** chỉ in số.
- `detail` = `mine` + thông tin nền (window dùng để mining, roots, entries/phase ms).
- `stream` mặc định xuất **mỗi pattern khi đạt** (real-time); `--step` xuất **log từng bước**.

### 6.4 `--detail` (chỉ dùng cho `stream --step`)

| Mức | Ghi lại |
|---|---|
| `--detail 2` | Chỉ khi một pattern **đạt DOP** |
| `--detail 1` | Thêm **UBDO/DO khi mở rộng** (chỉ khi điều kiện đúng) |
| `--detail 0` | Thêm **cả điều kiện sai** (log gần như toàn bộ) |

> Mode `log` (superset, tốn tài nguyên nhất) **để sau** (D47).
> Giai đoạn **construction/reconstruction không log** (đã có `window` thay thế).

---

## 7. Bộ lệnh khai phá — input/output

### 7.1 `mine`

```jsonc
{ "id":2,"v":1,"cmd":"mine","dataset":"retail.dat","format":"fimi",
  "partial":0.06,"f":0.9,"minOcc":1e-6,"limit":0,
  "workers":8,"parts":5,"versions":["v1","v2","v3"],"export":true }
```
```jsonc
{ "id":2,"v":1,"ok":true,
  "config":{"partial":0.06,"f":0.9,"minOcc":1e-6,"epsilon":1e-9,"workers":8},
  "totalTransactions":88162,"lastTid":88162,"minSup":9.18,"maxDO":10.0,
  "window":{"size":153,"effective":153,"minSup":9.18,"maxDO":10.0,
            "evictions":87981,"liveEntries":12045,"deadEntries":0},
  "issues":[{"severity":"WARNING","code":"INFEASIBLE_PARTIAL","message":"..."}],
  "jobs":[{"version":"v2","engine":"v2-minocc","total_ms":1800,"patterns":0}],
  "patterns":[],
  "perVersion":{"v2":{"patterns":[],"window":{"size":153,"effective":153}}},
  "saved":{"fileID":"26-10-11_001","path":"/mine/mine_26-10-11_001.txt"} }
```

### 7.2 `detail` — bổ sung `stats`

```jsonc
{"id":3,"v":1,"ok":true,"...":"như mine","stats":{
  "nodes":[{"item":"F","support":7,"do":1.2553,"head":0,"entries":7}],
  "roots":6,"globalEntries":120,"topN":5,"phaseMs":{"g0":1,"g1":3,"g2":4,"g3":9}}}
```

### 7.3 `stream` (real-time) và `stream --step`

```jsonc
// real-time: mỗi pattern đạt
{"id":4,"v":1,"event":"pattern","version":"v3","index":12,
 "pattern":{"items":["A","E"],"key":"A,E","do":1.2601,"support":3,"tids":[2,4,7]}}

// --step + --detail 2 (chỉ DOP đạt)
{"id":4,"v":1,"event":"step","version":"v3","phase":"MINING","kind":"DOP_CHECK",
 "pattern":"A,E","passed":true,"do":1.2601,"minSup":9.18,"elapsedMs":0}

// --detail 0: có cả passed:false
{"id":4,"v":1,"event":"step","version":"v3","phase":"MINING","kind":"UBDO",
 "pattern":"A,B,C","ubdo":2.10,"z":3.00,"passed":false}

// kết thúc
{"id":4,"v":1,"ok":true,"jobs":[{"version":"v3","total_ms":39,"patterns":3}],
 "patterns":[{"items":["A","E"],"key":"A,E","do":1.2601,"support":3,"tids":[2,4,7]}]}
```

### 7.4 `window` / `validate` (không cần dataset)

```jsonc
{"id":6,"v":1,"cmd":"window","f":0.9,"minOcc":1e-6}
→ {"id":6,"v":1,"ok":true,"window":{"size":153},
   "maxDOAsymptotic":10.0,"maxPartialAsymptotic":0.0654,
   "maxDOExact":null,"maxPartialExact":null}
```

> ⚠️ **Bắt buộc ghi hậu tố** (D38): không có `TL` ⇒ **chỉ** trả `…Asymptotic`. Có `TL` ⇒ **phải** trả thêm `maxDOExact`/`maxPartialExact` bằng `Z(f,TL)/N_eff`. **Validator chặn tham số dùng giá trị chính xác.**

### 7.5 `sweep`

```jsonc
{"id":7,"v":1,"cmd":"sweep","dataset":"mushroom.dat","f":0.9,
 "partials":[0.02,0.03,0.06],"minOccs":[1e-6],"versions":["v3"]}
→ {"id":7,"v":1,"ok":true,"rows":[
   {"partial":0.06,"f":0.9,"minOcc":1e-6,"minSup":9.18,"patterns":0,"total_ms":83,
    "issue":"INFEASIBLE_PARTIAL"}]}
```

### 7.6 Phiên (`session.create` → `feed` → `session.mine`)

```jsonc
{"id":9,"v":1,"cmd":"session.create","config":{"partial":0.15,"f":0.9,"minOcc":0}}
→ {"id":9,"v":1,"ok":true,"session":"s1"}
{"id":10,"v":1,"cmd":"feed","session":"s1","transactions":[{"tid":1,"items":["A","E"]}]}
→ {"id":10,"v":1,"ok":true,"loaded":1,"lastTid":1,"window":{"size":null,"effective":1}}
{"id":11,"v":1,"cmd":"session.mine","session":"s1","versions":["v3"],"minOcc":1e-6}
→ {"id":11,"v":1,"ok":true,"jobs":[{"version":"v3","total_ms":3,"patterns":1}],
   "patterns":[{"items":["A","E"],"key":"A,E","do":1.0,"support":1,"tids":[1]}]}
```

---

## 8. Lưu kết quả & tải file (D48)

### 8.1 Bật lưu

| Nguồn | Ý nghĩa |
|---|---|
| config `isLog` (mặc định `false`) | Nếu `true` ⇒ **luôn** ghi file kết quả sau mỗi lệnh mining |
| cờ `--export [<path>]` | **Ghi đè** `isLog`; có `<path>` ⇒ ghi vào đó, không có ⇒ dùng thư mục mặc định |

- `--export` **không có giá trị** → ghi thư mục mặc định (`export.dir`, mặc định `/mine`).
- `<path>` là folder ⇒ BE tự tạo tên file theo quy ước; `<path>` không tồn tại ⇒ tạo; không đủ quyền (vd `C:\Program Files`) ⇒ dùng mặc định + cảnh báo.

### 8.2 Quy ước tên file

```
/mine/mine_<yy-MM-dd>_<NNN>.txt      ví dụ: /mine/mine_26-10-11_001.txt
```

- `<NNN>`: 3 chữ số, **tự tăng trong ngày**, **reset về `001` mỗi ngày** (D48).
- `fileID` (cấp trong response) = `<yy-MM-dd>_<NNN>` (vd `26-10-11_001`).

### 8.3 `ResultStore` (phía BE)

- API tối giản: `write(fileID, str)`.
- Ghi qua **hàng đợi + 1 thread riêng** để **không chặn** worker mining; nhiều mining đồng thời vẫn nhớ nội dung cần ghi.

### 8.4 `fetch` (FE tải file từ BE)

```jsonc
{"id":12,"v":1,"cmd":"fetch","file":"26-10-11_001"}
→ {"id":12,"v":1,"ok":true,"fileID":"26-10-11_001",
   "path":"/mine/mine_26-10-11_001.txt","content":"..."}
```

- File lớn ⇒ chia thành chuỗi event `{"event":"chunk","data":"..."}` kết thúc bằng `{"event":"end","fileID":"..."}`.

### 8.5 FE sao lưu cục bộ (bắt buộc)

- Khi `isLog = true` (hoặc có `--export`), FE **phải lưu một bản sao cục bộ** nội dung kết quả sau khi mining xong — **không phụ thuộc BE** (D48).
- FE tự quyết thư mục lưu cục bộ; hợp đồng chỉ ràng buộc **có lưu**.

---

## 9. Config interface (D49)

### 9.1 Ba cấp (độ ưu tiên tăng dần)

```
default (built-in)  <  file JSON (thư mục cấu hình người dùng)  <  CLI args
```

- File JSON: `%APPDATA%\dhopm\config.json` (Windows) / `~/.config/dhopm/config.json` (macOS/Linux).
- CLI args luôn thắng file; file luôn thắng default.

### 9.2 Lệnh

```
config get <key>
config set <key> <value>
config list
```

### 9.3 Khoá khởi điểm

| Key | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `isLog` | bool | `false` | có ghi file kết quả |
| `export.dir` | string | `/mine` | thư mục ghi mặc định |
| `workers` | int | số CPU | số worker |
| `detail` | int | `1` | mức `--detail` mặc định |

---

## 10. Mã lỗi & cảnh báo (ổn định)

### 10.1 Lỗi request

| Code | Nghĩa |
|---|---|
| `UNKNOWN_COMMAND` | `cmd` không thuộc bộ lệnh |
| `UNKNOWN_VERSION` | version không tồn tại / chưa hỗ trợ |
| `MISSING_PARAMETER` | thiếu `partial`/`f`/`minOcc` (hoặc `dataset`/`session`) |
| `INVALID_PARAMETER` | giá trị ngoài miền |
| `MIN_OCC_TOO_LARGE` | `minOcc ≥ 1/(1−f)` ⇒ `W=0` (D30) |
| `WINDOW_TOO_LARGE` | `W > maxWindow` (10⁷) |
| `MISMATCHED_WINDOW_CONFIG` | so nhiều version với `(∂,f,minOcc)` khác (R7) |
| `DATASET_NOT_FOUND` | file dataset không tồn tại |
| `UNSUPPORTED_FORMAT` | format ngoài `fimi`/`text`/`.zip` (D43) |
| `SESSION_NOT_FOUND` | `session` không tồn tại |
| `FILE_NOT_FOUND` | `fileID` không tồn tại trong `/mine` |
| `EXPORT_FAILED` | không ghi được file (quyền/đường dẫn) |
| `CONFIG_KEY_UNKNOWN` / `CONFIG_INVALID_VALUE` | config get/set sai key/giá trị |
| `NOT_IMPLEMENTED` | version/feature chưa có |
| `INTERNAL_ERROR` | lỗi không lường trước |

### 10.2 Lỗi/trạng thái phía FE (client-side)

| Code | Nghĩa |
|---|---|
| `BE_NOT_FOUND` | dò localhost + LAN không thấy BE hợp lệ |
| `PROTOCOL_MISMATCH` | `hello.protocol` khác contract |
| `CONNECT_REFUSED` | cổng từ chối/không phản hồi |

### 10.3 Cảnh báo (request vẫn `ok`, kèm `issues[]`)

| Code | Nghĩa |
|---|---|
| `PARTIAL_ZERO` | `∂=0` ⇒ mọi pattern là DOP ⇒ output bùng nổ (D31) |
| `INFEASIBLE_PARTIAL` | `∂×N_eff > Z(f,TL)` ⇒ kết quả **chắc chắn rỗng** (D19/D38) |

---

## 11. Tích hợp phía FE (BackendClient)

```
FE (javanc)                          BE (JVNC)
────────────                         ─────────────
interface BridgeEngine  ◄── giữ nguyên DIP
   ▲
BackendClient implements BridgeEngine
   │  detect() → hello → lưu địa chỉ :7079
   │  gửi request JSON theo hợp đồng này
   │  nhận progress/step → result
   └──────────────────────────────► dhopm-cli (Manager, `serve`) ──► engines
```

| Việc | FE phải làm |
|---|---|
| Thay `TsonV1Bridge` | bằng `BackendClient` (TCP 7079) |
| Giữ `BridgeEngine` | interface DIP giữ nguyên |
| Giữ `TamSimulationBridge` | engine demo nội bộ — không đụng |
| Bỏ Maven-dependency | **không** import `dhopm.*` |
| Detect BE | nút Detect: localhost → LAN → `hello` → lưu địa chỉ |
| Hiển thị cửa sổ | đọc `window` (`size=null` ⇒ "W=∞") |
| Hiển thị cảnh báo | đọc `issues[]` + mã lỗi |
| **Sao lưu cục bộ** | khi `isLog`, lưu bản sao kết quả (D48) |

---

## 12. Ví dụ end-to-end

```jsonc
// 1) FE detect
{"id":0,"v":1,"cmd":"hello"}
// ← {"id":0,"v":1,"ok":true,"protocol":1,"versions":[{"id":"v3","status":"ready"}]}

// 2) Tra cửa sổ (không cần dataset)
{"id":1,"v":1,"cmd":"window","f":0.9,"minOcc":1e-6}
// ← {"id":1,"v":1,"ok":true,"window":{"size":153},"maxPartialAsymptotic":0.0654}

// 3) Chạy đối chiếu V2 vs V3 cùng (∂,f,minOcc), có lưu file
{"id":2,"v":1,"cmd":"mine","dataset":"retail.dat","format":"fimi",
 "partial":0.01,"f":0.9,"minOcc":1e-6,"versions":["v2","v3"],"export":true}
// ← {"id":2,"v":1,"ok":true,
//     "jobs":[{"version":"v2","total_ms":115,"patterns":0},
//             {"version":"v3","total_ms":39,"patterns":0}],
//     "saved":{"fileID":"26-10-11_001","path":"/mine/mine_26-10-11_001.txt"}}
//   ⇒ FE fetch "26-10-11_001" và lưu bản sao cục bộ.
```

---

## 13. Tiêu chí chấp nhận (test hợp đồng)

- [ ] `hello` handshake đúng; FE dò được localhost → LAN; sai contract ⇒ `BE_NOT_FOUND`/`PROTOCOL_MISMATCH`.
- [ ] Schema mỗi lệnh hợp lệ; `id` khớp request → event → response.
- [ ] `mine --json` cho **cùng kết quả** CLI văn bản cũ, cho V1/V2/V3.
- [ ] Mọi lệnh mining **trả danh sách DOPs** (D47).
- [ ] `window.size` khớp `WindowMathTest` + bảng `plans/00` §2.3.1.
- [ ] Thiếu `(∂,f,minOcc)` ⇒ `MISSING_PARAMETER`; multi-version lệch ⇒ `MISMATCHED_WINDOW_CONFIG`.
- [ ] `window` không `TL` ⇒ chỉ `…Asymptotic` (D38).
- [ ] `--export`/`isLog` ghi `mine_<yy-MM-dd>_<NNN>.txt`; NNN **reset theo ngày**; `fetch` lấy đúng nội dung; FE **mirror**.
- [ ] `stream --step --detail 0/1/2` phát đúng mức log.
- [ ] `config get/set/list` đúng 3 cấp (`default < file JSON < args`).
- [ ] FE (`javanc`) chạy **chỉ** với `BackendClient`; `grep -R "dhopm."` trong FE = 0.
- [ ] Cùng request + khác `workers` ⇒ kết quả **bit-for-bit** (R11).

---

## 14. Phụ lục — Ánh xạ lệnh ↔ API `dhopm-common`

| Lệnh | Dùng API |
|---|---|
| `mine`/`detail`/`session.mine` | `Engine.loadBatch` + `Engine.mineNow` → `MineResult`, `Pattern` |
| `stream` real-time | `ProgressAwareEngine` + `MiningProgressListener` |
| `stream --step` / `--detail` | `PhaseAwareEngine` (`Phase`) + listener (DOP/UBDO/DO) |
| `window` / window event | `WindowAwareEngine` + `WindowListener` (`WindowInfo`), `WindowMath` |
| `validate` | `ParameterValidator.validate(config[, tl])` (`Issue`, `Severity`) |
| `inspect` | `TransactionSource` / `FimiTransactionReader` / `TextTransactionReader` / `ZipDataset` |
| `golden` | `GoldenRunner` + `GoldenCases` (TestKit) |
| `mine` timing | `TimedEngine` / `TimingRecorder`, `PhaseAwareEngine` |
| `mine` workers | `MiningConfig.workers` + `WorkerPool` |
| `fetch` / lưu file | `ResultStore` (module `dhopm-cli`) |

---

*Kết thúc hợp đồng FE (v0.2). Thay đổi hợp đồng phải: (1) tăng `contract version`, (2) chỉ **thêm** field/cmd, (3) ghi quyết định vào `DECISIONS.md`. Ranh giới bắt buộc: FE **không** import `dhopm.*`.*
