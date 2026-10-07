# Phân tích Draft ý tưởng — Giao tiếp Backend ↔ Frontend (áp dụng vào project: vấn đề · hình dung · thiếu gì)

> **Document ID:** DHOPM-PLAN-006 · **Version:** 1.1 (⚠️ **PHÂN TÍCH LỊCH SỬ — ĐÃ BỊ VƯỢT QUA Ở NHIỀU ĐIỂM**)
> **Nguồn:** `docs/plans/Draft backend CLIAPI.txt` (nháp của tác giả backend)
> **Liên quan:** `docs/plans/05-BACKEND-CLIAPI-PLAN.md` (CLI plan — *trước đây gọi là "plan CLI 05", nay đã đánh số lại*) · `docs/plans/00-OVERALL-PLAN.md` mục 4.3 · `docs/DECISIONS.md`
> **Quy ước từ draft:** "module" / "service" coi là một (tên chưa chốt — gọi chung là **plugin**) · **Trạng thái:** không phải nguồn chân lý.

> ## ⚠️ Đọc trước khi dùng
>
> Tài liệu này ghi lại **việc phân tích một bản nháp** vào thời điểm trước khi có `docs/DECISIONS.md`. Giữ lại để **không đề xuất lại** những thứ đã bị bác bỏ. Khi mâu thuẫn với tài liệu khác, **`DECISIONS.md` thắng**.
>
> | Nội dung trong bản nháp | Phán quyết hiện tại |
> |---|---|
> | **SPI / plugin discovery**, mỗi thuật toán "đăng ký lên controller" (CLI-B, FLOW-B) | ⛔ **Bác bỏ** — có đúng 4 engine biết trước tên; hardcode list factory rõ hơn |
> | **Recovery** (SYS-A) / ledger phiên chạy | ⛔ **Bác bỏ** — sau cửa sổ ε một lần `mine` chỉ vài giây; replay construction vẫn khả dụng (INV-A/B) |
> | **JobManager / worker thật** (FLOW-C) | ⛔ **Để sau** — đã đồng bộ hoá đơn luồng (INV-E); không cần tách vai |
> | **`ResourceManager` → native Windows/macOS** (CLI-E, SYS-B, MA4) | ⛔ **Bác bỏ** (D8) — dùng `workers` / `limit` / `parts` là đủ |
> | **Daemon TCP / remote LAN** (FLOW-A) | ⛔ **Bác bỏ** — chưa có nhu cầu; JSONL qua stdio là đủ (D34 đang cân nhắc) |
> | **Bộ lệnh chuẩn** `mine/detail/stream/golden/inspect` | ✅ **Giữ** (D27), nay thêm `window/validate/sweep` |
> | **API ổn định ở `dhopm-common`, CLI là client** (D6) | ✅ **Giữ và đã chốt** |
> | **4 phiên bản** V1–V4 | ✅ **Giữ** (D13) — bản nháp này viết khi còn "3 version" |
>
> ⇒ Phần phân tích chi tiết bên dưới **giữ nguyên như bản ghi lịch sử**; sử dụng thực tế phải theo `05-BACKEND-CLIAPI-PLAN.md` + `DECISIONS.md`.

---

## 1. Mục đích

Draft của bạn còn "rối" và có vài chỗ chưa viết xong. Tài liệu này:

1. **Gom nhóm lại** các ý tưởng draft thành các chủ đề rõ ràng (đánh dấu chỗ câu bỏ dở).
2. **Đối chiếu** từng ý với CLI plan + hiện trạng code → chỗ nào **khớp, chỗ nào mâu thuẫn, chỗ nào mới**.
3. Trả lời 3 câu bạn hỏi: **áp dụng vào có vấn đề gì · project sẽ thế nào · thiếu gì**.
4. Đề xuất **điều chỉnh plan** (nếu bạn đồng ý) và **các câu hỏi cần bạn chốt**.

Kết luận lúc viết: **draft về cơ bản KHÔNG phá vỏ plan CLI / hiện trạng code**, phần lớn là *bổ sung* và *tinh chỉnh*. **Phần lớn các ý sau đó đã bị bác bỏ — xem bảng trạng thái ở đầu file.**

---

## 2. Tổng hợp ý tưởng draft (đã gom nhóm)

| Mã | Chủ đề | Ý tưởng gốc (viết lại gọn) | Ghi chú draft để lại |
|---|---|---|---|
| CLI-A | Config của CLI | mỗi CLI phải **có config** riêng; CLI phải **sửa được config của chính nó** | — |
| CLI-B | Tự mô tả | phải có lệnh **`info`** (CLI này là gì, làm gì, có gì, làm được gì — **thông tin tĩnh** về service được hỏi, ví dụ hỏi controller thì chỉ trả về controller, KHÔNG trả "đang liên kết/quản lý module nào"); kèm `version`, `module --list`, ... | "trả về thông tin **tỉnh**" (ý là *tĩnh*) |
| CLI-C | Config thay tham số gõ tay | thay vì gõ lệnh + điền thông tin cụ thể, **config lưu một phần thông tin**; có **3 cấp: `default`, `config` và ____** | **câu bỏ dở** — cấp thứ 3 chưa viết |
| CLI-D | Kiến trúc Manager | Manager theo **hybrid = microkernel (plug-in) + microservice**; **không bắt buộc mở port local cho từng thuật toán**; microkernel → giải quyết **mở rộng**, microservice → giải quyết **chạy độc lập** | — |
| CLI-E | Phân đoạn | ý tưởng lớn → **tách giai đoạn**: hiện tại **chỉ gọi trực tiếp thuật toán**; kiến trúc mới **phát triển cùng quản lý tài nguyên** | — |
| SYS-A | Recovery | hệ thống **phải khôi phục được**: đang mining mà ngưng → khởi động lại phải **biết trước đó đang làm gì** | — |
| SYS-B | Điều chỉnh dùng tài nguyên | hệ thống **điều chỉnh được mức dùng dữ liệu** (ví dụ chỉ dùng **~40% CPU**) | "% ở đây đại khái" |
| FLOW-A | Remote | frontend giao tiếp với controller **qua remote** (kết nối cổng/internet; remote lo **duy trì kết nối, đảm bảo đủ dữ liệu**, vấn đề client-server). Hiện tại **chỉ cần remote đơn giản mở cổng local**, giao tiếp **JSON**, chạy LAN nên **không cần bảo toàn/bảo mật dữ liệu** | — |
| FLOW-B | Controller gọi CLI thuật toán | CLImáy sẽ **chia việc ra và gọi CLI của từng thuật toán**, **không gọi trực tiếp**; vì controller **không biết có bao nhiêu version** → **mỗi thuật toán phải đăng ký lên controller** | — |
| FLOW-C | Worker làm thật | thuật toán **không thực sự làm mà worker làm** — thuật toán như "người chỉ huy worker"; **phức tạp quản lý worker → để sau** | — |

---

## 3. Đối chiếu draft ↔ plan CLI 05 ↔ hiện trạng code

| Draft | CLI plan (nay la `05-BACKEND-CLIAPI-PLAN.md`) | Hiện trạng code | Kết luận |
|---|---|---|---|
| CLI-A/C (config 3 cấp, tự sửa config) | chưa có khái niệm config file; tham số đi kèm lệnh | tham số inline (`--partial/f/--limit`); "default" nằm cứng trong `MiningConfig` | **MỚI** — cần bổ sung tầng config + lệnh `config get/set` |
| CLI-B (`info`/`version`/`module --list`) | chưa có | `Main` có `help` + exit code 0/1/2/3 | **MỚI** — đặc biệt `module --list` chính là "đăng ký" ở FLOW-B |
| CLI-D (hybrid microkernel+microservice) | OP-3: **in-process** trước; subprocess = dự phòng | engine gọi qua API `dhopm-common` (D6) | **Khớp hướng** — "không bắt buộc port từng algorithm" = cho phép in-process; hybrid (chạy độc lập) dồn về giai đoạn tài nguyên (CLI-E/MA4) |
| CLI-E (phân đoạn) | MA0→MA4 | — | **Khớp** — MA1 chính là "gọi trực tiếp hiện tại" |
| SYS-A (recovery) | chưa có (SessionManager mới ở MA2) | engine state trong RAM; construction **replay được** (quét lại theo TID, INV-A/B); INV-C: đổi dữ liệu → dựng lại từ 0 | **MỚI** — nhưng "replay lại" làm recovery rẻ hơn bạn tưởng (xem §4.3) |
| SYS-B (mức dùng ~40% CPU) | D8: tài nguyên = plan riêng; D4: worker = `availableProcessors()` | `WorkerPool` cấu hình được số worker | **Khớp hướng** — levers hiện có là *số worker*, chưa phải *% CPU* (xem §4.4) |
| FLOW-A (remote cổng local, JSON, LAN) | OP-1: **JSONL qua stdin/stdout**; OP-2: one-shot + daemon | CLI 5 lệnh + `--limit`, xuất ASCII | **Mâu thuẫn nhỏ** — draft muốn **cổng local (TCP)**, plan CLI 05 đang nghiêng **stdio**. Gợi ý hợp nhất ở §8 |
| FLOW-B (controller gọi CLI thuật toán; đăng ký) | OP-3: in-process; `VersionRegistry` đăng ký engine | mỗi version có `Main` (P4: CLI = client của API) | **Tinh chỉnh**: đổi "VersionRegistry khai báo cứng" → **phát hiện plugin (registration/discovery)**; "gọi CLI thuật toán" = gọi entry của version, in-process trước (xem §4.5) |
| FLOW-C (worker làm thật) | Level-1 threading (reconstruction theo node, mining theo cây con gốc) | `WorkerPool.invokeAll(tasks, onCompleted)` — thuật toán điều phối task, worker chạy thật | **Đã đúng hiện trạng** — ghi nhận, không cần làm thêm bây giờ |

**Tóm tắt:** 1 điểm đã khớp hoàn toàn (FLOW-C), 2 điểm mới hoàn toàn (config, info), 1 mâu thuẫn nhỏ (transport), còn lại là tinh chỉnh plan CLI 05.

---

## 4. Vấn đề khi áp dụng (trả lời "có vấn đề gì")

### 4.1 CLI-A/B — config + `info`

- **Cần phân tách 2 loại lệnh khỏi bộ lệnh mining (P5):**
  - *Lệnh vận hành:* `mine/detail/stream/golden/inspect` (đã có, trả kết quả thuật toán).
  - *Lệnh quản trị (mới):* `config get/set`, `info`, `version`, `module --list`.
  Tránh trộn: `info` không được nhìn vào nội bộ engine (P1) → phải trả **manifest tĩnh** (tên, mô tả, lệnh hỗ trợ, format đầu vào, tham số config nhận, phiên bản protocol).
- **`info` tĩnh vs `module --list` động không mâu thuẫn** nếu tách vai: `info` = tự mô tả bản thân (tĩnh, không phụ thuộc runtime); `module --list` = **hỏi registry** (động — đúng thứ draft cấm `info` không trả). Cần chốt schema tránh trùng.
- **Config tự sửa được** → phải có lệnh `config get/set` + **validate khi set** (∂∈[0,1], f∈(0,1) theo canonical 2.1); sửa **không làm hỏng job đang chạy** (hiệu lực từ job kế tiếp). Nếu daemon chạy lâu, ai sửa config lúc runtime + đọc đồng thời → khóa đơn giản hoặc reload-mỗi-job.
- **Rủi ro scope creep:** config + 3 lệnh quản trị là yêu cầu mới→ **phải đưa vào milestone** (MA1), không nhét lúc làm frontend (MA3).

### 4.2 CLI-D — hybrid microkernel + microservice

- **Việc tách giai đoạn của bạn (CLI-E) là đúng và quan trọng** — đây là cách duy nhất để ý tưởng này không kéo trễ V2/V3. Phân định lại:
  - **Microkernel** (plugin + registry/discovery) → **dùng ngay từ MA1** (rẻ: SPI/ServiceLoader + metadata).
  - **Microservice** (chạy độc lập, cô lập process/resource) → **theo D8/MA4** cùng quản lý tài nguyên. "Không bắt buộc port từng algorithm" = giai đoạn đầu **1 quá trình**, ai cô lập sau.
- **Lưu ý từ ngữ:** microservice cổ điển hay mỗi service 1 port — hybrid ở đây chỉ cần "có khả năng chạy độc lập khi cần", không phải tách port bắt buộc. Ghi rõ trong plan để sau không hiểu nhầm.

### 4.3 SYS-A — recovery

- **Phần lớn được "cho không" nhờ tính deterministic của thuật toán:**
  - Construction là **replay thuần** (quét theo TID tăng, append-only). DO chỉ có nghĩa tại đúng `(TL,f)` (INV-C) → **không cần serialize DHO-List** để khôi phục; chỉ cần "đọc lại dữ liệu tới `lastTid` + dựng lại".
  - Với dữ liệu dạng **file** (dataset FIMI): replay = đọc lại file → recovery giá rẻ: lưu **job ledger** (`{jobId, version, config, source, lastTid, trạng thái}`) ra file/text, khởi động lại đọc ledger, tái dựng, báo "job X đang dở".
- **Phần đắt hơn:** nếu dữ liệu đưa vào qua **session (feed từng lô)** — dữ liệu không nằm trong file → phải **lưu lại các transaction đã feed** để replay. → làm ở giai đoạn sau (cùng hybrid), ghi nhận thôi.
- **Vấn đề cần chốt:** mức recovery "nhẹ" (MA2, chỉ status + lastTid + repllay file) hay "đầy đủ" (MA4, journal feed + checkpoint). Khuyến nghị: **làm mức nhẹ trước** — nó thỏa câu "tôi đang làm gì" mà không tốn thiết kế lưu trữ.

### 4.4 SYS-B — ~40% CPU

- **JVM không có API "giới hạn % CPU" trực tiếp.** Lever hợp lý:
  1. **Số worker** (đã có, D4): 40% ≈ `workers = round(0.4 × cores)` — gần đúng, dễ làm ngay.
  2. Độ ưu tiên thread / backpressure — mịn hơn, làm sau.
  3. **Quota OS chuẩn xác** (cgroups/Win job object mà tạm bỏ qua tên) → **chỉ qua native modules (D8/MA4)**.
- → Plan nên hứa **"cấu hình được mức dùng" (worker count, ưu tiên)** chứ **không hứa % CPU chính xác** ở giai đoạn đầu. Draft ghi chú "đại khái" → như vậy là hợp lý.

### 4.5 FLOW-B — controller gọi CLI thuật toán + đăng ký

- **2 cách đọc "gọi CLI thuật toán":**
  - *(a) In-process:* controller gọi **entry/command của version** (version có `Main` = client của API, đúng P4) trong cùng process, qua **discovery** (version tự khai báo "tôi là ai, nhận lệnh gì" — chính là metadata `info`/`manifest`).
  - *(b) Subprocess:* spawn `Main mine …` riêng → mất chia sẻ worker/engine, phải vận chuyển dataset + đọc progress từ stdout — nặng.
  - **Khuyến nghị:** giai đoạn đầu làm **(a)** = "gọi CLI thuật toán" nhưng in-process — vừa đúng ý draft (controller không biết trước có bao nhiêu version) vừa rẻ (= OP-3 hiện tại + discovery). Lên hybrid/MA4 nếu cần **(b)**.
- **"Mỗi thuật toán phải đăng ký lên controller"** → dịch thành cơ chế chuẩn: **ServiceLoader (Java SPI) hoặc thư mục metadata** nhận dạng plugin + manifest thay vì hardcode `VersionRegistry`. `EngineFactory` hiện có là bề mặt SPI tự nhiên.

### 4.6 FLOW-A — remote (cổng local)

- Draft muốn **cổng local (TCP)** trong khi plan CLI 05 OP-1 đang nghiêng **JSONL stdin/stdout** → **mâu thuẫn nhỏ, cần chốt**.
  - TCP local **hợp GUI hơn** (frontend ở process riêng, kể cả máy khác trên LAN; kết nối lâu, reconnect); stdio rẻ hơn cho gỡ lỗi tự động hóa (test/script).
  - **Đề xuất:** daemon `serve` **mở cổng local (TCP)**, vẫn giữ `--json` one-shot (stdio) cho test/manual. Giao tiếp **JSON** như draft; **không auth** (LAN — ghi nhận rủi ro chấp nhận).
- **"Đảm bảo đủ dữ liệu" (client-server):** khi mining nhanh hơn frontend xử lý → progress/result bị tích buffer → cần **backpressure hoặc đánh rơi có kiểm soát**. Quyết muộn (MA4). Giai đoạn đầu đơn giản: progress theo bản tin, frontend chịu trách nhiệm tiêu thụ.
- **Remote không cần làm kỹ ngay** (draft cũng ghi vậy) — chỉ "mở cổng local" ở giai đoạn đầu.

---

## 5. Project sẽ thế nào nếu áp dụng (trả lời "project sẽ thế nào")

### 5.1 Giai đoạn A — hiện tại (MA1 + mở rộng; theo CLI-E "gọi trực tiếp")

```
[ Frontend (javanc) ]
   │  JSON qua cổng local (TCP)            ← remote đơn giản (FLOW-A)
   ▼
[ Controller CLI — dhopm-cli (1 process) ]
   ├── Remote (TCP local, JSON; giữ --json one-shot cho gỡ lỗi)
   ├── ConfigStore (3 cấp: default < config < ___ ) + lệnh config get/set
   ├── Query API: info (tĩnh), version, module --list (registry), help
   ├── JobManager + SessionManager (trạng thái DHO-List, lock theo session)
   ├── JobLedger (recovery mức nhẹ: status/lastTid/source → replay)
   ├── PluginRegistry (SPI discovery) ──► EngineFactory (dhopm-common, D6)
   └── Algorithm entrypoints: gọi V1 (in-process, cùng API) → WorkerPool
```

- 5 lệnh mining (P5) giữ nguyên; thêm 4 lệnh quản trị (`info/version/module --list/config`).
- V2/V3 thêm sau = **thêm 1 plugin** (không đụng controller/frontend) — đúng mục tiêu microkernel.
- Worker = `WorkerPool` sẵn có; "mức dùng" = số worker (SYS-B mức 1).

### 5.2 Giai đoạn B — cùng quản lý tài nguyên (MA4, hybrid)

```
[ Frontend ]
   ▼  remote hoàn chỉnh (đủ dữ liệu/backpressure, có thể nhiều client)
[ Controller (core kernel) ]
   ├── Plugin/service boundary: mỗi algorithm = plugin CÓ THỂ chạy độc lập
   │     (in-process hoặc subprocess — quyết theo ResourceManager)
   ├── ResourceManager → native API → Windows/Mac → OS API   (D8, quota CPU/memory)
   ├── Recovery đầy đủ: journal feed + checkpoint
   └── ConfigStore hoàn chỉnh + versioning protocol
```

- Đây là **kiến trúc đích** của Draft CLI-D; không vội — nó đi cùng MA4 (đúng CLI-E).

---

## 6. Thiếu gì để áp dụng được (gap list → gắn mốc)

| # | Thiếu (gap) | Mốc đề xuất | Ghi chú |
|---|---|---|---|
| G-1 | ConfigStore + lệnh `config get/set` + quy tắc ưu tiên 3 cấp (cần chốt cấp thứ 3) | MA1 | validate theo canonical 2.1 |
| G-2 | Manifest tĩnh cho `info` + `version` + `module --list` | MA1 | schema tĩnh; không dò nội bộ (P1) |
| G-3 | Discovery/registration plugin (SPI / metadata dir) qua `EngineFactory` | MA1 | thay "VersionRegistry hardcode" |
| G-4 | Remote TCP local (daemon `serve`) + giữ `--json` | MA1–MA2 | chốt transport ở §8 |
| G-5 | JobLedger + session persistence **mức nhẹ** (recovery: status/lastTid/source → replay) | MA2 | replay file; journal feed để MA4 |
| G-6 | Bản đồ "mức dùng → worker count" (SYS-B mức 1) | MA2 | % CPU chính xác = MA4 |
| G-7 | Backpressure / cơ chế "đủ dữ liệu" của remote | MA4 | cùng hybrid |
| G-8 | Quota CPU/memory qua native (Windows/Mac) + recovery đầy đủ | MA4 | D8 |
| G-9 | Test: concurrency config, replay recovery tái lập INV-E, golden TC1–TC8 không đổi sau khi thêm manager | Mỗi mốc | nền 56 test hiện có |

**Còn thiếu mà plan CLI 05 đã có sẵn:** `--json` schema (OP-4), protocol versioning (mục 12), progress event.

---

## 7. ~~Mâu thuẫn~~ / điểm chưa rõ trong draft cần tác giả chốt

| # | Câu hỏi | Gợi ý (nghiêng về) |
|---|---|---|
| Q-1 | Cấp thứ 3 của config: `default, config, ___` là gì? (`env` / `CLI args` / `profile`?) | **`CLI args`** (args override config override default — quy ước chuẩn) |
| Q-2 | Transport daemon: **stdio (plan CLI 05)** hay **cổng local TCP (draft)**? | **TCP local** cho `serve`; giữ `--json` stdio cho gỡ lỗi |
| Q-3 | "Controller gọi CLI thuật toán": **in-process** hay **subprocess**? | **In-process** giai đoạn đầu (entry của version + discovery); subprocess khi hybrid/MA4 |
| Q-4 | Recovery làm **mức nhẹ trước** hay chờ **đầy đủ**? | **Mức nhẹ (MA2)**: ledger + lastTid + replay file |
| Q-5 | "Mức dùng tài nguyên" giai đoạn đầu: **worker count** hay phải **% CPU**? | **Worker count** (≈0.4×cores); % chính xác ở MA4 |
| Q-6 | `info` schema: cần chốt danh mục manifest tối thiểu | tên/mô tả/lệnh hỗ trợ/format đầu vào/tham số config/protocol version |

---

## 8. Điều chỉnh đề xuất cho plan CLI 05 (nếu bạn đồng ý)

1. **OP-1/OP-2:** "daemon `serve` mở **cổng local TCP** (JSON)" thay "JSONL stdin/stdout"; giữ `--json` one-shot. Ghi rõ: remote LAN → **không auth, rủi ro chấp nhận**.
2. **OP-3:** đổi mô tả thành "controller gọi **entry/CLI của thuật toán** qua **discovery plugin**; **in-process giai đoạn đầu**; subprocess khi hybrid (MA4)" — hợp nhất FLOW-B + OP-3.
3. **Thêm lệnh quản trị** vào bộ lệnh controller: `info`, `version`, `module --list`, `config get/set` — tách khỏi 5 lệnh mining (P5). Thêm milestone nhỏ trong MA1.
4. **MA2:** thêm JobLedger + recovery mức nhẹ; "mức dùng → worker count".
5. **MA4:** recovery đầy đủ, quota CPU/memory (native), backpressure, remote hoàn chỉnh — một mốc cho **hybrid + tài nguyên** (đúng CLI-E).
6. **Đối chiếu:** ghi kết quả phân tích này vào plan CLI 05 (mục "Đối chiếu draft tác giả", hoặc giữ 05 làm phụ lục).

---

## 9. Kết luận ngắn

- **Áp dụng được, không phá vỡ gì:** FLOW-C đã đúng với code; FLOW-B/CLI-D chỉ cần **thêm discovery**; CLI-E khớp MA của plan CLI 05.
- **Mới cần thêm:** config (CLI-A/C) + `info`/`version`/`module --list` (CLI-B) + recovery (SYS-A) + remote cổng local (FLOW-A) — đều tách được vào các mốc (nhẹ trước, nặng theo MA4).
- **Rủi ro đáng lưu ý nhất:** scope creep nếu đưa hết hybrid/recovery/remote hoàn chỉnh/quota CPU vào giai đoạn đầu → chính draft (CLI-E) đã chỉ đạo tránh điều này.
- **Cần bạn chốt:** Q-1..Q-6 (§7); sau đó tôi cập nhật plan CLI 05 theo §8 (hoặc theo ý bạn sửa).

*File này là phụ lục phân tích cho `04-BACKEND-CLIAPI-PLAN.md`. Draft `.txt` của bạn giữ nguyên — tôi không xóa (chờ bạn chốt).*