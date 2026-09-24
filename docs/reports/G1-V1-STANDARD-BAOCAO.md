# Báo cáo chi tiết — Giai đoạn G1: V1 Standard (DHOPM)

> Nguồn triển khai: `docs/phases/P1-G1.md` · Đặc tả chuẩn: `docs/plans/00-OVERALL-PLAN.md`
> Trạng thái tại ngày báo cáo: **code + test + tài liệu đã xong**, chưa chốt tham số benchmark, chưa duyệt kế hoạch G1.

---

## 1. Mục tiêu giai đoạn

Xây dựng **mô-đun tham chiếu V1** của DHOPM — đúng đặc tả chuẩn (C1–C6), rõ ràng,
**tái lập được (deterministic)**, đạt bộ bài toán vàng TC1–TC8 và làm **golden tham
chiếu** cho V2/V3. Bộ ba sản phẩm của giai đoạn:

| Mô-đun | Vai trò |
|---|---|
| `dhopm-common` | Hợp đồng trung lập thuật toán: `Engine`, `PhaseAwareEngine`, đo lường, reader, TestKit, `WorkerPool` |
| `dhopm-v1-standard` | Thuật toán DHOPM chuẩn (item = String, DHO-List = LinkedHashMap, threading Level 1) |
| `dhopm-bench` | Trình điều khiển benchmark: load tăng dần, in CSV theo phần |

---

## 2. Những gì đã bàn giao

### 2.1 dhopm-common (thêm 7 lớp / sửa 3)

**Hợp đồng pha (contract/):**
- `Phase` — enum `CONSTRUCTION / RECONSTRUCTION / MINING`.
- `PhaseListener` — Observer nhận `onPhase(phase, startNanos, endNanos)`; engine **chỉ gọi ở
  biên pha và chỉ khi đặt listener** (chi phí 0 khi không dùng).
- `PhaseAwareEngine` — giao diện engine-hiểu-pha (kế thừa `Engine`).
- `RunMetrics` — hồ sơ một lần chạy (engine, 3 pha ms, patternCount, lastTid, minSup, heap).
- `TimedEngine` — Decorator: đo `loadBatch`/`mineNow` mà **không đụng thuật toán** (G1-D5).

**Tiện ích:**
- `TimingRecorder` — tích lũy ms/heap từ `PhaseListener`.
- `WorkerPool.invokeAll(List<Callable<T>>)` — chạy task, **gộp kết quả sau join theo thứ tự
  task** → deterministic (nền tảng cho mining song song theo root).

**Sửa:** `GoldenAssert.GOLDEN_TOLERANCE` 1e-6 → **1e-4** (bảng golden làm tròn 4 số lẻ,
sai số tối đa < 5e-5 nên 1e-6 quá chặt).

### 2.2 dhopm-v1-standard (mới) — lớp theo giai đoạn

| Giai đoạn | Lớp (package `dhopm.v1`) | Xử lý | Python |
|---|---|---|---|
| GĐ1 | `construction.DHOListBuilder` · `StreamStatus` | 1 luồng | nối entry TID-tăng (INV-B); TID tăng nghiêm ngặt (INV-A fail-fast tại engine); `minSup = ∂×tổng` tính lúc `mineNow` (INV-F) |
| GĐ2 | `reconstruction.Reconstructor` | song song theo nút | DO từ 0 → tích lũy **tuần tự trên 1 nút** (C5) → `List.sort` ổn định theo support (C3) |
| GĐ3 | `mining.Miner` · `mining.ConditionalListBuilder` | song song theo root | C2 bỏ nút khi support<minSup; C4 thêm DOP khi `DO ≥ minSup−ε`; **DUBO cắt tỉa** khi < minSup−ε; giao 2 con trỏ trên entry TID-tăng (INV-D) |
| công thức | `metrics.MetricCalculator` · `dubo.DuboCalculator` | — | DO = Σ (l/|T|)·f^(TL−Td) · DUBO group theo |T| tăng (C1) |
| engine | `engine.MiningEngine` (`PhaseAwareEngine` + `AutoCloseable`) | — | Facade của pipeline; sở hữu `WorkerPool`; đổi kết quả ổn định (INV-E) |
| CLI | `cli.Main` | — | `--dataset --format --partial --f --workers --parts`, in CSV tăng dần |

**Quyết định áp dụng từ P1-G1:** G1-D1 Item=`String` · G1-D2 DHO-List=`LinkedHashMap` ·
G1-D3 pool=`config.workers` · G1-D4 gộp-sau-join · G1-D5 `TimedEngine` ở common ·
G1-D6 snapshot `golden-doubles-v1.json` (sinh tự động bởi test, độ chính xác máy).

### 2.3 dhopm-bench (mới)

`BenchmarkMain` — tham số `--dataset --partial --f --workers --parts` (mặc định 5 phần),
chạy v1 qua reconstruct+mining từng phần, in CSV: `construction/reconstruction/mining/total_ms,
patterns, heap_mb`.

### 2.4 Tài liệu

- `docs/phases/P1-G1.md` — kế hoạch giai đoạn G1 (mốc M1–M6, quyết định G1-D*, tiêu chí chấp nhận, rủi ro).
- `implementation/dhopm-v1-standard/README.md` · `docs/design.md` · `docs/test-report.md` · `docs/benchmark.md`.
- `implementation/dhopm-v1-standard/docs/golden-doubles-v1.json` — snapshot double đầy đủ (nguồn cho V2/V3).
- Báo cáo này.

---

## 3. Kiểm thử — 46 test xanh / 0 lỗi

| Nhóm | Số test | Nội dung cốt lõi |
|---|---|---|
| dhopm-common (G0) | 29 | reader 7 dataset vs bảng 5.2, config, TestKit tự-kiểm |
| `DHOListBuilderTest` | 4 | thứ tự tạo nút + INV-B; INV-A fail-fast (giảm/trùng TID); stream rỗng |
| `ReconstructionTest` | 4 | DO(A)=0.8551, DO(F)=1.2553; **sort ổn định A trước C** (C3); lặp 2 lần = nhau; mọi DO 4 số lẻ |
| `DuboCalculatorTest` | 2 | E=4.5, B=1.8, CD=1.6402, AF=0.93, CDE=1.181, F=3.0375 (tính tay TL=8, f=0.9); DUBO tăng theo entry |
| `GoldenEngineTest` | 2 | **TC1–TC8** (tolerance 1e-4); kết quả sắp theo canonicalKey (C6) |
| `DeterminismTest` | 2 | TC3 **bit-for-bit giống hệt** workers ∈ {1,2,4,CPU} (INV-E); `mineNow` ×2 ổn định |
| `IncrementalTest` | 2 | load 2 phần == load đủ (cùng TL/minSup) từng bit; TC6 trên DB0 đúng |
| `GoldenDoublesSnapshotTest` | 1 | sinh `golden-doubles-v1.json` (mọi giá trị double round-trip chính xác, không NaN) |

**Bài toán vàng (in khi chạy test):**

```
TC1 f=0.9 ∂=0.15 minSup=1.2 -> 2/2   TC5 f=1.0 ∂=0.15 minSup=1.2 -> 9/9
TC2 f=0.9 ∂=0.20 minSup=1.6 -> 0/0   TC6 f=0.9 ∂=0.25 minSup=1.0 -> 3/3
TC3 f=0.9 ∂=0.10 minSup=0.8 -> 15/15 TC7 f=0.9 ∂=0.15 minSup=1.5 -> 9/9
TC4 f=0.8 ∂=0.15 minSup=1.2 -> 0/0   TC8 f=0.9 ∂=0.30 minSup=1.5 -> 1/1
```

---

## 4. Kết quả chạy thật

### 4.1 Demo hệ thống (default.dat, ∂=0.15, 2 phần — CLI)

```
part  loaded  last_tid  constr_ms  reconst_ms  mining_ms  total_ms  patterns
1     4       4         2          13          5          21        18
2     8       8         2          13          6          21        2   (== TC1)
```

### 4.2 Khảo sát retail.dat (88 162 tx, 16 470 nhãn, TB 10,3) — ∂=0,1%

| phần | tx | constr | reconst | mining(ms) | patterns |
|---|---|---|---|---|---|
| 1 | 17 633 | 23 | 43 | 7 750 | 0 |
| 2 | 35 266 | 49 | 65 | 20 135 | 0 |
| 3 | 52 899 | 79 | 83 | 36 708 | 0 |
| 4 | 70 532 | 115 | 104 | 62 055 | 0 |
| 5 | 88 162 | 145 | 124 | 86 949 | 0 |

### 4.3 Khảo sát mushroom.dat (8 124 tx, 119 nhãn, độ dài cố định 23) — ∂=6%

| phần | tx | constr | reconst | mining(ms) | patterns |
|---|---|---|---|---|---|
| 1 | 1 625 | 10 | 19 | 34 418 | 0 |
| 2 | 3 250 | 11 | 23 | 158 740 | 0 |
| 3 | 4 875 | 13 | 32 | 260 836 | 0 |
| 4 | 6 500 | 16 | 42 | 265 908 | 0 |
| 5 | 8 124 | 17 | 44 | 300 751 | 0 |

> Ngưỡng trên là kết quả thật của máy (Windows, JDK 25.0.4.1, 12 workers, f=0.9); chưa lấy median 3 lần như yêu cầu chính thức.

---

## 5. Phát hiện quan trọng về THANG ĐO tham số (quan trọng cho G4/benchmark)

1. **DO bị chặn trên cỡ `≈ 1/(1−f) ≈ 10`** với f=0.9 (chỉ "nhìn" cửa sổ đuôi stream,
   nửa đời ≈ 7 giao dịch). Do đó `minSup = ∂×tổng` chỉ so sánh được khi
   `∂ ≈ (1..30)/tổng`:
   - retail ∂=0,1% → minSup=88 ≫ mọi DO → **kết quả rỗng đúng chuẩn**;
   - mushroom ∂=6% → minSup=487 ≫ mọi DO → **rỗng đúng chuẩn**, nhưng vẫn phải trả chi phí duyệt.
2. **DUBO ~ support khi mọi giao dịch cùng độ dài** (mushroom): group cuối có
   `f^(TL−Tk)=1` nên DUBO ≈ support → hầu như không cắt tỉa ở mức thấp → bùng nổ tổ hợp
   (300 s/8k tx). DUBO thể hiện đúng sức mạnh trên dataset **độ dài biến thiên**
   (default/retail/kosarak).
3. Thời gian mining tăng siêu-tuyến tính theo phần vì mỗi phần chạy lại `mineNow`.

**Khuyến nghị benchmark chính thức** (cần người duyệt chốt): quét ∂ quanh `(1..30)/N`
(retail ~2e-5..2e-4, mushroom ~1e-4..1e-3), ưu tiên dataset độ dài biến thiên, rồi lấy
median ≥ 3 lần và ghi rõ cấu hình máy.

---

## 6. Tối ưu trung thực đã làm / chưa làm

**Đã làm (không đổi kết quả — đã kiểm chứng bằng Determinism + Golden):**
- Bỏ partner có `support < minSup` trước khi giao (`Miner.process`) vì
  `|Xi ∩ Xj| ≤ support_j → C2 loại` → retail phần 5: 180 s → 87 s.

**Chưa làm (dự trù cho bước duyệt tiếp):** tái sử dụng kết quả `mineNow` giữa các phần
incremental; cấu trúc dữ liệu DAM cho entry set; lựa chọn công cụ String tối ưu; chạy
đa lần lấy median; hồ sơ V2/V3.

---

## 7. Các quyết định còn treo dành cho người duyệt

1. Chốt cách chọn ∂ theo thang DO (→ áp dụng vào benchmark.md + 00-plans §5.2).
2. Xác nhận benchmark params chính thức → lấy median, hoàn thiện bảng số liệu.
3. (Trả lời câu hỏi ở cuối phiên) có cần thêm tối ưu cụ thể nào từ bản nháp của bạn không.

---

## 8. Cách chạy lại toàn bộ

```bat
:: từ thư mục gốc repo (set sẵn JDK 25 qua run.bat)
cmd /c run.bat -q                          :: build + 46 test

:: CLI v1 trên dataset mẫu
java -cp "implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.v1.cli.Main --dataset dataset\default.dat --partial 0.15 --f 0.9 --parts 2

:: khảo sát benchmark
java -cp "implementation\dhopm-bench\target\classes;implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.bench.BenchmarkMain --dataset dataset\retail.dat --partial 0.001 --f 0.9 --parts 5
```

---

## 9. Cấu trúc thay đổi (tổng kết)

```
docs/
  phases/P1-G1.md                     (mới) kế hoạch G1
  reports/G1-V1-STANDARD-BAOCAO.md    (mới) báo cáo này
implementation/
  pom.xml                             (sửa) <modules> += v1-standard, bench
  dhopm-common/  contract: Phase, PhaseAwareEngine, PhaseListener, RunMetrics, TimedEngine
                 util: TimingRecorder; (+ WorkerPool.invokeAll; GoldenAssert tolerance 1e-4)
  dhopm-v1-standard/  model·construction·metrics·reconstruction·dubo·mining·engine·cli
                      + README.md, docs/{design,test-report,benchmark}.md, docs/golden-doubles-v1.json
  dhopm-bench/        benchmark/BenchmarkMain.java
```