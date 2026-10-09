# Benchmark ablation — V1 (paper) vs V2 (window minOcc)

Máy đo: Windows, JDK 25.0.4.1, Maven 3.9.16, workers=8, f=0.9. Chạy bằng `dhopm.bench.BenchmarkMain`
(module `dhopm-bench`, đã mở rộng `--engine v1|v2 --minOcc --limit`), load tăng dần 5 phần.
**Số đo dưới đây là khảo sát ban đầu với `--limit`, chạy 1 lần** — chưa lấy median 3 lần
(00-OVERALL-PLAN 5.4) — chỉ để minh họa tác dụng ablation của cửa sổ, không phải con số nghiệm thu.

## Demo hệ thống — khối window trong `mine` (default.dat, ∂=0.1, minOcc=1e-6)

```
window: W=153 N_eff=8 minSup=0.8000 Z=5.6953 evict=0 live=26 dead=0
part   loaded  last_tid  constr_ms reconst_ms mining_ms total_ms patterns
1      8       8         2          8          4         14       15
```
TL=8 < W ⇒ pha 1 (`N_eff=TL`), minSup=∂×8, chưa evict — đúng miền "≡ paper trên window".

## retail.dat — 88 162 tx (limit 100 000 → đọc hết), ∂ = 0.15

| engine | constr | reconst | mining | **total (ms)** | patterns | heap (MB) |
|---|---|---|---|---|---|---|
| **v1** | 98 | 110 | 83 | **291** | 0 | 180.6 |
| **v2** (minOcc=1e-6) | 17 | 0 | 0 | **17** | 0 | 119.8 |

→ **≈ 17× nhanh hơn**, cùng kết quả (rỗng đúng chuẩn: DO ≤ 10 < minSup=∂×88 162).

## kosarak.dat — 200 000 tx (limit 200 000), ∂ = 0.01

| engine | constr | reconst | mining | **total (ms)** | patterns (part 5) | heap (MB) |
|---|---|---|---|---|---|---|
| **v1** | 149 | 153 | 1 642 | **1 946** | 0 (minSup=0.01×200 000=2 000) | 319.7 |
| **v2** (minOcc=1e-6) | 31 | 64 | 97 | **193** | 3 (minSup=0.01×153=1.53) | 153.9 |

→ **≈ 10× nhanh hơn.** ⚠️ Số pattern **khác nhau là do thiết kế, không phải lỗi**:
`minSup` 2 pha của V2 đóng băng ở `∂×W = ∂×153` (INV-F) nên các DOP mức thấp
(DO trong khoảng [1.53, 2 000)) chỉ V2 thấy được. Đây là hệ quả của "cửa sổ thu nhỏ miền so
sánh", nêu rõ ở `docs/design.md` và P2-G2 (TC14 `twoPhaseMinSupFreezesAtWindow`).

## Vì sao V2 nhanh hơn (không đổi kết quả khi minOcc=0 — INV-I)

1. **GĐ0 skip + evict**: giữ nhiều nhất W TID sống (W=153); `DHOListBuilder` bỏ qua ghi txs
   ngoài cửa sổ ngay khi quét ⇒ V2 cấu trúc danh sách bằng ≈ W transaction thay vì toàn stream.
2. **Short-circuit O(1)**: `minSup > Z + ε` ⇒ ∅. (G1 từng mất 87–300 s để ra 0 pattern —
   câu trả lời nằm ở đây; với retail/kosarak ∂ mặc định, trần Z≈10 < minSup nên kết quả rỗng tức thì.)
3. **Cận `min(DUBO, Z(X))`**: cắt phẳng hơn cận DUBO V1 khi window hữu hạn.

## Lưu ý đo đạc (thành thật)

- `chess.dat` ∂=0.15 từng vượt 180 s (dataset đặc, mọi giao dịch cùng độ dài ⇒ DUBO ≈ support,
  không cắt tỉa) — nhất quán với phát hiện mushroom ở G1; cần ∂ cao hơn nếu đo dense.
- Cột constr của V2 gồm cả evict; reconst/mining in 0 ms do mỗi pha < 1 ms (window chỉ 153 tids).
- Đối chiếu INV-I (minOcc=0 ≡ V1) được đảm bảo bằng test TC1–TC8 chứ không đo lặp ở đây.

## Cách chạy lại khảo sát

```bat
cmd /c run.bat -q
java -cp "implementation\dhopm-bench\target\classes;implementation\dhopm-v2-minocc\target\classes;implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.bench.BenchmarkMain --dataset dataset\retail.dat --engine v2 --minOcc 1e-6 --partial 0.15 --parts 5 --limit 100000
java -cp "implementation\dhopm-bench\target\classes;implementation\dhopm-v2-minocc\target\classes;implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.bench.BenchmarkMain --dataset dataset\kosarak.dat --engine v1 --partial 0.01 --parts 5 --limit 200000
```