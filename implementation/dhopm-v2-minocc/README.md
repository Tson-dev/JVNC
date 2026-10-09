# dhopm-v2-minocc — DHOPM V2 MinOcc/Window

Bản triển khai **cửa sổ suy giảm minOcc** (damped window) của DHOPM — mở rộng V1 bằng công thức
`W(f,minOcc)`; ở `minOcc = 0` **bit-for-bit ≡ V1** (INV-I). Early-access: đặc tả còn mâu thuẫn, bộ
code/báo cáo này pin lại ngữ nghĩa đã chọn (xem `docs/design.md`).

## Cấu trúc

```
dhopm.v2
  window           Handle / WindowBuffer / (DUBO)           — GĐ0: circular window O(1) evict, Handle 2 tầng
  dho              Entry(Handle ref1, int len, int tid)     — node + ⟨ref1, |T|, tid⟩ (D39: lưu tid chống lỗi slot vòng)
                   DHONode.head() / support()=size−head / discardDeadPrefix()
  construction     DHOListBuilder (add / addNoWindow)       — GĐ1 quét 1 lượt + bỏ qua ngoài cửa sổ
  metrics          MetricCalculator (giống V1) + ZCalculator — DO, f^(TL−Td), trần Z(f,TL)
  reconstruction   Reconstructor                            — GĐ2: bỏ prefix chết rồi tích lũy entry sống
  mining           DuboBound(dubo, zX) / ConditionalListBuilder / Miner — GĐ3 + bound min(DUBO, Z(X))
  engine           MiningEngineV2                           — Engine + PhaseAwareEngine + ProgressAwareEngine + WindowAwareEngine
  cli              Main                                     — 10 lệnh (G2-M6)
```

## Chạy

```bat
:: từ thư mục gốc (cần JDK 25, tự set JAVA_HOME qua run.bat)
cmd /c run.bat -q                                     :: biên dịch + test toàn bộ reactor
```

## Bộ lệnh CLI (plan 02 §7 — kế thừa 5 lệnh chuẩn G1 + 3 lệnh cửa sổ mới)

| Lệnh | Mục đích | Ví dụ |
|---|---|---|
| `mine` | Tóm tắt 1 dòng/part + **dòng window** (W, N_eff, minSup, Z, evict/live/dead) | `Main mine --dataset dataset\default.dat --minOcc 0.000001` |
| `detail` | Debug từng phần + top-N DO + dòng window | `Main detail --dataset dataset\default.dat --parts 2 --top 3` |
| `stream` | Log thời gian thực (load + tick mining qua `MiningProgressListener`) | `Main stream --dataset dataset\default.dat` |
| `window` | Tra cứu W(f,minOcc) **không cần dataset**; dp_max asymptotic + hậu tố `…Asymptotic`, `…Exact=null` (G2-D14) | `Main window --f 0.9 --minOcc 0.000001` |
| `validate` | Kiểm cấu hình: mã lỗi ổn định; có `--dataset` thì kiểm **chính xác** `Z/N_eff` (D38); lỗi ⇒ exit 1 | `Main validate --dataset dataset\default.dat --partial 0.15` |
| `sweep` | Quét lưới `(∂, f, minOcc)` qua `--partials --fs --minOccs`, mine từng ô, in bảng | `Main sweep --dataset dataset\default.dat --minOccs 0,1e-6` |
| `golden` | TestKit: TC1–TC8 (minOcc=0, INV-I) + TC10/TC12/TC18 (cửa sổ) | `Main golden` |
| `inspect` | Thống kê dataset/config, không mining | `Main inspect --dataset dataset\default.dat` |
| *(legacy)* `--dataset …` | = lệnh `mine` (tương thích V1) | `Main --dataset dataset\default.dat` |

Options: `--dataset <file>` `--format fimi|text` `--partial ∂` `--f f` `--minOcc <[0,1)> (mặc định 1e-6, D41)`
`--epsilon ε (mặc định 1e-9)` `--workers n` `--parts n` `--top n` `--limit <n>`.
Xem `Main help` trong lúc chạy. `sweep` đọc các option lưới `--partials/--fs/--minOccs` (danh sách phân tách bằng dấu phẩy).

## Xem thêm

- `docs/design.md` — ánh xạ GoF, GĐ0/window, minSup 2 pha, short-circuit, bất biến INV-I/G, D38–D41
- `docs/test-report.md` — 18 test V2 (TC9–TC18 + E1/E2/E6/E8/E9/E10) + 105 test toàn reactor
- `docs/benchmark.md` — đo ablation V1 vs V2 trên retail/kosarak (`--limit`)
- `../../docs/phases/P2-G2.md` — kế hoạch giai đoạn G2 · `../../docs/reports/G2-V2-MINOCC-BAOCAO.md` — báo cáo bàn giao