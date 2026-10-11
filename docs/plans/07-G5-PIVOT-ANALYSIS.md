# DHOPM — Phân tích chuyển hướng ưu tiên: cổng CL/API (G5) trước G4

> **Mục đích:** ghi lại một cách khách quan **vì sao nên triển khai G5 (Manager CLI/API) trước G4 (V4 Extreme)**, kèm bằng chứng và đề xuất.
>
> **Cấu trúc:** tài liệu chỉ gồm **3 mục** theo yêu cầu — `1. Hiện trạng` · `2. Mâu thuẫn` · `3. Đề xuất`.
>
> **Đọc kèm:** `plans/00-OVERALL-PLAN.md` (§1.1, §4.2–4.3, §8) · `plans/05-BACKEND-CLIAPI-PLAN.md` · `phases/P5-G5.md` (kế hoạch G5) · `plans/08-FE-CONTRACT.md` · `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` · `reports/G3-V3-OPTIMIZED-BAOCAO.md` · `DECISIONS.md`.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-007 |
| **Version** | 0.3 (Draft) |
| **Trạng thái** | 🕔 Draft — đề xuất; **`D45–D49` đã được tác giả chốt (2026-10-11)** |
| **Bản chất** | Tài liệu **chẩn đoán + đề xuất**, không phải nguồn chân lý quyết định (`DECISIONS.md` thắng) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Tạo mới: chuyển ưu tiên sang G5 vì **V3 khi đó không biên dịch** (tiền đề nay đã sai) |
| 0.2 | **Cập nhật sau khi V3 hoàn tất (2026-10-11):** A. Tiền đề "V3 hỏng" **đã bị loại bỏ** — G3 xong (121 test, V3 nhanh hơn V2 2.3–28×); B. **Đổi đề xuất thành "G5 trước G4"**; C. Bổ sung trạng thái V3; D. Đánh dấu các mâu thuẫn cần chốt (mục 2.7) |
| **0.3** | **Tác giả đã trả lời (2026-10-11):** mục 2.7 **M1–M5 đã chốt**; Q1–Q4 (3.7) **đã có đáp án**; đề xuất 3.1 thành **`D45`**; transport chốt **TCP `7079`** (**`D46`**, thay thế mục §6 bị bác bỏ); CLI/log/file/config chốt **`D47`–`D49`** |

---

## 1. Hiện trạng

### 1.1 Trạng thái các giai đoạn

| Giai đoạn | Nội dung | Trạng thái thực tế (có bằng chứng) |
|---|---|---|
| **G0** | Khung Maven, `dhopm-common`, TestKit | ✅ Xong |
| **G1** | V1 Standard (oracle, GoF, `minOcc=0`) | ✅ Xong — **32 test xanh**, CLI 5 lệnh, đóng băng |
| **G2** | V2 MinOcc/Window | ✅ Xong — **18 test xanh** (+55 common), CLI 10 lệnh |
| **G3** | V3 Optimized (tối ưu cấu trúc DL, Level 2) | ✅ **Xong (2026-10-11)** — **16 test V3**; `minOcc=0 ≡ V1/V2`, `minOcc>0 ≡ V2` bit-for-bit; **nhanh hơn V2 2.3–28×**, heap thấp hơn |
| **G4** | V4 Extreme | 🕓 Chưa code |
| **G5** | Manager CLI/API | 🕓 **Chưa có module** — đã có plan `05`, phân tích `06`/`07`, kế hoạch `phases/P5-G5`, hợp đồng `08`; **phạm vi đã chốt (`D45–D49`)** |
| **G6** | App debug/compare | 🕓 Chưa code, phụ thuộc G5 |

**Số liệu kiểm chứng được (2026-10-11):**

| Lệnh | Kết quả |
|---|---|
| `mvn -f implementation\pom.xml test` (toàn reactor) | ✅ **BUILD SUCCESS** — 55 + 32 + 18 + 16 = **121 test xanh** |
| `run.bat` (mvn test toàn reactor) | ✅ **thành công** |

*(Nguồn: `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` §1, §4; `reports/G3-V3-OPTIMIZED-BAOCAO.md` §3, §5.)*

### 1.2 Hạ tầng giao tiếp đã có

| Hạng mục | Trạng thái |
|---|---|
| API mở `dhopm-common` | ✅ `Engine`, `MineResult`, `Pattern`, `MiningConfig`, `WindowInfo`, `PhaseAwareEngine`, `ProgressAwareEngine`, `WindowAwareEngine`, `TimedEngine`, `WorkerPool`, `TimingRecorder` |
| Công thức & validator dùng chung | ✅ `WindowMath` (`W`, `Z`, `N_eff`, `minSup`, miền `∂`) + `ParameterValidator` (mã ổn định) |
| CLI V1 | ✅ 5 lệnh: `mine/detail/stream/golden/inspect`, `--limit` |
| CLI V2 | ✅ 10 lệnh: thêm `window/validate/sweep` |
| CLI V3 | ✅ có entry riêng (`dhopm.v3.cli.Main` + `Mine/Inspect/Window/Validate`), **chưa phủ đủ bộ lệnh** và chưa có docs module (M6) |
| **Manager CLI/API** | ❌ **chưa có** — chưa có module `dhopm-cli`, chưa có giao thức, chưa có registry/job/session |
| Progress/Window event | ✅ có `MiningProgressListener`, `WindowListener` (đủ để hiển thị tiến trình + cửa sổ) |

### 1.3 Phía Frontend (FE)

- FE (`javanc`, Tâm) hiện dùng **`TsonV1Bridge`** — `import dhopm.v1.engine.MiningEngine`, tạo engine **trong process FE**, gọi `loadBatch`/`mineNow` trực tiếp; kèm `EngineFactory`/`EngineMode` để (dự kiến) thêm `TsonV2Bridge`, … (`plans/05` §3).
- FE cũng có `TamSimulationBridge` là engine demo nội bộ để animation — **hợp lệ**, không liên quan backend.
- ⇒ FE đang là **client nội bộ của từng version**, không phải "chỉ gửi yêu cầu".

### 1.4 Giá trị đã đo được

| Dataset | V1 | V2 (`minOcc=1e-6`) | V3 | Ghi chú |
|---|---|---|---|---|
| `retail.dat` (88 162 tx) | 291–802 ms | 17–115 ms | **39 ms** | V3/V2 ≈ 2.9× (tập thưa) |
| `kosarak.dat` (200 000 tx) | 1 946–2 009 ms | 95–193 ms | **39 ms** | V3/V2 ≈ 2.4× |
| tập dày (`mushroom`,`newMushroom`,…) | — | 841–14 984 ms | **83–536 ms** | V3/V2 ≈ **10–28×** |
| Peak heap `retail` | 301 MB | 227 MB | **144 MB** | V3 nhẹ nhất |

*(Nguồn: `reports/G3-V3-OPTIMIZED-BAOCAO.md` §5; `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` §4.)*

---

## 2. Mâu thuẫn

### 2.1 Lộ trình "G3 → G4 → G6, G5 song song" vs thực tế đã có 3/4 version

Kế hoạch tổng thể xếp **V1 → V2 → V3 → V4** rồi mới tới app G6, với **G5 song song G3/G4**. Nay **G3 đã xong và tối ưu rõ rệt** (121 test, nhanh hơn V2 2.3–28×). Theo lộ trình, bước tiếp là **G4 (V4 Extreme)** — tức **thêm tối ưu hoá** trong khi **cổng giao tiếp G5 vẫn trống**. Đây là mâu thuẫn giữa **thứ tự kế hoạch** và **giá trị sử dụng được**: tối ưu thêm nhưng không có kênh nào để người dùng/FE nhìn thấy.

### 2.2 Tối ưu đã đạt mốc, phần tối ưu tiếp có lợi ích biên giảm

- V3 đã **vượt xa** mốc G3 ("nhanh hơn V2 ≥3/4 dataset"): tỉ lệ nhỏ nhất đo được **2.3×**, cao nhất **28×**.

| Điểm | Yêu cầu audit (2026-10-10) | Thực tế sau viết lại (2026-10-11) |
|---|---|---|
| Build/test | không biên dịch, 0 test | ✅ BUILD SUCCESS, **16 test V3**, 121 toàn reactor |
| D39 (slot tái dùng) | vi phạm | ✅ giữ `tid` gốc, liveness đúng |
| `W=∞` (`minOcc=0`) | ép `long`→`int`, OOM | ✅ cờ `infinite`, không cấp phát mảng |
| Sort canonical (C3) | giảm dần | ✅ tăng dần stable |
| DUBO bound | group toàn cục | ✅ per-node `min(DUBO,Z(X))` |
| Hiệu năng vs V2 | chậm hơn | ✅ **nhanh hơn 2.3–28×**, heap thấp hơn |

⇒ Kỳ vọng hợp lý của **G4** là biên lợi ích nhỏ hơn nhiều so với bước V2→V3 vừa rồi (V3 đã lấy phần lớn lợi ích SoA + bỏ `Handle` + cắt công việc dư). Trong khi đó, **không có cổng G5** thì không version nào (kể cả V3 tốt) tới được người dùng.

### 2.3 Hợp đồng FE ↔ BE vs quyết định D7

`DECISIONS.md` **D7** chốt: FE qua **một Manager CLI/API duy nhất**, hợp đồng = **đặc tả giao thức**, không share class Java. Thực tế FE lại **Maven-dependency vào `dhopm-common`/`dhopm-v1-standard`** và gọi engine trực tiếp. Đây là **mâu thuẫn trực tiếp** với D7 và với nguyên tắc F1/F2 của plan tổng thể.

### 2.4 "G5 song song G3/G4" trên thực tế là "G5 không tồn tại"

Plan tổng thể nói G5 làm song song, nhưng:
- Chỉ mới có **tài liệu** G5 (`05`/`06`/`07`/`P5-G5`/`08`); **không có module `implementation/dhopm-cli`** (parent POM chỉ có common/v1/v2/v3/bench).
- Không có hợp đồng giao thức chốt cho FE (đang ở dạng bản nháp — xem mục 2.7).

⇒ "Song song" chỉ đúng trên giấy.

### 2.5 Giá trị của V2/V3 không thể chứng minh nếu thiếu cổng G5

Điểm mạnh của V2/V3 (cửa sổ minOcc, nhanh 2.3–28×, bộ nhớ phẳng theo N) chỉ **thuyết phục khi người dùng thấy được**. Không có Manager + FE, phần giá trị này **không có bề mặt trình bày** ⇒ tiếp tục đổ công vào G4 (chỉ là tối ưu kỹ thuật) sẽ **không tạo giá trị nhìn thấy được**.

### 2.6 Tài liệu vs thực tế

- `plans/00` còn ghi G5 "Draft"; G5 vẫn **chưa có module**.
- **Hai bản nháp mới** của tác giả (`plans/Draft FE Contact.txt`, `plans/Draft mining comment.txt`) đặt thêm yêu cầu cho G5 (transport & khám phá BE, lệnh mining/step, `--detail`, `isLog`, tải file kết quả về FE). **Đã chốt & phản ánh vào `05`/`08`/`P5-G5` (2026-10-11)** — xem 2.7.

### 2.7 Mâu thuẫn đã chốt (transport / lệnh / log)

| # | Mâu thuẫn | Nguồn trái chiều | ✅ Chốt (2026-10-11) |
|---|---|---|---|
| M1 | **Transport**: stdio JSONL vs **TCP `localhost:7079`** + khám phá LAN + `connect` | `DECISIONS.md` §6 (bác bỏ daemon TCP) vs `Draft FE Contact.txt` | **TCP `7079` chính + giữ stdio** (`D46`, thay thế mục §6) |
| M2 | **Chạy độc lập/remote**: BE mở cổng chờ, FE tự dò localhost → LAN | `D34` chưa chốt vs `Draft FE Contact.txt` | **Có remote** — BE `serve` luôn mở cổng (`D46`, giải quyết `D34`) |
| M3 | **Lệnh mining**: `mine` luôn in **danh sách DOPs**; `detail` thêm window; `stream --step`; `--detail 0/1/2` | `Draft mining comment.txt` vs `D27` + `Mine Commend.md` | **Cú pháp Hybrid + `stream --step` + `--detail 0/1/2`** (`D47`) |
| M4 | **Config CLI**: `config get/set/list`, `isLog`/`export` | `Draft mining comment.txt`/`Document/CLI` vs `DECISIONS.md` §6 | **Config 3 cấp** `default < JSON < args` (`D49`); `isLog`/`--export` (`D48`) |
| M5 | **Lưu/đọc file**: `mine_<Date>_<NNN>.txt`, `fetch`, FE **sao lưu cục bộ** | 2 bản nháp vs hợp đồng `08` (chỉ JSON) | **`isLog`/`--export` + `fetch` + FE mirror** (`D48`) |
| M6 | **Discovery (SPI) & Recovery** | `06`/draft vs `DECISIONS.md` §6 | **Giữ bác bỏ** (`DECISIONS.md` §6) |

→ **Tất cả đã chốt, ghi ở `DECISIONS.md` `D45–D49`.** `M6` giữ nguyên (bác bỏ).

---

## 3. Đề xuất

### 3.1 Chuyển ưu tiên: làm **G5 trước G4** (đã chốt = `D45`)

**Nội dung đề xuất:** tạm hoãn **G4 (V4 Extreme)**; dựng **Manager CLI/API (`dhopm-cli`) + hợp đồng FE** trước. Lý do:

1. **Không phụ thuộc thuật toán** — G5 chỉ dùng API `dhopm-common` đã ổn (D6), và V1/V2/V3 đều đã sẵn sàng.
2. **Mở khoá FE ngay** — gỡ mâu thuẫn D7, cho Tâm làm client độc lập.
3. **Mở khoá app G6** và tạo **bề mặt trình bày** cho giá trị V1/V2/V3 đã đo.
4. **Lợi ích biên của G4 thấp hơn** — V3 đã lấy phần lớn lợi ích tối ưu; trong khi G5 là mảnh còn thiếu duy nhất chặn giá trị người dùng.

> Đây là quyết định thứ tự, **không** phải từ bỏ V4. V4 quay lại sau (mục 3.5).

### 3.2 Ghi nhận đúng trạng thái V3 (không còn bước "gỡ V3 khỏi reactor")

- **Không** cần sửa `<modules>` hay gỡ V3: toàn reactor **BUILD SUCCESS 121 test**.
- **Bổ sung còn thiếu của V3 (M6):** bộ tài liệu module `dhopm-v3-optimized/docs/` + phủ đủ bộ lệnh D27 trong CLI V3 (hiện mới có `mine/inspect/window/validate`).
- Ghi V3 là **version sẵn sàng** trong `VersionRegistry` của Manager (kèm V1/V2; V4 khi xong).

### 3.3 Dựng Manager `dhopm-cli`

Theo `phases/P5-G5.md` (đã chỉnh theo các chốt ở 2.7):
- **One-shot** `--json` cho test/automation; **`serve`** (JSONL); **TCP `7079`** cho remote (D46).
- `VersionRegistry` (hardcode v1..v4 — YAGNI) + `CommandDispatcher` + `JobManager` + `ResultAggregator` + `SessionManager` + `ParameterEcho` + `ResultStore`.
- Phủ bộ lệnh chuẩn D27 + `info`/`version`/`config`/`fetch`, cú pháp **Hybrid** + `stream --step`/`--detail` (D47), lưu file (D48).
- **Không** làm SPI, recovery, quota CPU (đã bác bỏ ở `DECISIONS.md` §6).

### 3.4 Chốt **hợp đồng giao thức** làm ranh giới FE ↔ BE

- Sản phẩm chính: `plans/08-FE-CONTRACT.md` — đặc tả **input/output**, **ràng buộc**, **mã lỗi**, **các client (FE)**, **version protocol**; đã bổ sung theo `Draft FE Contact.txt` (transport 7079, khám phá BE, `fetch`) và `Draft mining comment.txt` (lệnh/log/`isLog`).
- **Transport (đã chốt):** **TCP `7079` + giữ stdio** (D46) — `DECISIONS.md` đã **mở lại** mục "daemon TCP/remote LAN" bị bác bỏ ở §6 và **giải quyết `D34`**.
- Nguyên tắc giữ: `(∂, f, minOcc)` **bắt buộc** (D28/D41); double đầy đủ (C4); item canonical (C6); `minSup = ∂ × N_eff` (D20); multi-version cùng `(∂, f, minOcc)`, khác ⇒ lỗi.

### 3.5 Điều kiện để quay lại G4 (V4 Extreme)

Chỉ sau khi G5 đạt mốc tối thiểu (đủ cho FE hoạt động), xét G4:
1. G4 vẫn phải dùng đúng 4 trụ như V3: đúng đắn bit-for-bit (N1), nhanh hơn (N2), memory ≤ V3 (N3), bỏ lớp trung gian (N9).
2. Chốt tiêu chí định lượng (speedup ≥ x%, memory ↓ ≥ y%) và microbenchmark từng điểm trước khi công bố.
3. Đo peak memory bằng JMX/JFR; tinh chỉnh ngưỡng song song (việc còn lại của V3).

### 3.6 Rủi ro & giảm thiểu của đề xuất

| Rủi ro | Giảm thiểu |
|---|---|
| Hoãn G4 làm V4 "mục ruỗng" | Ghi rõ đây là **hoãn**, không phải bỏ; có điều kiện quay lại (3.5) |
| FE vẫn dùng bridge trực tiếp | Test contract: FE **chỉ** dùng `BackendClient`, 0 `import dhopm.*` |
| Protocol đổi khi V4 vào | Version hoá protocol; chỉ thêm field/cmd, không bỏ cũ |
| Transport remote chưa chốt (`D34`) | **Đã chốt** `D46` (TCP 7079); cập nhật `DECISIONS.md`; test khám phá localhost→LAN |
| Lặp lại scope creep (recovery/SPI) | Bám `DECISIONS.md` §6 (đã bác bỏ); config 3 cấp **đã được chấp nhận** (`D49`) |

### 3.7 Câu hỏi tác giả — **đã có đáp án (2026-10-11)**

| # | Câu hỏi | ✅ Đáp án |
|---|---|---|
| Q1 | Chốt `D45` (ưu tiên G5 trước G4)? | **Có** → `D45` |
| Q2 | `D34`: FE chạy **máy khác** ⇒ mở **TCP 7079 + khám phá LAN**? | **Có** → `D46` (thay thế §6) |
| Q3 | Lệnh/kết quả mining: luôn in DOP list; `stream` vs `step`; `--detail 0/1/2` | **Có** → `D47` (Hybrid; `stream --step`; `--detail 0/1/2`) |
| Q4 | Cơ chế lưu/đọc file (`isLog`, `mine_<Date>_<NNN>.txt`, `fetch`, FE mirror) | **Có** → `D48`; config 3 cấp → `D49` |

**Đối chiếu lựa chọn tác giả:** positional `dataset/partial/factor/limit` + `--minOcc` bắt buộc; top-level `detail`/`stream`; `--export` ghi đè `isLog`; `<NNN>` reset theo ngày; `fetch` qua TCP + FE mirror bắt buộc.

---

*Kết thúc phân tích. Kế hoạch thực thi: `phases/P5-G5.md`. Hợp đồng giao thức: `plans/08-FE-CONTRACT.md`. Quyết định: `DECISIONS.md`.*
