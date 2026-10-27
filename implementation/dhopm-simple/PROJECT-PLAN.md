# Kế hoạch `dhopm-simple`: ba engine độc lập và ứng dụng xem log

## 1. Mục tiêu

Tạo bốn project Java độc lập dưới `implementation/dhopm-simple` để chạy các phiên bản và thu thập/quan sát sự thay đổi trong cách chúng lưu, giữ, loại bỏ và xử lý dữ liệu. Project thứ tư trước tiên xem trực quan từng log riêng lẻ; chức năng nhóm log và so sánh phiên bản sẽ phát triển sau:

1. `v1-original`: triển khai DHOPM bám sát paper, không cắt dữ liệu bằng minDO/W.
2. `v2-minDO`: tại Reconstruction, so sánh trực tiếp `DO(T,T)` của từng transaction với ngưỡng minDO; chỉ loại toàn bộ transaction, không cắt riêng đóng góp của các pattern.
3. `v3-window`: dùng W suy ra từ minDO để loại transaction cũ ngay ở Construction.
4. `log-viewer`: ứng dụng JavaFX mở và trực quan hóa một log độc lập ở phiên bản đầu; so sánh/nhóm log là phần mở rộng sau.

Ba engine đầu phải build và chạy riêng, mỗi project tự chứa model, thuật toán, UI, logging và file Maven `pom.xml`. Không dùng chung class, module, thư viện nội bộ hay project cha Maven với nhau. Có thể dùng các thư viện ngoài giống nhau nhưng mỗi project khai báo dependency độc lập. Các codebase hiện hữu (`dhopm-common`, `dhopm-cli`, `dhopm-bench`, `dhopm-v1-standard`, `dhopm-v2-minocc`, `dhopm-v3-optimized`) chỉ dùng tham khảo, không phải dependency hoặc code nền bắt buộc.

Phạm vi là công cụ nghiên cứu/chạy thử đơn giản trên máy cá nhân; không đặt mục tiêu production, phân tán, quản trị người dùng hay benchmark cấp công nghiệp.

## 2. Căn cứ và cách hiểu thống nhất

- Paper gốc: `docs/root/1-s2_0-S095219762600792X-main.md`.
- Nguồn chính xác về minDO, cách áp dụng, thời điểm cắt và cách suy ra W: `docs/minOcc_full_extracted.txt`.
- Các tài liệu trong `docs/plans/` chỉ dùng tham khảo; phần mô tả format dataset được dùng để đọc dữ liệu. Chúng không thay thế/ghi đè semantics minDO của tài liệu chính.

### 2.1 Ba chế độ phải được phân biệt

| Project | Ý nghĩa | Thời điểm xử lý transaction cũ | Kết quả mong đợi |
|---|---|---|---|
| V1 Original | Tính đủ lịch sử theo paper | Không loại theo tuổi | Mốc đối chiếu chính xác với paper |
| V2 minDO | So sánh trực tiếp `DO(T,T)` của transaction đầy đủ với ngưỡng; nếu đạt thì loại toàn bộ transaction | Reconstruction | Xấp xỉ có chủ đích; đo sai khác và kiểm chứng pruning |
| V3 W | Tính số transaction cần giữ từ minDO rồi bỏ transaction quá cũ | Construction | Cắt theo tuổi bằng ngưỡng trên `f^age`; khác với phép thử `DO(T,T)` của V2 và cần đo riêng |

Mục tiêu so sánh không chỉ là thời gian chạy: cần thu thập và quan sát transaction/entry/node nào được nạp, giữ lại, loại bỏ, còn sống và được dùng ở mỗi giai đoạn; đặc biệt ghi nhận thay đổi của DUBO do minDO. Project viewer trực quan hóa các thay đổi theo pha và theo version.

**Ngữ nghĩa V2/V3 phải lấy `docs/minOcc_full_extracted.txt` làm chuẩn.** Không đồng nhất minDO với `minOcc` trong kế hoạch hiện hữu. Các plan có mô tả một công thức W khác:

`W_minOcc = ceil(ln(minOcc * (1 - f)) / ln(f))`

Ghi chú `minOcc_full_extracted.txt` đề xuất ngưỡng cho đóng góp của một transaction:

`f^age <= minDO`, với `age = TL - tid`, được dùng để dẫn giải cutoff W của V3.

Suy ra cutoff:

Với `minDO > 0`, đặt `q = minDO + epsilon`: nếu `q >= 1` thì W=0; nếu `0 < q < 1` và `0 < f < 1` thì `W_minDO = min { age ∈ Z, age >= 0 : f^age <= q }`, ước tính bằng `ceil(ln(q) / ln(f))` rồi kiểm tra lại predicate ở biên số nguyên. Nếu `f=1` và `q<1`, không có cutoff tuổi hữu hạn. `minDO = 0` luôn tắt cắt, không phụ thuộc epsilon.

Hai công thức có mục tiêu và ngữ nghĩa khác nhau; V2/V3 của kế hoạch này dùng ngưỡng minDO theo tài liệu chính, không lấy công thức cửa sổ `minOcc` trong các plan.

Giá trị mặc định đề xuất cho `minDO` là `1e-4`; người dùng có thể sửa. Trên V2/V3, `minDO >= 1` hiển thị cảnh báo “ngưỡng này sẽ trả về rỗng” và cho kết quả rỗng; V1 chỉ ghi tham số làm metadata. `minDO = 0` tắt cắt. Với V3 và `minDO > 0`, epsilon được dùng trong ngưỡng hiệu dụng `q = minDO + epsilon`; nếu `q >= 1`, W=0, dữ liệu bị loại hết và V3 cảnh báo.

`epsilon` là tham số nhập được, mặc định `1e-6`, dùng như sai số tuyệt đối khi so sánh. V2 chỉ loại transaction khi so sánh trực tiếp `DO(T,T) <= minDO + epsilon`; `minDO = 0` là chế độ tắt cắt và được xử lý riêng. Không dùng `f^age` làm điều kiện chung để cắt đóng góp pattern. Kiểm tra `minDO >= 1` được thực hiện trước so sánh epsilon. Với `minDO > 0` và `minDO + epsilon >= 1`, V2 cũng trả rỗng vì `DO(T,T)` nằm trong miền `[0,1]`; UI cảnh báo rõ. Các ngưỡng rất nhỏ như `1e-9` hoặc rất gần 1 như `1 - 1e-9` có thể bị epsilon chi phối; UI hiển thị cảnh báo và giá trị hiệu dụng.

V3 dùng cutoff theo tuổi `f^age <= minDO + epsilon`, như cách áp dụng W được dẫn giải trong tài liệu minDO. Đây là một bound theo tuổi, không đồng nhất với việc V2 kiểm tra trực tiếp từng `DO(X,T)`; V3 có thể loại transaction nếu bound theo tuổi thỏa ngưỡng, dù V2 không loại mọi đóng góp tương ứng. Phải đo khác biệt này thay vì tuyên bố hai engine tương đương. Không dùng công thức `minOcc` trong plans làm thay thế. `f = 1` được phép nhập nhưng hiển thị cảnh báo không có suy giảm theo thời gian; nếu `minDO + epsilon < 1`, W không có cutoff hữu hạn và không evict theo tuổi.

Do `ceil(log(...))` có thể lệch ở biên số thực, sau khi ước tính W cần kiểm tra lại `pow(f, age) <= minDO + epsilon`, điều chỉnh tới tuổi nguyên nhỏ nhất thỏa điều kiện, và test tuổi W−1/W/W+1 cùng các trường hợp W gần số nguyên. Nếu `f=1`, W không giới hạn tuổi và không evict; nếu `minDO=0`, cắt tắt.

TID là thứ tự transaction không rỗng liên tục (1-based), không phải số dòng vật lý khi có dòng trống. Như vậy `T_L = DHOList.TLast = |DB|`; `limit` và pending queue đều đếm transaction. Không được bỏ qua TID đã cấp trong lúc queue rồi dùng lại, hoặc đánh số lại các transaction cũ khi Construction incremental.

### 2.2 Mục tiêu đo lường minDO và DUBO

Tài liệu minDO nêu rõ việc loại transaction có thể làm thay đổi thống kê độ dài dùng để tính DUBO và làm giảm DUBO. Đây là hiện tượng cần quan sát và đo:

- Không tuyên bố V2/V3 trả kết quả chính xác bằng V1 khi `minDO > 0`.
- Dùng dữ liệu nhỏ để đối chiếu với phép tính tham chiếu và định lượng ảnh hưởng đến DUBO/pruning.
- Không che lấp hay khôi phục thay đổi DUBO do dữ liệu bị cắt; đó là một hiện tượng cần đo và trực quan hóa.
- Log minDO, epsilon, W, transaction/entry bị loại và các giá trị DUBO mà Mining thực sự so sánh. V1 cung cấp baseline; V3 log DUBO trên dữ liệu giữ lại để viewer có thể đối chiếu khi tham số tương thích.
- Engine chỉ ghi các giá trị DUBO thực sự được tính và dùng trong phép so sánh/pruning; không tuyên bố đã ghi mọi `DUBO(X)` hay chạy thêm một lượt để tính toàn bộ pattern. Mỗi giá trị được gắn khóa prefix/node ổn định, giá trị, pha/snapshot và tham số liên quan. Không ghi trace theo từng transaction. Project 4 có thể tính thêm DUBO từ snapshot, nhưng phải đánh dấu rõ là dữ liệu bổ sung/tính lại, không phải giá trị engine đã ghi.
- V2 chỉ loại đóng góp khi so sánh trực tiếp `DO(X,T) <= minDO + epsilon` tại Reconstruction; không dùng `f^age` làm điều kiện V2. Gắn kết quả đã so sánh với prefix/node tương ứng; không suy rộng một lần so sánh thành loại cả transaction hoặc entry khác.

## 3. Cấu trúc project dự kiến

```text
implementation/dhopm-simple/
  PROJECT-PLAN.md
  v1-original/
    pom.xml
    src/
  v2-minDO/
    pom.xml
    src/
  v3-window/
    pom.xml
    src/
  log-viewer/
    pom.xml
    src/
```

Mỗi project engine có thể copy/build độc lập và không tham chiếu code project khác. Log được xuất vào `..\logs\`, tính tương đối từ working directory khi khởi chạy engine; cần khởi chạy từ thư mục gốc của project engine. Với cấu trúc hiện tại, thư mục đích là `implementation/dhopm-simple/logs`. Viewer cũng chạy độc lập; bản đầu mở một file log tại một thời điểm.

## 4. Chức năng chung của ba ứng dụng engine

### 4.1 UI và luồng thao tác

Ba engine có UI đồng nhất, tối giản; không bắt buộc dùng JavaFX. Chỉ project viewer nên dùng JavaFX để có biểu đồ. Mỗi engine có:

- ComboBox chọn dataset trong `D:\JVNC\JVNC\dataset`.
- TextField cho `partial`, `factor` (`f`), `minDO`, `epsilon`, và `limit` số transaction tối đa đọc. Cả ba UI giữ cùng bố cục; V1 hiển thị minDO/epsilon như metadata so sánh nhưng ghi rõ thuật toán V1 không áp dụng ngưỡng minDO.
- TextArea chỉ đọc hiển thị các mốc logging/tóm tắt dễ đọc và trạng thái chạy; không nạp toàn bộ snapshot hay spam từng entry/pattern lên UI. Có thể cuộn/xem nội dung phiên log RAM; **Export log** ghi đầy đủ snapshot và các record theo schema.
- Nút **Construction**, **Reconstruction**, **Mining**, **Add transaction**, **Reset log**, **Export log**.
- Nút Mining chỉ bật sau Reconstruction và khi tham số/dataset chưa bị thay đổi.
- Chạy thuật toán ở background worker để UI không bị treo; lỗi input/đọc file được báo rõ trong UI.
- Mỗi engine chạy thuật toán trong một compute worker là tiến trình JVM con riêng để đo RSS tách khỏi UI/logging process. UI gửi dataset/queue/tham số và các lệnh pha; worker giữ DHO-List/snapshot trong suốt phiên. Sau khi compute worker dừng xử lý pha, logging worker tổng hợp gói kết quả trong RAM; không chạy logging trong hot path hoặc đồng thời với pha thuật toán. Không cần Diagnostic Worker quét lại thuật toán. Mỗi project tự triển khai worker của mình, không dùng chung class/protocol implementation.

State tối thiểu:

`EMPTY → CONSTRUCTED → RECONSTRUCTED → MINED`

- Bắt đầu Construction tạo bản ghi pha mới trong log RAM; không thay thế toàn bộ lịch sử Construction của phiên đang mở.
- Reconstruction chỉ hợp lệ sau Construction; sau khi hoàn tất, Mining được bật.
- Transaction nhập tay được đưa vào hàng đợi chờ (pending queue), chưa thay đổi DHO-List ngay. Khi nhấn Construction, các transaction đang chờ mới được nạp vào DHO-List; sau đó queue được đánh dấu đã tiêu thụ.
- TID cho transaction nhập tay được cấp lúc Construction tiêu thụ queue, tuần tự từ `DHOList.TLast + 1`; việc xếp hàng không làm thay đổi `TLast`.
- Construction lần đầu nạp dataset đã chọn theo `limit`; Construction các lần sau chỉ nạp pending queue, không đọc/nạp lặp lại toàn bộ dataset. Nếu queue rỗng, Construction không được âm thầm nạp lại dataset; báo không có transaction mới. Đổi dataset tạo data session/epoch mới; không trộn các DHO-List hoặc pending queue của dataset khác.
- Nếu đổi dataset khi pending queue còn transaction, hỏi người dùng có bỏ queue thuộc dataset cũ không; không tự chuyển transaction sang dataset mới.
- Thêm transaction sau Mining làm mất hiệu lực Reconstruction và kết quả Mining; lần Mining tiếp theo cần chạy Construction để nạp transaction chờ, sau đó Reconstruction.
- Đổi dataset tạo data epoch mới; đổi tham số làm Reconstruction/Mining cũ hết hiệu lực. Log cũ được giữ theo quy tắc lịch sử bên dưới, gắn với epoch/dataset/tham số gốc, không được ghép nhầm vào run mới.
- Đổi `limit` hoặc dataset luôn tạo data epoch mới ở cả ba engine. Đổi `partial` chỉ cần Reconstruction/Mining lại. Với V1/V2, đổi `factor` cần Reconstruction/Mining lại; V2 đổi `minDO` hoặc `epsilon` cũng cần Reconstruction/Mining lại. Với V3, đổi `factor`, `minDO` hoặc `epsilon` có thể làm thay đổi W/tập dữ liệu đã evict; phải mở data epoch mới và chạy lại Construction từ dataset/limit ban đầu, sau đó phát lại transaction thủ công đã nhập. Không cho phép chỉ Reconstruction trên DHO-List đã cắt bằng tham số cũ.
- Giữ transaction thủ công đã tiêu thụ trong một input journal nhỏ thuộc data epoch để có thể replay khi V3 phải dựng lại; journal không phải DHO-List và được xóa khi epoch bị reset. Nếu không muốn giữ journal, UI phải báo rằng người dùng cần nhập lại các transaction thủ công khi tạo epoch mới.
- **Reset log** chỉ xóa log trong RAM; không xóa DHO-List, pending queue hoặc file log đã export. Nếu cần làm sạch dữ liệu thuật toán, đó là thao tác riêng, không thuộc Reset log.
- Trong lúc compute worker đang chạy, khóa các thao tác Construction/Reconstruction/Mining/Reset log. Có thể xếp transaction mới vào pending queue nếu queue được bảo vệ an toàn; transaction đó chỉ thuộc Construction kế tiếp.
- Nếu Construction có dữ liệu mới để xử lý và còn bất kỳ log cũ nào chưa được xử lý trong RAM (kể cả Construction history), khi nhấn Construction hiện đúng câu **“Bạn còn log cũ, có muốn lưu lại không?”** với đúng hai nút **Yes** và **No**. Không thêm nút Hủy. Nếu không có dataset/transaction mới, báo không có dữ liệu mới và không hiện prompt/không thay đổi log.
- **Yes**: export log hiện có ra file theo quy tắc đặt tên rồi tiếp tục Construction; giữ log trong RAM và append log mới. Đánh dấu các record đã export để lần Construction kế tiếp không hỏi lại nếu chưa phát sinh log Reconstruction/Mining mới. Nếu export lỗi, báo lỗi và không chạy Construction để tránh mất dữ liệu log.
- **No**: xóa log Reconstruction/Mining cũ trong RAM nhưng giữ toàn bộ Construction history; đánh dấu history đã được người dùng xử lý để không hỏi lặp lại cho cùng record, rồi tiếp tục Construction mới. Reset log vẫn xóa toàn bộ log RAM.
- Mọi event phải có `dataEpochId`/`constructionId` để không ghép nhầm các pha.

### 4.2 Dataset và transaction nhập tay

- Nạp file `.dat` từ thư mục dataset; `limit = 0` nghĩa là đọc tất cả, số dương giới hạn số transaction đầu tiên.
- Format theo mô tả trong `docs/plans/00-OVERALL-PLAN.md`: mỗi dòng transaction chứa các item phân tách bằng khoảng trắng (`<item1> <item2> ...`).
- Các file `.dat` trong dataset đã thống nhất cùng format. Không xây parser tổng quát hoặc nhiều parser cho từng dataset; dùng một reader tuần tự đơn giản theo format này. Gán TID theo thứ tự transaction trong file, bắt đầu từ 1; dòng trống không tạo transaction. Không tự thêm quy tắc comment nếu format dataset không định nghĩa comment.
- Add transaction nhận cùng cú pháp item; tự cấp TID tiếp theo và báo lỗi nếu input rỗng/không hợp lệ.
- Hiển thị số transaction đã đọc, item/node count và TID cuối.
- ZIP, định dạng có utility, dataset ngoài thư mục mặc định và TID tường minh chưa thuộc phạm vi ban đầu.

Reader cần báo lỗi rõ nếu dữ liệu không khớp format chung; không tự đoán hay tự chuyển đổi format.

### 4.3 Tham số và kiểm tra đầu vào

- `partial`: trong `[0, 1]`; `factor` trong `(0, 1]`. `f = 1` được chấp nhận nhưng phải hiển thị cảnh báo rằng không có suy giảm theo thời gian và đây là biên mở rộng ngoài miền paper.
- `partial` và `factor` phải có giá trị khởi tạo rõ ràng trong UI; nếu paper không định nghĩa default, dùng giá trị ví dụ trong paper hoặc yêu cầu người dùng nhập trước lần chạy đầu. Luôn log giá trị thực dùng.
- `minDO`: mặc định `1e-4` cho V2/V3; V1 hiển thị/lưu cùng tham số để so sánh nhưng không áp dụng. Cho phép người dùng sửa.
- `epsilon`: tham số sai số tuyệt đối mặc định `1e-6`, nhập được, hữu hạn và không âm; dùng trong so sánh minDO/cutoff và phải được log cùng minDO.
- `minDO` phải hữu hạn và không âm; `minDO = 0` tắt cắt. Trên V2/V3, `minDO >= 1` hiển thị “ngưỡng này sẽ trả về rỗng” và short-circuit kết quả rỗng; với `minDO > 0` và `minDO + epsilon >= 1`, cũng cảnh báo và trả rỗng. V1 chỉ ghi minDO làm metadata, không áp dụng cảnh báo/ngưỡng này vào thuật toán.
- Khi `minDO` nằm trong epsilon của 0 hoặc 1, V2/V3 hiển thị cảnh báo rằng kết quả so sánh sẽ bị epsilon chi phối.
- V3 hiển thị `W_minDO` tính được, không cho nhập W tùy ý ở bản đầu để tránh cấu hình không nhất quán với minDO/factor.
- `f = 1` được phép nhập; hiển thị cảnh báo không có suy giảm theo thời gian. Nếu `minDO + epsilon < 1`, W không loại transaction theo tuổi; nếu ngưỡng hiệu dụng đạt 1, cảnh báo riêng và kết quả rỗng theo quy tắc epsilon.
- `limit` là số transaction đầu tiên đọc từ dataset; hiển thị limit thực dùng.
- `T_L` và `|DB|` bằng nhau trong mô hình này. Dùng `DHOList.TLast` làm nguồn giá trị `T_L`, tránh duyệt lại DHO-List chỉ để đếm transaction; ngưỡng là `minSup = partial × T_L`.
- Tất cả tham số số phải hữu hạn; từ chối `partial` ngoài `[0,1]`, `factor` ngoài `(0,1]`, `minDO < 0`, `epsilon < 0` và `limit < 0`/không phải số nguyên trước khi chạy, đồng thời nêu rõ trường/giá trị gây lỗi.

## 5. Đặc tả từng engine

### 5.1 V1 — `v1-original`

- Cài đặt theo paper, đúng thứ tự Construction → Reconstruction → Mining.
- Không cắt transaction theo minDO/W; mọi transaction đã nạp vẫn có thể đóng góp vào DO/DUBO.
- `minSup = partial × TL`; `DO(X) = Σ (|X| / |T|) × f^(TL - tid)`.
- Lưu giá trị minDO được chọn làm metadata đối chiếu (không áp dụng vào kết quả V1), để viewer ghép baseline với V2/V3.
- Dùng làm oracle cho test TC1–TC8 và đối chiếu V2/V3 trên các trường hợp minDO bằng 0.
- Không ghi trace từng phép tính; nếu Mining thực sự dùng DUBO để so sánh/pruning thì log đúng các giá trị đó theo quy tắc chung, còn lại chỉ ghi mốc và tổng kết.

### 5.2 V2 — `v2-minDO`

- Giữ dữ liệu Construction như V1.
- Khi Reconstruction, tính `DO(X,T)` và chỉ bỏ entry khi so sánh trực tiếp `DO(X,T) <= minDO + epsilon`. Không thay điều kiện này bằng `f^(TL-tid) <= minDO`; công thức theo tuổi chỉ dùng để dẫn giải/ước tính W cho V3.
- Đánh dấu entry cũ là inactive cho Reconstruction/Mining, giữ trạng thái cần thiết để log snapshot trước/sau và thống kê entry bị loại. V2 không kỳ vọng giảm RAM như V3; viewer dùng snapshot đã ghi để tính DUBO bổ sung, không chạy Diagnostic Worker trong engine.
- Chỉ ghi các DUBO mà Mining thực sự tính và dùng để so sánh/pruning, cùng prefix/node key và snapshot/parameter liên quan. Không yêu cầu tính thêm toàn bộ DUBO để phục vụ log; việc bổ sung giá trị chưa được engine tính thuộc viewer.
- Dùng dữ liệu nhỏ đối chiếu với tính toán tham chiếu để định lượng ảnh hưởng của thay đổi DUBO lên pruning và kết quả.

### 5.3 V3 — `v3-window`

- Với `minDO > 0`, nếu `minDO + epsilon >= 1`, cutoff W=0, kết quả rỗng và hiển thị cảnh báo. Nếu `minDO + epsilon < 1` và `0 < f < 1`, tự tính `W ≈ ceil(ln(minDO + epsilon) / ln(f))` rồi kiểm tra biên bằng predicate theo epsilon. `minDO = 0` tắt cửa sổ; `f = 1` và ngưỡng hiệu dụng dưới 1 không có W hữu hạn và không evict.
- Construction xử lý transaction theo TID tăng dần. Mỗi transaction mới được thêm vào DHO-List; khi `TLast` tăng, loại transaction đã có với `TLast - tid >= W_minDO`. Sau Construction, chỉ còn dữ liệu trong cửa sổ tương ứng với `TLast` hiện tại. Không khẳng định transaction mới đến đã ngoài cutoff: lúc vừa đến, tuổi của nó là 0.
- Duy trì ring buffer các transaction trong cửa sổ và danh sách item của transaction hết hạn; chỉ dời head/loại entry tại các node tương ứng, không quét toàn bộ DHO-List cho mỗi eviction. Compaction để thu hồi vùng đầu đã hết hạn được thực hiện amortized; log phân biệt entry inactive với vùng nhớ đã được thu hồi.
- Khi biết trước TLast cuối của một batch, có thể bỏ qua transaction ngoài cửa sổ trước khi ghi entry, nhưng bản đầu không được đọc dataset lần hai chỉ để biết số dòng; hành vi mặc định là cập nhật cửa sổ một lượt khi Construction tiến triển.
- Log riêng số transaction/entry được thêm, bị evict hoặc không được ghi, để đo chi phí và ảnh hưởng Construction; không quét lại toàn DHO-List chỉ để đếm `TLast`.
- `minDO = 0` giữ dữ liệu đầy đủ (không nhất thiết bit-for-bit nếu thứ tự tính toán khác). `f = 1` được phép nhập nhưng cảnh báo không có suy giảm theo thời gian.
- V3 phải đối chiếu với V1/V2 về transaction/entry được giữ, tác động lên DUBO và thời gian/bộ nhớ theo từng pha; không giả định kết quả pruning giống nhau.
- Ưu tiên cấu trúc đơn giản, dễ kiểm thử; chưa đặt mục tiêu SoA, ForkJoin hay tối ưu mức thấp.

## 6. Logging và định dạng file

### 6.1 Vòng đời log

- Không ghi log run ra disk tự động. Khi bắt đầu Construction, tạo log session trong RAM. Log session giữ Construction history và mọi record Reconstruction/Mining chưa bị người dùng xóa.
- Log chỉ được export khi có run record; Export disabled nếu chưa có dữ liệu log.
- Reset xóa toàn bộ log session trong RAM; export trước đó vẫn giữ nguyên.
- Nếu người dùng chọn **No** ở prompt lưu log cũ lúc bắt đầu Construction, chỉ xóa bản ghi Reconstruction/Mining cũ; giữ lại Construction history và append Construction mới. Chọn **Yes** export log hiện có rồi giữ lại trong RAM. Nút Reset mới xóa tất cả.
- Transaction nhập tay được xếp vào pending queue. Construction tiêu thụ queue và ghi rõ số transaction được nạp từ queue; Reconstruction/Mining phải liên kết với snapshot dữ liệu cụ thể đã được Construction tạo ra.
- Mỗi lần Mining dùng snapshot DHO-List thực tế do Reconstruction tạo trong compute worker; Mining đọc snapshot đó, không tạo bản sao chỉ để logging. Sau khi pha kết thúc, worker cung cấp snapshot log tối giản, đủ để project 4 tái dựng dữ liệu cần cho việc bổ sung DUBO.
- Gói snapshot log chứa dữ liệu transaction canonical (TID và item IDs) cùng trạng thái lọc cần thiết, ví dụ các entry/prefix đã inactive sau V2 Reconstruction; không serialize cấu trúc object nội bộ của DHO-List. Capture/serialize diễn ra sau khi phép đo pha dừng, thời gian và RAM capture báo riêng, không cộng vào runtime/peak của compute.
- Sau mỗi pha, compute worker trả metric và snapshot payload cần thiết vào hàng đợi log RAM; logging worker chỉ tổng hợp/cập nhật log khi compute worker đã ngừng xử lý pha, tuyệt đối không chạy trong hot path hay đồng thời với Construction/Reconstruction/Mining. Không cần Diagnostic Worker quét lại DHO-List để tạo log.
- Không ghi từng transaction/item/pattern vào log trong hot loop; ghi các thống kê sử dụng dữ liệu, DUBO, mốc pha, cảnh báo và lỗi cần thiết.
- Với DUBO, log từng giá trị engine thực sự tính và dùng để so sánh/pruning, gồm pattern key, DUBO, kết quả so với minSup, support/DO/minSup và snapshot/parameter liên quan. Không tính thêm giá trị chỉ để log.
- Ghi riêng danh sách DOP kết quả cuối của Mining; không gộp DOP vào danh sách các DUBO đã tính/so sánh. DOP và DUBO-comparison có các trường và view riêng trong log viewer.
- Không chạy lượt tính chẩn đoán toàn bộ DUBO trong engine. Project 4 dùng so sánh DUBO đã log để xác định các descendants bị prune và có thể liệt kê chúng với source `recompute`; placeholder này không bịa/tính DUBO, DO hay support. Dữ liệu đó không sửa/ghi đè observation do engine ghi.
- Khi có log lớn, TextArea cần giới hạn lượng text hiển thị/giữ lại; không được làm tăng vô hạn chỉ vì cập nhật UI.

### 6.2 Nội dung bắt buộc

Mỗi file log là một `logSession` có `schemaVersion`, `logSessionId`, version engine, thời điểm bắt đầu, OS/JVM/máy chạy; bên trong gồm data epoch, Construction event, Reconstruction và Mining event gắn bằng ID, dataset/path rút gọn, limit và tham số:

1. **Sau Construction:** số transaction từ dataset và pending queue đã nạp, TL (`DHOList.TLast`), số transaction được giữ/bỏ qua/evict, số item/node/entry active, snapshot ID và payload snapshot canonical sau Construction, cùng thời gian/RAM/CPU của Construction worker.
2. **Sau Reconstruction:** thống kê snapshot DHO-List đầu vào và snapshot đầu ra thực sự Mining sẽ dùng, minSup, W/minDO/epsilon nếu áp dụng, entry còn hiệu lực/bị loại, payload trạng thái sau Reconstruction, cùng thời gian/RAM/CPU của Reconstruction worker.
3. **Sau Mining:** thống kê DHO-List/snapshot được Mining worker sử dụng, số lần/nhánh xử lý phù hợp để quan sát lượng dữ liệu đã xét và thời gian/RAM/CPU của Mining worker. Không yêu cầu lưu danh sách DOP vào log.
4. **DUBO đã so sánh:** các giá trị engine thực sự tính và dùng để so sánh/pruning, kèm prefix/node key, snapshot ID, pha, giá trị và tham số; ghi thêm số record/byte capture. Không yêu cầu ghi DOP hoặc DUBO chưa được tính.
5. **Tổng thể:** `computeWallTotal` là tổng wall time hoạt động của Construction/Reconstruction/Mining; `computeCpuTotal` là tổng CPU time worker nếu khả dụng; `peakRssTotal` là RSS lớn nhất được sample qua các pha; `heapUsed` và `heapPeak` được ghi theo pha nếu khả dụng. Không cộng RAM UI/logging worker vào compute metrics.
6. Snapshot capture/serialization, nếu được đo, là overhead logging riêng, không cộng vào runtime thuật toán. `sessionElapsed` nếu ghi phải tách khỏi `computeWallTotal`.
7. Tham số đầy đủ, ID data epoch/construction/reconstruction/mining snapshot liên quan và trạng thái từng worker (hoàn tất/lỗi/bị reset); không biến worker lỗi thành worker thành công.

Snapshot DHO-List là dữ liệu thực sự worker dùng, không chỉ là bản tóm tắt. Log lưu ID, TL/counts và payload canonical tối giản đủ tái dựng dữ liệu cần cho viewer: transaction TID/item IDs, cùng trạng thái entry/prefix inactive sau Reconstruction nếu có. Payload được tạo sau khi phép đo compute dừng và giữ trong log RAM để export; không sao chép nguyên cấu trúc object DHO-List. Payload có thể lớn, do đó UI cần hiển thị dung lượng log RAM. Nếu ghi ước lượng byte của cấu trúc, phải đánh dấu là ước lượng; RSS/heap là metric chính, không khẳng định byte chính xác của từng object.

### 6.3 Định nghĩa metric hiệu năng

- Wall time: `System.nanoTime()` quanh từng pha.
- CPU: chênh lệch process/thread CPU time của compute worker khi JVM/OS cung cấp metric; nếu không hỗ trợ, ghi `unavailable`, không ghi số giả lập.
- RAM compute và RAM logging là hai phạm vi độc lập. Đo RAM của compute worker bằng RSS (working set/process resident memory) ở mức tương đối; đồng thời ghi heap used và heap peak/max của JVM worker để phân biệt bộ nhớ native với heap Java. RSS/heap của UI/logging process không tính vào compute metrics; buffer DUBO primitive nằm trong compute worker nên RSS/heap worker có chứa phần overhead này, cần ghi riêng dung lượng buffer để diễn giải số liệu.
- Lấy RSS tại đầu/cuối pha và RSS peak quan sát được bằng sampling chu kỳ thấp; heap used ghi tại ranh giới pha và heap peak lấy từ peak metric của JVM. Ghi rõ nguồn, thời điểm và độ phân giải sampling; các số đo là ước lượng, không đại diện phép đo tuyệt đối.
- Vì lấy mẫu RSS có thể bỏ lỡ spike ngắn, log rõ sampling interval; heap peak là high-water mark của JVM (ghi nhận/reset tại ranh giới pha nếu hỗ trợ), không suy ra từ mẫu RSS.
- Compute worker chỉ thu thập metric thô/tối thiểu; không định dạng log hay ghi file trong lúc pha quan trọng chạy. Logging worker đọc metrics/snapshot payload sau khi compute worker ngừng xử lý pha hiện tại và trước khi cho bắt đầu pha kế tiếp. RSS/heap của logging worker không được tính vào metric compute.
- Tổng thể chỉ báo cáo các metric compute worker đã đo; không cộng peak RAM giữa các pha. Không dùng một “overall RAM” chung nếu không có định nghĩa cụ thể.
- Ghi metadata máy để diễn giải phép so sánh: OS/architecture, số logical CPU, Java vendor/version, JVM max heap/flags và RSS sampling interval. Ghi nhận fingerprint dataset (path/name, size, last-modified; checksum chỉ khi chi phí chấp nhận được).

### 6.4 Tên file và thư mục export

- Thư mục mặc định: `..\logs\`, tính từ working directory (không hardcode ổ đĩa hoặc đường dẫn checkout); khởi chạy engine từ thư mục gốc của project để dùng đúng vị trí tương đối.
- Tên: `<version>_<yyyy-MM-dd>_<NNN>.txt`, ví dụ `v2-minDO_2026-10-27_001.txt`.
- `NNN` bắt đầu từ `001` và tăng riêng cho từng version/ngày; ngày mới tự bắt đầu lại `001`.
- Nội dung là UTF-8, có `schemaVersion` và cấu trúc có thể parse ổn định (khuyến nghị JSON hợp lệ nhưng giữ extension `.txt`).
- Cấp ID bằng cách dò tên đã tồn tại và tạo file theo chế độ không ghi đè; nếu trùng do nhiều instance cùng lúc, thử ID tiếp theo. Export lỗi phải báo rõ.

## 7. Project 4 — `log-viewer`

- JavaFX desktop app độc lập; không import class từ ba engine.
- Bản đầu mở và xem **một file log tại một thời điểm**; mở hộp thoại file mặc định ở project `log-viewer`. Kiểm tra `schemaVersion`, engine/version và trường bắt buộc, báo lỗi rõ nếu file không hợp lệ. Nạp nhiều log, nhóm cùng version và so sánh khác version để giai đoạn phát triển sau.
- Hiển thị tham số, snapshots, metric theo pha, dữ liệu được nạp/giữ/bỏ, DUBO đã so sánh và DOP cuối riêng. Có tab riêng tái dựng DHOList (node, support, DO, DUBO và entry theo TID) từ snapshot transaction cùng parameter; luôn gắn nhãn đây là dữ liệu viewer tái dựng. Tab DUBO hiển thị đúng số/value/result của các comparison engine đã làm và cho liệt kê các descendants bị prune (source `recompute`, không tính DUBO/DO/support). DOP cuối được hiển thị trong tab riêng. Chạy tái dựng ở background để UI không bị treo.
- Biểu đồ JavaFX tối giản nằm trong tab riêng để Overview đủ chỗ cho bảng summary và phase details; biểu đồ thời gian/RSS/heap theo pha, cùng dữ liệu transaction/entry/node và DUBO đã log. Danh sách DOP cuối vẫn hiển thị riêng.
- Thiếu metric (ví dụ CPU unavailable) hiển thị là “không có dữ liệu”, không thay bằng 0.

## 8. Kiểm thử và tiêu chí nghiệm thu

### 8.1 Thuật toán

- V1 được kiểm thử theo định nghĩa và ví dụ trong paper gốc `docs/root/1-s2_0-S095219762600792X-main.md`.
- V1 với cùng input/tham số cho kết quả deterministic.
- V2/V3 khi minDO tắt khớp kết quả V1; kiểm tra tập pattern và DO trước khi làm tròn.
- V2/V3 khi minDO bật: test V2 so sánh trực tiếp `DO(T,T) <= minDO + epsilon` và chỉ loại transaction đầy đủ; test V3 cutoff tại tuổi `W−1`, `W`, `W+1`, `f^age = minDO + epsilon`, W gần số nguyên, `minDO=0`, `minDO + epsilon >= 1`, stream ngắn hơn W và stream dài hơn W. `f=1` phải được hỗ trợ với cảnh báo; nếu ngưỡng hiệu dụng dưới 1 thì không eviction, nếu đạt 1 thì kết quả rỗng.
- Kiểm tra transaction queue chỉ tiêu thụ một lần; Construction thứ hai không nạp lặp dataset; TID liên tục qua nhiều batch; đổi dataset không trộn pending queue/data epoch.
- V3 Construction theo stream/batch phải tạo chính xác cửa sổ cuối cùng với `age < W`; so sánh sự khác nhau với V2 tại cùng `TLast`, không yêu cầu hai tập dữ liệu/phép pruning giống nhau.
- Dùng bộ dữ liệu nhỏ để tính tham chiếu đầy đủ, kiểm tra sai số DO và phát hiện DUBO prune sai; ghi rõ giới hạn nếu ngữ nghĩa xấp xỉ làm thay đổi tập DOP.
- V3 lưu đúng tập transaction mà điều kiện age cutoff xác định; ghi nhận retention độc lập với V2.
- Kiểm tra limit 0/1/lớn hơn file, transaction nhập sau Mining, pending queue trước/sau Construction, và dữ liệu không khớp format chung.

### 8.2 UI và logging

- Mining không chạy trên UI thread; các nút bật/tắt đúng state machine.
- Reset xóa log RAM và disable Export; Export tạo đúng tên/path/ID, không ghi đè file.
- Log đủ snapshot metadata, parameters, metrics, thống kê sử dụng dữ liệu, DUBO comparison outcomes và DOP cuối thành hai collection riêng. Run/worker bị lỗi phải ghi nhận trạng thái lỗi.
- Logger chỉ chạy sau compute worker; RSS, heap used/peak và CPU của compute worker không bao gồm UI/logging worker.
- Đo chéo một pha ngắn để xác nhận RSS sampler/IPC không làm tăng đáng kể thời gian compute; snapshot capture/serialization (nếu đo) được báo cáo tách khỏi compute time.
- Khi xác nhận xóa log cũ lúc Construction, Reconstruction/Mining cũ bị xóa nhưng Construction history còn nguyên; Reset xóa toàn bộ log RAM.
- Kiểm tra Yes export đúng log và đánh dấu đã xử lý; No bỏ Reconstruction/Mining RAM nhưng giữ Construction; xác nhận không prompt nếu Construction không có dữ liệu mới.
- Viewer mở được một log hợp lệ từ mỗi version, tái dựng DHOList từ snapshot/parameter và hiển thị các snapshot/metrics/DUBO bổ sung có gắn nhãn; báo rõ mismatch metadata, lỗi schema hoặc thiếu snapshot cần cho việc tính thêm.
- Kiểm tra chart hiển thị đúng đơn vị và phân biệt dữ liệu unavailable với zero. Kiểm thử multi-log/version comparison để dành cho giai đoạn sau, không chặn bản đầu.

## 9. Trình tự triển khai đề xuất

1. **V1 engine + test**: reader đơn giản theo format `.dat`, model, Construction/Reconstruction/Mining, UI tối giản, run record.
2. **Logging schema v1**: snapshot canonical, metrics và export; kiểm tra round-trip.
3. **V2 minDO**: Reconstruction cutoff trực tiếp, đối chiếu tham chiếu và kiểm chứng bound.
4. **V3 W**: cutoff tại Construction, test biên/retention và đo sai khác với V1/V2.
5. **Viewer JavaFX**: mở một log, hiển thị snapshot/data use, tái dựng DHOList và tính DUBO bổ sung có gắn nhãn.
6. **Kiểm tra độc lập**: build và chạy từng `pom.xml` riêng; xác nhận không phụ thuộc module khác.

## 10. Những điểm đã thống nhất và phần còn cần lưu ý

| Chủ đề | Quyết định hiện tại |
|---|---|
| Nguồn thuật toán | Paper gốc làm căn cứ V1; `minOcc_full_extracted.txt` là nguồn chính xác cho minDO; `docs/plans/` và các project hiện hữu chỉ tham khảo (riêng format `.dat` có thể theo mô tả trong plans). |
| Dataset | Các file `.dat` cùng format; chỉ cần một reader theo đúng format, không xây parser tổng quát hoặc xử lý nhiều format. |
| Ngưỡng và bộ đếm | Dùng `DHOList.TLast` làm `T_L`, vì `T_L = \|DB\|`; `minSup = partial × T_L`, không suy diễn `N_eff` từ plan minOcc. |
| DUBO | Chỉ log các giá trị engine thực sự tính/so sánh, theo prefix/node key và snapshot nguồn; viewer có thể tính thêm từ snapshot, phải gắn nhãn dữ liệu bổ sung/tính lại. |
| DOP output | Ghi riêng các DOP cuối Mining và trình bày tách biệt khỏi các DUBO engine đã tính/so sánh. |
| Snapshot | Worker dùng snapshot thực tế; log RAM/export lưu payload canonical tối giản sau pha để viewer có thể tái dựng dữ liệu. Snapshot capture không tính vào thời gian compute. |
| UI | Ba engine dùng UI đồng nhất, toolkit đơn giản tùy chọn; JavaFX ưu tiên cho log viewer. |
| RAM và worker | Ưu tiên RSS của compute worker, đồng thời ghi heap used và heap peak. Logging worker chỉ tổng hợp/cập nhật log sau khi compute worker kết thúc. |
| Log folder | `..\logs\` tính từ working directory khi engine được chạy từ project root; tên file theo version, ngày và ID tăng từ `001`, reset ID theo ngày. |
| Lịch sử log RAM | Có log cũ chưa được xử lý (kể cả Construction) thì hỏi “Bạn còn log cũ, có muốn lưu lại không?” với Yes/No. Yes export, đánh dấu đã export và giữ log RAM; No xóa Reconstruction/Mining cũ nhưng giữ Construction history. Reset xóa toàn bộ log RAM. |
| Transaction nhập tay | Đưa vào pending queue; chỉ đưa transaction vào DHO-List khi chạy Construction. |
| Thay đổi tham số | `partial` cần Reconstruction/Mining lại; dataset/limit luôn tạo epoch mới; V2 cần Reconstruction/Mining lại khi đổi `minDO`/`epsilon`; V3 tạo epoch mới khi đổi `factor`/`minDO`/`epsilon`, vì transaction ngoài cửa sổ cũ không thể khôi phục từ DHO-List đã cắt. |

### 10.1 Ghi chú triển khai (không phải quyết định còn mở)

- Dùng compute worker riêng cho mỗi engine để RSS tách khỏi UI/logging; worker giữ DHO-List qua các pha. Logging chạy sau khi compute phase đã dừng, không chạy trong hot path hoặc đồng thời với phase tiếp theo.
- Lấy RSS ở mức tương đối bằng sampling nhẹ; ghi riêng heap used/peak và CPU nếu hệ thống cung cấp. Metric không khả dụng phải ghi rõ `unavailable`, không giả làm số 0.
- Chọn schema snapshot/DUBO gọn nhưng đủ dữ liệu để log viewer độc lập tái dựng. Ghi rõ kích thước log RAM và để người dùng Reset/Export khi snapshot lớn.
- So sánh kết quả chỉ khi dataset fingerprint/limit/partial/factor/minDO/epsilon tương thích; viewer bản đầu chỉ xem một log nên chưa triển khai so sánh đa run.

### 10.2 Trạng thái triển khai ban đầu

- Đã tạo bốn Maven project độc lập; ba engine dùng Swing, viewer dùng JavaFX. Không có dependency project-to-project.
- V1 có kiểm thử theo ví dụ paper; V2 lọc transaction đầy đủ bằng phép so sánh trực tiếp `DO(T,T)` tại Reconstruction; V3 cắt theo W ngay trong Construction và snapshot ghi lại cả transaction bị loại trong batch.
- Ba UI hỗ trợ chọn `.dat`, parameter, limit, transaction queue, Construction/Reconstruction/Mining, reset log RAM và export theo tên version/ngày/ID. Log lưu DUBO engine đã tính/so sánh và danh sách DOP kết quả trong hai trường riêng.
- Ba engine xuất log vào `..\logs\` tương đối từ working directory khi chạy; khởi chạy từ project root để log nằm ở `implementation/dhopm-simple/logs`. Không còn phụ thuộc `D:\JVNC\JVNC\logs`.
- Viewer đọc một log, mở mặc định ở project `log-viewer`; tab Overview không chứa đồ thị. Tab Graphs riêng; tab DUBO phân biệt comparison đã log với placeholder descendants bị prune; tab DOP hiển thị kết quả cuối riêng. Log cũ thiếu field DOP vẫn đọc được và hiện trạng thái không có DOP đã ghi; không suy đoán DOP lịch sử từ DUBO comparisons.
- **RSS worker:** Cả V1, V2 và V3 hiện chạy Construction/Reconstruction/Mining trong persistent child JVM riêng, giữ state qua pha, nhận transaction queue khi Construction, và hỗ trợ reset epoch/shutdown. Worker RSS dùng Windows process working set theo PID. Log viewer đọc/hiển thị metric RSS mới và vẫn tương thích với log cũ.
- **Validation RSS:** kiểm thử từng project độc lập đều pass: V1 6 tests, V2 10, V3 10, viewer 7; worker tests xác nhận PID riêng và Windows RSS metrics. Chưa chạy build gộp (không có reactor project chủ quản; các project được thiết kế độc lập).
- **Giới hạn đo hiện tại:** sampling working set chạy mỗi 20 ms; overhead sampler-on/off chưa benchmark. Đây là sampled peak, không phải absolute peak. Chưa có baseline đủ để kết luận về RAM tăng hoặc leak; JFR/JMC vẫn là bước điều tra tiếp theo. Log lịch sử không có RSS nên không dùng làm kết luận RSS chính thức.
- Viewer chưa hỗ trợ nhóm nhiều log hoặc so sánh các version; đây là phần mở rộng đã thống nhất để sau.
- Kế hoạch/tiến độ worker/RSS nằm trong `RSS-WORKER-IMPLEMENTATION-PLAN.md`; kế hoạch baseline/JFR/JMC và cách phân biệt allocation, GC, retained heap, RSS/native memory nằm trong `RAM-INVESTIGATION-PLAN.md`. Worker RSS đã có kiểm thử tích hợp trên cả ba engine; chưa chạy phép đo benchmark sampler overhead hay điều tra JFR/JMC chuyên sâu.

## 11. Ngoài phạm vi bản đầu

- Tái sử dụng code qua `dhopm-common` hoặc gom bốn ứng dụng thành một multi-module.
- Các tối ưu cấp thấp như SoA, ForkJoin, pipeline song song hoặc memory-mapped I/O.
- Sửa/đồng bộ toàn bộ SRS, plan G0–G7, CLI/API Manager hiện hữu.
- Lưu log tự động khi chạy, upload log, database log, hoặc dashboard web.
- Hỗ trợ format dataset khác định dạng chung, zip, streaming từ network, hay môi trường multi-user.
