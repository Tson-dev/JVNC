# DHOPM / DOPM

Dự án minh họa thuật toán khai thác **DOP (Damped Occupancy Pattern)** — 3 phiên bản thuật toán:

- **V1 Standard** (GoF, Level 1 threading) — `implementation/dhopm-v1-standard` (G1)
- **V2 Optimized** (tối ưu cấu trúc dữ liệu, rationale vs V1) — `implementation/dhopm-v2-optimized` (G2)
- **V3 Extreme** (ý tưởng, không ràng buộc pattern) — `implementation/dhopm-v3-extreme` (G3)

Còn lại: `dhopm-common` (hạ tầng chung), `dhopm-bench` (benchmark), `dhopm-app` (debug/compare app + UI, G4).

## Cấu trúc repo

```
JVNC/
├── docs/
│   ├── plans/        00-OVERALL-PLAN, 01/02/03 theo phiên bản
│   ├── srs/          DHOPM-SRS (SRS tổng thể), DHOPM-UI-LAYOUT (bố cục UI)
│   ├── phases/       P0-G0.md … (plan từng giai đoạn G0→G4)
│   └── root/         mốc: paper + bảng chạy tay TC1–TC8
├── dataset/          7 dataset FIMI + default.dat (demo 8 giao dịch)
├── implementation/   Maven multi-module (Java 25)
│   ├── pom.xml           parent
│   └── dhopm-common/     io · config · contract · util · TestKit (TC1–TC8)
├── run.bat           chạy test một lệnh (Windows)
└── README.md
```

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

## TestKit (TC1–TC8)

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

Engine mới chỉ cần implement `dhopm.common.contract.Engine` rồi gọi `GoldenRunner.runAll(...)` để soát toàn bộ.

## Datasets

| File | Giao dịch | Items (distinct) | Avg len | Gợi ý ∂ |
|---|---|---|---|---|
| chess.dat | 3.196 | 75 | 37.0 | 35% |
| connect.dat | 67.557 | 129 | 43.0 | 30% |
| kosarak.dat | 990.002 | 41.270 | 8.1 | 0.05% (scalability 200K→990K) |
| mushroom.dat | 8.124 | 119 | 23.0 | 6% |
| pumsb.dat | 49.046 | 2.113 | 74.0 | 30% |
| pumsb_star.dat | 49.046 | 2.088 | 50.5 | 30% |
| retail.dat | 88.162 | 16.470 | 10.3 | 0.1% |

`default.dat`: 8 giao dịch demo (A–G), dùng cho TC1–TC6 / kiểm thử nhanh.

**FIMI format**: mỗi dòng 1 giao dịch, TID = số dòng (1-based); dòng trống / bắt đầu `#` bị bỏ qua.

## Tiến độ

- [x] **C0** Planning — plans 00–03, SRS, UI layout
- [x] **G0** Khởi động — Maven structure, `dhopm-common` seed, TestKit TC1–TC8, validate 7 dataset (JDK 25)
- [ ] **G1** V1 Standard
- [ ] **G2** V2 Optimized
- [ ] **G3** V3 Extreme
- [ ] **G4** Debug/Compare App (UI module, loading screen, chọn ≥1 engine)

Mỗi giai đoạn sẽ có plan riêng tại `docs/phases/` trước khi code (xem `docs/plans/00-OVERALL-PLAN.md` mục 4.1).