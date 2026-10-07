# DHOPM / DOPM

Dự án triển khai và nghiên cứu thuật toán khai thác **DOP (Damped Occupancy Pattern)** — **4 phiên bản thuật toán**:

| Phiên bản | Tên | Module | Threading | Trạng thái |
|---|---|---|---|---|
| **V1** | Standard (GoF, **oracle**) | `dhopm-v1-standard` | Level 1 | ✅ **Xong (G1)** — đóng băng |
| **V2** | **Epsilon / Window** | `dhopm-v2-epsilon` | Level 1 | 🕔 **Kế tiếp (G2)** |
| **V3** | Optimized | `dhopm-v3-optimized` | Level 2 (ForkJoinPool) | 🕓 Chưa code |
| **V4** | Extreme | `dhopm-v4-extreme` | Level 3 (data-oriented) | 🕓 Chưa code |

Còn lại: `dhopm-common` (hạ tầng chung + `WindowMath`), `dhopm-bench` (benchmark), `dhopm-app` (debug/compare app + UI, G4).

## Ý tưởng trung tâm — cửa sổ ε

Nguồn ý tưởng: `docs/Draft Idea.txt` (tác giả). Chuẩn hoá tại `docs/plans/00-OVERALL-PLAN.md` §2.

```
W(f,ε)  = ⌈ln(ε(1−f)) / ln f⌉          kích thước cửa sổ (ε=0 hoặc f=1 ⇒ W = ∞)
N_eff   = min(TL, W)
minSup  = ∂ × N_eff                    hai giai đoạn: TL<W ⇒ ∂×TL ; TL≥W ⇒ ∂×W
```

Ba hệ quả quan trọng:

1. **Bất biến bảo toàn:** `ε = 0` ⇒ mọi phiên bản cho kết quả **bit-for-bit giống V1 ≡ paper** (INV-I).
2. **Sai số kiểm soát được:** mọi transaction ngoài cửa sổ có tổng đóng góp `< ε` ⇒ `|DO_win − DO_full| ≤ ε` (INV-G).
3. **Trần DO:** vì `|X| ≤ |T|` nên `DO(X) ≤ Z(f,TL) = (1−f^TL)/(1−f)`. Nếu `minSup > Z(f,TL)` ⇒ kết quả **chắc chắn rỗng** ⇒ short-circuit O(1). Đây là câu trả lời cho việc benchmark mất 87–300 s mới ra 0 pattern.

## Cấu trúc repo

```
JVNC/
├── docs/
│   ├── DECISIONS.md    ⭐ sổ quyết định (ADR) — nguồn chân lý
│   ├── plans/          00-OVERALL-PLAN, 01–04 theo phiên bản, 05 CLI/API, 06 draft analysis
│   ├── srs/            DHOPM-SRS (SRS tổng thể), DHOPM-UI-LAYOUT (bố cục UI)
│   ├── phases/         P1-G1.md ✅ · P2-G2.md 🕔 · (G3+ tạo dần)
│   ├── root/           mốc: paper + bảng chạy tay TC1–TC8
│   └── reports/        báo cáo G1, báo cáo đo ε (G2)
├── dataset/            10 dataset FIMI + default.dat (demo 8 giao dịch) + zip/ (bản nén)
├── implementation/     Maven multi-module (Java 25)
│   ├── pom.xml           parent
│   └── dhopm-common/     io · config · contract · util · TestKit (TC1–TC18)
├── run.bat           chạy test một lệnh (Windows)
└── README.md
```

> **Thang tài liệu** (D24): `DECISIONS.md` → `plans/` → `srs/` → `phases/` → `module/docs/` → `reports/`. Khi hai tài liệu mâu thuẫn, tài liệu **cao hơn trong thang thắng**.

## Yêu cầu

- **JDK 25** (`C:\Program Files\Java\jdk-25.0.4.1`) — đã cấu hình `JAVA_HOME` + PATH
- **Maven 3.9+**

## Chạy test (một lệnh)

```bat
run.bat
```

Không cần chạy từng lệnh — script tự set `JAVA_HOME` rồi chạy toàn bộ test (`mvn test`).

Tùy chọn truyền goal cho Maven (mặc định là `test`):

```bat
run.bat clean test
run.bat package
run.bat -DskipTests=false test -Dtest=DatasetValidationTest
```

Hoặc gọi Maven trực tiếp:

```bat
mvn -f implementation\pom.xml test
```

**Chạy lại test nhanh dataset** (tái sinh bảng thống kê 5.2):

```bat
mvn -f implementation\pom.xml test -Dtest=DatasetValidationTest
```

## TestKit (TC1–TC8 · mở rộng TC9–TC18 ở V2)

Golden cases lấy từ bảng chạy tay `docs/root/Nhom01_VDChayTay.md` (Phần 11):

| Case | Tham số (f, ∂) | Kỳ vọng |
|---|---|---|
| TC1 | 0.9, 0.15 | 2 DOP: AE=1.2601, F=1.2553 |
| TC2 | 0.9, 0.20 | 0 DOP |
| TC3 | 0.9, 0.10 | 15 DOP |
| TC4 | 0.8, 0.15 | 0 DOP |
| TC5 | 1.0, 0.15 | 9 HOP (không suy giảm) |
| TC6 | 0.9, 0.25 (DB0, 4 TID) | 3 DOP |
| TC7 | 0.9, 0.15 (10 TID tùy chỉnh) | 9 DOP |
| TC8 | 0.9, 0.30 (1 item/TID) | 1 DOP: A=2.4661 |

**TC9–TC18** kiểm tra ngữ nghĩa cửa sổ (sẽ thêm ở G2). Bảng tra cứu nhanh `W(f,ε)`:

| f \ ε | 1e-3 | 1e-6 | 1e-9 | 1e-12 |
|---|---|---|---|---|
| 0.5 | 11 | 21 | 31 | 41 |
| 0.8 | 39 | 70 | 101 | 132 |
| **0.9** | **88** | **153** | **219** | **285** |
| 0.95 | 194 | 328 | 463 | 598 |
| 0.99 | 1 146 | 1 833 | 2 521 | 3 208 |
| 0.999 | 13 809 | 20 713 | 27 618 | 34 522 |

Engine mới chỉ cần implement `dhopm.common.contract.Engine` rồi gọi `GoldenRunner.runAll(...)` để soát toàn bộ. V2+ implement thêm `WindowAwareEngine`.

## Datasets

| File | Giao dịch | Items (distinct) | Avg len | `∂` của paper | Ghi chú |
|---|---|---|---|---|---|
| accidents.dat | 340.183 | 468 | 33.81 | 3% | **dataset của paper [1]** |
| chainstore.dat | 1.112.949 | 46.086 | 7.23 | 0.05% | **lớn nhất** — scalability 200K→1.1M |
| chess.dat | 3.196 | 75 | 37.0 | 35% | |
| connect.dat | 67.557 | 129 | 43.0 | 30% | `∂` nằm **ngoài** miền khả thi khi dùng ε |
| kosarak.dat | 990.002 | 41.270 | 8.1 | 0.05% | scalability 200K→990K |
| mushroom.dat | 8.124 | 119 | 23.0 | 6% | |
| newMushroom.dat | 8.416 | 119 | 23.0 | 6% | ⚠️ **khác `mushroom.dat`** — không so trực tiếp |
| pumsb.dat | 49.046 | 2.113 | 74.0 | 30% | |
| pumsb_star.dat | 49.046 | 2.088 | 50.5 | 30% | |
| retail.dat | 88.162 | 16.470 | 10.3 | 0.1% | |

Thêm `default.dat`: 8 giao dịch demo (A–G), dùng cho TC1–TC6 / kiểm thử nhanh.

**FIMI format**: mỗi dòng 1 giao dịch, TID = số dòng (1-based); dòng trống / bắt đầu `#` bị bỏ qua.

> ⚠️ **`newMushroom.dat` ≠ `mushroom.dat`.** Cả hai có 8.124 tập phân biệt nhưng **không giao nhau một tập nào**, và `newMushroom.dat` có thêm 292 dòng lặp — do **gán item id khác**, không phải lệch thứ tự. Không so benchmark hai dataset này.

> 📦 **`dataset/zip/`** chứa bản nén của cả 11 file `.dat`. `--dataset` nhận trực tiếp đường dẫn `.zip` và giải nén khi đọc (không ghi tạm ra đĩa). Extension hợp lệ: `.dat`/`.txt`/`.text`/`.csv`/`.tsv`/`.zip` — **không nhận `.rar`/`.7z`**.

> ⚠️ **Cảnh báo về `∂` của paper.** Với `f=0.9`, `ε=1e-6` ⇒ `W=153` và trần `Z(f,TL)=10`. Do đó `minSup = ∂ × 153`: `∂=6%` ⇒ `minSup=9.18` (gần trần, rất ít pattern), còn `∂=30–50%` ⇒ **bất khả thi, kết quả chắc chắn rỗng**. Chi tiết: `docs/plans/00-OVERALL-PLAN.md` §6.4.

## Tiến độ

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| **C0** | Planning — plans, SRS, UI layout, `DECISIONS.md` | ✅ |
| **G0** | Khởi động — Maven structure, `dhopm-common`, TestKit TC1–TC8, validate dataset | ✅ |
| **G1** | **V1 Standard** (oracle, `ε=0`) | ✅ **`docs/phases/P1-G1.md`** |
| **G2** | **V2 Epsilon/Window** | 🕔 **`docs/phases/P2-G2.md`** |
| G3 | V3 Optimized | 🕓 Chưa lập plan |
| G4 | Debug/Compare App (UI module) | 🕓 Chưa lập plan — **chỉ bắt đầu sau G2** |
| G5 | Manager CLI/API (JSONL) | 🕓 |
| G7 | Utility-FIMI reader (planned, D42) | 🕓 |

Mỗi giai đoạn có plan riêng tại `docs/phases/` trước khi code (D26). Xem `docs/DECISIONS.md` cho quyết định kiến trúc và roadmap `docs/plans/00-OVERALL-PLAN.md` §8.