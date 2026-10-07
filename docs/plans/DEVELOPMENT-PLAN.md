# KẾ HOẠCH PHÁT TRIỂN CHI TIẾT — DHOPM (DỰA TRÊN BÁO CÁO TỔNG HỢP)

> **Nguồn:** `docs/reports/SYNTHESIS-REPORT.md`  
> **Nguyên tắc:** Mỗi mốc **độc lập tạo ra giá trị** — dừng ở đâu cũng không phí. Ưu tiên **G2 (V2)** rồi **G5 (Manager CLI/API)** song song G3/G4.

---

## 1. GIẢI QUYẾT MÂU THUẬT VÀ LỖ HỒNG TỪ BÁO CÁO

| # | Mâu thuẫn/Lỗ hỏng | Giải pháp | Mốc thực hiện |
|---|-------------------|-----------|---------------|
| **C1** | CLI: 2 thiết kế mâu thuẫn (Positional `docs/Document/CLI` vs Flag-based V1 impl) | **Chọn V1 implementation làm chuẩn**. Xoá/cập nhật `docs/Document/CLI/**` thành "UX sketch cho App" hoặc bỏ. Cập nhật `Plan 01`, `Plan 05` khớp. | **Trước G2** (commit riêng) |
| **C2** | ResourceManager: Plan 00 F4 có, Plan 05 §9 bác bỏ, DECISIONS D8 chốt bác bỏ, nhưng MA4 vẫn ghi | **Xoá hoàn toàn ResourceManager native** khỏi mọi plan. Chỉ giữ `workers`, `limit`, `parts` trong Manager config. Cập nhật Plan 00 §4.3 F4, Plan 05 §9, §11 MA4. | **Trước G2** |
| **C3** | Config 3 cấp: Draft bỏ dở cấp 3, V1 không có config file | **Định nghĩa 3 cấp: `Default (code)` < `Config file (YAML)` < `CLI args`**. Triển khai `ConfigStore` ở `dhopm-common` + `config get/set` command (Plan 05 MA1 G-1). | **G5-MA1** |
| **C4** | Transport Manager: stdio (Plan 05 OP-1) vs TCP (Draft FLOW-A) | **Chốt: `serve` mở TCP local port + giữ `--json` stdio cho test**. Ghi rõ: LAN, không auth, rủi ro chấp nhận (Plan 05 §8). | **G5-MA1** (Q-2 Plan 06) |
| **C5** | Recovery: Draft muốn đầy đủ, Plan 06 §4.3 khuyến nghị mức nhẹ (MA2), DECISIONS bác bỏ đầy đủ | **Làm mức nhẹ ở MA2**: `JobLedger` (jobId, version, config, source, lastTid, status) + replay file. Journal feed để MA4 (nếu cần). | **G5-MA2** (G-5 Plan 06) |
| **C6** | Benchmark ∂: Paper ∂ không dùng được, Plan 00 §6.4 có miền khả thi nhưng chưa chốt giá trị chính thức | **Chốt bộ `∂` benchmark cho từng dataset** dựa trên miền khả thi `∂ ≤ Z/N_eff`. Ví dụ: retail ∂∈{2e-5..2e-4}, mushroom ∂∈{1e-4..1e-3}. Median ≥3 lần. | **G2-M7** (khi có V2) |
| **C7** | Tài liệu rỗng/cũ (`Overview.md`, `Spec.md`, `Document/Compoment/*`, `List Config.md`) | **Dọn dẹp**: Xoá file rỗng, sửa typo (`Compoment`→`Component`, `Commend`→`Command`, `dateset`→`dataset`, `parial`→`partial`). `Overview.md` → con trỏ vào README/DECISIONS/Plan 00. | **Song song G2** |
| **C8** | Protocol frontend: Plan 05 §7 draft, chưa chốt schema, mã lỗi, versioning | **Hoàn thiện §7 Plan 05 thành Contract v1.0** (JSONL, schema request/response/progress/error, version header). Giao cho Tâm review + test vector. | **G5-MA1** (trước MA3) |
| **C9** | V3 tối ưu: 14 điểm cần microbenchmark, chưa có tiêu chí định lượng | **Định nghĩa tiêu chí**: Speedup ≥ 1.2x hoặc Memory giảm ≥ 20% so V2 trên ≥ 3/4 dataset. Mỗi điểm có microbenchmark riêng, ghi rationale + số liệu vào design doc V3. | **G3-M2** |
| **C10** | `window`/`validate` trả asymptotic khi không có TL — chưa có test case | **Bổ sung test**: `WindowMathTest` case `TL < W` (chống nhầm 2 công thức D38), `ValidateCommandTest` case thiếu TL. | **G2-M1, M6** |

---

## 2. LỘ TRÌNH PHÁT TRIỂN (ROADMAP)

### G2 — V2 Epsilon/Window (**ƯU TIÊN SỐ 1**) — *"Đổi đẳng cấp phức tạp"*
**Thời gian:** 2–3 tuần | **Đầu vào:** V1 oracle hoàn tất | **Đầu ra:** V2 chạy, ablation V1↔V2

| Mốc | Nội dung | Tiêu chí kỹ thuật (Done Criteria) | Sản phẩm |
|-----|----------|-----------------------------------|----------|
| **G2-M1** | Nền tảng cửa sổ trong `dhopm-common` | `WindowMathTest` xanh: khớp bảng W (§2.3.1 Plan 00), bảng ∂ khả thi (§6.4), **case TL<W phân biệt 2 công thức D38**. `ParameterValidator` ném mã lỗi ổn định. V1 vẫn xanh sau đổi tên `epsilon`→`epsilonCmp`. | `WindowMath`, `WindowInfo`, `ParameterValidator`, `WindowAwareEngine`, `MiningConfig` cập nhật |
| **G2-M2** | Handle & Evict (GĐ0) | `Handle` + `WindowBuffer` circular O(1). `Entry(ref1,ref2,tid)` + `isLive()` check `tid` **bắt buộc**. Test nạp `≥2W` tx: **không đọc nhầm transaction mới** (D39). Evict đơn luồng trong `loadBatch` trước mọi pha song song. | `dhopm.v2.window.*`, `Entry`, `DHONode.head/size` |
| **G2-M3** | minSup 2 pha & Reconstruction | `minSup = ∂ × min(TL, W)` tính tại `mineNow`. `Reconstructor` dời `head`, DO chỉ entry sống. Level 1 threading. | `DHOListBuilder` (bỏ qua tx ngoài cửa sổ), `Reconstructor` |
| **G2-M4** | Trần, Bound & Mining | `ZCalculator` (`Z(f,TL)`, `Z(X)`). `DUBOCalculator` trả `UB' = min(DUBO, Z)`. Short-circuit `minSup > Z + ε_cmp` ⇒ ∅ O(1) + cảnh báo có số liệu. `Miner` dùng support sống, prune `UB'`. | `ZCalculator`, `DUBOCalculator`, `Miner`, `MiningEngineV2` |
| **G2-M5** | Nghiệm thu đúng đắn | **TC1–TC8 (ε=0) bit-for-bit ≡ V1** (INV-I). **TC9–TC18 + E1–E10 xanh**. Determinism workers {1,2,4,CPU}. Test `ε=0` không kích hoạt cửa sổ. | Test report đầy đủ |
| **G2-M6** | CLI & Quan sát | Lệnh `window` (không cần dataset, trả asymptotic + hậu tố), `validate` (mã lỗi ổn định), `sweep` (quét tổ hợp). `windowInfo` trong output `mine`/`detail`. `golden` chạy TC1–TC18. | CLI commands mới |
| **G2-M7** | Đo lường & Báo cáo | Instrumentation (evict count, live/dead, prune-hit). **Đo `|DO_win − DO_full|` trên dataset thật ⇒ kiểm chứng INV-G**. Ablation V1↔V2 ≥4 dataset (dense+sparse), cùng `(∂,f,ε)`, 5 phần, median ≥3 lần. **Scalability kosarak 200K→990K: memory đỉnh gần như phẳng**. Báo cáo ghi phần cứng/JDK/workers. | Báo cáo ablation V1↔V2, benchmark report |

---

### G5 — Manager CLI/API (`dhopm-cli`) — **SONG SONG G3/G4**
**Thời gian:** 2 tuần MA1, 1 tuần MA2, 1 tuần MA3 | **Đầu vào:** V1/V2 Engine API ổn định | **Đầu ra:** Frontend tích hợp được

| Mốc | Nội dung | Tiêu chí kỹ thuật | Sản phẩm |
|-----|----------|-------------------|----------|
| **G5-MA0** | Chốt OP-1..OP-6, Protocol v1.0, thống nhất Tâm | Protocol doc hoàn chỉnh (§7 Plan 05), test vector JSONL, Tâm xác nhận `BackendClient` thay `TsonV1Bridge`. | `docs/plans/05-BACKEND-CLIAPI-PLAN.md` v1.0 (chốt), `docs/contract/PROTOCOL-v1.md` |
| **G5-MA1** | Manager tối thiểu: `--json` + `serve`(TCP), Registry V1, `window`/`validate`, ParameterEcho | `--json mine` ≡ CLI text `mine`. `serve` TCP local port, JSONL. `window` khớp `WindowMathTest`. `validate` trả mã lỗi ổn định. Registry hardcode V1 (sẵn sàng thêm V2/V3/V4). | Module `dhopm-cli`, `ApiServer`, `CommandDispatcher`, `VersionRegistry`, `ParameterEcho` |
| **G5-MA2** | JobManager + SessionManager + JobLedger (mức nhẹ) + ConfigStore | Fan-out request nhiều version → sub-jobs → gộp `perVersion`. Session lock tuần tự. JobLedger: status/lastTid/source → replay file. ConfigStore 3 cấp + `config get/set` validate canonical. | `JobManager`, `SessionManager`, `JobLedger`, `ConfigStore`, `config` command |
| **G5-MA3** | Tích hợp Frontend (`javanc`) | Tâm thay `TsonV1Bridge` bằng `BackendClient` (theo Contract). Frontend hiển thị đúng kết quả qua Manager. Giữ `TamSimulationBridge` cho animation. | Frontend chạy được với backend |
| **G5-MA4** | (Tuỳ chọn) Remote hoàn chỉnh, backpressure, quota OS | Chỉ khi có nhu cầu thực tế. Mở D38 mới nếu cần. | — |

---

### G3 — V3 Optimized — *"Tối ưu trên nền ε"*
**Thời gian:** 2 tuần | **Đầu vào:** V2 hoàn tất + benchmark baseline | **Đầu ra:** V3 ≡ V2, nhanh hơn ≥ 3/4 dataset

| Mốc | Nội dung | Tiêu chí kỹ thuật |
|-----|----------|-------------------|
| **G3-M1** | Baseline V2 benchmark | Chạy V2 trên 10 dataset, f=0.9, ε cố định, ∂ trong miền khả thi, 5 phần, median ≥3. Lưu số liệu làm denominator. |
| **G3-M2** | Cải tiến cấu trúc dữ liệu (tuần tự → song song) | **Microbenchmark từng điểm** (14 điểm Plan 03 §4). Chỉ giữ phương án **speedup ≥ 1.2x HOẶC memory ↓ ≥ 20%**. Ghi rationale + số liệu. Điểm then chốt: bỏ Handle→`int txSlot`, SoA WindowBuffer, decay lookup `k=TL-tid`, Item=int + dict. |
| **G3-M3** | Threading Level 2 (ForkJoinPool) | Reconstruction node/task trên ForkJoin. Mining chia cây con theo ngưỡng độ sâu + min task size. Determinism pool {1,2,4,CPU}. Construction song song **có switch** (tắt nếu không tốt). |
| **G3-M4** | Nghiệm thu | **ε=0 ≡ V1/V2; ε>0 ≡ V2 double đầy đủ**. Determinism. Không còn đối tượng Handle. |
| **G3-M5** | Benchmark đầy đủ & Báo cáo | 10 dataset + scalability kosarak/chainstore. So sánh V1↔V2↔V3: runtime 3 pha, peak memory, throughput, latency batch, ratio. Báo cáo kèm quyết định tối ưu từng điểm. |

---

### G4 — V4 Extreme — *"Trần hiệu năng" (Ý tưởng, chi tiết hoá sau G3)*
**Chỉ ghi ý hướng, không commit deadline:**
- Data-oriented thuần: mảng phẳng, static method, recycle buffer
- Pipeline 3 pha xen kẽ (nếu chứng minh được semantic one-scan + cùng snapshot cửa sổ)
- Memory-mapped I/O, GC/JIT tuning
- **Tiêu chí:** ≡ V3; có báo cáo "giữ/bỏ từng ý tưởng" kèm số liệu.

---

### G6 — Debug/Compare App (`dhopm-app`) — *"Thấy được, hiểu được"*
**Bắt đầu SAU KHI G2 CÓ BẢNG SỐ** (STRATEGY quy tắc vàng)
- 2 tab: **Trends** (vòng đời pattern qua stream) + **Explain** (click pattern → xem DO, DUBO, Z(X), prune reason, cửa sổ ε)
- Mining chạy nền, loading screen + progress (NFR-FX)
- Module riêng, không đụng logic engine

---

### G7 — Dữ liệu mở rộng (Tuỳ chọn)
- Reader `Utility-FIMI` (D42) → module riêng `dhopm-baseline` hoặc mở rộng HUIM

---

## 3. TIÊU CHÍ KỸ THUẬT CHUẨN (ACCEPTANCE CRITERIA)

### 3.1 Mức bắt buộc (Mọi giai đoạn)
- [ ] **TC1–TC18 xanh** trên engine tương ứng
- [ ] **INV-I**: `ε = 0` ⇒ V2/V3/V4 bit-for-bit ≡ V1
- [ ] **INV-G**: `|DO_win − DO_full| ≤ ε` kiểm chứng trên dataset thật
- [ ] **Determinism**: Cùng input/tham số → cùng output bit-for-bit với workers {1,2,4,CPU}
- [ ] **Median ≥ 3 lần** + ghi rõ: phần cứng, JDK, số worker, OS
- [ ] Mọi quyết định mới có `D<n>` trong `DECISIONS.md`
- [ ] Không tài liệu mâu thuẫn (cao hơn trong thang thắng)

### 3.2 Mức cửa sổ ε (G2+)
- [ ] Evict **O(1)** đo được (không phụ thuộc số node)
- [ ] Peak memory **gần như phẳng** khi stream dài ≥ 10× N (kosarak 200K→990K)
- [ ] `window`/`validate` trả số liệu đúng khớp `WindowMathTest`
- [ ] `mushroom ∂=6%` **không còn chạy 300s**: hoặc cảnh báo "∂ vượt miền khả thi", hoặc ra kết quả trong vài giây
- [ ] Bảng ablation V1→V4 có cột: evict count, memory, runtime, độ lệch ≤ ε

### 3.3 Mức Manager CLI/API (G5)
- [ ] Cùng request qua `--json` và CLI text → **cùng kết quả**
- [ ] Request nhiều version → `perVersion` đúng, không crash, kết quả trùng từng version chạy riêng
- [ ] `window` trả `W` khớp `WindowMathTest` (bảng tra cứu Plan 00 §2.3.1)
- [ ] `validate` trả mã lỗi ổn định + thông điệp có số liệu (`INFEASIBLE_PARTIAL`, `EPSILON_TOO_LARGE`, `WINDOW_TOO_LARGE`, `PARTIAL_ZERO`)
- [ ] Stream lớn (kosarak, `--limit`) có progress event, không đóng băng
- [ ] Golden TC1–TC18 vẫn xanh sau khi thêm Manager
- [ ] Frontend hiển thị đúng chỉ qua `BackendClient` (không import backend class)

### 3.4 Mức V3 (G3)
- [ ] ≡ V2 (kể cả `ε > 0`) double đầy đủ
- [ ] **Không còn đối tượng Handle** (INV-H tự thoả)
- [ ] Nhanh hơn V2 ở ≥ 3/4 dataset (hoặc phân tích rõ tại sao không)
- [ ] Report rõ từng quyết định tối ưu kèm số liệu trước/sau (microbenchmark)

---

## 4. PHÂN BỔ CÔNG VIỆC VÀ TRÁCH NHIỆM

| Vai trò | Nhiệm vụ chính | Giai đoạn |
|---------|----------------|-----------|
| **Sơn (Backend)** | V2 implementation (toàn bộ G2-M1→M7), V3/V4 sau | G2 → G3 → G4 |
| **Sơn (Backend)** | `dhopm-cli` (G5-MA0→MA3), Contract protocol | Song song G3/G4 |
| **Tâm (Frontend)** | `BackendClient` theo Contract, UI Visualizer (G6) | MA3 → G6 |
| **Cả hai** | Review Protocol, test vector, benchmark params | MA0, G2-M7, G3-M1 |

---

## 5. RỦI RO VÀ GIẢM THIỂU (BỔ SUNG TỪ BÁO CÁO)

| Rủi ro | Dấu hiệu sớm | Giảm thiểu |
|--------|--------------|------------|
| **CLI mâu thuẫn không giải quyết** → frontend/backend không khớp | Tâm vẫn dùng `TsonV1Bridge`, Sơn code Manager theo design khác | **Chốt C1 trước G2**. Commit統一 CLI design. |
| **V2 handle 2 tầng sai → đọc tham chiếu chết / lỗi âm thầm** | Crash evict hoặc test `≥2W` phát hiện `tid` lệch | INV-H, TC13, **không dùng `try/catch`**. Code review tập trung M2. |
| **Validator dùng nhầm `maxPartialAsymptotic` chặn oan config hợp lệ** | Test `TL < W` báo lỗi nhưng chạy thật ra pattern | **D38/G2-D14**: Validator **bắt buộc** dùng `maxPartialExact = Z(f,TL)/N_eff`. Test case bắt buộc. |
| **Benchmark ∂ vẫn dùng giá trị paper → 0 pattern, hiểu sai hiệu năng** | Chạy benchmark ra 0 pattern, time lớn | **Chốt C6 trước G2-M7**. Dùng miền khả thi Plan 00 §6.4. |
| **V3 tối ưu mù → code phức tạp mà không nhanh hơn** | Chạy benchmark V3 ≤ V2 | **C9**: Microbenchmark từng điểm, tiêu chí ≥1.2x hoặc memory ↓20%. Bỏ điểm không đạt. |
| **Scope creep Manager (ResourceManager, Recovery đầy đủ, TCP daemon phức tạp)** | MA1/MA2 kéo dài > 2 tuần | **C2, C5**: Đã bác bỏ ResourceManager native, recovery chỉ mức nhẹ. Giữ MA1 nhỏ. |
| **Frontend không bỏ bridge trực tiếp** | Tâm vẫn import jar backend | MA3 có **test chuẩn**: client chỉ dùng Contract. Ghép bàn bạc rõ với Tâm. |
| **Sunk cost vào V4-Extreme** | Tốn >2 tuần G3 mà chưa có số liệu | Đặt "deadline giả" G2; trượt thì V4 về phụ lục (STRATEGY §10). |

---

## 6. CHECKLIST KHỞI ĐỘNG G2 (TUẦN TUẦN)

### Tuần 1: G2-M1 + M2 (Nền tảng + Handle/Evict)
- [ ] Tạo module `dhopm-v2-epsilon` + dependency `dhopm-common`
- [ ] `WindowMath` + `ParameterValidator` + `WindowInfo` + `WindowAwareEngine` trong `dhopm-common`
- [ ] `WindowMathTest`: bảng W, bảng ∂, **case TL<W** (D38)
- [ ] Đổi `epsilon` → `epsilonCmp`, thêm `epsilon` default `1e-6` trong `MiningConfig`
- [ ] `Handle`, `WindowBuffer` circular, `evict(tid)` O(1)
- [ ] `Entry(ref1,ref2,tid)` + `isLive()` check `tid`
- [ ] Test nạp `2W` tx: không đọc nhầm transaction mới

### Tuần 2: G2-M3 + M4 (minSup 2 pha + Reconstruction + Bound + Mining)
- [ ] `DHONode.head/size`, `support() = size - head`
- [ ] `DHOListBuilder` bỏ qua tx ngoài cửa sổ
- [ ] `minSup = ∂ × min(TL, W)` tại `mineNow`
- [ ] `Reconstructor` dời `head`, DO chỉ entry sống, Level 1
- [ ] `ZCalculator`, `DUBOCalculator` trả `UB'`
- [ ] Short-circuit `minSup > Z + ε_cmp`
- [ ] `Miner` support sống + prune `UB'`

### Tuần 3: G2-M5 + M6 + M7 (Nghiệm thu + CLI + Đo lường)
- [ ] TC1–TC8 (ε=0) ≡ V1 bit-for-bit
- [ ] TC9–TC18 + E1–E10 xanh
- [ ] Determinism workers {1,2,4,CPU}
- [ ] CLI `window`/`validate`/`sweep` + `windowInfo` output
- [ ] Instrumentation evict/live/dead/prune-hit
- [ ] Ablation V1↔V2 ≥4 dataset, median ≥3
- [ ] Scalability kosarak 200K→990K: memory phẳng
- [ ] Báo cáo V2 đầy đủ

---

## 7. KẾT LUẬN

**Thứ tự ưu tiên tuyệt đối:**
1. **C1, C2, C7** — Dọn dẹp tài liệu/CLI mâu thuẫn (1–2 ngày, **làm ngay**)
2. **G2 (V2 Epsilon/Window)** — Toàn lực 2–3 tuần, tạo ra giá trị khoa học cốt lõi
3. **G5-MA0, MA1** — Song song G3, chốt Protocol + Manager tối thiểu cho Frontend
4. **G3 (V3 Optimized)** — Profile thật, microbenchmark, chỉ giữ tối ưu đo được lợi
5. **G6 (App)** — Chỉ bắt đầu khi G2 có bảng số

**Quy tắc vàng:** *Một mốc chỉ xong khi nó tạo ra một câu hoặc một con số viết được ra báo cáo — không xong vì "code chạy".*

---