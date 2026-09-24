# Báo cáo test — dhopm-v1-standard

Cập nhật lần cuối: chạy `cmd /c run.bat -q` (JDK 25.0.4.1, Maven 3.9.16).

## Tổng quan

| Nguồn | Lượng test | Lỗi |
|---|---|---|
| dhopm-common (G0, base: reader/config/testkit/worker) | 29 | 0 |
| dhopm-v1-standard | 27 | 0 |
| **Tổng** | 56 | 0 |

## Đếm test đồng thuận theo bài toán vàng (Golden)

Tất cả 8 bài TC1–TC8 PASS với tolerance `GOLDEN_TOLERANCE = 1e-4`:

```
TC1 f=0.9 ∂=0.15 minSup=1.2 -> 2/2   (AE=1.2601, F=1.2553)
TC2 f=0.9 ∂=0.20 minSup=1.6 -> 0/0
TC3 f=0.9 ∂=0.10 minSup=0.8 -> 15/15
TC4 f=0.8 ∂=0.15 minSup=1.2 -> 0/0
TC5 f=1.0 ∂=0.15 minSup=1.2 -> 9/9
TC6 f=0.9 ∂=0.25 minSup=1.0 -> 3/3   (FCD=1.0000, CD=1.4812, CDE=1.2218)
TC7 f=0.9 ∂=0.15 minSup=1.5 -> 9/9
TC8 f=0.9 ∂=0.30 minSup=1.5 -> 1/1   (A=2.4661)
```

## Nhóm test v1 (27)

- **DHOListBuilderTest (4)** — thứ tự tạo nút + entry TID-tăng (INV-B); INV-A fail-fast
  (giảm TID, trùng TID); stream rỗng.
- **ReconstructionTest (4)** — DO(A)=0.8551, DO(F)=1.2553 (sai số 1e-4); sort ổn định
  giữ thứ tự tạo khi support bằng (A trước C); lặp lại 2 lần bằng bit; mọi DO 4 số lẻ.
- **DuboCalculatorTest (2)** — E=4.5, B=1.8, CD=1.6402, AF=0.93, CDE=1.181, F=3.0375
  (tính tay TL=8, f=0.9); DUBO tăng khi thêm entry.
- **GoldenEngineTest (2)** — cả 8 TC1–TC8 (GoldenRunner, tolerance 1e-4); kết quả sắp
  theo canonicalKey (C6).
- **DeterminismTest (2)** — TC3 bit-for-bit giống hệt với workers ∈ {1,2,4,CPU}; gọi
  `mineNow` 2 lần trên cùng engine ổn định (INV-E).
- **IncrementalTest (2)** — load 2 phần == load đủ (cùng TL/minSup) từng bit; TC6 đúng
  (chỉ DB0 4 giao dịch).
- **GoldenDoublesSnapshotTest (1)** — sinh `docs/golden-doubles-v1.json` (double chính xác).
- **CliCommandsTest (10)** — 5 lệnh chuẩn chạy trên dataset FIMI tạm (mine/detail/stream/
  golden/inspect); lệnh cũ `--dataset …` = `mine`; lệnh lạ → exit 2. Kiểm tra lệnh `stream`
  xuất tick tiến trình đồng thời chứng minh bật `MiningProgressListener` không đổi kết quả
  (INV-E, xác nhận `golden` = 8/8 PASS). `--limit` đúng 3 hành vi: dừng sau đúng n giao dịch
  (đánh dấu "stopped after reading"), dataset nhỏ hơn n → đọc hết (đánh dấu "whole dataset
  read", limit bỏ qua), limit âm → bị chặn.

## Cải tiến hiệu năng không đổi kết quả

- Bỏ partner có `support < minSup` trước khi giao (Miner.process) — kiểm chứng bằng
  DeterminismTest + Golden (toàn bộ TC vẫn bit-giống hệt).
- API tiến trình (`MiningProgressListener`) chỉ kích hoạt khi set — `mine`/`detail` không
  bị chậm thêm; đối chiếu bằng CliCommandsTest (kết quả `mine` ≡ `stream`).

## Cách chạy lại

```bat
cmd /c run.bat -q
```
Xem phụ lục từng class: `implementation/dhopm-v1-standard/target/surefire-reports/TEST-*.xml`.