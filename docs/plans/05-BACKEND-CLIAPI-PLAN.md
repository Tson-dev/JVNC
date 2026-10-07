# Kế hoạch Giao tiếp Backend ↔ Frontend (Manager CLI / API) — DHOPM

> **Document ID:** DHOPM-PLAN-005 · **Version:** 1.1 (Draft để phân tích)
> **Bí danh:** Backend CLI/API (Manager)
> **Vai trò:** Sơn = **backend** (thuật toán **4 version** + CLI/API) · Tâm = **frontend** (JavaFX Visualizer, repo `2312441-sudo/javanc`)
> **Tài liệu gốc:** `docs/plans/00-OVERALL-PLAN.md` (mục 4.2, 4.3; quyết định D6, D7, D8)
> **Trạng thái:** PLÂN — chưa code. Tài liệu này để **phân tích hướng đi** trước khi sửa project.
>
> **Cập nhật 1.1:** đồng bộ với plan tổng thể v2.0 — **4 version** (thêm `window`/`validate`/`sweep` vào bộ lệnh chuẩn; schema có `epsilon` + `windowInfo`; `minSup` = `∂ × N_eff` thay vì `∂ × tổng`).

---

## 1. Mục đích tài liệu

Tài liệu này là **plan cập nhật cho phần giao tiếp giữa vỏ bọc frontend và phần backend**,
được viết TRƯỚC khi thay đổi code, để phân tích:

1. Frontend (Tâm) sẽ **giao việc và lấy kết quả** từ backend như thế nào.
2. Vị trí "đường giao tiếp đúng" nằm ở đâu (một **Manager CLI/API duy nhất** phía backend),
   và vì sao cách hiện tại của frontend (bridge gọi thẳng từng engine) **chưa khớp** thỏa thuận.
3. Backend cần thêm gì trên nền CLI đã có (`mine/detail/stream/golden/inspect`, `--limit`,
   `ProgressAwareEngine`) để phục vụ frontend.

> **Quy tắc quan trọng:** phần **thuật toán cốt lõi + lộ trình 4 version KHÔNG đổi**.
> Tài liệu này chỉ thêm **một lớp giao tiếp** (Manager CLI/API) vào hệ thống.
>
> **Lưu ý φạm vi:** ε là **tham số ngữ nghĩa** (C12) ⇒ mọi request phải khai báo
> `(∂, f, ε)`; Manager phải trả kèm **`windowInfo`** để frontend hiển thị đúng
> (kích thước cửa sổ, số transaction hiệu dụng, số lần evict). Chi tiết ở mục 7.1.

---

## 2. Bối cảnh & vai trò

| Thành viên | Vai trò | Sản phẩm | Repo |
|---|---|---|---|
| Sơn | **Backend** | 4 version thuật toán (V1 ✅ oracle, V2 ε, V3 tối ưu, V4 cực đoan) + `dhopm-common` + **CLI/API quản lý (đề xuất)** | `Tson-dev/JVNC` (local `D:\JVNC\JVNC`) |
| Tâm | **Frontend** | JavaFX Visualizer: DHO-List cards, cây DFS, biểu đồ DO, mô phỏng stream | `2312441-sudo/javanc` (clone tại `D:\JVNC\JVNC Tam\javanc`) |

**Thỏa thuận giữa 2 bên (đã bàn):**
- Backend/ frontend tách repo, làm việc song song, không xung đột code.
- Frontend **giao tiếp với CLI/API của backend** để giao việc và lấy kết quả.
- Backend chịu trách nhiệm xử lý, điều phối, quản lý tài nguyên.

---

## 3. Hiện trạng frontend & *sự hiểu lầm* cần ghi nhận

### 3.1 Trong README frontend (`javanc`, mục 7 "Bridge Architecture")

Tâm đã làm một **tầng cầu nối trực tiếp** và đề xuất nó như cách tích hợp chuẩn:

- Frontend khai báo Maven dependency vào **`dhopm-common`** và **`dhopm-v1-standard`**
  (bước 1: `mvn install` jar của Sơn vào Maven local repo; bước 3: bỏ comment import).
- `bridge/TsonV1Bridge.java` **`import dhopm.v1.engine.MiningEngine`**, tạo engine trong
  process rồi gọi `loadBatch`/`mineNow` trực tiếp; chuyển model qua lại (Tâm model ↔ Tson model).
- `EngineFactory` + `EngineMode`: khi có **V2/V3/V4** → Tâm tạo `TsonV2Bridge`, …, thêm enum → sửa code frontend.
- Đề xuất "monorepo thống nhất" gộp cả `dhopm-ui` vào repo JVNC.

### 3.2 Đâu là phần ĐÚNG, đâu là phần SAI

| Phần | Ý tưởng | Đánh giá |
|---|---|---|
| DIP — frontend chỉ biết **một interface duy nhất** (`BridgeEngine`) | Đúng | Giữ nguyên tinh thần này, nhưng interface đó **không được là adapter gọi trực tiếp từng version** |
| Adapter bọc từng engine `TsonV1Bridge` gọi trực tiếp `MiningEngine` | **Sai vị trí** | Frontend trở thành client nội bộ của từng version: biết version, biết cấu hình engine, phải rebuild khi backend đổi, phải `mvn install` jar — trái flow "frontend chỉ gửi yêu cầu" |
| Maven dependency trực tiếp vào `dhopm-v1-standard` (về sau `v2`, `v3`, `v4`) | **Sai về khớp nối** | Mỗi version mới = sửa frontend; môi trường khác nhau (Sơn Windows, Tâm macOS) làm khó build/install jar |
| Monorepo gộp UI vào JVNC | **Lệch quyết định** | Đã chốt tách repo, giao tiếp qua CLI/API |
| `TamSimulationBridge` (engine demo của Tâm để animation step-by-step) | Đúng | Là engine **demo nội bộ** của frontend cho mô phỏng từng giao dịch — giữ nguyên, không liên quan backend |

> **Kết luận mục này:** ý tưởng "frontend chỉ biết một hợp đồng" của Tâm là tốt. Điểm cần
> sửa là **chỗ cài đặt hợp đồng đó**: thay vì bridge→engine trong process, nó phải là
> **client → một Manager CLI/API duy nhất của backend**.

---

## 4. Flow mong muốn (quyết định của 2 bên)

```
[ Frontend (javanc) ]
   │  gửi YÊU CẦU (khai báo: dataset / tham số / muốn version nào)   ← chỉ làm việc này
   ▼
[ Manager CLI / API của Backend ]   ← ĐIỂM VÀO DUY NHẤT
   ├── hiểu yêu cầu, quyết cách xử lý
   ├── quản lý tài nguyên + đồng bộ (worker, memory, session)
├── TÁCH yêu cầu thành yêu cầu con theo từng version
    │      ví dụ: frontend muốn "chạy V2 và V3" → 2 yêu cầu con → V2 engine, V3 engine
    ├── điều phối + tổng hợp kết quả theo đúng bộ lệnh chuẩn
    ▼
[ V1 engine ]  [ V2 engine ]  [ V3 engine ]  [ V4 engine ]   ← (chỉ backend chạy, qua API dhopm-common, D6)
```

Nguyên tắc cốt lõi:

| # | Nguyên tắc giao tiếp frontend (bổ sung vào 4.2) |
|---|---|
| F1 | Frontend **KHÔNG biết / KHÔNG gọi trực tiếp bất kỳ engine nào** (v1/v2/v3/v4) — không dependency jar backend, không import `dhopm.v1.*`. |
| F2 | Frontend giao việc/lấy kết quả **chỉ qua 1 Manager CLI/API duy nhất** của backend. "Hợp đồng" giữa 2 bên = **đặc tả giao thức** (protocol spec), không phải class/API Java được share. |
| F3 | Manager chịu trách nhiệm: hiểu yêu cầu, **tách thành yêu cầu con theo version**, điều phối, đồng bộ, quản lý tài nguyên, tổng hợp kết quả. |
| F4 | Quản lý tài nguyên là **plan riêng** (D8): `ResourceManager → native API → WindowsModule/MacModule → OS API`. Lý do tách: nền tảng khác nhau (Windows vs macOS) và trách nhiệm tăng. |
| F5 | Mọi kênh đều là **client của API mở trong `dhopm-common`** (D6) — Manager gọi engine bằng đúng contract dùng chung; hot path thuật toán **không đổi**, không bị làm chậm bởi phần giao tiếp. |
| F6 | Kết quả trả frontend theo **cùng bộ lệnh chuẩn** (`mine/detail/stream/golden/inspect/window/validate`) — frontend nhận cùng dữ liệu dù backend đổi internal. |
| **F7** | **Mọi request phải khai báo `(∂, f, ε)`** (C12). ε là tham số ngữ nghĩa ⇒ không có mặc định "im lặng"; nếu thiếu ⇒ Manager trả lỗi yêu cầu, không tự đoán. |

---

## 5. So sánh phương án (phân tích hướng đi)

| Tiêu chí | A — Bridge trực tiếp (frontend hiện tại) | B — Manager CLI/API (đề xuất) |
|---|---|---|
| Khớp vai trò backend/frontend | ❌ Frontend tự điều khiển engine | ✅ Frontend chỉ gửi yêu cầu |
| Thêm version mới (V2/V3/V4) | ❌ Phải sửa frontend (thêm bridge+enum) | ✅ Backend mở thêm 1 engine trong registry; frontend không đổi |
| Đổi internal backend | ❌ Frontend phải rebuild/re-install jar | ✅ Không ảnh hưởng frontend (chỉ đặc tả giao thức) |
| Môi trường Win vs Mac | ❌ Phải build jar cho đúng máy, đúng bản dependency | ✅ Chỉ cần backend chạy trên 1 máy/môi trường có JDK |
| Nhiều version cùng lúc (so sánh) | ⚠️ Frontend tự gọi nhiều engine, tự gộp | ✅ Manager tách yêu cầu con + tổng hợp có sẵn |
| Quản lý tài nguyên/đồng bộ | ❌ Không có (mỗi engine 1 thread pool riêng) | ✅ Tập trung ở Manager (D8, plan riêng) |
| Khớp D6 / §4.2 của plan tổng thể | ❌ Gọi trực tiếp nội bộ module | ✅ Tuân thủ (Manager = client của API) |
| Chi phí thực hiện | Đã làm 1 phần (bridge sẵn có) | Phải thêm: giao thức, daemon, job manager |
| Overhead hiệu năng | In-process (nhanh, dễ) | Một chút overhead I/O biên (JSON/socket) — **hot path thuật toán không đổi** |

**Kết luận phân tích:** chọn **B**. Overhead của B nằm ngoài hot path (chỉ là I/O gửi/nhận
yêu cầu), còn lại lợi ích về vai trò, tài nguyên, và không phụ thuộc bản build — đúng thỏa thuận.

---

## 6. Kiến trúc đề xuất B (phác thảo để phân tích — Sơn hiệu chỉnh sau)

```
implementation/ (backend, repo JVNC)
├── dhopm-common/         (giữ nguyên — D6)
├── dhopm-v1-standard/    (giữ nguyên — D6)
├── dhopm-v2-epsilon/     (ε / window — mốc ngữ nghĩa cho V3/V4)
├── dhopm-v3-optimized/   (khi làm, giữ nguyên tư tưởng)
├── dhopm-v4-extreme/     (khi làm)
└── dhopm-cli/            (MỚI — Manager CLI/API; có thể là module hoặc subcommand của v1 cli)
    ├── ApiServer           nhận request JSON → trả response/progress (OP-2: JSONL qua stdin/stdout
    │                       hoặc TCP/HTTP/WebSocket daemon)
    ├── CommandDispatcher   ánh xạ lệnh chuẩn: mine/detail/stream/golden/inspect/window/validate/sweep
    │                       + session/feed
    ├── VersionRegistry     đăng ký engine sẵn có qua EngineFactory (chỉ API dhopm-common)
    ├── JobManager          tách request → sub-jobs theo version; đặt id, theo dõi tiến trình
    ├── ResultAggregator    chuẩn hóa schema + gộp khi request nhiều version
    ├── ParameterEcho       trả `windowInfo` + cảnh báo validator (∂ khả thi, ε quá lớn)
    └── SessionManager      trạng thái DHO-List theo session (stream incremental), lock đồng bộ

[ Frontend javanc ]  →  thay BridgeEngine/TsonV1Bridge bằng 1 BackendClient
                       (gửi JSON theo đặc tả protocol, nhận result/progress)
                       GIỮ Nguyên: TamSimulationBridge (engine demo animation)
```

### 6.1 Hai chế độ chạy Manager (được chốt ở OP-2)

| Chế độ | Cách dùng | Khi nào |
|---|---|---|
| **One-shot** | `Main <command>` như hiện tại; thêm `--json` để xuất dữ liệu máy-đọc | Frontend gọi 1 lần, không cần giữ trạng thái |
| **Daemon (session)** | Backend chạy 1 tiến trình `serve`; frontend kết nối, `session.create → feed → mine → …` | Mô phỏng stream thời gian thực, nhiều request liên tiếp |

### 6.2 Các lựa chọn cần CHỐT (để bạn phân tích/sửa)

| OP# | Câu hỏi | Phương án (đang nghiêng về) |
|---|---|---|
| OP-1 | Giao thức vận chuyển | **JSON Lines (JSONL)** qua stdin/stdout chuẩn hóa — đơn giản, dễ test; nâng cấp TCP/HTTP khi cần |
| OP-2 | Chế độ chạy | Làm **cả 2**: `--json` (one-shot, rẻ nhất) + `serve` (daemon, giữ session) |
| OP-3 | Engine chạy in-process hay subprocess | **In-process qua API dhopm-common** (đơn giản, D6); chuyển subprocess chỉ khi cần cô lập crash / JVM khác / đo resource theo tiến trình — bàn sau, không chặn MA1 |
| OP-4 | Schema result | `patterns[] {items[], do (double), support, tids[]}` + `perVersion` khi nhiều version; trùng khớp Pattern/DO của dhopm-common (C4–C6) |
| OP-5 | Frontend cần gì tối thiểu | Chỉ 1 lớp `BackendClient` thay `TsonV1Bridge`; giữ `BridgeEngine` interface (DIP không đổi) |
| OP-6 | Tên module | `dhopm-cli` (Manager) — hoặc tách `dhopm-manager` nếu muốn rõ vai trò điều phối |

---

## 7. Đặc tả giao thức (draft — để phân tích, sẽ viết thành tài liệu Contract cho Tâm)

Giao thức đề xuất: **mỗi dòng = 1 JSON request/response** (JSONL). ID đồng bộ giữa
request/response/progress.

```jsonc
// Frontend → Backend (one-shot)
{"id":1,"cmd":"mine","dataset":"retail.dat","format":"fimi","partial":0.001,"f":0.9,
 "epsilon":1e-6,"parts":5,"limit":0,"versions":["v2","v3"]}
// Backend → Frontend
{"id":1,"ok":true,
 "jobs":[{"version":"v2","total_ms":5123,"patterns":42},
         {"version":"v3","total_ms":4801,"patterns":42}],
 "window":{"f":0.9,"epsilon":1e-6,"size":153,"effective":153,
           "evictions":0,"liveEntries":12045,"deadEntries":0},
 "minSup":0.153,"maxDO":10.0,
 "totalTransactions":88162,"lastTid":88162}

// Daemon/session
{"id":2,"cmd":"session.create","config":{"partial":0.15,"f":0.9,"epsilon":0}}
                                         → {"id":2,"ok":true,"session":"s1"}
{"id":3,"cmd":"feed","session":"s1","transactions":[[1,["A","E"]], ...]}
                                    → {"id":3,"ok":true,"loaded":2,"lastTid":2,
                                       "window":{"size":null,"effective":2}}
{"id":4,"cmd":"mine","session":"s1","versions":["v2"],"partial":0.15,"f":0.9,"epsilon":1e-6}
{"id":4,"event":"progress","session":"s1","version":"v2","done":3,"total":6,"patterns":12}
{"id":4,"ok":true,"patterns":[{"items":["A","E"],"do":1.2601091166666667,"support":3,"tids":[2,4,7]}, ...]}

// Kiểm tra cấu hình (không cần dataset)
{"id":5,"cmd":"validate","partial":1.0,"f":0.9,"epsilon":1e-3}
 → {"id":5,"ok":false,"code":"INFEASIBLE_PARTIAL",
    "message":"∂ = 1.0 ⇒ minSup = 88.0 > Z = 10.0 ; không thể có DOP. Giảm ∂ hoặc tăng f.",
    "maxDO":10.0,"maxDOExact":null,"maxPartialAsymptotic":0.1136,"maxPartialExact":null}

// Tra bảng cửa sổ (không cần dataset)
{"id":6,"cmd":"window","f":0.9,"epsilon":1e-6}
 → {"id":6,"ok":true,"window":{"size":153},
    "maxDOAsymptotic":10.0,"maxPartialAsymptotic":0.0654}
```

> ⚠️ **Lệnh `validate` / `window` không có `TL` ⇒ chỉ trả được giá trị xấp xỉ.** `maxDO` và `maxPartial` chính xác phụ thuộc `TL` (`Z(f,TL)/N_eff`), mà `window` chạy **trước khi có dataset** ⇒ không biết `TL`. Vì vậy:
> - Field trả về **phải ghi rõ hậu tố** `…Asymptotic` như trên (giá trị ở giới hạn `TL → ∞`), và `…Exact = null`.
> - Khi `validate`/`window` **có** `TL` trong request (hoặc được gọi sau khi nạp xong), Manager **phải** trả thêm `maxDOExact` và `maxPartialExact` bằng công thức chính xác.
> - **Miền `∂` khả thi dùng để chặn tham số phải lấy từ công thức chính xác**, không lấy từ giá trị xấp xỉ — ở `TL` ngắn hai giá trị lệch nhau một bậc độ lớn (ví dụ `f=0.9, TL=4`: xấp xỉ 6.54 %, chính xác 85.98 %).

Nguyên tắc schema:
- **double đầy đủ** (C4 — không làm tròn khi truyền).
- Item theo `canonicalKey` (C6).
- **`minSup` = `∂ × N_eff`** (canonical §2.3, C9), `N_eff = min(TL, W)` — **không phải** `∂ × tổng`.
- `window.size = null` nghĩa là `W = ∞` (chế độ `ε = 0` hoặc `f = 1`).
- Pattern kèm `support`/`tids` như `MineResult`.
- **Bắt buộc khai báo `(∂, f, ε)`** ở mọi request **giao diện Manager** (F7); thiếu ⇒ lỗi `MISSING_PARAMETER`, không tự đoán.
  - ⚠️ **Đây là quy định cho *giao thức*, không phải cho thư viện.** `MiningConfig` trong `dhopm-common` **vẫn có** giá trị mặc định theo phiên bản (V1 = `0`, V2+ = `1e-6`) để tiện cho lập trình viên và test; Manager **không** được suy ra mặc định đó cho request của frontend, vì `ε` thay đổi **tập DOP** (C12) ⇒ hai lần chạy cho kết quả khác nhau mà người dùng không hề biết. Xem canonical §2.1 "Chính sách mặc định của ε".
- Lỗi cấu hình trả **mã ổn định** để frontend hiển thị bằng thông điệp có số liệu.

---

## 8. Trạng thái & đồng bộ (điều phối của Manager)

| Vấn đề | Xử lý đề xuất |
|---|---|
| Engine có trạng thái (DHO-List tích lũy, TL, `minSup = ∂ × N_eff`) | Manager giữ **session** trên engine; `feed` cập nhật, `mine` snapshot kết quả |
| **V2+ có cửa sổ ⇒ evict diễn ra trong `feed`** | Session phải giữ **cùng cửa sổ** cho mọi version cùng chạy để so sánh công bằng ⇒ Manager **gửi cùng `(∂, f, ε)` cho mọi job con**; nếu các version khác ε ⇒ ghi rõ vào `perVersion` và coi là **hai thí nghiệm khác nhau**, không gộp kết luận |
| Yêu cầu nhiều version cùng lúc | Manager tạo **1 job con/version** (cùng dữ liệu batch), chạy song song (in-process, worker pool riêng mỗi engine — D4), gộp theo digest |
| Xung đột request trên cùng session | Khóa đồng bộ: 1 session xử lý tuần tự (simple); nhiều session chạy song song |
| Yêu cầu dài (retail/kosarak) | Progress event (P3: `MiningProgressListener` đã có) → frontend hiển thị %; thêm `WindowListener` (mới) để báo số lần evict |
| Dataset lớn | `--limit` (đã có) + incremental `parts`; manager có thể chặn job quá tải (bàn ở D8) |

---

## 9. Quản lý tài nguyên — ⛔ ĐÃ BÁC BỎ (xem `../DECISIONS.md` §6)

> **Trạng thái:** Đề xuất `ResourceManager → native API → WindowsModule/MacModule` **đã bị bác bỏ**. Lý do: Java không có API quota CPU/RAM portable giữa Windows và macOS, và nhu cầu thực tế (`--workers`, `--limit`) đã được đáp ứng bằng cấu hình đơn giản.
>
> **Thay thế đã chốt:** chính sách tài nguyên nằm ngay trong Manager dưới dạng cấu hình thuần (`workers`, `limit`, `parts`) — không cần lớp native.
>
> Nếu sau này thực sự cần (ví dụ: chạy nhiều job nền trên laptop tiết kiệm pin), thì **mở D38** và viết plan riêng — không quay lại dùng bản thiết kế này.

---

## 10. Backend đã có (tái sử dụng) & cần thêm

**Đã có và dùng lại:**
- CLI 5 lệnh: `mine/detail/stream/golden/inspect` + `--limit` + header/marker (G1-D7).
- API mở `dhopm-common`: `Engine`, `PhaseAwareEngine`, `ProgressAwareEngine` + `MiningProgressListener`,
  `TimedEngine`, `WorkerPool`, `TimingRecorder` (D6).
- Test nền: 56 test xanh, golden TC1–TC8 (thước đo cho Manager).

**Cần thêm (theo phân tích này):**
- Module/entry `dhopm-cli` (Manager): `--json` (one-shot) + `serve` (daemon).
- `VersionRegistry` (đăng ký engine; thêm V2/V3/V4 sau không đụng protocol).
- `JobManager` + tách yêu cầu con + `ResultAggregator`.
- `SessionManager` (trạng thái stream) + đồng bộ.
- **`ParameterEcho`**: trả `windowInfo` + cảnh báo validator (`∂` khả thi, `ε ≥ 1/(1−f)`, `W > maxWindow`) bằng **mã lỗi ổn định**.
- Serializer JSON (tự viết tối giản hoặc thư viện — chọn ở MA1).
- **Đặc tả giao thức** = tài liệu Contract cho frontend (mục 7 hoàn thiện).
- *(Sau G2)*: `WindowAwareEngine`/`WindowListener` trong `dhopm-common` để Manager báo `evictions`/`liveEntries`.

---

## 11. Lộ trình (theo phân tích — có thể sửa sau khi bạn xem)

| Mốc | Nội dung | Đầu ra | Chế độ chờ |
|---|---|---|---|
| **MA0** | Hoàn thiện tài liệu này: chốt OP-1..OP-6, thống nhất với Tâm | Plan chốt + đặc tả protocol draft | Hiện tại |
| **MA1** | Manager tối thiểu: `--json` + `serve`(JSONL), map lệnh chuẩn, registry cho **V1**; `window`/`validate` trả số liệu công thức | `dhopm-cli` chạy được, cùng kết quả CLI cũ (`--json` `mine` == `Main mine`) | MA0 |
| **MA2** | JobManager: yêu cầu nhiều version → tách job con → gộp kết quả; session đơn giản | Fan-out V1 (+V2/V3/V4 khi có) + progress event + `windowInfo` | MA1 |
| **MA3** | Tích hợp frontend javanc: thay `TsonV1Bridge` bằng `BackendClient` (theo đặc tả), giữ `BridgeEngine`; **Tâm làm client phía mình, Sơn cấp đặc tả + test chuẩn** | Frontend chạy được với backend qua Manager | MA2 |
| **MA4** | Plan tài nguyên **riêng** (ResourceManager→native→Win/Mac) | Tài liệu plan tài nguyên | Song song từ MA2 |

**Tiêu chí chấp nhận:**
- [ ] Cùng một yêu cầu qua `--json` và qua CLI văn bản cũ → **cùng kết quả** (so `inspect`/`mine`).
- [ ] Request nhiều version trả `perVersion` đúng, không crash, kết quả trùng từng version.
- [ ] `window` trả `W` **khớp** với `WindowMathTest` (bảng tra cứu ở plan tổng thể §2.3.1).
- [ ] `validate` trả mã lỗi ổn định + thông điệp có số liệu cho `∂` ngoài miền khả thi / `ε ≥ 1/(1−f)`.
- [ ] Stream lớn (kosarak, `--limit`) có progress event, không đóng băng.
- [ ] Golden TC1–TC8 vẫn xanh sau khi thêm Manager.
- [ ] Frontend hiển thị đúng kết quả chỉ qua `BackendClient` (không import backend class).

---

## 12. Rủi ro & giảm thiểu (bổ sung cho mục 9 của plan tổng thể)

| Rủi ro | Giảm thiểu |
|---|---|
| Frontend vẫn giữ bridge trực tiếp (không bỏ) | MA3 có test chuẩn: client chỉ dùng đặc tả; ghép bàn bạc rõ với Tâm |
| Protocol đổi khi V2/V3/V4 thêm command | Phiên bản hóa protocol (v1, v2…) giữ backward-compatible; chỉ thêm field/cmd, không bỏ cũ |
| **Thêm field `epsilon` làm frontend cũ hỏng** | `epsilon` có giá trị mặc định quy ước = `0` ⇒ **≡ hành vi cũ**; version protocol tăng chỉ khi bỏ field |
| **So sánh nhiều version với ε khác nhau** | Manager **ép cùng `(∂, f, ε)`** cho mọi job con (C12); nếu client cố gửi khác ⇒ trả `MISMATCHED_WINDOW_CONFIG` thay vì gộp kết quả không tương đương |
| Overhead JSON/socket bị hiểu là "thuật toán chậm" | Nêu rõ: hot path thuật toán không đổi; I/O biên tách qua `--json`/daemon, benchmark in-process là thước đo thật |
| Đồng bộ session (2 request đè nhau) | Khóa theo session (tuần tự); test concurrency |
| Khác nền tảng Win/Mac | D8 tách abstraction sớm; MA1 chỉ phụ thuộc JDK (platform-independent) |
| Khối lượng việc tăng đột biến | Plan tách MA1→MA4; MA1 (manager tối thiểu) nhỏ, có thể giao việc trong vài ngày |

---

## 13. Việc tiếp theo (sau khi bạn review tài liệu này)

1. Bạn sửa/thêm trong tài liệu này (OP-1..OP-6, lộ trình MA*, schema).
2. Thống nhất với Tâm: giao thức JSONL + bỏ bridge trực tiếp, thay `BackendClient`.
3. Rồi mới dựa vào kết quả phân tích này để cập nhật project (module `dhopm-cli`, tài liệu Contract).