lệnh Mine:

cấu trúc: >mine <dateset> <mode> <parial> <factor> <limit> --y <exportLocation>
ví dụ: >mine "Địa chỉ dataset local" detail 0.15 0.9 100 --y "địa chỉ xuất"
trong đó:
<dateset> là một chuỗi string chỉ về một dataset tồn tại trên máy hiện tại
<mode> là chế độ mining, gồm số mode sau: #sẽ phải tạo md riêng nói về <mode>
- để trống (null): mining bình thường, kết quả xuất ra chỉ gồm nhưng thông tin quan trọng của một lần mining.
- detail: Xuất chi tiết thông tin hơn bình thường.
- stream: với mỗi lần có một pattern đạt thì xuất kết quả, phù hợp cho việc cần kết quả cập nhật liên tục
- step: xuất nội dung tương ứng với mỗi bước trong thuật toán
- log: là chế độ sẽ tốn tài nguyên và giảm hiệu năng nhất, nó sẽ ghi gần như toàn bộ thuật toán đã làm gì, tức là mỗi lần tính toán hoặc thực thi logic thì sẽ log lại. (chế độ này sẽ được cân nhắc kỹ cằng để làm sau).
<parial>: input thứ nhất của thuật toán
<factor>: ký hiệu là f, là input thứ 2
<limit>: là giới hạn số dòng đọc dataset
<export?>: một giá trị bool y hoặc n, đơn giản là có lưu kết quả lại thành file không
- nếu --y: <exportLocation> sẽ là địa chỉ file được ghi vào
+ nếu <exportLocation> không tồn tại -> tạo file mặc định (phụ thuộc vào config)
+ nếu <exportLocation> là folder (không có file cụ thể) thì tự tạo file mới để lưu
++ nếu trong trường hợp cần cấp quyền như quyền admin để lưu như tại C:\ProgramFiles bỏ địa chỉ và tạo default.
+ nếu <exportLocation> trống, dùng địa chỉ mặc định
- nếu trống: <exportLocation> bị thừa, nếu không có --y mà có <exportLocation> thì trả về lỗi.
- rằng buộc ở đây là <exportLocation> chỉ cần khi có --y