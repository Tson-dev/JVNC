# Báo cáo test — dhopm-v2-minocc

Cập nhật lần cuối: `mvn -f implementation\pom.xml test` (JDK 25.0.4.1, Maven 3.9.16).

## Tổng quan

| Nguồn | Lượng test | Lỗi |
|---|---|---|
| dhopm-common (G0/G2-M1: `WindowMathTest`, `MiningConfigTest` mở rộng, ParameterValidator, reader/config/testkit) | 55 | 0 |
| dhopm-v1-standard (oracle, không đổi hành vi — chỉ thêm `minOcc` vào config) | 32 | 0 |
| dhopm-v2-minocc | 18 | 0 |
| **Tổng reactor** | **105** | **0** |

## Golden — INV-I hồi quy (bắt buộc G2-M5)

Tất cả 8 bài TC1–TC8 chạy **với minOcc = 0** trên V2 ⇒ **bit-for-bit ≡ V1** (INV-I), chạy trước
mọi test cửa sổ khác. CLI `golden` xác nhận 8/8 PASS.

```
TC1 f=0.9 ∂=0.15 minSup=1.2 -> 2/2   TC5 f=1.0 ∂=0.15 minSup=1.2 -> 9/9
TC2 f=0.9 ∂=0.20 minSup=1.6 -> 0/0   TC6 f=0.9 ∂=0.25 minSup=1.0 -> 3/3
TC3 f=0.9 ∂=0.10 minSup=0.8 -> 15/15 TC7 f=0.9 ∂=0.15 minSup=1.5 -> 9/9
TC4 f=0.8 ∂=0.15 minSup=1.2 -> 0/0   TC8 f=0.9 ∂=0.30 minSup=1.5 -> 1/1
```

## Nhóm test V2 (18)

### InvarianceTest (4)

- `tc9_minOccZeroIsBitForBitV1` — **TC9**: minOcc=0 ⇒ V2 ≡ V1 bit-for-bit trên TC1–TC8 (INV-I).
- `minOccZeroDoesNotActivateWindow` — minOcc=0 ⇒ cửa sổ không kích hoạt (không evict, N_eff=TL, minSup=∂×TL).
- `invarianceAcrossPoolSizes` — minOcc=0, workers ∈ {1,2,4,CPU}: kết quả bit-for-bit (INV-E).
- `deterministicAcrossPoolSizesWindowed` — minOcc>0 (cửa sổ bật): kết quả vẫn độc lập số luồng.

### WindowBehaviorTest (14)

- `tc10_windowWiderThanStreamEqualsPaper` — W ≥ TL ⇒ kết quả ≡ paper (mọi DOP bằng nhau).
- `tc11_narrowWindowInvG` — cửa sổ hẹp: mọi `|DO_win − DO_full| ≤ minOcc` (INV-G); tập DOP cửa sổ ⊇ đầy đủ theo pha 2 minSup.
- `tc12_factoryOneKeepsFullWindow` — f=1 ⇒ W=∞, không suy giảm, giữ toàn bộ stream.
- `tc13_deadPrefixInvariantAndTruncatedEquivalence` — node mất head/entry chết bị `discardDeadPrefix`; kết quả ≡
  chạy trên stream bị cắt ngay trước window (truncated equivalence).
- `tc14_twoPhaseMinSupFreezesAtWindow` — TL vượt W: minSup đóng băng ở `∂×W` (pha 2), không theo ∂×TL.
- `tc15_failFastOnInvalidConfig` — cấu hình bất hợp lệ (∂/f/minOcc ngoài miền) bị chặn với mã lỗi ổn định (ParameterValidator).
- `tc16_shortCircuitWhenMinSupExceedsDoCeiling` — `minSup > Z + ε` ⇒ ∅ O(1) không cần mine.
- `tc17_itemAOutsideWindowHasNoLiveSupport` — item chỉ xuất hiện ngoài cửa sổ: không có entry sống đóng góp.
- `tc18_windowEqualsTruncatedRecomputeOverCombos` — so lại trên tổ hợp (∂,f,minOcc): DO cửa sổ = full-recompute trên window.
- `e1_minOccZeroIsPaper` — minOcc=0 = paper (tương đương TC9, độc lập ∂).
- `e2_decayOneIsDegenerateButRuns` — f=1 (W=∞) suy biến nhưng chạy ổn định (HOP không suy giảm).
- `e8_partialZeroWarnsAndExplodesOutput` — ∂=0 ⇒ minSup=0, toàn bộ sinh DOP; cảnh báo khi config.
- `e9_tlBelowWindowIsPhaseOne` — TL < W ⇒ pha 1: minSup=∂×TL (≡ paper), chống nhầm 2 công thức maxPartial (D38).
- `e6_e10_feasiblePartialRuns` — ∂ khả thi (chính xác `Z/N_eff`) chạy được; dùng cả ∂ asymptotic và exact tách bạch.

## Ghi chú sửa khi chạy thật (early-access)

Các test này **không sửa thuật toán**, mà pin lại ngữ nghĩa khi đặc tả mâu thuẫn (chi tiết ở
`../../docs/reports/G2-V2-MINOCC-BAOCAO.md`):
- Evict count trong chế độ bulk+skip là 0 ở bước đầu (mọi tx ngoài window) — test assert theo tổng entry sống,
  không theo số sự kiện evict.
- Bỏ assert sai kiểu `DOP_full ⊆ DOP_win` (minSup 2 pha làm cửa sổ **rộng hơn** ở pha 2) — giữ INV-G +
  `DOP_win ⊆ DOP_full` theo support.
- E2 dùng stream 1 phần tử (∂=1, f=1) ⇒ chính xác `{A}`.
- `maxPartialExact` TL=88 vs asymptotic 1.295% (lệch ×5,7) — khẳng định D38.

## Cách chạy lại

```bat
cmd /c run.bat -q        :: hoặc
mvn -f implementation\pom.xml test
```
Phụ lục: `implementation/dhopm-v2-minocc/target/surefire-reports/TEST-*.xml`.