# Kế hoạch triển khai RSS bằng compute worker riêng

## Mục tiêu

Đo bộ nhớ của thuật toán độc lập với UI và việc giữ log; giải thích được RSS tăng theo các pha và qua nhiều lần Construction/Reconstruction/Mining trong cùng một phiên.

## Trạng thái thực thi

- V1 đã chuyển Construction/Reconstruction/Mining sang một JVM worker tồn tại qua các pha. Parent lấy working set của PID worker trên Windows bằng JNA, còn worker ghi heap used đầu/cuối, heap peak, CPU, GC count delta và PID.
- V2 và V3 cũng đã chuyển sang worker riêng, giữ nguyên các API/semantics riêng của engine. Mỗi project vẫn độc lập và chứa implementation tương ứng.
- V1/V2/V3 đều có integration test xác nhận PID riêng, giữ state qua pha, queue transaction, reset epoch và shutdown worker. Test Windows xác nhận RSS status `available` và lấy được mẫu start/end/peak.
- Log viewer đọc được metric mới và cũ; có bảng/charts RSS start, end, sampled peak, cùng metadata nguồn/trạng thái/số mẫu/interval trong chi tiết pha.
- Maven test từng project đã pass: V1 6, V2 10, V3 10, log viewer 7 tests. Chạy riêng từng project; chưa chạy một build gộp vì các project được thiết kế standalone.
- Chu kỳ sampling 20 ms mới được xác nhận hoạt động trong test trên Windows; overhead so với sampler-off và khả năng bắt spike rất ngắn chưa được benchmark. Chưa có kết luận RAM/leak và chưa chạy JFR/JMC baseline.

## Kiến trúc đề xuất

### Compute worker

- Mỗi engine (`v1-original`, `v2-minDO`, `v3-window`) tự có worker launcher/protocol/process implementation trong project của nó. Không tạo project dùng chung và không import class giữa các engine.
- Khi UI mở, launcher khởi động **một JVM worker riêng, tồn tại suốt phiên ứng dụng**. Worker giữ `DhopmEngine` và toàn bộ state giữa Construction, Reconstruction, Mining; không khởi động JVM mới cho mỗi pha, vì việc đó sẽ bỏ qua warm-up, tái sử dụng dữ liệu và biến động RAM mà chúng ta cần quan sát.
- UI/host process giữ Swing controls, xác nhận log cũ, TextArea, log RAM và thao tác export/reset. Dữ liệu transaction chờ được gửi sang worker khi Construction tiêu thụ queue; worker là nơi duy nhất giữ DHO-List/engine state.
- Worker thoát khi UI đóng bình thường; host phải phát hiện worker crash/timeout, báo lỗi rõ, không cho nút Mining hoạt động với state chưa xác nhận. Không được âm thầm tạo worker mới giữa phiên vì sẽ làm mất DHO-List và làm sai số đo.
- Mỗi engine build/ship worker riêng cùng ứng dụng đó. Cần xác nhận cách launch worker hoạt động cả khi chạy trong quá trình phát triển và từ artifact được build; classpath/dependency phải được đóng gói hoặc xác định rõ, không dựa vào classpath ngẫu nhiên của Maven.

### Giao thức parent/worker

Giao tiếp dùng JSON Lines trên stdin/stdout với request ID và một response cho mỗi request. Stdout chỉ dành cho protocol; diagnostic JVM output đi stderr. Tối thiểu:

- `CONSTRUCT`: dataset path/limit và transaction queue mới.
- `RECONSTRUCT`: `partial`, `factor`, `minDO`, `epsilon`.
- `MINE`: chạy Mining trên state Reconstruction hiện tại.
- `RESET_EPOCH`: kết thúc state hiện tại và khởi tạo data epoch mới.
- `SHUTDOWN`: dừng worker có kiểm soát.

Mỗi pha phát sự kiện `PHASE_STARTED`, sau khi thuật toán kết thúc phát `PHASE_COMPUTE_DONE` ngay trước khi serialize/trả snapshot và kết quả, rồi trả response `RESULT`. Như vậy host có thể dừng RSS sampling đúng tại ranh giới compute; IPC serialization, truyền snapshot, log formatting và export không bị tính vào compute-phase RSS/time. Wall time thuật toán do chính worker đo quanh lời gọi pha. Snapshot/log payload được chuyển sau dấu `PHASE_COMPUTE_DONE`; thời gian và kích thước chuyển/capture hiện chưa được đo riêng.

Protocol cần giới hạn kích thước frame hợp lý hoặc hỗ trợ đọc frame theo dòng không giới hạn nhỏ tùy tiện, đồng thời xử lý EOF, JSON không hợp lệ, request ID lệch, worker exit code khác 0, lỗi thuật toán và timeout. Không ghi log hay stack trace vào stdout. Dữ liệu lỗi phải trả trạng thái lỗi rõ và không tạo response thành công giả.

## Định nghĩa RSS và cách đo

- Trên Windows, RSS được đo từ **process compute worker** bằng process working set, đọc qua Windows `GetProcessMemoryInfo` (JNA/JNA-platform trong từng project engine). Ghi rõ `rssMetric = windows-working-set`; đây là RSS-like resident working set của Windows, không phải private bytes, và hệ điều hành có thể trim working set.
- Không dùng heap `Runtime.totalMemory() - freeMemory()` hay process-wide MXBean làm RSS. Chúng không đo resident memory của child process.
- Host giữ worker PID/ProcessHandle, lấy mẫu RSS trực tiếp trên PID worker. Lấy mẫu ngay khi pha bắt đầu, định kỳ mặc định mỗi 20 ms trong thời gian compute, và thêm một mẫu khi pha hoàn tất. Sampling chạy trong host, không dùng `tasklist`/PowerShell subprocess lặp lại trong hot path.
- Mỗi event ghi riêng `rssStartBytes`, `rssEndBytes`, `rssPeakSampledBytes`, `rssSampleCount`, `rssSamplingIntervalMs`, metric/source, process ID và trạng thái/chi tiết lỗi. Peak là **peak đã quan sát được**, không tuyên bố là peak tuyệt đối. Pha ngắn hơn chu kỳ có thể bỏ lỡ spike; số mẫu và chu kỳ phải hiện trong log/viewer.
- Không cộng RSS của UI/logging với RSS compute. Host RSS nếu được ghi phải mang tên riêng `hostRss...` và được mô tả là UI + in-memory logging, không phải RSS riêng của logger. Log payload bytes/event count cung cấp chỉ số trực tiếp cho lượng log; nếu sau này cần RSS thuần logging thì mới xem xét logger process riêng.
- Worker báo `heapUsedBytes` và `heapCommittedBytes` ở mốc bắt đầu/kết thúc pha; ghi `heapMaxBytes` theo JVM. Heap peak lấy từ peak usage của memory pool/JVM nếu khả dụng, reset high-water mark theo pha khi API hỗ trợ. Ghi rõ nguồn metric và không nhầm heap peak với RSS peak.
- CPU và heap worker được đọc từ trong worker, còn RSS lấy từ host theo PID. Compute wall time đo trong worker. Host ghi `workerStartupNanos`/startup RSS tách khỏi Construction; không cộng startup/JIT warm-up vào algorithm phase time.
- Nếu Windows API hoặc sampler lỗi, ghi metric unavailable cùng lỗi có thể chẩn đoán; không thay bằng 0 hay RSS host. Môi trường không phải Windows được ghi unsupported cho đến khi có backend đã kiểm chứng.

## Vì sao dùng một worker sống qua nhiều pha

Đây là điều kiện để đo hiện tượng người dùng quan sát: Mining đầu chậm, lần lặp nhanh, và memory/heap thay đổi khi cùng JVM tái sử dụng runtime. Worker mới cho mỗi pha sẽ đưa startup/JIT vào từng pha, mất state và không phản ánh ứng dụng đang chạy thật. Worker mới cho mỗi **phiên chạy** vẫn cần thiết để có baseline sạch giữa các lần thử.

Tách process làm cho compute RSS không còn bị trộn với TextArea, snapshot log RAM, Gson serialization và UI. Tuy nhiên worker vẫn giữ engine data giữa các pha; nếu working set/live heap tiếp tục tăng trong cùng phiên, đó là dữ liệu cần điều tra chứ không được che bằng restart tự động.

## Các giai đoạn triển khai

### 1. Prototype riêng, chưa chuyển engine

1. Tạo một probe nhỏ ngoài hot path để launch child JVM, lấy PID và gọi `GetProcessMemoryInfo` liên tục.
2. So sánh kết quả với Windows `Get-Process -Id <pid> | Select-Object WorkingSet64` và Performance Monitor cùng thời điểm; chênh lệch phải được giải thích theo thời điểm/định nghĩa counter.
3. Kiểm tra sampler trên worker idle, allocation tăng theo bước, process shutdown và worker crash. Xác nhận không lấy nhầm PID UI.
4. Chạy benchmark sampler-off/on, chọn chu kỳ mặc định sau khi thấy overhead; 20 ms là giá trị bắt đầu để đánh giá, không phải cam kết đã xác nhận.

### 2. V1 end-to-end

1. Chuyển `DhopmEngine` ownership vào persistent worker; giữ semantics/queue/TID và output algorithm.
2. Tích hợp protocol, lifecycle và parent-side RSS sampling.
3. Ghi event RSS theo pha, startup riêng, `hostRss` riêng tùy chọn và status đầy đủ.
4. Kiểm tra UI không treo; một pha hoàn tất mới cho phép log worker/host ghi record và mới gửi pha kế tiếp.
5. Test process PID tách biệt, state tồn tại qua pha, reset epoch, shutdown, crash, timeout, result/snapshot transfer và RSS sampler error.

### 3. Sao chép thiết kế độc lập sang V2/V3

- Tự thực hiện cùng worker protocol/metric code trong từng project (có thể chép implementation theo yêu cầu độc lập, không tạo shared module).
- V2 xác nhận worker giữ transactions, active TIDs và reconstructed nodes qua Reconstruction/Mining.
- V3 xác nhận worker giữ DHO-List đã áp dụng W và TID qua batch.
- So sánh kết quả phase/pattern/DO với kiểm thử engine hiện có để đảm bảo chuyển process không đổi semantics.

### 4. Đo baseline và điều tra tăng RAM

1. Chạy workload chuẩn trong JVM mới mỗi lần, nhưng giữ **một worker JVM liên tục trong mỗi run**.
2. Chạy ít nhất ba lần với cùng dataset, limit, `partial`, `factor`, `minDO`, `epsilon`, JDK/JVM flags.
3. Ghi phase đầu và các pha lặp riêng; ghi startup, warm-up, RSS start/end/peak sampled, heap used/committed/max/peak, CPU, GC counters, số transaction/entry/node, event count và log payload bytes.
4. Thực hiện đối chứng: logging minimal, logging snapshot đầy đủ, Mining lặp và Reset log theo protocol hiện hành. Giữ log behavior tương đương giữa các run so sánh.
5. Chỉ sau khi RSS trend tái lập mới chạy JFR trên compute worker. So sánh run JFR và không-JFR để ước lượng profiler overhead; dùng JMC xem allocation/GC/old-object/retaining path. Heap dump là thao tác chẩn đoán thủ công, không bật mặc định.
6. Nếu cần chẩn đoán riêng UI/logging, đo host RSS và log payload bytes song song; không gán toàn bộ host RSS cho log. Chỉ thêm logging process thứ ba nếu đối chứng cho thấy cần cô lập chính xác RSS logger.

## Kiểm thử và tiêu chí nghiệm thu

- Mỗi project vẫn build/test độc lập; không có dependency vào source/class project engine khác.
- Worker PID khác UI PID; RSS API truy vấn đúng worker và kết quả có cùng xu hướng với Windows process working-set counter.
- RSS event có đủ start/end/peak-sampled/count/interval/source/status; thiếu metric không biến thành zero.
- Worker process giữ engine state qua các pha lặp, dùng queue một lần, không nạp lại dataset ngoài ý muốn; kết quả thuật toán giống trước khi chuyển process.
- Phase wall/CPU/heap/RSS compute không bao gồm host-side JSON formatting, TextArea, log accumulation hoặc file export. Worker startup ghi riêng.
- Sampling-off/on đo overhead bằng cùng workload; xác định chu kỳ sao cho có thể sử dụng mà không làm runtime tăng đáng kể. Báo rõ độ phân giải đối với phase ngắn hơn sampling interval.
- Đóng UI không để lại worker sống sót; worker crash/IPC failure được thông báo và không cho Mining trên state không xác định.
- Viewer hiển thị RSS theo pha và đơn vị MB, phân biệt unavailable/error với zero; chart không biến metric thiếu thành cột zero.
- Kết quả điều tra RAM ghi rõ JDK/JVM flags, OS, dataset fingerprint, tham số, số lần lặp, sampler interval, JFR preset/overhead và giới hạn suy luận.

## Các quyết định chưa mặc định thay người dùng

- Chưa đặt hard cap heap hoặc tự restart worker để “sửa” mức RAM tăng; cả hai có thể che vấn đề và làm thay đổi workload đo.
- Chưa tạo logging worker process riêng. Bước đầu tách compute khỏi host UI/logging, ghi host RSS/log-payload bytes riêng. Nếu cần RAM thuần logging, đánh giá process thứ ba sau baseline.
- JFR/JMC dùng cho điều tra sau khi có RSS baseline, không bật mặc định trong các run thu thập hiệu năng.
- Sampling interval mặc định 20 ms chỉ là giá trị khởi đầu của prototype; phải đo overhead và giới hạn bắt spike trước khi chốt.
