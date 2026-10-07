# DHOPM / DOPM — Overview

> Cổng vào để đọc dự án. Bắt đầu ở đây, rồi đi xuống `README.md` → `docs/DECISIONS.md` → `docs/plans/00-OVERALL-PLAN.md`.

---

## 1. Dự án này là gì

Triển khai và nghiên cứu thuật toán **DHOPM / DOPM** — khai thác **DOP (Damped Occupancy Pattern)** trên **transactional stream** (dữ liệu đến dần theo thời gian), với hệ số suy giảm `f` theo độ tuổi của transaction.

Nền tảng lý thuyết bắt nguồn từ **HOP (Hierarchical Occurrence-Pattern)** và thiết kế thành **"không ràng buộc kiểu trước"** (no pre-defined schema): coi transaction như một danh sách item có thứ tự, và đo mức độ "phổ biến có suy giảm" bằng **DO (Damped Occupancy)**.

## 2. Ba khái niệm cốt lõi

| Khái niệm | Công thức | Ý nghĩa |
|---|---|---|
| **Occupancy** | `O(X, T) = |X| / |T|` | Tỉ lệ item của pattern X trong transaction T |
| **Decay factor** | `dF(T) = f^(TL − T.tid)` | Transaction cũ hơn `TL` giá trị càng nhỏ |
| **DO** | `DO(X) = Σ_T O(X,T) × f^(TL−T.tid)` | Tổng "mức phổ biến có suy giảm" của pattern X |
| **minSup** | `∂ × N_eff` | Ngưỡng để X được coi là **DOP** |

`TL` = TID lớn nhất đã thấy. Với `f = 1` thuật toán trở về **HOP kinh điển**.

## 3. Ba tầng tài liệu (đọc theo thứ tự này)

### Tầng 1 — Mốc gốc (không được sửa)
- `docs/root/1-s2_0-S095219762600792X-main.md` — **paper**
- `docs/root/Nhom01_VDChayTay.md` — **bảng chạy tay TC1–TC8**
- `docs/Draft Idea.txt` — **ý tưởng ε / cửa sổ** của tác giả (chưa có trong paper)

### Tầng 2 — Quyết định & kế hoạch
- **`docs/DECISIONS.md`** ⭐ — sổ quyết định (ADR D1–D41), nguồn chân lý
- `docs/plans/00-OVERALL-PLAN.md` — **canonical spec C1–C12, bất biến INV-A–J**, roadmap G0–G7
- `docs/plans/01…04` — plan từng phiên bản (V1/V2/V3/V4)
- `docs/plans/05-BACKEND-CLIAPI-PLAN.md` — Manager CLI/API
- `docs/plans/06-…-DRAFT-ANALYSIS.md` — phân tích bản nháp (⚠️ đã bị vượt qua)

### Tầng 3 — Yêu cầu, kế hoạch thực thi, kết quả
- `docs/srs/DHOPM-SRS.md` — yêu cầu mức hệ thống (FE/NFR)
- `docs/srs/DHOPM-UI-LAYOUT.md` — bố cục debug/compare app
- `docs/phases/P1-G1.md` ✅ · `docs/phases/P2-G2.md` 🕔 — plan từng giai đoạn
- `docs/reports/` — báo cáo kết quả từng giai đoạn
- `implementation/*/docs/` — **as-built** của từng module

> **Quy tắc:** khi hai tài liệu mâu thuẫn, tài liệu **cao hơn trong thang thắng**. Nếu vẫn bất đồng → mở `D<n>` mới trong `DECISIONS.md`, **không sửa lịch sử**.

## 4. Bốn phiên bản

| | Tên | Điểm khác biệt | Threading |
|---|---|---|---|
| **V1** | Standard | Paper + GoF, `ε = 0`, **oracle** | Level 1 |
| **V2** | Epsilon / Window | **Cửa sổ ε**, evict O(1), `minSup` 2 pha, trần DO | Level 1 |
| **V3** | Optimized | Bỏ lớp Handle, SoA/primitive, `ForkJoinPool` | Level 2 |
| **V4** | Extreme | Data-oriented thuần, pipeline 3 pha | Level 3 |

Ưu tiên: **V1 → V2 → V3 → V4**. V2 tách khỏi V3/V4 để khi so sánh, ta biết phần nào do **ngữ nghĩa cửa sổ** và phần nào do **tối ưu hoá**.

## 5. Ý tưởng trung tâm của V2 — cửa sổ ε

```
W(f,ε) = ⌈ln(ε(1−f)) / ln f⌉              (ε=0 hoặc f=1 ⇒ W = ∞)
N_eff  = min(TL, W)
minSup = ∂ × N_eff                        (2 pha: TL<W ⇒ ∂×TL ; TL≥W ⇒ ∂×W)
```

Ba hệ quả đã chứng minh trong `00-OVERALL-PLAN.md` §2:

1. **Bảo toàn:** `ε = 0` ⇒ V2/V3/V4 **bit-for-bit ≡ V1** (INV-I).
2. **Kiểm soát sai số:** tổng đóng góp của transaction ngoài cửa sổ `< ε` ⇒ `|DO_win − DO_full| ≤ ε` (INV-G).
3. **Trần DO:** `|X| ≤ |T|` ⇒ `DO(X) ≤ Z(f,TL) = (1−f^TL)/(1−f)`. Nếu `minSup > Z(f,TL)` ⇒ **kết quả chắc chắn rỗng** ⇒ short-circuit O(1).

Điểm 3 giải quyết trực tiếp vấn đề quan sát được ở G1: `retail.dat` chạy **87 s**, `mushroom.dat` chạy **301 s** để kết luận "0 pattern" — trong khi một phép so sánh đã đủ để biết trước.

## 6. Trạng thái hiện tại

| Giai đoạn | Trạng thái |
|---|---|
| C0 Planning | ✅ |
| G0 Khởi động | ✅ |
| **G1 V1 Standard** | ✅ **Xong** — 56 test xanh, đóng băng làm oracle |
| **G2 V2 Epsilon/Window** | 🕔 **Đang tiếp theo** — plan tại `docs/phases/P2-G2.md` |
| G3–G7 | 🕓 |

## 7. Bản đồ nhanh "muốn biết gì thì đọc gì"

| Muốn biết | Đọc |
|---|---|
| Thuật toán chính xác làm gì | `plans/00-OVERALL-PLAN.md` §2 |
| Công thức cửa sổ + bảng tra cứu | `plans/00-OVERALL-PLAN.md` §2.3.1 |
| Cấu trúc class/hàm của V1 | `plans/01-STANDARD-VERSION-PLAN.md` |
| Thiết kế V2 chi tiết + test | `plans/02-EPSILON-WINDOW-VERSION-PLAN.md` |
| Làm sao chạy code | `README.md` |
| Test hiện có | `README.md` §TestKit |
| Vì sao chọn kiến trúc này | `DECISIONS.md` |
| G1 kết quả thế nào | `reports/G1-V1-STANDARD-BAOCAO.md` |
| Làm tiếp gì | `phases/P2-G2.md` |

---

*Overview cập nhật cùng `DECISIONS.md`. Khi roadmap đổi, sửa mục 4 và 6 ở đây trước.*