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
  engine           MiningEngine                         — Engine + PhaseAwareEngine + ProgressAwareEngine
  cli              Main                                 — bộ lệnh chuẩn (G1-D7)
```

## Chạy

```bat
:: từ thư mục gốc (cần JDK 25, tự set JAVA_HOME qua run.bat)
cmd /c run.bat -q                                     :: biên dịch + test
```

## Bộ lệnh CLI (quyết định G1-D7, plan tổng thể mục 4.2 — 5 lệnh chuẩn chung cho cả 3 version)

```bat
:: cú pháp cũ vẫn chạy (= lệnh mine)
java -cp "implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.v1.cli.Main --dataset dataset\default.dat --partial 0.15 --f 0.9 --parts 2
```

| Lệnh | Mục đích | Ví dụ |
|---|---|---|
| `mine` | Tóm tắt ngắn: 1 dòng/part (3 pha ms, patterns, heap) + dòng tổng | `Main mine --dataset dataset\default.dat --parts 2` |
| `detail` | Debug từng phần: nodes/entries/rootTasks + ms + top-N mẫu theo DO (6 số lẻ) | `Main detail --dataset dataset\default.dat --parts 2 --top 3` |
| `stream` | Log thời gian thực: sự kiện load + tick tiến trình mining (`MiningProgressListener`) | `Main stream --dataset dataset\default.dat --parts 2` |
| `golden` | TestKit TC1–TC8 qua engine, in PASS/FAIL (nghiệm thu M5) | `Main golden` |
| `inspect` | Thống kê dataset/config, không mining | `Main inspect --dataset dataset\default.dat --top 3` |

Options: `--dataset <file>` `--format fimi|text` `--partial ∂` `--f f` `--workers n` `--parts n` `--top n`
`--limit <n>`.
Cần `dhopm-common` và `dhopm-v1-standard` trên classpath. Xem `Main help` trong lúc chạy.

`--limit <n>` đọc **tối đa n giao dịch đầu** của dataset, đọc xong bắt đầu mining — tiện cho
dataset rất lớn (kosarak ~1M tx): không phải đọc/mine hết. Bỏ `--limit` (hoặc `0`) = đọc hết
như cũ. Nếu dataset có ít hơn n giao dịch thì **đọc hết dataset** và in dấu đánh dấu
(`note: limit=n ignored - whole dataset read (…)`) — limit tự bỏ qua.

CLI chỉ dùng API mở trong `dhopm-common` (Engine / PhaseAwareEngine / ProgressAwareEngine /
TimedEngine / testkit Golden) — không truy cập nội bộ thuật toán (plan 00 §4.2 P1–P6).

## Xem thêm

- `docs/design.md` — ánh xạ GoF, quyết định lập-trình, bất biến INV-A..E, API mở cho công cụ
- `docs/test-report.md` — kết quả test nhiều lần chạy
- `docs/benchmark.md` — số đo trên 7 dataset + khuyến nghị chọn tham số
- `docs/golden-doubles-v1.json` — snapshot độ chính xác máy (double đầy đủ) cho V2/V3
- `../..` + `../../../docs/plans/01-STANDARD-VERSION-PLAN.md` — kế hoạch giai đoạn G1