# DHOPM – Thiết kế Bố cục UI (Debug/Compare App)

> Thiết kế **trình bày/trực quan hóa** cho debug app (giai đoạn G4): trả lời **so sánh gì, đồ thị gì, so sánh những giá trị nào, trình bày như thế nào**. Đây là tài liệu thiết kế mức hệ thống; chi tiết công nghệ GUI (JavaFX/Swing/…) và cài đặt sẽ được lập plan riêng tại G4.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-UI-000 |
| **Version** | 0.1 (Draft – planning) |
| **Phụ thuộc** | SRS `DHOPM-SRS.md` (mục 3: FE7/FE9/FE10; mục 9) ; plan tổng thể (dataset, benchmark) |
| **Triển khai** | Giai đoạn **G4** (lập plan riêng khi tới) |

---

## 1. Mục đích của màn hình App

App giải quyết 3 việc chính, tương ứng 3 khối màn hình:
1. **So sánh hiệu năng** 3 engine (V1/V2/V3) trên cùng dataset/tham số.
2. **So sánh kết quả** (tập DOP, giá trị DO) giữa 3 engine.
3. **Debug nội bộ thuật toán** + **sửa đổi chi tiết thuật toán** rồi chạy lại (debug app — FE10).

## 2. Nguyên tắc trình bày

- **Nhất quán màu theo phiên bản:** V1 = xanh dương, V2 = xanh lá, V3 = cam (applied cho mọi biểu đồ/bảng/card trên toàn app).
- **Cùng một dataset + tham số** thì các engine đang chọn luôn hiển thị cạnh nhau (grouped), không tách màn hình riêng lẻ → dễ so.
- **Bộ chọn engine:** chọn **≥1 engine** để chạy (`min 1 = chạy đơn`; chọn nhiều = chế độ so sánh). Màu theo từng version; engine không chọn bị làm mờ.
- **UI là module riêng (`dhopm-app`); mining chạy nền** (không chặn UI thread): khi đang chạy luôn hiển thị **loading/mining screen** (spinner + tiến trình theo giai đoạn) → không freeze giao diện (NFR-FX).
- Giá trị kèm đơn vị rõ (ms, s, MB, DOP/s); tooltip giải thích; hỗ trợ dark/light mode.
- Mọi bảng có export CSV/JSON và đều có filter/tìm kiếm.
- Trạng thái (đang chạy / xong / lỗi) hiển thị trên từng engine card.

## 3. Bố cục tổng thể (cửa sổ chính)

```
┌──────────────────────────────────────────────────────────────────────┐
│  Toolbar: [Dataset ▾] [∂ ▾] [f ▾] [Workers] [ε] [Engine ☑☐☐ ≥1]     │
│           [Run] [Reset] [Export…]                      [Theme ▾]  │
├──────────┬───────────────────────────────────────────────────────────┤
│ Sidebar  │            Vùng nội dung — Tab chính                      │
│ (Điều     │  ┌─────────────────────────────────────────────────────┐  │
│ khiển &   │  │ Tab1 So sánh hiệu năng │ Tab2 Kết quả │ Tab3 Debug │  │
│ chạy thử) │  │                                                     │  │
│  · chọn   │  │  <nội dung theo từng tab — dưới đây>                │  │
│    engines│  │                                                     │  │
│  · slider │  └─────────────────────────────────────────────────────┘  │
│    tham số│  │  Loading/Mining screen: (✓ spinner + giai đoạn + %)   │
├──────────┴───────────────────────────────────────────────────────────┤
│  Status bar: trạng thái chạy, thời gian, dataset, minSup hiện tại     │
└──────────────────────────────────────────────────────────────────────┘
```

- **Engine selector:** tick `≥1` phiên bản (V1/V2/V3); **1 tick = chạy đơn**, `≥2` tick = chế độ so sánh (min 1 bắt buộc).
- **Loading/Mining screen:** xuất hiện ngay khi Run — overlay dải tiến trình theo GĐ1→GĐ3, dừng phép tính nền, **không chặn UI** (NFR-FX); log hiển thị qua util chung ghi async, không làm chậm thuật toán (NFR-LOG).

## 4. Tab 1 — So sánh Hiệu năng (benchmark)

**So sánh gì:** V1 vs V2 vs V3 trên **cùng dataset + tham số**, sau khi chạy mỗi engine.

**Các giá trị so sánh & đồ thị:**

| # | Giá trị so sánh | Đồ thị | Ghi chú |
|---|---|---|---|
| G1 | Runtime tổng & chia 3 giai đoạn (load+construction / reconstruction / mining) | **Bar chart (grouped** theo giai đoạn, 3 màu engine) + bảng số | Bảng kèm % và ratio V2/V1, V3/V2 |
| G2 | Runtime theo từng batch 1–5 (incremental) | **Line chart**: trục X = phần 1/5, trục Y = ms; 1 đường/engine | Thấy latency tăng theo stream |
| G3 | Peak memory | **Bar chart** (3 engine cạnh nhau) + số MB | Đo JMX/JFR |
| G4 | Throughput | **Scatter/bar**: DOP/s trên từng dataset | Có thể để trong bảng tổng nếu ít dataset |
| G5 | Scalability (kosarak cắt 200K→990K) | **Line chart** runtime & memory theo số transaction | Trục X = size dataset |
| G6 | Số pattern DOP / số node khám phá | Bar (bảng kèm) | Kiểm chứng 3 engine cho cùng số (INV-E) |
| G7 | Chi tiết từng batch: minSup hiện tại, TL, #trans | Bảng | Ngữ cảnh cho latency |

**Trình bày:** 3–6 card tổng (runtime total, peak mem, throughput, #DOP, #node) hàng đầu; bên dưới là grid biểu đồ G1–G5 + bảng số đầy đủ (export CSV).

## 5. Tab 2 — Kết quả Khai phá (pattern so sánh)

**So sánh gì:** tập DOP + giá trị DO của từng pattern giữa 3 engine.

| # | Nội dung | Trình bày |
|---|---|---|
| P1 | Bảng pattern (items, DO, occurrences/tids, length) | Bảng sort/filter; cột "DO" hiển thị **chung kèm delta**: hiện 1 cột DO + 3 delta (V2−V1, V3−V2, V3−V1) — chuẩn: tất cả = 0 |
| P2 | Khác biệt tập hợp giữa các engine | Venn / danh sách: pattern chỉ có ở V1, chỉ V2, chỉ V3, chung cả 3 (`Set diff`) |
| P3 | Phân bố DO của các pattern | **Histogram** DO (hoặc biểu đồ điểm) |
| P4 | Phân bố độ dài pattern | **Histogram/bar** theo length | 
| P5 | Truy vấn: lọc pattern chứa item X, DO ≥ ngưỡng, length = k | Filter + search theo item id/name |
| P6 | Tìm kiếm 1 pattern cụ thể | Ô tìm kiếm "items" → hiển thị + highlight trong DHO-List (đi sang Tab3) |

**Trình bày:** toolbar filter ngang; bảng chính (P1) + mini-tabs (P2/P3/P4); dưới là bảng diff tập hợp.

## 6. Tab 3 — Debug nội bộ Thuật toán (debug app)

**Mục đích: xem + sửa chi tiết thuật toán rồi chạy lại.** Chọn engine + prefix/pattern để soi.

| # | Khu vực | Nội dung hiển thị | Thao tác (FE10) |
|---|---|---|---|
| D1 | Thuộc tính chạy | ∂, f, ε, TL, tổng trans, **minSup hiện tại** | Thay đổi ∂/f/ε → nút **Run lại** |
| D2 | **Quyết định canonical (C1–C6) dạng switch** | C1: decay trong DUBO (một factor vs per-group); C2: skip khi sup<minSup; C3: sort stable/tie-break; C4: ε; C6: chuẩn hoá pattern | Bật/tắt/sửa → nút **Run lại**; khi đổi so với canonical → **cảnh báo "khác chuẩn"** (đổi màu header) |
| D3 | Global DHO-List | Bảng node: item, support, count, DO, số entries | Click node → highlight entries (TID) |
| D4 | Entries của node | Danh sách (tid, len) | Filter theo TID |
| D5 | DUBO của prefix chọn | Các nhóm length (n_k, T_k, DUBO(k)) + DUBO cuối | Chọn prefix; xem từng group |
| D6 | Conditional list | Nội dung các node và thứ tự (không sort — INV-D) | Chọn từ trace |
| D7 | Trace DFS | Cây duyệt: node mở rộng, bị **SKIP** (sup<minSup), bị **PRUNE** (DUBO<minSup), sinh pattern | Highlight màu SKIP/PRUNE/PATTERN |
| D8 | Counters | Số node khám phá, số nhánh cắt, số pattern | Cập nhật live |
| D9 | Cấu hình threading/DS (theo engine) | workers, bật/tắt construction-parallel, (V2) chọn DS (mảng vs Set, decay lookup on/off), (V3) pipeline option | Thay đổi → Run lại; xem ảnh hưởng runtime (liên kết Tab1) |

**Trình bày:** layout 2 cột — trái: cây trace/hệ thống phân cấp (D7/D3); phải: panel chi tiết của phần tử chọn (D4/D5/D6/D8). Panel trên cùng là D1/D2 (config + switches) với nút Run. Mọi thay đổi cấu hình đều có nút **Run / Run & So sánh** để so delta với lần chạy chuẩn (chênh lệch hiển thị đỏ/xanh).

**Quan trọng:** khi bật "so sánh sau sửa", app chạy cả lần chuẩn (canonical) và lần đã sửa → hiển thị **delta DO từng pattern + delta runtime/memory** (tái dùng Tab1/Tab2).

## 7. Tab 4 — Dữ liệu thô (Xem)

| # | Nội dung | Trình bày |
|---|---|---|
| R1 | Danh sách transaction | Bảng (TID, độ dài, items), filter theo TID/range, độ dài |
| R2 | Chi tiết 1 transaction | Items phân biệt, độ dài (sau chuẩn hoá) |
| R3 | Thống kê dataset | Số trans, distinct items, avg len, histogram độ dài — đối chiếu bảng 5.2 |

## 8. Thành phần dùng chung

- **Component "Engine card"**: tên version, màu tương ứng, trạng thái chạy, thời gian, memory — dùng lại mọi tab.
- **Nút Run/Run lại**: chạy **≥1 engine** đang chọn (min 1 = chạy đơn) trên dataset + tham số hiện hành; chạy bất đồng bộ; khi đang chạy hiển thị loading/mining screen và cập nhật dần lên biểu đồ.
- **Loading/Mining screen**: overlay tiến trình (GĐ1–GĐ3, %), map sang util tiến trình từ `dhopm-common`; không chặn UI thread (NFR-FX).
- **Export**: CSV cho bảng, JSON cho toàn bộ phiên chạy (config + kết quả + metric) → tái lập được về sau.
- **Log console**: ghi theo từng giai đoạn; timestamp; **ghi async qua util chung**, không nằm trong hot path của thuật toán (NFR-LOG) và không chặn UI; hỗ trợ copy.

## 9. Phạm vi hiện tại

- Tài liệu này **chỉ là thiết kế bố cục** (planning). Không ảnh hưởng đến G1–G3 ngoài yêu cầu module hoá ranh giới engine (SRS mục 3 note).
- Công nghệ GUI, cấu trúc `dhopm-app`, màn hình chi tiết → **lập plan riêng tại G4**.

---

*Kết thúc thiết kế bố cục UI. Gắn với SRS tổng thể (FE7/FE9/FE10) và lộ trình G4 của plan tổng thể.*