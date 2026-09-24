# dhopm-v1-standard — DHOPM V1 chuẩn (goi là "V1 Standard")

Engine tham chiếu của dự án DHOPM: đúng-chuẩn, rõ ràng, lặp được (deterministic), đạt
bài toán vàng TC1–TC8 và là tham chiếu (golden) cho V2/V3.

## Cấu trúc

```
dhopm.v1
  model            Entry / DHONode / DHOList            — DHO-List: node + ⟨TID,|T|⟩
  construction     DHOListBuilder, StreamStatus         — GĐ1 quét 1 lượt
  reconstruction   Reconstructor                        — GĐ2 tính DO + sắp xếp ổn định
  metrics          MetricCalculator                     — DO, f^(TL−Td), occupancy
  dubo             DuboCalculator                       — cận trên DUBO (C1)
  mining           ConditionalListBuilder, Miner        — GĐ3 DFS tăng-trưởng mẫu
  engine           MiningEngine                         — Engine + PhaseAwareEngine
  cli              Main                                 — CLI incremental
```

## Chạy

```bat
:: từ thư mục gốc (cần JDK 25, tự set JAVA_HOME qua run.bat)
cmd /c run.bat -q                                     :: biên dịch + test

:: CLI: nạp incremental, in từng phần
java -cp "implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.v1.cli.Main --dataset dataset\default.dat --partial 0.15 --f 0.9 --parts 2
```

## Xem thêm

- `docs/design.md` — ánh xạ GoF, quyết định lập-trình, bất biến INV-A..E
- `docs/test-report.md` — kết quả test nhiều lần chạy
- `docs/benchmark.md` — số đo trên 7 dataset + khuyến nghị chọn tham số
- `docs/golden-doubles-v1.json` — snapshot độ chính xác máy (double đầy đủ) cho V2/V3
- `../..` + `../../../docs/plans/01-STANDARD-VERSION-PLAN.md` — kế hoạch giai đoạn G1