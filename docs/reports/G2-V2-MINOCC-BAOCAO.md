# Báo cáo chi tiết — Giai đoạn G2: V2 MinOcc/Window (DHOPM)

> Nguồn triển khai: `docs/phases/P2-G2.md` · Đặc tả: `docs/plans/02-MINOCC-WINDOW-VERSION-PLAN.md`,
> `docs/plans/00-OVERALL-PLAN.md` §2.3/2.7 · Sổ quyết định: `docs/DECISIONS.md` D18, D38–D41.
> Trạng thái tại ngày báo cáo: **code + test + CLI + tài liệu đã xong**; số đo benchmark là khảo sát
> `--limit` 1 lần (chưa median), chờ người duyệt chốt tham số chính thức.

---

## 1. Mục tiêu giai đoạn

Triển khai **V2 MinOcc/Window** — cửa sổ suy giảm `W(f,minOcc)` trên nền V1 (oracle). Ba trụ:

1. **Bảo toàn (INV-I):** `minOcc = 0 ⇒ V2 bit-for-bit ≡ V1`; TC1–TC8 luôn xanh trên V2.
2. **Sai số kiểm soát (INV-G):** `|DO_win − DO_full| ≤ minOcc`; minSup 2 pha `∂×N_eff`, `N_eff=min(TL,W)`.
3. **Hiệu quả:** GĐ0 window O(1), bỏ qua ngoài cửa sổ khi xây, cận `min(DUBO, Z(X))`, short-circuit rỗng O(1).

Bộ ba sản phẩm: nền cửa sổ trong `dhopm-common` (M1), module `dhopm-v2-minocc` (M2–M5),
CLI 10 lệnh + bench ablation (M6), tài liệu + đo `--limit` (M7).

---

## 2. Những gì đã bàn giao

### 2.1 dhopm-common (mở rộng — G2-M1)

| Thành phần | Vai trò |
|---|---|
| `window.WindowMath` + `WindowInfo` | `computeWindow(f,minOcc)` (W=∞ khi minOcc=0/f=1); `N_eff`, `minSup`, `maxDO=Z(f,TL)`; **hai hàm ∂ riêng**: `maxPartialExact = Z/N_eff` (validator dùng) và `maxPartialAsymptotic = 1/((1−f)·W)` (chỉ in bảng) — **D38** |
| `window.ParameterValidator` | mã lỗi ổn định; chặn ∂/f/minOcc ngoài miền; dùng công thức chính xác |
| `config.MiningConfig` | thêm tham số `minOcc` (cửa sổ; lib default `1e-6` — **D41**); `epsilon` = sai số so sánh (`1e-9`) |
| `contract.WindowAwareEngine` + `WindowListener` | SPI đọc window (`windowInfo()`, `warnings()`) — CLI in khối window qua API mở (D6) |
| `WindowMathTest` | đối chiếu bảng W (§2.3.1), bảng ∂ (§6.4), **case TL<W phân biệt 2 công thức D38** (TL=88: exact 7.39% vs asymptotic 1.295%) |

### 2.2 dhopm-v2-minocc (mới) — lớp theo giai đoạn (package `dhopm.v2`)

| Giai đoạn | Lớp | Nội dung |
|---|---|---|
| GĐ0 | `window.Handle` + `WindowBuffer` | Proxy 2 tầng (slot `tx` + `tid`); circular `tid mod W`, evict O(1) trả boolean; evict `m−W` trước write `m`; skip write khi `batchMax−tid ≥ W` |
| GĐ1 | `dho.Entry(Handle ref1, int len, int tid)` + `DHONode`/`DHOList` · `construction.DHOListBuilder` | Entry 3 trường (G2-D13/D39); node có `head()`, `support()=size−head`, `discardDeadPrefix()`, `tids-live-only`; `add`/`addNoWindow` |
| GĐ2 | `reconstruction.Reconstructor` | bỏ prefix chết rồi tích lũy entry sống; do song song theo nút, gộp sau join (INV-E) |
| GĐ3 | `mining.DuboBound(dubo,zX)` + `ConditionalListBuilder` + `Miner` | bound `min(DUBO, Z(X))` khi window hữu hạn; `windowTruncated` flag; bỏ partner `support<minSup` trước khi giao |
| công thức | `metrics.MetricCalculator` (≡ V1) + `metrics.ZCalculator` | DO; `Z(f,TL)=(1−f^TL)/(1−f)` |
| engine | `engine.MiningEngineV2` | `PhaseAwareEngine + ProgressAwareEngine + WindowAwareEngine + AutoCloseable`; `defaults(∂,f)` ⇒ minOcc=1e-6 (D41); short-circuit; getter `windowInfo()/warnings()/globalNodeCount/…` |
| CLI | `cli.Main` + `CliSupport` + **10 lệnh** | `mine` (có **khối window**) · `detail` · `stream` · `window` (tra cứu, no dataset, hậu tố `…Asymptotic`/`…Exact=null` — G2-D14) · `validate` (mã ổn định, exact khi có dataset, exit 1) · `sweep` (`--partials/--fs/--minOccs`) · `golden` (TC1–TC8 minOcc=0 + TC10/12/18) · `inspect` · legacy `--dataset…`=`mine` |

**Ghi chú tên gọi (early-access):** đặc tả §5.2.1 gọi trường giữa Entry là `ref2` nhưng đồng thời
dùng làm độ dài `|T|`; code đặt tên `len`. Node **không** lưu ref2 — `len/tid` là bản sao nội trang
(Entry dùng lại cho virtual-TID ngoài cửa sổ). `tid` khai `int` (not long) — đủ cho W ≤ 1e7.

### 2.3 dhopm-bench (mở rộng)

`BenchmarkMain` thêm `--engine v1|v2`, `--minOcc`, `--limit` (đọc tối đa n tx đầu) ⇒ chạy
**ablation V1 vs V2** trên cùng input, in minOcc trong header (D41).

### 2.4 Tài liệu

- `implementation/dhopm-v2-minocc/README.md` · `docs/design.md` · `docs/test-report.md` · `docs/benchmark.md`.
- Báo cáo này (`docs/reports/G2-V2-MINOCC-BAOCAO.md`).
- `README.md` gốc cập nhật V2 ✅ (mục Tiến độ + bảng phiên bản).

---

## 3. Kiểm thử — 105 test xanh / 0 lỗi

| Nguồn | Số test | Nội dung cốt lõi |
|---|---|---|
| dhopm-common | 55 | + `WindowMathTest` (bảng W/∂, **TL<W chống nhầm D38**), `MiningConfigTest` mở rộng minOcc |
| dhopm-v1-standard (oracle) | 32 | không đổi hành vi (chỉ thêm `minOcc` vào config) |
| dhopm-v2-minocc | 18 | dưới đây |

**InvarianceTest (4):** TC9 minOcc=0 bit-for-bit ≡ V1 (INV-I, chạy trước mọi test) · minOcc=0 window không kích
hoạt · invariance workers {1,2,4,CPU} minOcc=0 · deterministic windowed minOcc>0.

**WindowBehaviorTest (14):** TC10 W≥TL ≡ paper · TC11 INV-G (`|ΔDO| ≤ minOcc`) · TC12 f=1 giữ full window ·
TC13 dead-prefix invariant + **truncated equivalence** · TC14 minSup đóng băng ∂×W (pha 2) ·
TC15 fail-fast config · TC16 **short-circuit O(1)** khi minSup > Z+ε · TC17 item ngoài window
không có live support · TC18 window ≡ full-recompute trên window, so tổ hợp (∂,f,minOcc) ·
E1 minOcc=0=paper · E2 f=1 suy biến nhưng chạy · E8 ∂=0 cảnh báo + bùng nổ DOP ·
E9 TL<W pha 1 (≡ paper) · E6/E10 ∂ khả thi exact/asymptotic.

**Golden (CLI):** `golden` ⇒ TC1–TC8 8/8 PASS.

> Một test assert trong quá trình làm bị **viết sai theo kỳ vọng đặc tả** (không phải lỗi thuật
> toán) và được sửa về đúng ngữ nghĩa: `DOP_win ⊇ DOP_full` không đúng vì minSup 2 pha mở rộng tập
> ở pha 2 — xem mục 5. Không ai sửa golden/paper output.

---

## 4. Kết quả chạy thật — ablation V1 vs V2 (1 lần, `--limit`)

### 4.1 Demo `mine` trên default.dat (∂=0.1, minOcc=1e-6)

```
window: W=153 N_eff=8 minSup=0.8000 Z=5.6953 evict=0 live=26 dead=0
part   loaded  last_tid  constr_ms reconst_ms mining_ms total_ms patterns
1      8       8         2          8          4         14       15
```

### 4.2 retail.dat — 88 162 tx (limit 100 000 → đọc hết), ∂ = 0.15

| engine | constr | reconst | mining | total (ms) | patterns | heap (MB) |
|---|---|---|---|---|---|---|
| v1 | 98 | 110 | 83 | **291** | 0 | 180.6 |
| v2 (minOcc=1e-6) | 17 | 0 | 0 | **17** | 0 | 119.8 |

→ **≈ 17×**, cùng kết quả rỗng (đúng chuẩn: DO ≤ Z ≈ 10 < minSup = 0.15×88 162).

### 4.3 kosarak.dat — 200 000 tx, ∂ = 0.01

| engine | constr | reconst | mining | total (ms) | patterns (part 5) |
|---|---|---|---|---|---|
| v1 | 149 | 153 | 1 642 | **1 946** | 0 (minSup=2 000) |
| v2 (minOcc=1e-6) | 31 | 64 | 97 | **193** | 3 (minSup=∂×153=1.53) |

→ **≈ 10×.** Số pattern chênh là **do thiết kế** (minSup đóng băng ở ∂×W pha 2), không phải lỗi —
đã ghi chú rõ trong `docs/benchmark.md`.

---

## 5. Phát hiện quan trọng khi triển khai (early-access)

1. **minSup 2 pha làm "đảo chiều" tập DOP.** Khi TL ≥ W, minSup của V2 đông cứng ở `∂×W` ≤ `∂×TL`.
   Vì vậy một pattern có `DO ≤ ∂×TL` nhưng `DO ≥ ∂×W` sẽ **có trong V2, không có trong V1** ở cùng ∂.
   Hệ quả: (a) TC11 phải dùng INV-G (sai số DO) và quan hệ `DOP_win ⊆ DOP_full` theo support, không
   được assert đẳng thức hay bao hàm đơn giản; (b) khi so ablation V1 vs V2 phải luôn nêu minSup
   từng bên (đã làm ở mục 4.3).
2. **Trần `Z(f,TL) < minSup` là lý do rỗng phổ biến nhất của benchmark.** Short-circuit O(1) biến
   trường hợp G1 từng mất 87–300 s (retail ∂=0.1%, mushroom ∂=6%) thành ∅ tức thì — đúng y Điểm 3
   của `README.md` gốc.
3. **Ngữ nghĩa "dead prefix"/evict ảnh hưởng tiếm ẩn đến minSup**: `N_eff` tính theo `TL` (số
   transaction stream) chứ không theo số entry sống còn lại — giữ đúng công thức đặc tả; test
   TC13 chứng minh kết quả window ≡ chạy stream cắt ngay trước cửa sổ.
4. **Hai công thức maxPartial lệch nhau một bậc** (TL=88, f=0.9, minOcc=1e-6: exact 7.39% vs asymptotic
   1.295% — tỉ lệ ×5.7, ở TL=4 còn ×13) — D38 đúng đắn: validator luôn dùng exact.
5. **Dense dataset vẫn bùng nổ tổ hợp** dù có window (chess ∂=0.15 quá 180 s; nhất quán với
   mushroom ở G1) — cửa sổ giảm chiều dài stream nhưng không chữa được độ đặc.

---

## 6. Điều chỉnh so với kế hoạch P2-G2

| Mục | Kế hoạch | Thực hiện |
|---|---|---|
| G2-D13 | `Entry(Handle, int ref2, long tid)` | `Entry(Handle, int len, int tid)` — đặt tên `len` cho rõ độ dài; `tid` int (đủ) |
| §5.2.1 | node giữ `ref2` riêng | node chỉ giữ `ref1`; `len/tid` là bản sao nội trang (tránh deref) |
| TC11 | đếm DOP | assert INV-G + `DOP_win ⊆ DOP_full` theo support (minSup 2 pha — mục 5.1) |
| Bench | (P2-G2 không quy) | `BenchmarkMain` mở rộng `--engine --minOcc --limit` — chạy ablation được ngay |

---

## 7. Tối ưu trung thực đã làm / chưa làm

**Đã làm (không đổi kết quả, kiểm chứng bằng INV-I/Determinism):**
- GĐ0 skip-write khi `batchMax−tid ≥ W` — V2 giữ O(live) entry thay vì O(TL).
- Bỏ partner `support < minSup` trước khi giao (kế thừa từ V1).
- Short-circuit `minSup > Z+ε ⇒ ∅` (mới, O(1)).
- Cận `min(DUBO, Z(X))` (mới, chỉ window hữu hạn).

**Chưa làm:** tái sử dụng kết quả mine giữa các phần incremental; FJ/lock-free (V3); median ≥ 3;
hồ sơ V2 `--limit` trên full chainstore (1.1M tx); `windowOverride` CLI (D40, research-mode).

---

## 8. Quyết định còn treo dành cho người duyệt

1. Chốt **param đo chính thức** (∂, minOcc, dataset, `--limit`) → lấy median 3 lần cho `benchmark.md` và báo cáo chính thức.
2. Xác nhận **tên trường `len`** thay `ref2` và `tid: int` (mục 6) là chấp nhận được ở tầng thiết kế.
3. Xác nhận cách trình bày **pattern khác nhau do minSup 2 pha** (mục 4.3/5.1) — có cần flag `--paper-minsup` để so sánh đồng minSup không.
4. Trả lời: có mở `windowOverride` (D40) ngay ở G2 hay chờ G4/app để cấp `research-mode`.

---

## 9. Cách chạy lại toàn bộ

```bat
:: từ thư mục gốc (set sẵn JDK 25 qua run.bat)
cmd /c run.bat -q                          :: build + 105 test

:: CLI v2 (cần 3 classpath: v2 + common + v1 cho golden)
java -cp "implementation\dhopm-v2-minocc\target\classes;implementation\dhopm-common\target\classes;implementation\dhopm-v1-standard\target\classes" ^
     dhopm.v2.cli.Main mine --dataset dataset\default.dat --minOcc 0.000001
java -cp "..." dhopm.v2.cli.Main window --f 0.9 --minOcc 1e-6
java -cp "..." dhopm.v2.cli.Main validate --dataset dataset\default.dat --partial 0.15
java -cp "..." dhopm.v2.cli.Main sweep --dataset dataset\default.dat --minOccs 0,1e-6
java -cp "..." dhopm.v2.cli.Main golden

:: ablation V1 vs V2
java -cp "implementation\dhopm-bench\target\classes;implementation\dhopm-v2-minocc\target\classes;implementation\dhopm-v1-standard\target\classes;implementation\dhopm-common\target\classes" ^
     dhopm.bench.BenchmarkMain --dataset dataset\retail.dat --engine v2 --minOcc 1e-6 --partial 0.15 --parts 5 --limit 100000
```

---

## 10. Cấu trúc thay đổi (tổng kết)

```
implementation/
  dhopm-common/         window: WindowMath · WindowInfo · ParameterValidator
                        contract: WindowAwareEngine · WindowListener
                        config: MiningConfig (+ minOcc, epsilon; default 1e-6)
                        + WindowMathTest, MiningConfigTest mở rộng
  dhopm-v2-minocc/     (MỚI) window·dho·construction·metrics·reconstruction·mining·engine·cli
                        cli: Main + CliSupport + Mine/Detail/Stream/Window/Validate/Sweep/Golden/Inspect
                        docs: README, design, test-report, benchmark
                        test: InvarianceTest(4) + WindowBehaviorTest(14)
  dhopm-bench/          BenchmarkMain: + --engine v1|v2 --minOcc --limit (ablation)
  pom.xml               <modules> += dhopm-v2-minocc
docs/
  reports/G2-V2-MINOCC-BAOCAO.md    (MỚI) báo cáo này
  plans/… (02, 00)                    giữ nguyên — nghĩa triển khai pin ở design.md (early-access)
README.md                             cập nhật V2 ✅ (mục Tiến độ + bảng phiên bản, TC9–TC18)
```