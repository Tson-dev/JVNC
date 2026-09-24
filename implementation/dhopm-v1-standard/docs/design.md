# Thiết kế V1 Standard (implementation/dhopm-v1-standard)

Tuân theo tài liệu kế hoạch `docs/plans/00-OVERALL-PLAN.md` (đặc tả chuẩn C1–C6) và
`docs/phases/P1-G1.md`. Tài liệu này ghi lại các quyết định lập trình (không lặp lại
đặc tả thuật toán).

## Đường ống (GĐ1 → GĐ2 → GĐ3)

| Giai đoạn | Lớp | Xử lý theo | Ghi chú |
|---|---|---|---|
| GĐ1 Construction | `DHOListBuilder` | 1 luồng | nối entry theo TID tăng (INV-B); TID tăng nghiêm ngặt (INV-A, fail-fast trong `MiningEngine.loadBatch`) |
| GĐ2 Reconstruction | `Reconstructor` | song song theo nút | DO(0) → tích lũy tuần tự trên 1 nút (C5) → `List.sort` ổn định theo support (C3) |
| GĐ3 Mining | `Miner` + `ConditionalListBuilder` | song song theo root | C2 bỏ nút, C4 cộng DOP với ε, DUBO cắt tỉa, giao 2 con trỏ (kết quả TID-tăng, INV-D) |

## Ánh xạ GoF

- **Strategy**: `MetricCalculator`, `DuboCalculator` tách công thức — V2 thay đổi
  cách tính DO/DUBO mà không đụng pipeline.
- **Builder**: `DHOListBuilder`/`ConditionalListBuilder` — xây DS nút.
- **Template Method**: ngầm trong `MiningEngine` (pipeline cố định, khác nhau ở các
  khối công thức) — tách riêng `MiningEngine`/`Miner` để kế thừa-trên-khung dễ dàng.
- **Facade**: `MiningEngine` ẩn toàn bộ pipeline sau giao diện kiến trúc
  `Engine`/`PhaseAwareEngine`/`ProgressAwareEngine` (common).
- **Decorator**: `TimedEngine` (common) đo thời gian mà không sửa engine.
- **Observer**: `PhaseListener` (common) nhận sự kiện đầu/cuối pha; `TimingRecorder`
  (common) tích lũy ms/heap; `MiningProgressListener` + `MiningProgress` (common) nhận
  tick tiến trình mining. Engine chỉ gọi khi có listener (chi phí 0 khi không dùng).

## API mở cho công cụ (plan 00 mục 4.2 — CLI / API / gọi trực tiếp)

Mọi công cụ (CLI V1, `dhopm-bench`, debug app G4) nói chuyện với engine **chỉ qua API
ổn định trong `dhopm-common`** và getter chỉ đọc của `MiningEngine` — không lộ cấu trúc
nội bộ (quyết định tổng thể D6).

| Giao diện (common) | Mục đích |
|---|---|
| `Engine` | `loadBatch` / `mineNow` — chạy đủ pipeline |
| `PhaseAwareEngine` + `PhaseListener` | đo 3 pha (CONSTRUCTION / RECONSTRUCTION / MINING), nuôi `TimingRecorder` |
| `ProgressAwareEngine` + `MiningProgressListener` + `MiningProgress` | tick tiến trình mining (root-task xong, patterns, ms) — dùng cho CLI `stream`, G4 loading screen |
| `TimedEngine` | decorator đo thô 2 op (nếu muốn không chạm engine) |
| `WorkerPool.invokeAll(tasks, onCompleted)` | callback tiến trình theo thứ tự task — gộp-sau-join giữ INV-E |

Getter chỉ đọc của `MiningEngine`: `globalNodeCount()`, `globalEntryCount()`,
`totalLoaded()`, `lastTid()`, `lastMiningTasks()`.

Bật listener KHÔNG đổi kết quả (INV-E) — chỉ thêm gọi callback ngoài luồng hot path
(giao `partial` từ task, `config` từ đối số) và đo `System.nanoTime` thô ngay ngoài công việc.

## Quyết định chính (ánh xạ G1-D*)

- **G1-D1 Item = `String`** — `Transaction.items` là `String[]`; canonicalKey = items
  sắp tự nhiên, nối `,`. V1 ưu tiên rõ ràng (\(f\)/∂ dạng double, debug in được tên item).
- **G1-D2 DHO-List = `LinkedHashMap<String, DHONode>`** — vòng lặp theo thứ tự tạo nút
  (INV-B/C3 cần "thứ tự xuất hiện đầu tiên", không cần index theo thứ tự từ điển).
- **G1-D3 pool = `config.workers`** — `WorkerPool` (common) được `MiningEngine` sở hữu
  và đóng trong `close()` (AutoCloseable).
- **G1-D4 gộp kết quả sau join** — mining 1 task/root (Callable) qua
  `WorkerPool.invokeAll`, gộp theo thứ tự task → deterministic dù pool ≥ 1 luồng (INV-E).
- **G1-D5 `TimedEngine` ở dhopm-common** — timing không phụ thuộc thuật toán.
- **G1-D6 snapshot `docs/golden-doubles-v1.json`** — do `GoldenDoublesSnapshotTest`
  sinh ra (độ chính xác máy: `Double.toString` round-trip), làm tham chiếu cho V2/V3.
- **G1-D7 CLI đa lệnh + SPI tiến trình** — `cli` class `Main` + `CliSupport`
  (bộ lệnh `mine/detail/stream/golden/inspect`); `MiningEngine` implement
  `ProgressAwareEngine` (đổ `MiningProgress` theo root task qua `WorkerPool.invokeAll(tasks, callback)`).
  Cú pháp cũ `--dataset …` = lệnh `mine` (tương thích). Chi tiết bảng lệnh ở README.

## Bất biến lập trình

- **INV-A** TID tăng nghiêm ngặt toàn stream; vi phạm → `IllegalArgumentException` ở `loadBatch`.
- **INV-B** entry của mỗi nút luôn nối theo TID tăng.
- **INV-C** `|T|` giữ theo từng entry (sau khi Transaction đã loại trùng + sắp xếp item).
- **INV-D** không sắp xếp lại list điều kiện; kết quả giao giữ nguyên thứ tự TID.
- **INV-E** kết quả độc lập với số luồng: mỗi nút tích lũy DO trên 1 luồng (C5),
  gộp sau join theo thứ tự task.
- **INV-F** `minSup = ∂ × tổng giao dịch` tính ở thời điểm `mineNow` (khi load tăng dần).

## Cải tiến hiệu năng trung thực (không đổi kết quả)

- Trong `Miner.process`: partner có `support < minSup` bị bỏ trước khi giao
  (`|Xi ∩ Xj| ≤ support_j < minSup` → C2 sẽ loại), tránh O(n²) trên đuôi hiếm.

## Điểm cần chú ý khi đọc code

- `Reconstructor` snapshot list theo thứ tự tạo rồi mới sort → stable sort theo support.
- Giao hai nút dùng two-pointer trên entry TID-tăng (INV-B), không cần Set/Map trung gian.
- DUBO group theo |T| tăng (TreeMap), group k = {count, lastTid}, áp 1 hệ số suy giảm
  cho cả group (C1) — xem `DuboCalculator`.