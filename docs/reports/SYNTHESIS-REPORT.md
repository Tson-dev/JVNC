# BÁO CÁO TỔNG HỢP: PHÂN TÍCH TÀI LIỆU VÀ KẾ HOẠCH DHOPM

> **Mục đích:** Tìm và chỉ rõ những chỗ **rõ ràng**, **cần/chưa làm rõ**, và **thiếu/lỗ hỏng kỹ thuật** trong toàn bộ tài liệu dự án.
> **Phạm vi:** 1 Plan Overview (00), 4 Plan Version (01–04), 1 CLI/API Plan (05), 1 Draft Analysis (06), SRS, DECISIONS, STRATEGY, Reports, Phases, và tài liệu module V1.

---

## 1. NHỮNG CHỖ RÕ RÀNG (ĐÃ CHỐT, CÓ THỂ TRIỂN KHAI)

### 1.1 Canonical Spec — Nền móng thuật toán (Plan 00 §2, DECISIONS D9–D23, D38–D41)
| Yếu tố | Trạng thái | Ghi chú |
|--------|------------|---------|
| Tham số đầu vào (`∂`, `f`, `minOcc`, `ε`, `W`, `N_eff`) | ✅ **Chốt** | Miền giá trị, mặc định, quy tắc biên rõ ràng (C7–C12) |
| Pipeline 4 giai đoạn (GĐ0–GĐ3) | ✅ **Chốt** | GĐ0 mới cho V2+, V1 bỏ qua (W=∞) |
| Công thức cửa sổ `W = ⌈ln(minOcc(1−f))/ln f⌉` | ✅ **Chốt** | Có bảng tra cứu, xử lý trường hợp biên (minOcc=0, f=1, minOcc≥1/(1−f)) |
| `minSup` 2 giai đoạn (`∂ × min(TL, W)`) | ✅ **Chốt** | Pha 1: `∂×TL`, Pha 2: `∂×W` đóng băng |
| Trần DO `Z(f,TL)` + short-circuit | ✅ **Chốt** | `minSup > Z + ε` ⇒ ∅ tức thì O(1) |
| Bound chặt hơn `UB'(X) = min(DUBO, Z(X))` | ✅ **Chốt** | Tính cùng vòng duyệt entry, chỉ prune thêm |
| Handle 2 tầng `ref1 → ref2` + `Entry.tid` chống slot tái dùng | ✅ **Chốt** | Evict O(1), phát hiện chết ẩn trong pha duyệt, compaction bằng `head` |
| Bất biến toàn cục INV-A–J | ✅ **Chốt** | INV-I (minOcc=0 ≡ V1), INV-G (|DO_win−DO_full|≤minOcc) là then chốt |
| Quy tắc `minOcc` 2 tầng (thư viện có default, giao thức bắt buộc khai báo) | ✅ **Chốt** | D41: tránh im lặng thay đổi tập DOP |

### 1.2 Kiến trúc 4 phiên bản (Plan 00 §1.1, §8, DECISIONS D13)
| Phiên bản | Module | Định vị | Threading | Trạng thái |
|-----------|--------|---------|-----------|------------|
| V1 Standard | `dhopm-v1-standard` | Oracle (minOcc=0), GoF | Level 1 | ✅ **Hoàn thành, đóng băng** |
| V2 MinOcc | `dhopm-v2-minocc` | Ngữ nghĩa cửa sổ minOcc | Level 1 | 🕔 **Chưa bắt đầu (G2)** |
| V3 Optimized | `dhopm-v3-optimized` | Tối ưu cấu trúc dữ liệu | Level 2 | 🕓 Sau G2 |
| V4 Extreme | `dhopm-v4-extreme` | Trần hiệu năng | Level 3 | 🕓 Sau G3 |

### 1.3 Module `dhopm-common` — Hạ tầng dùng chung (Plan 00 §4, V1 design.md)
- Reader FIMI/Text/ZIP, `MiningConfig`, Contract (`Engine`, `PhaseAwareEngine`, `ProgressAwareEngine`, `TimedEngine`, `WindowAwareEngine` mới)
- `WorkerPool` (invokeAll deterministic), `TimingRecorder`, `Log`, TestKit (GoldenRunner, DeterminismAssert)
- **WindowMath**, **ParameterValidator**, **WindowInfo** — định nghĩa dùng chung cho cả 4 version (D22)

### 1.4 Bộ test chuẩn (Plan 00 §6, V1 test-report.md)
- **TC1–TC8** (minOcc=0): Golden reference, bắt buộc pass trên **mọi** phiên bản (INV-I)
- **TC9–TC18** (minOcc>0): Ngữ nghĩa cửa sổ, chỉ V2+
- **E1–E10**: Trường hợp biên tham số (bắt buộc)
- **Double đầy đủ** so sánh chéo version (bit-for-bit khi minOcc=0)

### 1.5 Giai đoạn G1 — V1 Standard (P1-G1.md, G1 Report)
- ✅ 56 test xanh, deterministic bit-for-bit workers {1,2,4,CPU}
- ✅ Level 1 threading thực sự (Reconstruction theo node, Mining theo cây con gốc)
- ✅ CLI 5 lệnh chuẩn (`mine/detail/stream/golden/inspect`) + `--limit` + SPI tiến trình
- ✅ Benchmark sơ bộ 2 dataset (retail 87s, mushroom 301s → 0 pattern — **phát hiện then chốt**)
- ✅ Tài liệu module đầy đủ (README, design, test-report, benchmark, golden-doubles-v1.json)

---

## 2. NHỮNG CHỖ CẦN/CHƯA LÀM RÕ (MÂU THUẬN, THIẾU CHI TIẾT, CHƯA CHỐT)

### 2.1 Mâu thuẫn thiết kế CLI — **QUAN TRỌNG NHẤT**
| Nguồn | Thiết kế | Trạng thái |
|-------|----------|------------|
| `docs/Document/CLI/Commend/Mine Commend.md` | **Positional args**: `mine <dataset> <mode> <partial> <factor> <limit> --y <export>`<br>Mode: `detail/stream/step/log` | Draft cũ, **rỗng/không hoàn chỉnh** |
| `docs/Document/CLI/Config/Config.md` | `config get/set/list/cancel` — 3 cấp: `default, config, ___` (cấp 3 **bỏ dở**) | Draft cũ |
| `Plan 01` + **V1 implementation** | **Flag-based subcommands**: `Main mine --dataset --partial --f --parts`<br>`Main detail/stream/golden/inspect` | **Đã triển khai, chạy được** |
| `Plan 05` (Manager CLI/API) | `--json` one-shot + `serve` daemon (JSONL stdio/TCP)<br>Protocol JSON schema (mục 7) | Plan, **chưa code** |

**→ Cần chọn 1 thiết kế CLI duy nhất và cập nhật toàn bộ tài liệu.**  
Khuyến nghị: Dùng thiết kế **V1 implementation** (flag-based, subcommand) làm chuẩn — đã code, test, khớp D6/D27.

### 2.2 Giao thức Manager CLI/API (Plan 05 §7, §11, §12) — **CHƯA CHỐT**
| Vấn đề | Trạng thái | Cần quyết định |
|--------|------------|----------------|
| Transport: JSONL **stdio** vs **TCP local port** | Plan 05 nghiêng stdio (OP-1), Draft muốn TCP | Chốt OP-1/OP-2 (§6.1 Plan 05) |
| Chế độ: **One-shot** (`--json`) vs **Daemon** (`serve`) | Plan 05 muốn **cả 2** (OP-2) | Xác nhận MA1 làm cả 2 |
| Engine chạy **in-process** vs **subprocess** | Plan 05 OP-3: in-process trước | Xác nhận |
| Schema `window`/`validate` trả `maxPartialAsymptotic` khi không có `TL` | Plan 05 §7 có quy tắc, nhưng **chưa có test case** | Bổ sung test |
| Mã lỗi ổn định cho validator (`INFEASIBLE_PARTIAL`, `MIN_OCC_TOO_LARGE`...) | Plan 05 §10, P2-G2 §5 M6 | Triển khai đồng bộ |
| Frontend (`javanc`) có chạy máy khác không? | D34 **chưa chốt** | Hỏi Tâm — quyết định stdio vs TCP |

### 2.3 Hệ thống Config (Draft CLI-A/C, Plan 05 G-1) — **CHƯA CÓ**
- Draft nhắc 3 cấp: `default`, `config`, **___ (bỏ dở)** — gợi ý: `CLI args` (args > config > default)
- V1 hiện tại: **chỉ inline args**, không có file config
- Plan 05 MA1 yêu cầu `ConfigStore` + `config get/set` + validate theo canonical
- **Thiếu:** Định dạng file (YAML/JSON/Properties?), vị trí lưu, precedence, reload runtime

### 2.4 Resource Manager (Plan 00 §4.3 F4, Plan 05 §9, DECISIONS D8, §6)
| Tài liệu | Nội dung | Mâu thuẫn |
|----------|----------|-----------|
| Plan 00 §4.3 F4 | `ResourceManager → native API → WindowsModule/MacModule` (plan riêng D8) |  |
| Plan 05 §9 | **ĐÃ BÁC BỎ** — dùng `workers` + `limit` + `parts` trong Manager | ⚠️ **Mâu thuẫn trực tiếp** |
| DECISIONS D8 | **Chốt bác bỏ** — Java không có API quota CPU portable |  |
| Plan 05 §11 MA4 | Vẫn ghi "Plan tài nguyên riêng (ResourceManager→native→Win/Mac)" | Cần **xoá hoặc ghi rõ đã bác bỏ** |

**→ Cần thống nhất: ResourceManager native đã bị bác bỏ (D8), chỉ giữ cấu hình đơn giản.**

### 2.5 Recovery / JobLedger (Draft SYS-A, Plan 05 MA2 G-5, 06 §4.3)
- Draft: recovery đầy đủ (job ledger, replay)
- Plan 06 §4.3: **Recovery mức nhẹ (MA2)** — chỉ ledger + lastTid + replay file (rẻ, đủ)
- Plan 05 MA2: có `JobLedger` + session persistence mức nhẹ
- DECISIONS §6: **Bác bỏ recovery đầy đủ** — sau cửa sổ minOcc, 1 lần `mine` vài giây
- **→ Cần làm rõ: có làm recovery mức nhẹ ở MA2 không? Nếu có, scope gì?**

### 2.6 Benchmark tham số chính thức (G1 Report §5, Plan 00 §7, STRATEGY §3.2)
- G1 phát hiện: `∂` của paper **không dùng được** trên thang DO (minSup ≫ Z ⇒ 0 pattern)
- Plan 00 §6.4 có bảng miền `∂` khả thi, nhưng **chưa chốt giá trị benchmark chính thức**
- STRATEGY §3.2: cần kiểm chứng giả thuyết (paper dùng occupancy không damping?)
- **→ Cần chốt: bộ `∂` benchmark cho từng dataset, median ≥3 lần, ghi phần cứng/JDK**

### 2.7 Tối ưu V3 — Quyết định từng điểm (Plan 03 §4 bảng so sánh)
- 14 điểm tối ưu (Item=int, Handle→int slot, SoA WindowBuffer, Entry SoA, decay lookup...)
- **Mỗi điểm cần microbenchmark so V2 trước khi chốt** — Plan 03 §4.0 yêu cầu ghi rationale + số liệu
- **Thiếu:** Tiêu chí "tốt hơn" định lượng (speedup ≥ x%, memory giảm ≥ y%)

### 2.8 Tài liệu rỗng/cũ/cần dọn (STRATEGY Phụ lục C, DECISIONS D35)
| File | Vấn đề |
|------|--------|
| `Overview.md` | Rỗng (chỉ có header) |
| `spec/Spec.md` | Cắt dở |
| `Document/Compoment/{Factor,Limit,Mode,Parial}.md` | 4 file rỗng, typo `Compoment` |
| `Document/CLI/Config/List Config.md` | Rỗng |
| `Document/CLI/Commend/Mine Commend.md` | Typo: `Commend`→`Command`, `dateset`→`dataset`, `parial`→`partial` |

---

## 3. LỖ HỒNG KỸ THUẬT (THIẾU, CHƯA TRIỂN KHAI, RỦI RO CAO)

### 3.1 Toàn bộ V2 MinOcc/Window — **CHƯA CÓ CODE** (G2)
| Thành phần | Plan tham chiếu | Trạng thái |
|------------|----------------|------------|
| `WindowMath.computeWindow`, `maxPartialExact/Asymptotic` | P2-G2 M1, Plan 02 §5.1 | ❌ Chưa có |
| `ParameterValidator` (miền ∂, f, minOcc, mã lỗi) | P2-G2 M1, Plan 02 §5.1 | ❌ Chưa có |
| `WindowInfo`, `WindowAwareEngine`, `WindowListener` | P2-G2 M1, Plan 02 §5 | ❌ Chưa có |
| `Handle`, `WindowBuffer` (circular, evict O(1)) | P2-G2 M2, Plan 02 §5.2 | ❌ Chưa có |
| `Entry(ref1, ref2, tid)` + `isLive()` check `tid` | P2-G2 M2, Plan 02 §5.2.1 (D39) | ❌ Chưa có |
| `DHONode.head/size`, `support() = size - head` | P2-G2 M2 | ❌ Chưa có |
| `minSup = ∂ × min(TL, W)` tính tại `mineNow` | P2-G2 M3, Plan 02 §5.3 | ❌ Chưa có |
| `Reconstructor` dời `head`, DO chỉ entry sống | P2-G2 M3 | ❌ Chưa có |
| `ZCalculator` (`Z(f,TL)`, `Z(X)`), `DUBOCalculator` trả `UB'` | P2-G2 M4 | ❌ Chưa có |
| Short-circuit toàn cục `minSup > Z + ε` | P2-G2 M4, Plan 02 §5.4 | ❌ Chưa có |
| `Miner` dùng support sống, prune `UB'` | P2-G2 M4 | ❌ Chưa có |
| TC9–TC18 + E1–E10 | P2-G2 M5, Plan 02 §6 | ❌ Chưa có |
| CLI `window`/`validate`/`sweep` | P2-G2 M6, Plan 02 §7 | ❌ Chưa có |
| Instrumentation (evict count, live/dead, prune-hit) | P2-G2 M7 | ❌ Chưa có |
| Báo cáo ablation V1↔V2, scalability kosarak | P2-G2 M7 | ❌ Chưa có |

### 3.2 Manager CLI/API (`dhopm-cli`) — **CHƯA TẠO MODULE** (Plan 05 MA1–MA3)
| Thành phần | Plan 05 §10, §11 | Trạng thái |
|------------|-------------------|------------|
| Module `dhopm-cli` entry point | MA1 | ❌ Chưa có |
| `--json` one-shot (map lệnh chuẩn) | MA1 | ❌ Chưa có |
| `serve` daemon (JSONL stdio/TCP) | MA1–MA2 | ❌ Chưa có |
| `VersionRegistry` (đăng ký engine cứng/SPI) | MA1 | ❌ Chưa có |
| `JobManager` (tách request → sub-jobs theo version) | MA2 | ❌ Chưa có |
| `ResultAggregator` (chuẩn hóa schema, gộp perVersion) | MA2 | ❌ Chưa có |
| `SessionManager` (trạng thái DHO-List, lock session) | MA2 | ❌ Chưa có |
| `ParameterEcho` (trả `windowInfo`, cảnh báo validator) | MA1 | ❌ Chưa có |
| Serializer JSON (tự viết hoặc thư viện) | MA1 | ❌ Chưa có |
| **Đặc tả giao thức = Contract cho frontend** | MA3 (cần hoàn thiện §7) | ❌ Chưa hoàn thiện |

### 3.3 Tích hợp Frontend (`javanc`) — **CHƯA BẮT ĐẦU**
- Frontend hiện tại dùng `TsonV1Bridge` gọi **trực tiếp** `MiningEngine` (import jar backend) — **vi phạm F1, F2**
- Cần thay bằng `BackendClient` gọi Manager CLI/API theo protocol
- Plan 05 MA3: **Tâm làm client, Sơn cấp đặc tả + test chuẩn**
- **Rủi ro:** Frontend không bỏ bridge trực tiếp (Plan 05 §12 rủi ro 1)

### 3.4 CLI V1 hiện tại **KHÔNG CÓ** các lệnh V2
- V1 CLI: `mine/detail/stream/golden/inspect` (5 lệnh)
- **Thiếu:** `window`, `validate`, `sweep` (Plan 00 §4.2 P5, Plan 02 §7)
- **Thiếu:** `--json` output cho mọi lệnh
- **Thiếu:** `windowInfo` trong output `mine`/`detail`

### 3.5 Benchmark harness (`dhopm-bench`) — **CHƯA ĐẦY ĐỦ**
- Hiện tại: incremental 5 phần, CSV cơ bản (construction/reconstruction/mining/total, patterns, heap)
- **Thiếu:** Ablation V1↔V2↔V3↔V4 (cùng tham số, ≥3 lần, median)
- **Thiếu:** Scalability test tự động (kosarak 200K→990K, chainstore)
- **Thiếu:** Độ lệch `|DO_win − DO_full|` đo trên dataset thật (INV-G)
- **Thiếu:** Output JSON cho so sánh chéo

### 3.6 V3 Optimized — **CHƯA CÓ QUYẾT ĐỊNH MICROBENCHMARK** (Plan 03 §4, §6 M2)
- 14 điểm tối ưu cần đo so V2 (bảng Plan 03 §4)
- **Chưa có:** Test harness microbenchmark, tiêu chí chấp nhận định lượng
- **Rủi ro:** Tối ưu mù (Plan 03 §8 rủi ro 1, 4)

### 3.7 Đồng bộ tài liệu — **NHIỀU NỖI MÂU THUẬN**
| Vấn đề | Ví dụ |
|--------|-------|
| Plan 00 §4.3 F4 vs Plan 05 §9 vs DECISIONS D8 | ResourceManager: có/không/bác bỏ |
| Plan 05 §11 MA4 vs DECISIONS §6 | ResourceManager vẫn ghi trong lộ trình MA4 |
| Plan 00 §4.1 bảng tài liệu vs thực tế | `spec/Spec.md` rỗng, `Document/Compoment/*.md` rỗng |
| Draft CLI-A/C 3 cấp config vs thực tế | Cấp 3 bỏ dở, V1 không có config file |
| Draft FLOW-A TCP vs Plan 05 OP-1 stdio | Mâu thuẫn transport |

---

## 4. TỔNG KẾT PHÂN LOẠI

| Loại | Số lượng | Mức độ ưu tiên |
|------|----------|----------------|
| **Rõ ràng, đã chốt** | 15+ mục | Cơ sở triển khai |
| **Cần làm rõ/chốt** | 8 nhóm lớn | **Chặn G2/G5** |
| **Lỗ hỏng kỹ thuật (chưa code)** | 30+ mục | **G2 = ưu tiên #1**, G5 song song |

**Kết luận:** Dự án **rất vững ở tầng thuật toán** (canonical, test, V1 oracle) nhưng **yếu ở tầng giao tiếp & vận hành** (CLI mâu thuẫn, Manager chưa có, Config chưa có, Protocol chưa chốt, Benchmark chưa đủ). V2 là mốc then chốt — nó biến "chạy 300s ra rỗng" thành "cảnh báo tức thì + cửa sổ O(1)".

---