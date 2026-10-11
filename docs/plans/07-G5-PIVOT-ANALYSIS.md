# DHOPM — Phân tích chuyển hướng G3 → G5 (cổng CL/API)

> **Mục đích:** ghi lại một cách khách quan **vì sao** nên tạm dừng G3 (V3 Optimized) và **triển khai G5 (Manager CLI/API) trước**, kèm bằng chứng và đề xuất.
>
> **Cấu trúc:** tài liệu chỉ gồm **3 mục** theo yêu cầu — `1. Hiện trạng` · `2. Mâu thuẫn` · `3. Đề xuất`.
>
> **Đọc kèm:** `plans/00-OVERALL-PLAN.md` (§1.1, §4.2–4.3, §8) · `plans/05-BACKEND-CLIAPI-PLAN.md` · `phases/P5-G5.md` (kế hoạch G5) · `plans/08-FE-CONTRACT.md` · `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` · `DECISIONS.md`.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-007 |
| **Version** | 0.1 (Draft) |
| **Trạng thái** | 🕔 Draft — đề xuất, chờ tác giả chốt |
| **Bản chất** | Tài liệu **chẩn đoán + đề xuất**, không phải nguồn chân lý quyết định (`DECISIONS.md` thắng) |

> ## ⚠️ ERRATA (cập nhật 2026-10-11)
>
> Tiền đề của tài liệu này — **"V3 không biên dịch, 0 test, kéo đỏ `run.bat`, chặn mọi bước sau"** (§1) — **không còn đúng**. V3 đã được **viết lại**:
> build xanh, **16 test V3**, toàn reactor **121 test**; `minOcc=0 ≡ V1/V2`, `minOcc>0 ≡ V2` bit-for-bit. Do đó **G3 không còn là nút chặn** như phân tích giả định.
> Trạng thái mới nhất: `docs/reports/G3-V3-OPTIMIZED-BAOCAO.md` · `docs/Readme.md`. Phần thân bài giữ nguyên làm hồ sơ chẩn đoán tại thời điểm 2026-10-10.

---

## 1. Hiện trạng

### 1.1 Trạng thái các giai đoạn

| Giai đoạn | Nội dung | Trạng thái thực tế (có bằng chứng) |
|---|---|---|
| **G0** | Khung Maven, `dhopm-common`, TestKit | ✅ Xong |
| **G1** | V1 Standard (oracle, GoF, `minOcc=0`) | ✅ Xong — **32 test xanh**, CLI 5 lệnh, đóng băng |
| **G2** | V2 MinOcc/Window | ✅ Xong — **18 test xanh** (+55 common), CLI 10 lệnh |
| **G3** | V3 Optimized (tối ưu cấu trúc DL, Level 2) | ⚠️ **Đang dở, KHÔNG biên dịch được (~30 lỗi), 0 test, không CLI** |
| **G4** | V4 Extreme | 🕓 Chưa code |
| **G5** | Manager CLI/API | 🕓 **Chưa có module/plan/protocol** (`docs/phases/` không có P5-G5 trước tài liệu này) |
| **G6** | App debug/compare | 🕓 Chưa code, phụ thuộc G5 |

**Số liệu kiểm chứng được:**

| Lệnh | Kết quả |
|---|---|
| `mvn -pl dhopm-common,dhopm-v1-standard,dhopm-v2-minocc test` | ✅ **BUILD SUCCESS** — 55 + 32 + 18 = **105 test xanh** |
| `mvn -pl dhopm-v3-optimized -am test-compile` | ❌ **COMPILATION FAILURE** (~30 lỗi), 0 test |
| `run.bat` (mvn test toàn reactor) | ❌ **thất bại** tại `dhopm-v3-optimized` (vì nằm trong `<modules>`) |

*(Nguồn: `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` §1, §5.)*

### 1.2 Hạ tầng giao tiếp đã có

| Hạng mục | Trạng thái |
|---|---|
| API mở `dhopm-common` | ✅ `Engine`, `MineResult`, `Pattern`, `MiningConfig`, `WindowInfo`, `PhaseAwareEngine`, `ProgressAwareEngine`, `WindowAwareEngine`, `TimedEngine`, `WorkerPool`, `TimingRecorder` |
| Công thức & validator dùng chung | ✅ `WindowMath` (`W`, `Z`, `N_eff`, `minSup`, miền `∂`) + `ParameterValidator` (mã ổn định) |
| CLI V1 | ✅ 5 lệnh: `mine/detail/stream/golden/inspect`, `--limit` |
| CLI V2 | ✅ 10 lệnh: thêm `window/validate/sweep` |
| **Manager CLI/API** | ❌ **chưa có** — chưa có module `dhopm-cli`, chưa có protocol, chưa có registry/job/session |
| Progress/Window event | ✅ có `MiningProgressListener`, `WindowListener` (đủ để hiển thị tiến trình + cửa sổ) |

### 1.3 Phía Frontend (FE)

- FE (`javanc`, Tâm) hiện dùng **`TsonV1Bridge`** — `import dhopm.v1.engine.MiningEngine`, tạo engine **trong process FE**, gọi `loadBatch`/`mineNow` trực tiếp; kèm `EngineFactory`/`EngineMode` để (dự kiến) thêm `TsonV2Bridge`, … (`plans/05` §3).
- FE cũng có `TamSimulationBridge` là engine demo nội bộ để animation — **hợp lệ**, không liên quan backend.
- ⇒ FE đang là **client nội bộ của từng version**, không phải "chỉ gửi yêu cầu".

### 1.4 Giá trị đã đo được

| Dataset | V1 | V2 (`minOcc=1e-6`) | Ghi chú |
|---|---|---|---|
| `retail.dat` (88 162 tx) | 291 ms | **17 ms (≈17×)** | cùng kết quả rỗng (DO ≤ Z ≈ 10 < minSup) |
| `kosarak.dat` (200 000 tx) | 1 946 ms | **193 ms (≈10×)** | số pattern chênh do **minSup 2 pha** (đúng ngữ nghĩa) |
| Peak memory `retail` | 180.6 MB | **119.8 MB** | |

*(1 lần, khảo sát; nguồn `reports/G2-V2-MINOCC-BAOCAO.md` §4.)*

---

## 2. Mâu thuẫn

### 2.1 Lộ trình vs thực tế: G3 là "nút cổ chai đỏ"

Kế hoạch tổng thể xếp thứ tự **G3 → G4 → G6**, với **G5 song song G3/G4**. Nhưng G3 hiện **không build**, nằm trong reactor nên **kéo đỏ cả `run.bat`**. Kết quả: mọi bước phía sau (đo V3, so ablation V1→V4, app G6) **đều bị chặn**, còn bước song song G5 lại **chưa tồn tại**. Đây là mâu thuẫn giữa **thứ tự kế hoạch** và **năng lực thực tế**.

### 2.2 "Tối ưu" vs bằng chứng: V3 đi lùi so với V2

V3 tự nhận "Optimized" nhưng theo kiểm chứng lại **kém an toàn hơn V2**:

| Điểm | V2 (đúng) | V3 (thực tế) |
|---|---|---|
| Liveness chống tái sử dụng slot (D39) | `Entry` kiểm `ref1.tid == tid` ✅ | kiểm tautology / heuristic ⇒ vi phạm D39 ❌ |
| Cửa sổ `W=∞` (`minOcc=0` ⇒ ≡ V1) | xử lý bằng `ref1=null`, không buffer ✅ | ép `long INFINITE` về `int`, cấp phát theo `W` ⇒ OOM ❌ |
| Sort canonical (C3) | tăng dần, stable ✅ | **giảm dần**, tie-break `itemId` ❌ |
| DUBO bound `min(DUBO, Z(X))` | per-node ✅ | tính theo **group toàn cục** ❌ |
| Build/test | build xanh, 18 test ✅ | ~30 lỗi, 0 test ❌ |

⇒ Không có cơ sở khoa học nào để gọi V3 là "tối ưu hơn V2" ở thời điểm này.

### 2.3 Hợp đồng FE ↔ BE vs quyết định D7

`DECISIONS.md` **D7** chốt: FE qua **một Manager CLI/API duy nhất**, hợp đồng = **đặc tả giao thức**, không share class Java. Thực tế FE lại **Maven-dependency vào `dhopm-common`/`dhopm-v1-standard`** và gọi engine trực tiếp. Đây là **mâu thuẫn trực tiếp** với D7 và với nguyên tắc F1/F2 của plan tổng thể.

### 2.4 Tài liệu vs thực tế

- `plans/00` còn ghi G3 "🕔" và G5 "Draft", trong khi thực tế **V3 đã có code dở không build** và **G5 trống**.
- `README.md` gốc / `docs/Overview.md` / `SYNTHESIS-REPORT.md` từng ghi V2 "chưa làm", V3 "chưa code" — đã được sửa một phần (`docs/Readme.md` cập nhật 2026-10-10) nhưng **G5 vẫn không có tài liệu nào**.
- Việc có **3 tài liệu G5 khác nhau** (`05`, `06`, `Draft backend CLIAPI.txt` với các phán quyết trái ngược: TCP vs stdio, SPI vs hardcode) khiến người đọc không biết đâu là mốc.

### 2.5 "G5 song song G3/G4" trên thực tế là "G5 không tồn tại"

Plan tổng thể nói G5 làm song song, nhưng:
- Không có `docs/phases/P5-G5.md` (trước tài liệu này).
- Không có module `implementation/dhopm-cli` (parent POM chỉ có common/v1/v2/v3/bench).
- Không có hợp đồng giao thức chốt cho FE.

⇒ "Song song" chỉ đúng trên giấy.

### 2.6 Giá trị của V2 không thể chứng minh nếu thiếu cổng G5

Điểm mạnh của V2 (cửa sổ minOcc, nhanh ~10–17×, bộ nhớ phẳng theo N) chỉ **thuyết phục khi người dùng thấy được**. Không có Manager + FE, phần giá trị này **không có bề mặt trình bày** ⇒ tiếp tục đổ công vào V3/V4 (chỉ là tối ưu kỹ thuật) sẽ **không tạo giá trị nhìn thấy được**.

### 2.7 Mâu thuẫn cần chốt (transport / phạm vi)

| # | Mâu thuẫn | Nguồn trái chiều |
|---|---|---|
| M1 | **Transport**: stdio JSONL vs **cổng local TCP** | `DECISIONS.md` §6 (bác bỏ daemon TCP) vs `06` §8 & `Draft`/`FLOW-A` (muốn TCP local) |
| M2 | **Discovery**: SPI/ServiceLoader vs **hardcode 4 factory** | `06`/draft (SPI) vs `DECISIONS.md` §6 (YAGNI) |
| M3 | **Recovery/JobLedger** nên làm? | `Draft`/`SYS-A` (cần) vs `DECISIONS.md` §6 (bác bỏ) |

→ Đề xuất giải theo `DECISIONS.md` cho M2/M3 (đã bác bỏ), và **để M1 phụ thuộc `D34`** (FE có chạy máy khác không?).

---

## 3. Đề xuất

### 3.1 Chuyển ưu tiên: làm **G5 trước G3/G4** (đề xuất quyết định `D45`)

**Nội dung đề xuất:** tạm hoãn G3/G4; dựng **Manager CLI/API (`dhopm-cli`) + hợp đồng FE** trước. Lý do:

1. **Không phụ thuộc thuật toán** — G5 chỉ dùng API `dhopm-common` đã ổn (D6).
2. **Mở khoá FE ngay** — gỡ mâu thuẫn D7, cho Tâm làm client độc lập.
3. **Mở khoá app G6** và tạo **bề mặt trình bày** cho giá trị V1/V2 đã đo.
4. **Chi phí nhỏ, tiêu chí rõ** — so với việc sửa ~30 lỗi V3 + khôi phục D39/canonical mà chưa chắc tạo giá trị.

> Đây là quyết định thứ tự, **không** phải từ bỏ V3/V4. V3/V4 quay lại sau (mục 3.5).

### 3.2 Gỡ chặn trước tiên: đưa V3 ra khỏi reactor

- Sửa `implementation/pom.xml`: **loại `dhopm-v3-optimized` khỏi `<modules>`** (giữ code trên nhánh/draft), để `run.bat` trở lại **xanh với 105 test**.
- Cập nhật tài liệu trạng thái: ghi rõ **"V3 tạm gỡ khỏi reactor"**, không nói "đã xong".
- Đây là thao tác kỹ thuật **có thể đảo ngược**, không phải phán quyết về V3.

### 3.3 Dựng Manager `dhopm-cli`

Theo `phases/P5-G5.md`:
- **One-shot** `--json` (stdio) cho test/automation; **`serve`** (JSONL) cho phiên stream.
- `VersionRegistry` (hardcode v1..v4 — YAGNI) + `CommandDispatcher` + `JobManager` + `ResultAggregator` + `SessionManager` + `ParameterEcho`.
- Phủ bộ lệnh chuẩn D27 + `info`/`version` (tĩnh, không dò nội bộ — P1).
- **Không** làm config 3 cấp, recovery, quota CPU, SPI (đã bác bỏ ở `DECISIONS.md` §6).

### 3.4 Chốt **hợp đồng giao thức** làm ranh giới FE ↔ BE

- Sản phẩm chính: `plans/08-FE-CONTRACT.md` — đặc tả **input/output**, **ràng buộc**, **mã lỗi**, **các client (FE)**, có **version protocol**.
- Nguyên tắc: `(∂, f, minOcc)` **bắt buộc** khai báo (D28/D41); double đầy đủ (C4); item canonical (C6); `minSup = ∂ × N_eff` (D20); multi-version phải **cùng** `(∂, f, minOcc)`, khác ⇒ lỗi (không gộp).
- **Transport:** mặc định **stdio JSONL**; **chỉ mở TCP** nếu `D34` kết luận FE chạy máy khác (khi đó thêm chế độ, vẫn giữ stdio cho test).

### 3.5 Điều kiện để quay lại G3/G4

Chỉ sau khi G5-M0..M2 xong, quay lại G3 với checklist sửa đúng thứ tự (**không** gọi là "tối ưu" trước khi build xanh + test đủ):

1. Sửa build (~30 lỗi); đưa V3 lại vào reactor.
2. Khôi phục D39 (lưu `tid` gốc / generation counter) — an toàn bộ nhớ.
3. Khôi phục canonical sort (tăng dần, stable, tie-break thứ tự tạo node).
4. Sửa DUBO/`Z(X)` **per-node**; bỏ `CopyOnWriteArrayList` khi harvest.
5. Hỗ trợ chế độ `minOcc=0` (paper mode) mà **không** cấp phát `W=∞`.
6. Viết **TC1–TC18 + E1–E10**; chứng minh `minOcc=0 ≡ V1`, `minOcc>0 ≡ V2` **trước** khi công bố bất kỳ số hiệu năng nào.

*(Nguồn checklist: `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` §7.)*

### 3.6 Rủi ro & giảm thiểu của đề xuất

| Rủi ro | Giảm thiểu |
|---|---|
| Chuyển ưu tiên làm G3/V3 "mục ruỗng" | Ghi rõ đây là **hoãn**, không phải bỏ; có checklist quay lại (3.5) |
| FE vẫn dùng bridge trực tiếp | Test contract G5-M4: FE **chỉ** dùng `BackendClient`, 0 `import dhopm.*` |
| Protocol đổi khi V3/V4 vào | Version hoá protocol; chỉ thêm field/cmd, không bỏ cũ |
| Transport chưa chốt (`D34`) | Mặc định stdio; TCP là tuỳ chọn mở sau, không chặn G5-M1..M3 |
| Lặp lại scope creep (config/recovery/SPI) | Bám `DECISIONS.md` §6 (đã bác bỏ) |

### 3.7 Cần tác giả chốt

| # | Câu hỏi | Khuyến nghị |
|---|---|---|
| Q1 | Chốt quyết định `D45` (ưu tiên G5 trước G3/G4)? | **Có** — ghi vào `DECISIONS.md` |
| Q2 | Gỡ V3 khỏi `<modules>` để reactor xanh? | **Có** (thao tác đảo ngược được) |
| Q3 | Transport G5: stdio JSONL (theo `DECISIONS.md`) hay mở TCP sớm? | **Stdio**; TCP chỉ khi `D34` = FE máy khác |
| Q4 | G5 dừng ở mốc nào (M2 đủ cho FE demo, hay phải đủ M4)? | Đủ **M2** để FE bắt đầu; M4 để "chốt hợp đồng" |

---

*Kết thúc phân tích. Kế hoạch thực thi: `phases/P5-G5.md`. Hợp đồng giao thức: `plans/08-FE-CONTRACT.md`. Quyết định: `DECISIONS.md`.*
