# DHOPM – Software Requirements Specification (Tổng thể)

> Đặc tả yêu cầu **mức hệ thống**: 4 project engine (V1–V4) + harness benchmark + debug/compare app kết nối chúng. Đây là tầng "yêu cầu" của bản đồ tài liệu (xem `docs/plans/00-OVERALL-PLAN.md` mục 4.1). Mỗi giai đoạn khi triển khai sẽ có **SRS/đặc tả riêng** của giai đoạn đó; tài liệu này là khung yêu cầu chung.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-SRS-000 |
| **Version** | 0.2 (Draft – planning) |
| **Trạng thái** | 🕓 Draft — chưa đồng bộ hoàn toàn với 4 phiên bản; xem `docs/DECISIONS.md` |
| **Phụ thuộc** | `docs/plans/00-OVERALL-PLAN.md` (canonical **C1–C12**, **INV-A–J**, threading, dataset) ; `docs/DECISIONS.md` ; `docs/srs/DHOPM-UI-LAYOUT.md` |
| **Source** | `docs/root/1-s2_0-….md` ; `docs/root/Nhom01_VDChayTay.md` ; `docs/Draft Idea.txt` |

---

## 1. Giới thiệu

### 1.1 Mục đích hệ thống

Cung cấp một nền tảng triển khai **4 phiên bản thuật toán DHOPM/DOPM** (V1 Standard · **V2 MinOcc/Window** · V3 Optimized · V4 Extreme), có khả năng:
1. Khai phá DOP patterns đúng theo canonical spec (TC1–TC8, kết quả **4 phiên bản trùng khớp** khi cùng tham số).
2. **Đo lường & so sánh** hiệu năng (runtime, memory, throughput, latency theo batch, scalability).
3. **Kiểm chứng hiệu quả của cửa sổ minOcc**: đo `max_X |DO_win(X) − DO_full(X)|` ≤ minOcc, số lần evict, bộ nhớ đỉnh khi stream dài.
4. **Trực quan hóa, so sánh và debug** nội bộ thuật toán qua một app đồ hoạ — app được thiết kế như **debug app**: người dùng **có thể thao tác, sửa đổi lại các chi tiết trong thuật toán** (tham số, bật/tắt quyết định, công thức, thứ tự duyệt, cấu trúc dữ liệu) và quan sát ảnh hưởng lên kết quả/hiệu năng.

### 1.2 Phạm vi

- **Trong:** 4 engine khai phá; input FIMI + text + ZIP; mô phỏng stream incremental; cửa sổ minOcc + evict; benchmark; xuất kết quả; GUI so sánh/debug; bộ test chuẩn **TC1–TC18**.
- **Ngoài:** chi tiết thiết kế class (plan 01/02/03/04), chi tiết bố cục UI (UI-LAYOUT), triển khai cho hệ phân tán/cluster, các thuật toán khác ngoài DOPM/DHOPM.

### 1.3 Người dùng hệ thống

| Vai trò | Nhu cầu chính |
|---|---|
| Sinh viên / nhà nghiên cứu | Chạy thuật toán, so sánh **4 phiên bản**, xuất kết quả dạng bảng/biểu đồ |
| Kỹ sư thuật toán | Debug cấu trúc nội bộ (DHO-List, DO, DUBO, conditional list, trace DFS), **thử sửa chi tiết thuật toán** và xem ảnh hưởng |
| Giảng viên / đánh giá | Tái lập kết quả trên dataset chuẩn, xem báo cáo benchmark |

### 1.4 Thuật ngữ viết tắt

DOP, DO, DUBO, DHO-List, TL, `∂`, `f`, `minOcc`, `ε`, `W`, `N_eff`, `Z(f,TL)`, minSup, FIMI, TID — theo canonical spec trong plan tổng thể.

---

## 2. Mô tả tổng quan

### 2.1 Thành phần hệ thống

```
┌─ Input ─────────────────────────────────────────────┐
│  TC1–TC8 (text, inline) · dataset FIMI (7 file cục bộ)│
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─ dhopm-common ──────────────────────────────────────┐
│  reader FIMI/text · MiningConfig · contract Engine  │
│  TestKit (golden TC1–TC8, IncrementalDriver, …)     │
└───────┬───────────────┬────────────────┬────────────┘
        ▼               ▼                ▼
┌─ dhopm-v1 ────┐ ┌─ dhopm-v2 ────┐ ┌─ dhopm-v3 ────┐
│ Engine(std)   │ │ Engine(opt)   │ │ Engine(ext)   │
│ + metrics     │ │ + metrics     │ │ + metrics     │
└───────┬───────┘ └───────┬───────┘ └───────┬───────┘
        └──────────────────┼─────────────────┘
                           ▼
        ┌─ dhopm-bench / dhopm-app ──────────────┐
        │  đo/ghi metric · so sánh · visualize    │
        │  debug (sửa chi tiết thuật toán)        │
        └────────────────────────────────────────┘
```

### 2.2 Nguyên tắc thiết kế

- **Deterministic:** cùng input + tham số → cùng output (bất kể workers/threading).
- **Trùng kết quả:** 4 engine cho tập DOP giống nhau khi cùng `(∂, f, minOcc, TL)` (INV-E, C5, C12).
- **Tương đương paper:** `minOcc = 0` ⇒ mọi phiên bản phải **bit-for-bit ≡ V1** (INV-I, N1).
- **Cô lập:** engine không "rò rỉ" tối ưu cho nhau; `dhopm-common` chỉ chia input/hạ tầng đo + **`WindowMath` (định nghĩa dùng chung, D22)**.
- **Đo được:** mọi trạng thái chạy đều ống ghi metric chuẩn (**4 giai đoạn GĐ0–GĐ3**).

### 2.3 Mốc chuẩn hóa

Canonical spec **C1–C12**, bất biến **INV-A–INV-J**, pipeline **GĐ0–GĐ3** — **bắt buộc** cho cả 4 engine (đã định nghĩa tại `plans/00-OVERALL-PLAN.md` §2).

---

## 3. Yêu cầu chức năng (mức tổng thể)

| ID | Yêu cầu | Ưu tiên | Giai đoạn |
|---|---|---|---|
| FE1 | Nạp dữ liệu: text (TID tường minh) cho TC; FIMI (TID = số dòng) cho benchmark; bỏ dòng trống/`#`; transaction rỗng bị loại | Cao | G0 |
| FE2 | Cấu hình: `∂`, `f`, `minOcc`, worker count, (V3) cấu hình cấu trúc/tối ưu, (V4) cấu hình pipeline. **Mọi request Manager phải khai báo tường minh `(∂, f, minOcc)`** — thiếu ⇒ lỗi (D28/D41) | Cao | G0/G1/G2/G3 |
| FE3 | `loadBatch(batch)`: cập nhật global DHO-List, tiến stream (one-scan); `mineNow()`: reconstruction + DOP mining theo canonical | Cao | G1–G4 |
| FE4 | Output kết quả: tập pattern kèm DO (+ occurrences tuỳ chọn); chuẩn hoá thứ tự hiển thị (C6) | Cao | G1 |
| FE5 | Mô phỏng incremental: chia dataset thành 5 phần, nạp tuần tự, đo mỗi bước | Cao | G1 |
| FE6 | Đo & ghi metric: runtime **4 giai đoạn**, peak memory, throughput, latency batch, scalability, **số lần evict / entry sống-chết**, **độ lệch `\|DO_win − DO_full\|`** — **độc lập với thuật toán** (đo qua wrapper ở tầng contract, không chèn hot path; tắt log = gần zero-overhead) | Cao | G1/G2 (`dhopm-bench` + util chung `dhopm-common`) |
| FE7 | So sánh chéo engine: **chọn ≥1 engine** để chạy/so sánh (min 1 = chạy đơn) trên cùng dataset/tham số; hiển thị bảng + biểu đồ (chi tiết: UI-LAYOUT) | Cao | G4 |
| FE8 | Xuất dữ liệu: CSV/JSON (kết quả DOP + metric) | TB | G2/G4 |
| FE8a | **Module hoá:** 4 algorithm là 4 module engine (mỗi module tài liệu riêng); util chung (thread/worker, benchmark, logging, `WindowMath`) dùng chung từ `dhopm-common`; UI là **module riêng (`dhopm-app`)** có **loading/mining screen** để user biết app đang mining, tránh freeze UI (chi tiết tại G4) | Cao | G1–G4 |
| FE9 | **Debug nội bộ:** xem global DHO-List (node: item, support, entries/TID), DO từng node, DUBO của prefix, nội dung conditional list, trace DFS (nhánh mở rộng/prune/skip), số node khám phá, **cửa sổ minOcc (W, N_eff, entry sống/chết)** | Cao | G4 |
| FE10 | **Bảng tham số nâng cao (debug app):** hiển thị canonical C1–C12 dạng bảng tham chiếu; cho sửa `∂/f/minOcc/workers/windowOverride`; **(V3)** chọn cấu trúc dữ liệu/decay lookup, **(V4)** chọn pipeline; khi đổi khác canonical ⇒ **cảnh báo "khác chuẩn"**. ⛔ **Không** bật/tắt từng quyết định C1–C12 như tính năng (xem `DECISIONS.md` §6) | Cao | G4 |
| FE11 | Xem dữ liệu thô: transaction theo TID, độ dài, item tham gia | TB | G4 |
| **FE12** | **Tra cứu & kiểm tra cửa sổ** (không cần dataset): `W(f,minOcc)`, `N_eff`, `minSup`, trần `Z(f,TL)`, `∂` tối đa; validator với **mã lỗi ổn định** | Cao | **G2** |
| **FE13** | **Cảnh báo tham số bất khả thi:** `minSup > Z(f,TL)` ⇒ **short-circuit O(1)** trả ∅ kèm thông điệp có số liệu, không dò DFS | Cao | **G2** |
| **FE14** | **Quét tham số (`sweep`):** nhiều tổ hợp `(minOcc, ∂, f)` → bảng #DOP / runtime / memory / độ dài TB | TB | **G2** |

> Lưu ý: FE9–FE10 là lý do các engine cần **module hoá ranh giới** (contract `Engine` + đạt "điểm sửa đổi" rõ ràng) ngay từ G1–G3, để G4 bọc được GUI lên trên mà không đụng logic lõi.

---

## 4. Yêu cầu phi chức năng

| ID | Yêu cầu | Cách đánh giá |
|---|---|---|
| NFR-C | **Correctness:** TC1–TC8 xanh; tập DOP **4 phiên bản** trùng nhau (double đầy đủ) | TestKit / DeterminismAssert |
| **NFR-EPS** | ** Sai số cửa sổ:** mọi pattern `\|DO_win(X) − DO_full(X)\| ≤ minOcc` (INV-G); khi `minOcc = 0` bất biến thành đẳng thức | đo trên dataset thật + TC18 |
| NFR-D | **Determinism:** cùng input/tham số → cùng output bất kể pool size | chạy lại nhiều lần, pool {1,2,4,cpu} |
| NFR-P | **Performance:** V3 ≥ V1, V4 ≥ V3 trên ≥3/4 dataset (hoặc phân tích rõ) | benchmark 5.2 |
| NFR-M | **Memory:** peak memory đo được; V3/V4 ≤ V1, **V2 gần như phẳng** khi stream dài (≥10× số transaction) | JMX/JFR |
| NFR-S | **Scalability:** runtime/memory theo số transaction (kosarak 200K→990K) | benchmark |
| NFR-T | **Thread-safety:** không race; gộp kết quả deterministic | tests + chạy lặp |
| NFR-U | **Usability (G4):** thao tác trực quan; các biểu đồ so sánh đúng trọng tâm; filter/tìm kiếm; export | review G4 |
| NFR-LOG | **Logging/Benchmark độc lập:** hoạt động đo/ghi thời gian không làm thay đổi hành vi và không làm thuật toán chạy chậm (instrument qua Decorator/Proxy ở tầng contract, ghi log async/buffered; tắt = zero-overhead) | kiểm tra đo chênh lệch bật/tắt log |
| NFR-FX | **UI không bị freeze (G4):** mining chạy nền (không chặn UI thread); hiển thị loading/mining screen + tiến trình | review G4 |
| NFR-SEC | Không nhúng secret; không yêu cầu mạng khi chạy | review |

---

## 5. Yêu cầu dữ liệu

- **TC1–TC8:** inline trong TestKit (text TID tường minh) — mốc đúng đắn.
- **Dataset benchmark:** 7 file FIMI cục bộ `dataset/` (chess, connect, kosarak, mushroom, pumsb, pumsb_star, retail) + ∂ gợi ý — bảng 5.2 của plan tổng thể; **validate** tại G0.
- Định dạng: mỗi dòng = transaction (int cách khoảng trắng); dòng trống/`#` bỏ qua; TID = số thứ tự dòng (1-based) cho FIMI.
- Scalability: cắt `kosarak.dat` theo số dòng; synthetic dự phòng (tuỳ chọn).

---

## 6. Giao diện hệ thống

| Giao diện | Mô tả | Giai đoạn |
|---|---|---|
| CLI (engine/bench) | Tham số: `dataset, ∂, f, minOcc, workers, [options version]`; bộ lệnh chuẩn `mine/detail/stream/golden/inspect/window/validate/sweep` (D27); in/ghi kết quả + metric | G1–G4 |
| File | Đọc dataset (FIMI / text / ZIP); xuất CSV/JSON | G1–G4 |
| GUI (debug/compare app) | So sánh **4 engine** + debug nội bộ + bảng tham số nâng cao — chi tiết ở UI-LAYOUT | G4 |

---

## 7. Ràng buộc & Giả định

- Ràng buộc: **Java 25 LTS**; môi trường dev **Windows**; build **Maven (đa module)** (D1–D2 ✅ đã chốt).
- Quyết định kiến trúc: xem `docs/DECISIONS.md` (D6 API ổn định, D7 Manager duy nhất, D27 bộ lệnh CLI, D13 4 phiên bản).
- Giả định: dữ liệu vừa bộ nhớ máy dev hiện tại; dataset FIMI đã có sẵn; không cần phân tán.

---

## 8. Điều kiện nghiệm thu mức hệ thống

- [ ] TestKit **TC1–TC18** xanh trên cả 4 engine.
- [ ] INV-E: tập DOP trùng nhau khi cùng `(∂, f, minOcc, TL)` (so double đầy đủ).
- [ ] **INV-I:** `minOcc = 0` ⇒ V2/V3/V4 **bit-for-bit ≡ V1**.
- [ ] **NFR-EPS:** `|DO_win − DO_full| ≤ minOcc` kiểm chứng trên dataset thật.
- [ ] Benchmark đầy đủ trên 10 dataset cục bộ + scalability kosarak/chainstore.
- [ ] Bộ tài liệu từng project engine đầy đủ.
- [ ] (G4) App cho phép so sánh 4 engine + debug nội bộ (FE9) + bảng tham số nâng cao (FE10).

---

## 9. Yêu cầu Debug/Compare App (ghi nhận cho G4 — plan chi tiết lập sau)

> Giai đoạn G4 sẽ lập plan/thiết kế riêng. Tại đây chỉ chốt **yêu cầu mức hệ thống** để các giai đoạn trước giữ thiết kế tương thích:

- **So sánh:** chọn **≥1 engine** (min 1 = chạy đơn) trên cùng dataset/tham số — bảng giá trị, biểu đồ; xem khác biệt chi tiết (FE7).
- **Debug nội bộ:** các "điểm khảo sát" thuật toán hiển thị được (DHO-List, DO, DUBO, conditional list, trace DFS, counters, **trạng thái cửa sổ minOcc**) (FE9).
- **Tham số nâng cao:** canonical **C1–C12** hiển thị dạng **bảng tham chiếu**; cho sửa `∂/f/minOcc/workers/windowOverride` và lựa chọn tối ưu của V3/V4; khi lệch canonical ⇒ **cảnh báo "khác chuẩn"**; chạy lại và so delta (FE10).
  - ⛔ **Không** bật/tắt từng quyết định canonical như tính năng chính — đã bác bỏ (`DECISIONS.md` §6).
- **Kiến trúc:** UI là **module riêng** (`dhopm-app`) trên nền 4 module engine; mining chạy nền, có **loading/mining screen + tiến trình** tránh freeze UI (NFR-FX); log qua util chung, không chặn UI.
- Ràng buộc kỹ thuật GUI (JavaFX/Swing, v.v.) → **quyết định tại G4**.
- Yêu cầu này **không làm tăng yêu cầu của G1–G4** ngoài việc giữ module hoá ranh giới (mục 3 — note FE9/FE10).

---

*Kết thúc SRS tổng thể (v0.2). Chi tiết trình bày ở `DHOPM-UI-LAYOUT.md`; chi tiết giai đoạn ở `docs/phases/P1-G1.md` (✅ xong) và `docs/phases/P2-G2.md` (🕔 kế tiếp).*
