# Benchmark — dhopm-v1-standard (khung V1, chạy nền)

Máy đo: Windows, JDK 25.0.4.1, workers = availableProcessors = 12, f = 0.9.
Chạy bằng `dhopm.bench.BenchmarkMain` (module `dhopm-bench`), load tăng dần 5 phần,
in theo phần. **Số đo dưới đây là khảo sát ban đầu**, chưa lấy median 3 lần như yêu
cầu chính thức (00-OVERALL-PLAN 5.4) — cần lặp lại khi chốt tham số.

## Demo kiểm tra hệ thống (có pattern thật)

`default.dat` (8 giao dịch, ∂=0.15, 2 phần) qua CLI:

```
part  loaded  last_tid  constr_ms  reconst_ms  mining_ms  total_ms  patterns
1     4       4         2          13          5          21        18
2     8       8         2          13          6          21        2      (== TC1)
```

## Khảo sát 2 dataset đại diện (kết quả thật của máy)

### retail.dat — 88 162 giao dịch, 16 470 nhãn, độ dài TB 10,3 — ∂ = 0,1 %

| phần | giao dịch | constr | reconst | mining (ms) | pattern |
|---|---|---|---|---|---|
| 1 | 17 633 | 23 | 43 | 7 750 | 0 |
| 2 | 35 266 | 49 | 65 | 20 135 | 0 |
| 3 | 52 899 | 79 | 83 | 36 708 | 0 |
| 4 | 70 532 | 115 | 104 | 62 055 | 0 |
| 5 | 88 162 | 145 | 124 | 86 949 | 0 |

(*) sau thu gọn đuôi hiếm trong `Miner.process`: từ 180 s → 87 s (phần 5), kết quả không đổi.

### mushroom.dat — 8 124 giao dịch, 119 nhãn, độ dài cố định 23 — ∂ = 6 %

| phần | giao dịch | constr | reconst | mining (ms) | pattern |
|---|---|---|---|---|---|
| 1 | 1 625 | 10 | 19 | 34 418 | 0 |
| 2 | 3 250 | 11 | 23 | 158 740 | 0 |
| 3 | 4 875 | 13 | 32 | 260 836 | 0 |
| 4 | 6 500 | 16 | 42 | 265 908 | 0 |
| 5 | 8 124 | 17 | 44 | 300 751 | 0 |

## Kết luận quan trọng về THANG ĐO tham số (phát hiện khi chạy thật)

1. **Với f = 0,9, giá trị DO bị chặn trên cỡ `≈ 1/(1−f) ≈ 10` × (|X|/|T|) ≤ 10.**
   DO chỉ "nhìn" cửa sổ phía đuôi stream (nửa đời f^(TL−Td) ≈ 7 giao dịch), nên
   `minSup = ∂ × tổng` chỉ so sánh được khi `∂ ≈ (1..30)/tổng`. Với retail ∂=0,1% thì
   minSup=88 ≫ mọi DO → **đúng chuẩn là rỗng** (và vẫn phải trả chi phí duyệt).
2. **DUBO ~ support khi mọi giao dịch cùng độ dài** (mushroom): group cuối có
   `f^(TL−Tk)=1`, tổng = support nên DUBO ≈ support → không cắt tỉa được gì ở mức
   thấp → bùng nổ tổ hợp (bản chất dữ liệu, không phải lỗi). Các dataset độ dài biến
   thiên (retail, kosarak, default) mới thể hiện được sức mạnh cắt tỉa của DUBO.
3. Thời gian mining tăng siêu-tuyến tính theo số phần vì mỗi phần chạy lại `mineNow`.

**Khuyến nghị chuẩn bị cho benchmark chính thức** (cần bàn với người duyệt trước):
- Chọn ∂ theo thang DO: thử chạy quét một vài ∂ (ví dụ retail ∂∈{2e-5..2e-4}, mushroom
  ∂∈{1e-4..1e-3}) để minSup nằm trong thang DO, đủ cho kết quả không rỗng mà không bùng nổ.
- `mushroom` và `kosarak` (độ dài biến thiên) tốt hơn cho việc minh họa DUBO prune.
- Lấy median ≥ 3 lần, ghi rõ phần cứng, JDK, workers.

## Cách chạy lại khảo sát

```bat
cmd /c run.bat -q
java -cp "implementation\dhopm-bench\target\classes;implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.bench.BenchmarkMain --dataset dataset\retail.dat --partial 0.001 --f 0.9 --parts 5
```