# dhopm-v3-optimized — DHOPM V3 Optimized

Bản tối ưu của DHOPM trên nền ngữ nghĩa **cửa sổ suy giảm minOcc** của V2. V3 giữ nguyên số học
(DO/DUBO/Z dựng bằng đúng công thức V1/V2) nên tập DOP **bit-for-bit ≡ V1/V2** (INV-I); khi
`minOcc = 0` (hoặc `f = 1`) rơi về đúng chế độ paper của V1. Điểm mới là cấu trúc dữ liệu
SoA (`int[] tids / int[] lens`, `int` itemId + `ItemDictionary`, không còn `Handle`) và **đa luồng
Level 2** (`ForkJoinPool` work-stealing ở giai đoạn tái dựng + khai phá).

## Cấu trúc

```
dhopm.v3
  io               ItemDictionary                        — ánh xạ item ↔ int id (SoA)
  window           WindowBufferOptimized / DecayLookup   — GĐ0 evict O(1); bảng f^(tl−tid) dựng bằng Math.pow
  dho              DHONodeOptimized / DHOListOptimized   — node SoA: int[] tids / int[] lens
  construction     DHOListBuilderOptimized               — GĐ1 quét 1 lượt
  reconstruction   ReconstructorOptimized                — GĐ2 song song (ForkJoinPool)
  metrics          MetricCalculator / ZCalculator        — DO, f^(TL−Td), trần Z(f,TL)
  mining           MinerOptimized / ConditionalListBuilderOptimized / DuboBound
                                                          — GĐ3 song song + prune min(DUBO, Z)
  engine           MiningEngineV3                         — Engine + PhaseAwareEngine + ProgressAwareEngine + WindowAwareEngine
  cli              Main                                   — CLI tối giản độc lập (mine/inspect/window/validate)
```

## Chạy

```bat
:: từ thư mục gốc (cần JDK 25, tự set JAVA_HOME qua run.bat)
cmd /c run.bat -q install -DskipTests      :: biên dịch + cài cả reactor (bỏ test)
cmd /c run.bat -q                          :: biên dịch + test toàn bộ reactor
```

## Chạy tay độc lập (không qua bench)

V3 có CLI riêng để chạy như V1/V2. Classpath cần `dhopm-common` + `dhopm-v3-optimized`:

```bat
java -cp "implementation\dhopm-common\target\classes;implementation\dhopm-v3-optimized\target\classes" ^
     dhopm.v3.cli.Main mine --dataset dataset\default.dat --partial 0.15 --f 0.9 --parts 2
```

| Lệnh | Mục đích | Ví dụ |
|---|---|---|
| `mine` | Tóm tắt 1 dòng/part + **dòng window** (W, N_eff, minSup, Z, evict/live/dead) + dòng tổng | `Main mine --dataset dataset\default.dat --partial 0.15 --f 0.9 --parts 2` |
| `inspect` | Thống kê dataset/config + W/Z, **không mining** | `Main inspect --dataset dataset\retail.dat` |
| `window` | Tra cứu W(f,minOcc) **không cần dataset**: dp_max asymptotic + hậu tố tail | `Main window --f 0.9 --minOcc 0.000001` |
| `validate` | Kiểm cấu hình: mã lỗi ổn định; có `--dataset` thì kiểm **chính xác** `Z/N_eff` (D38); lỗi ⇒ exit 1 | `Main validate --dataset dataset\default.dat --partial 0.15` |
| *(legacy)* `--dataset …` | = lệnh `mine` (tương thích V1/V2) | `Main --dataset dataset\default.dat` |

Options: `--dataset <file>` `--format fimi|text` `--partial ∂` `--f f` `--minOcc <[0,1)> (mặc định 1e-6, D41)`
`--epsilon ε (mặc định 1e-9)` `--workers n` `--parts n` `--top n` `--limit <n>`.
Xem `Main help` trong lúc chạy.

`--limit <n>` đọc **tối đa n giao dịch đầu** rồi mới mining (tiện cho dataset lớn như kosarak ~1M tx);
bỏ `--limit` (hoặc `0`) = đọc hết. Nếu dataset ít hơn n giao dịch thì đọc hết và in dấu
(`note: limit=n ignored - whole dataset read (…)`).

`--dataset` bắt buộc có extension (`DatasetFile.requireSupported`): `.dat/.txt/.text/.csv/.tsv` (text),
`.zip` (giải nén entry, đúng 1 entry); `.rar/.7z` và không có extension bị **từ chối**. Không phân biệt hoa/thường.
Bản nén nằm ở `dataset/zip/`.

```bat
Main mine --dataset dataset\zip\chess.dat.zip --partial 0.15 --f 0.9 --parts 1
```

## Ví dụ

```bat
:: dataset thưa, nhiều part
Main mine --dataset dataset\retail.dat --partial 0.01 --f 0.9 --workers 12 --parts 5

:: dataset lớn — chỉ đọc 200k giao dịch đầu
Main mine --dataset dataset\kosarak.dat --limit 200000 --partial 0.01 --f 0.9 --parts 5

:: tra cứu cửa sổ trước khi chọn ∂
Main window --f 0.9 --minOcc 0.000001
Main validate --dataset dataset\retail.dat --partial 0.01 --f 0.9
```

## Lưu ý

- **Ngưỡng ∂ khả thi**: với `f=0.9, minOcc=1e-6` ⇒ W=153 và `dp_max = Z/N_eff ≤ ~0.065`.
  Chọn `∂` lớn hơn sẽ **short-circuit** (DOP rỗng theo D19) — dùng `window`/`validate` để kiểm trước.
- **Dataset dày** (mushroom, newMushroom, pumsb_star, accidents…) có thể bùng nổ số mẫu ở ∂ thấp;
  `newMushroom` ở ∂=0.05 đã cho hàng chục nghìn DOP (V3 vẫn nhanh hơn V2 4.5–28× nhờ prune + đa luồng).
- Ở dataset **nhỏ**, `--workers 12` có thể chậm hơn `--workers 1` do overhead fork/join — muốn đo thuần
  nên thử cả hai.
- Số học dùng `Double.compare` (exact) trong golden-assert: không "gần đúng" mà khớp bit-for-bit.

## Xem thêm

- `pom.xml` — mô tả module; phụ thuộc `dhopm-common` (V1/V2 chỉ ở `test` scope).
- `../../docs/reports/G3-V3-OPTIMIZED-BAOCAO.md` — báo cáo G3 (thiết kế, test, benchmark).
- `../../docs/reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` — so sánh V1/V2/V3.
- `../../docs/reports/G3-V3-OPTIMIZED-BAOCAO.md` §5 — số đo: V3 nhanh hơn V2 2.3–2.9× (thưa) và 4.5–28× (dày).
- `../dhopm-v2-minocc/README.md` — ngữ nghĩa cửa sổ minOcc (V2) mà V3 kế thừa.
- `../dhopm-v1-standard/README.md` — engine tham chiếu (golden) và bộ lệnh chuẩn.
