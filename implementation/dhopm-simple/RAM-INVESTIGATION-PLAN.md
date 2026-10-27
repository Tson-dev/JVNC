# Kế hoạch kiểm tra RAM và hành vi heap của DHOPM

## Mục đích

Phân biệt được các hiện tượng thường bị gọi chung là “RAM tăng”:

1. Heap đang dùng tăng tạm thời do cấp phát trong Construction, Reconstruction hoặc Mining.
2. Garbage collector chưa chạy hoặc JVM giữ các object còn dùng được cho các pha tiếp theo.
3. Heap đã giải phóng object nhưng JVM vẫn giữ vùng heap đã commit để tái sử dụng, nên RSS chưa giảm.
4. Object vẫn được giữ bởi tham chiếu sống (retained/live set tăng), có thể là tăng dữ liệu có chủ đích hoặc memory leak.
5. RSS tăng do vùng ngoài heap, gồm Metaspace, JIT code cache, thread stacks, native memory, direct buffers và overhead của GC/JVM.

Không kết luận “memory leak” chỉ từ một số đo heap/RSS tại cuối pha. Ngưỡng khoảng 2 GB là tín hiệu cần điều tra, không tự nó chứng minh leak hay hành vi bất thường.

## Dữ liệu hiện có và giới hạn kết luận

Cả V1, V2 và V3 hiện đã chuyển engine sang compute-worker JVM riêng; integration test Windows từng project xác nhận đọc working set của đúng worker PID. Kết quả test chỉ xác nhận sampler và protocol hoạt động trên workload nhỏ, chưa phải baseline hiệu năng, chưa đo overhead sampler-on/off và không giải thích mức tăng RAM 2 GB đã thấy ở project khác.

Ba file log người dùng nêu (`v2-minDO_2026-10-27_003.txt`, `_005.txt`, `_006.txt`) đều lưu `rssBytes = null`/`unavailable`; vì vậy chúng không đo được RSS. Các giá trị heap chỉ là mẫu `usedHeapBytes` sau pha và `heapPeakBytes` lấy mẫu định kỳ trong cùng JVM với UI. Chúng không phải số đo heap live sau GC, cũng không phải RSS riêng compute.

Quan sát được ở log `003`: các lần Mining cùng trạng thái có runtime khoảng 2.922 ms ở lần đầu rồi khoảng 0.116–0.141 ms ở những lần sau; heap-used cuối pha tăng từ 44.87 MB lên 48.87 MB. Log `005` có lần Mining đầu 1.887 ms, các lần sau khoảng 0.193–0.204 ms; heap-used tăng từ 39.61 MB lên 42.44 MB. Log `006` lặp lại chuỗi này rồi thêm một transaction và chạy các pha tiếp; heap-used đạt 47.61 MB ở cuối log. Đây là quan sát phù hợp với warm-up/tái sử dụng, cấp phát và trạng thái heap thay đổi, nhưng dữ liệu đó chưa xác định được phần nào còn reachable hoặc đã được GC.

Trong implementation cũ (các log lịch sử), engine, `LogSession` và UI cùng chạy trong một JVM. Trong thiết kế worker hiện tại, cần tiếp tục phân biệt:

- Compute worker giữ transactions, global entries và các node sau Reconstruction để dùng giữa các pha; RSS/heap/CPU theo worker PID thuộc vùng này.
- Host process giữ `LogSession` và UI. Mỗi event trong log RAM có snapshot; Mining event còn giữ danh sách DUBO engine đã tính. Lặp Mining vì vậy có thể làm log host tăng dù engine không giữ kết quả Mining cũ. Host RSS là UI + log, không phải RSS riêng logger.
- TextArea cũng giữ chuỗi output. Export không tự giải phóng log RAM; Reset mới xóa log hiện hành.
- Worker gửi snapshot/result về host sau thông điệp `PHASE_COMPUTE_DONE`; RSS sampling theo pha dừng trước IPC serialization/transfer. Việc serialization vẫn có thể làm thay đổi heap/working set của worker và ảnh hưởng RSS baseline của pha kế tiếp, nên cần ghi nhận đây là chi phí IPC ngoài compute interval chứ không quy kết thành allocation thuật toán.
- Các đối tượng tạm trong Mining có thể được tái sử dụng bởi allocator/GC hoặc được giải phóng; heap-used và RSS hiện tại không cho biết chính xác object nào còn sống.

Các ý trên là các khả năng có căn cứ từ cấu trúc hiện tại, chưa phải kết luận về nguyên nhân mức 2 GB ở project khác.

## Nguyên tắc và quy tắc so sánh

- Mỗi lần chạy bắt đầu trong app-host JVM mới và compute-worker JVM mới để có baseline tương đương; trong một run, giữ cùng compute worker qua Construction/Reconstruction/Mining và các lần Mining lặp để đo được reuse/warm-up. Ghi chính xác JDK, JVM flags, OS, dataset, limit và tham số.
- Giữ nguyên dataset và input; không so sánh các lần chạy có cấu hình khác nhau.
- Ghi riêng thời gian pha, heap used, heap committed, heap max, live heap sau GC ở lượt chẩn đoán, RSS/working set của process, và số liệu GC.
- Không gọi `System.gc()` trong lượt benchmark thông thường. Chỉ dùng thao tác GC có kiểm soát ở lượt chẩn đoán riêng và ghi rõ vì nó ảnh hưởng runtime.
- Giữ nguyên UI/logging trong một nhóm test để đo chi phí thực tế; nhóm khác chạy engine/headless hoặc tắt log detail có kiểm soát để cô lập nguồn tăng.
- JFR/JMC là công cụ quan sát, không thay thế số đo chuẩn. So sánh một lượt không-profiler với một lượt JFR để ước lượng overhead.
- Không dùng heap post-GC hay RSS đơn lẻ làm đại diện cho tất cả memory.
- Đo compute RSS trên PID worker, không đo RSS host thay cho worker. Host process RSS chỉ là UI + log RAM; không gọi đó là logger-only RSS.
- Triển khai process/IPC/sampler theo [RSS-WORKER-IMPLEMENTATION-PLAN.md](./RSS-WORKER-IMPLEMENTATION-PLAN.md), sau đó quay lại các giai đoạn baseline/JFR trong tài liệu này.

## Các giai đoạn điều tra

### A. Kiểm kê và baseline

1. Lưu các file log cũ làm baseline read-only; không chuyển/xóa log đã tạo tại `D:\JVNC\JVNC\logs`.
2. Chạy một workload cố định mỗi version trong JVM mới, tối thiểu ba lần.
3. Chạy cùng dataset/tham số với một chuỗi Construction → Reconstruction → Mining; sau đó lặp Reconstruction/Mining không thay đổi dữ liệu.
4. Ghi các đường chuẩn theo thời gian: process working set/RSS, heap used/committed/max, GC count/time, phase wall time, số transaction/entry/node, số event và kích thước serialized log.
5. Đánh dấu warm-up riêng: lượt khởi động và lượt lặp; không lấy lần đầu làm đại diện cho steady state.

**Câu hỏi cần trả lời:** tăng heap/RSS có tái lập không, xảy ra ở pha nào, tiếp tục tăng sau các pha giống hệt nhau hay hội tụ?

### B. Tách RAM thuật toán khỏi RAM logging/UI

Chạy các biến thể cùng dữ liệu và tham số:

1. Engine-only/headless, không tạo UI và không tích lũy log chi tiết.
2. UI bật, log chỉ lưu metadata/counters.
3. UI bật, log lưu snapshot transaction như hiện tại.
4. UI bật, lặp Mining và giữ toàn bộ event như quy trình người dùng.
5. Cùng run nhưng Reset log sau mỗi pha/nhóm pha, chỉ ở lượt chẩn đoán.

So sánh delta của heap live và RSS giữa các biến thể. Tính tổng dung lượng payload JSON snapshot/DUBO để kiểm tra log có giải thích được phần tăng hay không. Không tự xóa log trong benchmark thông thường vì sẽ làm thay đổi behavior cần đo.

### C. Phân biệt allocation, GC và retained objects bằng JFR/JMC

1. Ghi profile JFR theo preset low-overhead, bao gồm GC, allocation sampling, object allocation by class, heap summary, safepoint, thread/CPU, class loading và process metadata.
2. Mở JFR trong JDK Mission Control (JMC); so sánh allocation hot spots giữa Mining đầu và Mining lặp. Kiểm tra allocation rate, GC pause/count, heap after GC, top allocated classes và old-object sampling nếu JDK/preset hỗ trợ.
3. Chụp heap histogram hoặc heap dump ở các mốc riêng: sau warm-up, sau các lần lặp cùng trạng thái, sau Reset log, và sau workload tăng dữ liệu. Heap dump chỉ bật trong lượt chẩn đoán vì có thể dừng ứng dụng và tạo file lớn.
4. Dùng JMC/JFR để kiểm tra retaining path khi live set sau GC tăng. Phân biệt references do `LogSession.events`, `DhopmEngine`, UI controls và cache/runtime.
5. Ghi rõ JDK version, JFR settings, command line, sampling limitations và profiler overhead.

**Diễn giải:**

- Allocation cao nhưng post-GC live set ổn định: khả năng là temporary allocation/GC churn, không tự động là leak.
- Heap used tăng trước GC rồi giảm, trong khi RSS/committed giữ cao: phù hợp với heap reservation/commit được giữ để JVM tái sử dụng; RSS có thể không giảm ngay.
- Post-GC live set tăng theo số lần lặp hoặc kích thước log, và heap dump cho retaining path vào event/snapshot/cache: dữ liệu đang được giữ; xác định đó là chủ đích hay leak trước khi sửa.
- RSS tăng nhưng heap live ổn định: điều tra native memory, Metaspace, code cache, stacks, direct buffers và GC/native overhead.

### D. Kiểm tra tăng trưởng tới workload lớn

Chỉ sau khi các giai đoạn A–C tạo baseline đáng tin:

1. Tăng quy mô dữ liệu theo bước (transactions/items) và lặp lại cùng số phase.
2. Đo heap live sau GC ở các mốc, RSS peak/steady-state, GC pause/time, log bytes và thời gian.
3. So sánh model tăng trưởng với transaction count, entry count, DUBO count, event count và snapshot bytes.
4. Kiểm tra memory cap/heap flags như thí nghiệm riêng; không dùng cap để che retention không giải thích được.
5. Đặt ngưỡng cảnh báo và tiêu chí chấp nhận sau khi có dữ liệu baseline; hiện chưa ấn định một con số giới hạn RAM vì chưa có workload/dataset chuẩn.

## Yêu cầu số đo và công cụ

| Metric | Công cụ/phương pháp | Ý nghĩa |
|---|---|---|
| RSS/working set theo thời gian | Windows Performance Monitor/Process Explorer, hoặc sampler đọc process working set; PID phải là compute process nếu tách process | Physical resident memory của process, gồm heap và native |
| Heap used/committed/max | JVM MemoryMXBean/JFR | Dung lượng heap dùng, đã commit và giới hạn |
| Live heap sau GC | JFR heap summary/heap dump; GC chẩn đoán riêng khi cần | Ước lượng retained heap; không lấy mẫu thay cho benchmark |
| Allocation/GC | JFR phân tích trong JMC | Phân biệt temporary allocation, GC churn và retained growth |
| Native memory | Native Memory Tracking nếu JVM hỗ trợ và được bật từ lúc startup; JFR/OS counters bổ sung | Điều tra RSS tăng ngoài Java heap; NMT có overhead |
| Pha/khối lượng dữ liệu | Log app với phase ID, T_L, active transactions, entries/nodes, event count và log payload bytes | Tương quan memory với khối lượng công việc |

## Tiêu chí hoàn thành điều tra

- Có thể tái lập các xu hướng trong log cũ bằng workload được ghi rõ.
- Có đồ thị thời gian cho RSS, heap used/committed, sau-GC live heap và GC; thiếu metric được ghi rõ là unavailable.
- Xác định tăng trưởng chủ yếu thuộc engine state, retained log/UI payload, JVM heap retention/warm-up hay native memory; nếu chưa phân biệt được thì nêu giới hạn thay vì đoán.
- Nếu live set tăng, có retaining path và số liệu trước/sau thao tác Reset/logging để chứng minh nguồn tham chiếu.
- Đưa ra đề xuất có số đo: giới hạn/stream log snapshot, summary-only mode, reset policy hoặc xử lý retention khác; chỉ triển khai sau khi người dùng duyệt kết quả và phạm vi.
- Lưu cấu hình, lệnh chạy, JFR, bảng/đồ thị và kết luận trong báo cáo điều tra; giữ raw heap dumps/JFR riêng vì có thể rất lớn.

## Phạm vi hiện tại

Tài liệu này là kế hoạch và ghi nhận quan sát sơ bộ. Chưa chạy JFR/JMC, heap dump, tăng tải tới 2 GB, hoặc thay đổi chiến lược logging/heap. Các thử nghiệm chi tiết sẽ bắt đầu sau khi kế hoạch và workload chuẩn được xác nhận.
