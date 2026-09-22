# DHOPM – Software Requirements Specification (Tổng thể)

> Đặc tả yêu cầu **mức hệ thống**: 3 project engine (V1–V3) + harness benchmark + debug/compare app kết nối chúng. Đây là tầng "yêu cầu" của bản đồ tài liệu (xem `docs/plans/00-OVERALL-PLAN.md` mục 4.1). Mỗi giai đoạn khi triển khai sẽ có **SRS/đặc tả riêng** của giai đoạn đó; tài liệu này là khung yêu cầu chung.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-SRS-000 |
| **Version** | 0.1 (Draft – planning) |
| **Phụ thuộc** | `docs/plans/00-OVERALL-PLAN.md` (canonical C1–C6, threading, dataset) ; `docs/srs/DHOPM-UI-LAYOUT.md` |
| **Source** | `docs/root/1-s2_0-….md` ; `docs/root/Nhom01_VDChayTay.md` |

---

## 1. Giới thiệu

### 1.1 Mục đích hệ thống

Cung cấp một nền tảng triển khai **3 phiên bản thuật toán DHOPM/DOPM** (Standard, Optimized, Extreme), có khả năng:
1. Khai phá DOP patterns đúng theo canonical spec (TC1–TC8, kết quả 3 phiên bản trùng khớp).
2. **Đo lường & so sánh** hiệu năng (runtime, memory, throughput, latency theo batch, scalability).
3. **Trực quan hóa, so sánh và debug** nội bộ thuật toán qua một app đồ hoạ — app được thiết kế như **debug app**: người dùng **có thể thao tác, sửa đổi lại các chi tiết trong thuật toán** (tham số, bật/tắt quyết định, công thức, thứ tự duyệt, cấu trúc dữ liệu) và quan sát ảnh hưởng lên kết quả/hiệu năng.

### 1.2 Phạm vi

- **Trong:** 3 engine khai phá; input FIMI + text; mô phỏng stream incremental; benchmark; xuất kết quả; GUI so sánh/debug; bộ test chuẩn TC1–TC8.
- **Ngoài:** chi tiết thiết kế class (plan 01/02/03), chi tiết bố cục UI (UI-LAYOUT), triển khai cho hệ phân tán/cluster, các thuật toán khác ngoài DOPM/DHOPM.

### 1.3 Người dùng hệ thống

| Vai trò | Nhu cầu chính |
|---|---|
| Sinh viên / nhà nghiên cứu | Chạy thuật toán, so sánh 3 phiên bản, xuất kết quả dạng bảng/biểu đồ |
| Kỹ sư thuật toán | Debug cấu trúc nội bộ (DHO-List, DO, DUBO, conditional list, trace DFS), **thử sửa chi tiết thuật toán** và xem ảnh hưởng |
| Giảng viên / đánh giá | Tái lập kết quả trên dataset chuẩn, xem báo cáo benchmark |

### 1.4 Thuật ngữ viết tắt

DOP, DO, DUBO, DHO-List, TL, `∂`, `f`, minSup, FIMI, TID — theo canonical spec trong plan tổng thể.

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
- **Trùng kết quả:** 3 engine cho tập DOP giống nhau (INV-E, C5).
- **Cô lập:** engine không "rò rỉ" tối ưu cho nhau; `dhopm-common` chỉ chia input/hạ tầng đo.
- **Đo được:** mọi trạng thái chạy đều ống ghi metric chuẩn (3 giai đoạn).

### 2.3 Mốc chuẩn hóa

Canonical spec C1–C6, bất biến INV-A..E, pipeline GĐ1–GĐ3 — **bắt buộc** cho cả 3 engine (đã định nghĩa tại plan tổng thể).

---

## 3. Yêu cầu chức năng (mức tổng thể)

| ID | Yêu cầu | Ưu tiên | Giai đoạn |
|---|---|---|---|
| FE1 | Nạp dữ liệu: text (TID tường minh) cho TC; FIMI (TID = số dòng) cho benchmark; bỏ dòng trống/`#`; transaction rỗng bị loại | Cao | G0 |
| FE2 | Cấu hình: `∂`, `f`, `ε`, worker count, (V2) cấu hình cấu trúc/tối ưu, (V3) cấu hình pipeline | Cao | G0/G1/G2/G3 |
| FE3 | `loadBatch(batch)`: cập nhật global DHO-List, tiến stream (one-scan); `mineNow()`: reconstruction + DOP mining theo canonical | Cao | G1–G3 |
| FE4 | Output kết quả: tập pattern kèm DO (+ occurrences tuỳ chọn); chuẩn hoá thứ tự hiển thị (C6) | Cao | G1 |
| FE5 | Mô phỏng incremental: chia dataset thành 5 phần, nạp tuần tự, đo mỗi bước | Cao | G1 |
| FE6 | Đo & ghi metric: runtime 3 giai đoạn, peak memory, throughput, latency batch, scalability | Cao | G1/G2 (`dhopm-bench`) |
| FE7 | So sánh chéo 3 engine: cùng dataset/tham số, hiển thị bảng + biểu đồ (chi tiết: UI-LAYOUT) | Cao | G4 |
| FE8 | Xuất dữ liệu: CSV/JSON (kết quả DOP + metric) | TB | G2/G4 |
| FE9 | **Debug nội bộ:** xem global DHO-List (node: item, support, entries/TID), DO từng node, DUBO của prefix, nội dung conditional list, trace DFS (nhánh mở rộng/prune/skip), số node khám phá | Cao | G4 |
| FE10 | **Sửa đổi chi tiết thuật toán tại runtime (debug app):** bật/tắt skip khi `support < minSup` (C2), bật/tắt prune DUBO, bật/tắt chèn kết quả, chọn cách DUBO (C1), chọn tie-break/sort stable (C3), thay `∂/f/ε`, chọn threading level & worker count, (V2) chọn cấu trúc dữ liệu/decay lookup, (V3) chọn pipeline — rồi **chạy lại** và xem khác biệt kết quả/hiệu năng | Cao | G4 (plan chi tiết tại G4) |
| FE11 | Xem dữ liệu thô: transaction theo TID, độ dài, item tham gia | TB | G4 |

> Lưu ý: FE9–FE10 là lý do các engine cần **module hoá ranh giới** (contract `Engine` + đạt “điểm sửa đổi” rõ ràng) ngay từ G1–G3, để G4 bọc được GUI lên trên mà không đụng logic lõi.

---

## 4. Yêu cầu phi chức năng

| ID | Yêu cầu | Cách đánh giá |
|---|---|---|
| NFR-C | **Correctness:** TC1–TC8 xanh; tập DOP 3 phiên bản trùng nhau (double đầy đủ) | TestKit / DeterminismAssert |
| NFR-D | **Determinism:** cùng input/tham số → cùng output bất kể pool size | chạy lại nhiều lần, pool {1,2,4,cpu} |
| NFR-P | **Performance:** V2 ≥ V1, V3 ≥ V2 trên ≥3/4 dataset (hoặc phân tích rõ) | benchmark 5.2 |
| NFR-M | **Memory:** peak memory đo được; V2/V3 ≤ V1 (mục tiêu) | JMX/JFR |
| NFR-S | **Scalability:** runtime/memory theo số transaction (kosarak 200K→990K) | benchmark |
| NFR-T | **Thread-safety:** không race; gộp kết quả deterministic | tests + chạy lặp |
| NFR-U | **Usability (G4):** thao tác trực quan; các biểu đồ so sánh đúng trọng tâm; filter/tìm kiếm; export | review G4 |
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
| CLI (engine/bench) | Tham số: `dataset, ∂, f, workers, epsilon, [options version]`; in/ghi kết quả + metric | G1–G3 |
| File | Đọc dataset; xuất CSV/JSON | G1–G3/G4 |
| GUI (debug/compare app) | So sánh 3 engine + debug nội bộ + sửa chi tiết thuật toán — chi tiết ở UI-LAYOUT | G4 |

---

## 7. Ràng buộc & Giả định

- Ràng buộc: **Java 17 LTS**; môi trường dev **Windows**; build **Maven (đa module)** (đang chốt D1–D2).
- Giả định: dữ liệu vừa bộ nhớ máy dev hiện tại; dataset FIMI đã có sẵn; không cần phân tán.
- Quyết định mở D1–D5 của plan tổng thể là đầu vào ràng buộc khi triển khai G0.

---

## 8. Điều kiện nghiệm thu mức hệ thống

- [ ] TestKit TC1–TC8 xanh trên cả 3 engine.
- [ ] INV-E: tập DOP trùng nhau (so double đầy đủ).
- [ ] Benchmark đầy đủ trên 7 dataset cục bộ + scalability kosarak.
- [ ] Bộ tài liệu từng project engine đầy đủ.
- [ ] (G4) App cho phép so sánh + sửa đổi chi tiết thuật toán theo FE9/FE10.

---

## 9. Yêu cầu Debug/Compare App (ghi nhận cho G4 — plan chi tiết lập sau)

> Giai đoạn G4 sẽ lập plan/thiết kế riêng. Tại đây chỉ chốt **yêu cầu mức hệ thống** để các giai đoạn trước giữ thiết kế tương thích:

- **So sánh:** 3 engine cùng dataset/tham số — bảng giá trị, biểu đồ; xem khác biệt chi tiết (FE7).
- **Debug nội bộ:** các "điểm khảo sát" thuật toán hiển thị được (DHO-List, DO, DUBO, conditional list, trace DFS, counters) (FE9).
- **Sửa đổi chi tiết thuật toán:** các thuộc tính/quyết định canonical (C1–C6) và cấu trúc phơi bày thành tham số để bật/tắt/sửa, chạy lại lập tức, so sánh delta kết quả & hiệu năng (FE10).
- Ràng buộc kỹ thuật GUI (JavaFX/Swing, v.v.) → **quyết định tại G4**.
- Yêu cầu này **không làm tăng yêu cầu của G1–G3** ngoài việc giữ module hoá ranh giới (mục 3 — note FE9/FE10).

---

*Kết thúc SRS tổng thể. Chi tiết trình bày ở `DHOPM-UI-LAYOUT.md`; chi tiết giai đoạn ở `docs/plans/01|02|03` + `docs/phases/` (tạo dần).*