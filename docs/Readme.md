# DHOPM / DOPM — README tổng thể (docs)

> **Vai trò của tài liệu này:** điểm vào (entry point) tóm tắt toàn cảnh dự án — dự án là gì, hiện có gì, đã kiểm chứng tới đâu, đọc tài liệu nào, và những điểm cần biết trước khi tin vào bất kỳ con số nào.
>
> Đây **không phải** nguồn chân lý về quyết định (nguồn đó là `docs/DECISIONS.md`) cũng không phải hướng dẫn cài đặt chi tiết (nguồn đó là `README.md` ở thư mục gốc). Đây là **bản đồ tổng thể + trạng thái thực tế đã kiểm chứng**.

---

## 1. Dự án này là gì

Triển khai & nghiên cứu thuật toán **DHOPM/DOPM** — khai thác **DOP (Damped Occupancy Pattern)** trên **transactional stream** (giao dịch tới dần theo thời gian), với hệ số suy giảm `f` theo độ tuổi giao dịch.

- **Đơn vị đo:** `DO(X) = Σ_T O(X,T) · f^(TL−T.tid)`, với `O(X,T) = |X| / |T|`.
- **Ngưỡng:** `X` là DOP ⇔ `DO(X) ≥ minSup`.
- **Ý tưởng mới (ngoài paper):** thêm **`minOcc`** (ngưỡng occupancy / ngưỡng dưới của DO) ⇒ **cửa sổ suy giảm hữu hạn** `W(f,minOcc)`. Nhờ đó `minSup = ∂ × N_eff` **đóng băng** khi cửa sổ đầy, và bộ nhớ/thời gian gần như **độc lập độ dài stream**.

Nguồn gốc: paper (`docs/root/1-s2_0-S095219762600792X-main.md`), bảng chạy tay TC1–TC8 (`docs/root/Nhom01_VDChayTay.md`), ý tưởng minOcc (`docs/Draft Idea.txt`).

---

## 2. Bốn phiên bản

| Phiên bản | Tên | Module | Định vị | Threading | Trạng thái **thực tế** |
|---|---|---|---|---|---|
| **V1** | Standard | `dhopm-v1-standard` | Oracle (paper, `minOcc=0`), GoF | Level 1 | ✅ Xong — 32 test xanh, đóng băng |
| **V2** | MinOcc / Window | `dhopm-v2-minocc` | Ngữ nghĩa cửa sổ minOcc | Level 1 | ✅ Xong — 18 test xanh (+55 common) |
| **V3** | Optimized | `dhopm-v3-optimized` | Tối ưu cấu trúc dữ liệu | Level 2 (ForkJoin) | ✅ Xong — 16 test xanh, `minOcc=0 ≡ V1`, `minOcc>0 ≡ V2` bit-for-bit |
| **V4** | Extreme | `dhopm-v4-extreme` | Trần hiệu năng | Level 3 | 🕓 Chưa code |

> ✅ **Cập nhật V3 (2026-10-11).** V3 đã được **viết lại toàn bộ** + **sửa lỗi nhân bản tác vụ mining** (`MinerOptimized`): build xanh, **16 test** (invariance + window behavior), toàn reactor **121 test**; bit-for-bit với V1 (paper) và V2 (window), deterministic theo số luồng. Về hiệu năng, V3 **nhanh hơn V2 2.3–2.9×** trên dataset thưa (mining thật) và **4.5–28×** trên dataset dày, **heap thấp hơn**. Số liệu & phân tích: `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` §4.

---

## 3. Trạng thái kiểm chứng (bằng chứng chạy được)

| Lệnh | Kết quả đã xác nhận |
|---|---|
| `mvn -f implementation\pom.xml test` (toàn reactor) | ✅ **BUILD SUCCESS** · common **55** + v1 **32** + v2 **18** + v3 **16** = **121 test xanh** |
| `mvn -f implementation\pom.xml -pl dhopm-v3-optimized test` | ✅ **BUILD SUCCESS** — v3 **16 test xanh** |
| `run.bat` (mvn test toàn reactor) | ✅ **thành công** |

Số test: **121 xanh** (không phải "56" như README gốc ghi — xem báo cáo mâu thuẫn).

Đo ablation V1 vs V2 (1 lần, khảo sát `--limit`, từ `G2-V2-MINOCC-BAOCAO.md`):

| Dataset | V1 | V2 (`minOcc=1e-6`) | Ghi chú |
|---|---|---|---|
| `retail.dat` (88 162 tx) | 291 ms | 17 ms (≈ **17×**) | cùng kết quả rỗng (DO ≤ Z ≈ 10 < minSup) |
| `kosarak.dat` (200 000 tx) | 1 946 ms | 193 ms (≈ **10×**) | số pattern chênh **do minSup 2 pha**, không phải lỗi |

Đo trực tiếp V3 vs V2 (2026-10-11, `BenchmarkMain --engine v2|v3`, `f=0.9`, `minOcc=1e-6`, `parts=5`):

| Dataset | V2 total | V3 total | patterns | Ghi chú |
|---|---|---|---|---|
| `retail.dat` 88 162 tx, ∂=0.15 | 17 ms | 17 ms | 0 | short-circuit, không phân biệt |
| `kosarak.dat` 200 000 tx, ∂=0.01 | 89 ms | **80 ms** | 3 | reconst 24 → 9 ms |
| `kosarak.dat` 990 002 tx (full), ∂=0.01 | 130 ms | **100 ms** | 1 | reconst 23 → 10 ms; ~23% nhanh hơn |

---

## 4. Công thức cốt lõi (canonical — `docs/plans/00-OVERALL-PLAN.md` §2)

```
W(f, minOcc) = ⌈ ln(minOcc·(1−f)) / ln f ⌉       minOcc=0 hoặc f=1 ⇒ W = ∞
N_eff(TL)    = min(TL, W)
minSup(TL)   = ∂ × N_eff(TL)                      pha 1: ∂×TL ; pha 2: ∂×W (đóng băng)
Z(f, TL)     = (1 − f^TL) / (1 − f)               trần của mọi DO
UB'(X)       = min( DUBO(X), Z(X) )               bound prune chặt hơn (chỉ cắt thêm, không cắt sai)
```

**Ba bất biến then chốt:**
1. **INV-I** — `minOcc = 0` ⇒ mọi phiên bản **bit-for-bit ≡ V1 ≡ paper**.
2. **INV-G** — `|DO_win(X) − DO_full(X)| ≤ minOcc` (sai số kiểm soát được).
3. **Short-circuit trần DO** — `minSup > Z(f,TL)` ⇒ kết quả **chắc chắn rỗng**, trả O(1).

Hai quy ước dễ nhầm:
- `minOcc` (cửa sổ) **≠** `ε`/`epsilon` (sai số so sánh, mặc định `1e-9`).
- `minSup = ∂ × N_eff` (2 pha), **không** phải `∂ × TL` khi đã có cửa sổ.

---

## 5. Cấu trúc repo

```
JVNC/
├── docs/
│   ├── Readme.md            ⬅ tài liệu này (tóm tắt tổng thể)
│   ├── Overview.md          cổng vào cũ (một phần đã lỗi thời)
│   ├── DECISIONS.md         ⭐ sổ quyết định (ADR D1–D49) — nguồn chân lý
│   ├── plans/               00 tổng thể, 01–04 theo phiên bản, 05 CLI/API, 07–08 G5 (phân tích + contract)
│   ├── srs/                 DHOPM-SRS, DHOPM-UI-LAYOUT
│   ├── phases/              P0–P5 (kế hoạch từng giai đoạn; P5 = G5)
│   ├── reports/             G1, G2, SYNTHESIS, + báo cáo so sánh V1–V2–V3
│   ├── root/                paper + bảng chạy tay TC (mốc, không sửa)
│   └── Draft Idea.txt       ý tưởng minOcc của tác giả
├── dataset/                 10 dataset FIMI + default.dat + zip/
├── implementation/          Maven multi-module (Java 25)
│   ├── dhopm-common/        io · config · contract · util · window · TestKit (55 test)
│   ├── dhopm-v1-standard/   oracle V1 (32 test) + CLI 5 lệnh
│   ├── dhopm-v2-minocc/     V2 cửa sổ minOcc (18 test) + CLI 10 lệnh
│   ├── dhopm-v3-optimized/  V3 tối ưu — 16 test, build xanh
│   └── dhopm-bench/         harness benchmark (ablation V1/V2/V3)
├── run.bat                  chạy test một lệnh (toàn reactor xanh — 121 test)
└── README.md                README gốc (chạy/cài đặt)
```

---

## 6. Thang tài liệu (khi mâu thuẫn, tài liệu cao hơn thắng)

`DECISIONS.md` → `plans/` → `srs/` → `phases/` → `module/docs/` → `reports/`.

Quy tắc: plan/SRS **không tự quyết lại**; quyết định mới phải thêm `D<n>` vào `DECISIONS.md`, **không sửa lịch sử**.

---

## 7. Chạy dự án

```bat
:: cài đặt: JDK 25 (C:\Program Files\Java\jdk-25.0.4.1) + Maven 3.9+
:: chạy test (một lệnh)
run.bat

:: hoặc chạy trực tiếp toàn reactor
mvn -f implementation\pom.xml test
```

CLI: V1 5 lệnh (`mine/detail/stream/golden/inspect`); V2 10 lệnh (thêm `window/validate/sweep`).

---

## 8. Muốn biết gì thì đọc gì

| Muốn biết | Đọc |
|---|---|
| Thuật toán chính xác làm gì | `plans/00-OVERALL-PLAN.md` §2 |
| Công thức cửa sổ + bảng tra `W` | `plans/00-OVERALL-PLAN.md` §2.3.1 |
| Vì sao chọn kiến trúc này | `DECISIONS.md` |
| Kết quả G1 (V1) | `reports/G1-V1-STANDARD-BAOCAO.md` |
| Kết quả G2 (V2) | `reports/G2-V2-MINOCC-BAOCAO.md` |
| Kết quả G3 (V3) | `reports/G3-V3-OPTIMIZED-BAOCAO.md` |
| **G5 (kế hoạch + hợp đồng FE)** | `phases/P5-G5.md` · `plans/08-FE-CONTRACT.md` · `plans/07-G5-PIVOT-ANALYSIS.md` |
| **So sánh tối ưu V1–V2–V3 + mâu thuẫn** | `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` |
| Làm tiếp gì | `phases/` + `plans/00` §8 |

---

## 9. Tổng kết trạng thái

- **Nền tảng thuật toán vững:** canonical spec, bộ test TC1–TC18 + E1–E10, V1 oracle, V2 cửa sổ minOcc, **V3 tối ưu** — **121 test xanh**, hồi quy `minOcc=0 ⇒ ≡ V1` bit-for-bit.
- **V2 là mốc giá trị thực đã đo:** ~10–17× nhanh hơn V1 trên ablation, biến "87–300 s ra rỗng" thành ∅ tức thì.
- **V3 đã đạt tính đúng:** bit-for-bit với V1 (paper) và V2 (window), deterministic theo số luồng; **tối ưu rõ rệt** — nhanh hơn V2 **2.3–2.9×** (thưa/∂=0.01) và **4.5–28×** (dày/∂=0.05), heap thấp hơn. Chi tiết: `reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md`.
- **Tài liệu trạng thái đã đồng bộ:** README gốc / Overview / SYNTHESIS đã cập nhật theo thực tế 121 test và trạng thái các phiên bản.
- **Hướng tiếp theo (2026-10-11):** **G5 (Manager CLI/API) ưu tiên trước G4** (`D45`); phạm vi G5 + hợp đồng FE đã chốt (`D46`–`D49`): transport **TCP `7079`** + stdio, CLI **Hybrid**, lưu file `isLog`/`fetch`, config 3 cấp. Kế hoạch: `phases/P5-G5.md`; contract: `plans/08-FE-CONTRACT.md`.

---

*Cập nhật khi roadmap/trạng thái đổi. Nguồn chân lý quyết định vẫn là `docs/DECISIONS.md`.*
