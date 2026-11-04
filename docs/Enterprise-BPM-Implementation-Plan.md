# Enterprise BPM — Kế hoạch triển khai MVP theo phase

**Ngày lập:** 30/10/2026.  
**Trạng thái:** Chính sách và trải nghiệm P0.1–P0.6 đã được người dùng xác nhận; Phase 0 còn đầu vào và hợp đồng kỹ thuật cần hoàn thiện theo mục 4. Ngày 02/11/2026, người dùng xác nhận P1.4/P1.5 và sau đó P1.6 đã build/test thành công; chốt P1.6 để chuyển sang Phase 2. Agent không chạy lại build/test.

**Nguồn nghiệp vụ:** [Enterprise-BPM-MVP-Specification.md](Enterprise-BPM-MVP-Specification.md).  
**Nguồn giao diện:** 4 ảnh trong [concept-ui](../concept-ui/).

## 1. Cách hiểu dự án và hiện trạng

Mục tiêu là xây một nền tảng BPM dùng nội bộ cho một doanh nghiệp: người có quyền cấu hình quy trình và biểu mẫu; nhân viên tạo hồ sơ; hệ thống giao việc, điều phối phê duyệt và lưu lịch sử. Quy trình mua sắm là luồng nghiệm thu chính; quy trình nghỉ phép đơn giản chứng minh nền tảng có thể tái sử dụng bằng cấu hình.

Các quyết định đã chốt được giữ nguyên: **bpmn-js cho designer/viewer, Java Spring Boot tích hợp Flowable BPMN Engine, một doanh nghiệp sử dụng nội bộ**. Các phương án cơ sở như React + TypeScript, PostgreSQL, MinIO/S3, modular monolith và JSON Schema được dùng để lập kế hoạch; việc chọn thư viện và chính sách chi tiết vẫn phải được ghi nhận theo mức C/B/O của đặc tả.

Hiện trạng đọc được trong repository:

| Thành phần | Hiện trạng | Ý nghĩa đối với kế hoạch |
| --- | --- | --- |
| Backend | Có entry point Spring Boot; `pom.xml` khai báo Java 25, Spring Boot 4.1.2 và Flowable 8.0.0. Người dùng xác nhận đã kiểm thử bộ phiên bản ngày 01/11/2026. | Giữ nguyên, không kiểm chứng lại tương thích; C-15. Còn chuẩn bị Docker và kiểm thử engine/nghiệp vụ theo các bước sau. |
| Database local | `compose.yaml` dùng PostgreSQL 18.6, named volume và health check; backend chạy cùng stack Docker. | Đã chuẩn bị cấu hình ngày 01/11/2026; người dùng chạy build/khởi động để kiểm chứng kết nối và persistence. |
| Cấu hình ứng dụng | Có profile dev/test, PostgreSQL datasource, schema tự quản lý trong dev, health endpoint và graceful shutdown. | Chưa có storage/auth nghiệp vụ; cấu hình runtime cần người dùng kiểm chứng. |
| Kiểm thử | Có smoke test PostgreSQL `bpm_test`, JPA schema/seed, integration test Flowable qua adapter và bộ kiểm chứng transaction/concurrency/restart P1.6; có stack test riêng và cấu hình CI. | Người dùng xác nhận P1.6 đã build/test thành công ngày 02/11/2026; chốt bước theo xác nhận này. Agent không chạy lại; chưa có kết quả GitHub Actions. |
| Frontend | Chưa thấy mã frontend. | Cần dựng nền giao diện và các màn hình từ đầu. |
| Nghiệp vụ | Chưa thấy module quy trình, biểu mẫu, hồ sơ, task hoặc tổ chức. | Không đánh dấu bất kỳ tính năng MVP nào là đã hoàn thành. |

Kế hoạch có **10 phase, 64 bước**. Mỗi bước có mô tả và yêu cầu đầu ra có thể kiểm tra. Không gán số ngày/tuần khi chưa biết đội ngũ, năng lực triển khai và quy mô sử dụng.

## 2. Chuyển concept UI thành phạm vi triển khai

| Ảnh | Ý định sản phẩm đọc được | Cách đưa vào MVP |
| --- | --- | --- |
| `02_19_38 PM-1.png` — Tổng quan | Sidebar cố định, header, số liệu tổng hợp, quy trình phổ biến, công việc hôm nay và yêu cầu gần đây. | Giữ cấu trúc và ngôn ngữ thị giác; dữ liệu phải theo quyền. Thực hiện dashboard sau khi đã có luồng hồ sơ/task thật. |
| `02_19_44 PM-2.png` — Thiết kế quy trình | Bố cục palette trái, canvas giữa, cấu hình bước phải; lưu nháp, kiểm tra, công bố. | Dùng bpmn-js và ký hiệu BPMN chuẩn trong tập hỗ trợ. Property panel nghiệp vụ cấu hình assignment, form, action và deadline. |
| `02_19_47 PM-3.png` — Quản lý quy trình | Tìm/lọc, thẻ quy trình, tab theo vòng đời, lịch sử phiên bản và thao tác tạo/sửa/nhân bản. | Phân biệt rõ danh mục cho người khởi tạo và quản lý cấu hình cho Designer/Owner. Sửa bản đã công bố phải tạo nháp mới. |
| `02_19_49 PM-4.png` — Chi tiết yêu cầu | Thông tin hồ sơ, tiến trình, người xử lý, tệp, trao đổi, lịch sử và thanh hành động. | Là màn hình nghiệp vụ trọng tâm. Trường dữ liệu và nút hành động lấy từ quyền, task và trạng thái thực tế do backend trả về. |

Các điểm phải điều chỉnh so với ảnh:

- **Chuyển tổ chức:** MVP chỉ có một doanh nghiệp; không xây luồng đổi tenant.
- **Canvas:** không thay bpmn-js bằng graph node card riêng. Approval được mô hình bằng User Task và Exclusive Gateway.
- **Node nâng cao:** gửi thông báo, chờ thời gian, gọi quy trình khác, truy vấn dữ liệu và nhóm bước trong ảnh không mặc nhiên là các node được thiết kế trong MVP. Thông báo cơ bản là chức năng ứng dụng.
- **Nhập BPMN, tự động bố cục:** không lấy nút trong ảnh làm yêu cầu bắt buộc. Import bên ngoài cần quyết định phạm vi và đi qua cùng validator nếu được bổ sung.
- **Luồng mua sắm:** thứ tự kế toán/giám đốc và nhánh ngân sách trong ảnh khác mẫu tại mục 6 của đặc tả. P0.2 phải thống nhất một luồng nghiệm thu; chưa coi ảnh là chính sách nghiệp vụ.
- **Tiến trình và công việc tương lai:** bước chưa đến chỉ là mô tả đường đi có thể xảy ra; không tạo sẵn task, assignee hoặc deadline giả, nhất là khi có XOR và vòng bổ sung.
- **Nút chỉnh sửa:** requester không được sửa tự do hồ sơ đã gửi; chỉ sửa nháp hoặc trường được cấp ở task bổ sung. Designer không sửa trực tiếp artifact đã publish.
- **Ưu tiên, tìm kiếm toàn hệ thống, báo cáo nâng cao:** chưa phải cam kết MVP. Baseline dùng tìm/lọc theo từng trang và dashboard cơ bản; chỉ bổ sung khi có use case được ghi nhận.
- **Chỉ số, phần trăm tăng giảm, biểu đồ:** phải có định nghĩa và dữ liệu thật. Không dùng số demo làm kết quả vận hành; chỉ số chưa đủ dữ liệu có trạng thái trống rõ ràng.

Các màn chưa có ảnh nhưng vẫn cần cho MVP: đăng nhập, quản trị người dùng/phòng ban/quyền, quản lý và thiết kế biểu mẫu, tạo hồ sơ, hồ sơ của tôi, inbox/candidate queue, ngoại lệ phân công, thông báo và tra cứu audit.

## 3. Tổng quan phase và thứ tự phụ thuộc

| Phase | Kết quả cần đạt | Bước | Phụ thuộc chính |
| --- | --- | --- | --- |
| 0 — Chốt phạm vi và nghiệp vụ | Có bộ quy tắc, luồng mẫu và bản đồ màn hình đủ để triển khai. | P0.1–P0.6 | Tài liệu và concept hiện có. |
| 1 — Nền kỹ thuật và kiểm chứng Flowable | Backend/database chạy được; chứng minh transaction, task và lưu trạng thái. | P1.1–P1.6 | P0.1; các quyết định kỹ thuật liên quan. |
| 2 — Danh tính, tổ chức, phân quyền và khung UI | Người dùng đăng nhập đúng quyền; có cơ cấu tổ chức và khung ứng dụng. | P2.1–P2.6 | Phase 1; quyết định quyền/assignment/auth ở Phase 0. |
| 3 — Nền tảng biểu mẫu | Tạo, hiển thị, kiểm tra và công bố form version được. | P3.1–P3.6 | Phase 2; P0.4. |
| 4 — Workflow Designer và công bố phiên bản | Thiết kế bằng bpmn-js, validate, publish và quản lý version được. | P4.1–P4.8 | Phase 2–3; kiểm chứng engine ở Phase 1. |
| 5 — Hồ sơ và xử lý công việc xuyên suốt | Mua sắm chạy được qua các nhánh duyệt, từ chối và bổ sung. | P5.1–P5.8 | Phase 3–4; chính sách nghiệp vụ đã thống nhất. |
| 6 — Theo dõi, cộng tác và chi tiết yêu cầu | Màn chi tiết hoàn chỉnh với tệp, trao đổi, tiến trình, hạn và thông báo. | P6.1–P6.7 | Phase 5. |
| 7 — Tổng quan, giám sát và workflow thứ hai | Có trải nghiệm quản lý hằng ngày và chứng minh tái sử dụng bằng cấu hình. | P7.1–P7.5 | Phase 5–6. |
| 8 — Kiểm thử tích hợp và hoàn thiện MVP | Có bằng chứng đạt acceptance criteria và xử lý các lỗi chặn. | P8.1–P8.6 | Tính năng Phase 2–7 đã tích hợp. |
| 9 — Vận hành, UAT và pilot | Phục hồi được dữ liệu, người dùng nghiệm thu và pilot đạt điều kiện mở rộng. | P9.1–P9.6 | Phase 8; quyết định vận hành đã chốt. |

Thứ tự mặc định: **0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9**. Một số việc có thể làm đồng thời sau khi đầu vào tương ứng ổn định: định nghĩa token UI không cần chờ toàn bộ backend; shell UI có thể đi cùng CRUD tổ chức; khung canvas có thể bắt đầu sau P2.5 nhưng binding/publish phải chờ form contract. Đây là quan hệ công việc, không phải yêu cầu phải có nhiều người thực hiện.

Nếu chính sách mẫu mua sắm yêu cầu đính kèm ngay khi nộp, đưa P6.1 lên trước P5.2 và nối trường attachment vào form tại thời điểm đó. Các phase là mốc quản lý kết quả; phụ thuộc nghiệp vụ cụ thể được ưu tiên hơn thứ tự số bước.

Kiểm thử quyền, versioning, rollback và chống xử lý lặp bắt đầu trong phase xây tính năng. Phase 8 kiểm tra tích hợp và các tình huống giao nhau, không phải mốc bắt đầu bổ sung tính đúng đắn.

## 4. Phase 0 — Chốt phạm vi, nghiệp vụ và trải nghiệm

**Mục tiêu:** Có đầu vào rõ ràng trước khi xây những phần dễ phải sửa lại. Mỗi quyết định còn mở phải có kết luận hoặc mốc xử lý trước bước phụ thuộc; không cần chốt mọi vấn đề vận hành ngay ngày đầu.

### Kết luận thảo luận đã xác nhận — 30/10/2026

| Phần | Kết luận | Trạng thái |
| --- | --- | --- |
| P0.1 — Quy trình nghiệm thu | Mua sắm là luồng chính; nghỉ phép đơn giản là quy trình thứ hai, chưa tính số dư phép. | Đã xác nhận; C-05 trong đặc tả. |
| P0.1 — Người thiết kế/sử dụng | Một Admin thiết kế quy trình; nhân viên chỉ sử dụng, tạo và xử lý hồ sơ theo quyền. | Đã xác nhận; C-06. Quyền publish/giám sát/ngoại lệ đã chốt tại C-09/P0.3. |
| P0.1 — Hạng mục hoãn | Hoãn duyệt song song/hội đồng, SSO, ERP/HRM, email, chữ ký số, mobile native, báo cáo nâng cao và migration instance đang chạy; không có ngoại lệ bắt buộc cho pilot. | Đã xác nhận; C-07. |
| P0.2 — Luồng và chính sách mua sắm | Trưởng phòng → kế toán → giám đốc nếu `amount >= 50000000` VND → mua hàng; VND nguyên đồng; bổ sung duyệt lại từ trưởng phòng; từ chối/yêu cầu bổ sung bắt buộc lý do; thiếu ngân sách phải điều chỉnh hoặc từ chối; mua hàng xác nhận nhận đủ hàng kèm chứng từ mới hoàn tất. | Đã xác nhận; C-08/C-13/mục 6.5 đặc tả, có expected result 49.999.999/50.000.000/50.000.001 VND. Self-approval theo C-09. |
| P0.3 — Quyền và giao việc | Admin thiết kế/tự công bố, xem toàn bộ hồ sơ/lịch sử, phân công lại cho người đủ thẩm quyền có lý do/audit. Nhân viên xem hồ sơ của mình/từng trực tiếp xử lý. Direct + queue/claim; không tự duyệt; giữ phòng ban lúc gửi, resolve người khi tạo task, không tự chuyển task cũ theo đổi nhân sự. Hoãn ủy quyền tự phục vụ, trưởng phòng chia việc, unclaim và thu hồi/hủy/mở lại sau gửi; cho xóa nháp của mình. | Đã xác nhận chính sách; C-09/mục 5.7. Quyền trường đã chốt tại C-10/mục 5.8; chi tiết kỹ thuật assignment được hoàn thiện trước P2.4/P5.3. |
| P0.4 — Biểu mẫu/version | Một form chung chia nhóm; trường cơ bản và quyền Ẩn/Chỉ xem/Được sửa theo bước. Lưu nháp được thiếu dữ liệu; validation theo action. Published bất biến, hồ sơ chạy giữ process/form/ngưỡng cũ. Nháp cũ được giữ khi tạo nháp theo bản mới và sao chép trường tương thích. Hoãn bảng nhiều dòng, công thức tùy ý, dữ liệu ngoài và bố cục phức tạp. | Đã xác nhận chính sách; C-10/mục 5.8, có ma trận quyền trường mẫu mua sắm. Còn đầu ra kỹ thuật schema/UI schema, field ID, binding và validator trước P3/P4. |
| P0.5 — Nền/vận hành | Authentication cập nhật C-18: JWT Bearer do BPM tự phát, không session; TTL dev mặc định 30 phút từ lúc phát. Admin quản lý tài khoản và mật khẩu tạm; in-app notification; deadline giờ/ngày lịch, gồm cuối tuần, UTC+7, reassign không reset. PDF/DOCX/XLSX/PNG/JPG, 20 MB/tệp, 10 tệp/hồ sơ; bình luận 2.000 ký tự không sửa/xóa; không tự xóa dữ liệu. Backup DB/tệp hằng ngày, mục tiêu RPO 24 giờ/RTO 4 giờ cần kiểm chứng. Quy mô 50 nhân sự, cao điểm khoảng 500 request/giây. | Đã xác nhận chính sách/đầu vào; C-11/C-14/C-18. O-12 còn môi trường/runbook/retention backup; O-16 còn thời hạn lưu/quét file/audit nâng cao. Chi tiết workload O-17 hoàn thiện tại P8.5. |
| P0.6 — UI/nghiệm thu | Bản đồ màn hình, route/điều hướng, trạng thái UI, đối chiếu 4 concept, actor/fixture và acceptance matrix AC-01–AC-38 tại [UI/nghiệm thu Phase 0](Enterprise-BPM-Phase-0-UX-Acceptance.md). | Đã được người dùng xác nhận; C-12. Là kế hoạch kiểm thử, chưa có kết quả chạy AC. |

Các kết luận trên chưa có nghĩa toàn bộ P0.1 hoặc Phase 0 đã hoàn thành. Danh sách O-01–O-17 tại mục 16 của đặc tả tiếp tục là đầu vào theo dõi quyết định; chỉ đóng hoặc cập nhật từng mục khi có kết luận tương ứng.

### Thuật ngữ thống nhất P0.1

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Quy trình | Loại nghiệp vụ có cấu hình form, bước xử lý, người xử lý và điều kiện; ví dụ mua sắm. |
| Phiên bản | Bộ cấu hình cụ thể của quy trình/form; bản công bố bất biến, được gắn với hồ sơ đang chạy. |
| Hồ sơ | Một đề xuất nghiệp vụ và dữ liệu/tệp/lịch sử của nó; nháp chưa có instance, gửi hợp lệ tạo instance tương ứng. |
| Task/công việc | Một lần thực hiện một bước đang hoạt động; quay lại cùng bước tạo lượt task mới, giữ lịch sử cũ. |
| Action/hành động | Thao tác actor được phép thực hiện; ví dụ APPROVE, REJECT, REQUEST_CHANGES, RESUBMIT, COMPLETE. |
| Outcome/kết quả bước | Kết quả action được xác nhận để BPMN định tuyến; không đồng nghĩa trạng thái cuối của cả hồ sơ. |

### Đầu vào đã bổ sung và phần kỹ thuật cần hoàn thiện

Người dùng đã bổ sung ngưỡng qua giám đốc **50.000.000 VND**, quy mô **50 nhân sự**, cao điểm khoảng **500 request/giây**. O-13 đã chốt; O-17 đủ đầu vào Phase 0, chi tiết kiểm thử tải chuyển sang P8.5 theo yêu cầu không cần thảo luận sâu thêm về tải.

| Đầu vào | Trạng thái và người cung cấp | Mốc bắt buộc |
| --- | --- | --- |
| O-17 — Kịch bản tải và tiêu chí đo | Đầu vào đã chốt: 50 nhân sự, cao điểm khoảng 500 request/giây. Kỹ thuật hoàn thiện workload, dữ liệu nền, tỷ lệ API, thời gian/đỉnh và mục tiêu độ trễ/lỗi; chưa có kết quả đo. | Trước khi thực hiện P8.5; không cần thảo luận sâu thêm tại Phase 0. |
| O-12 — Hạ tầng/runbook/backup | RPO 24 giờ/RTO 4 giờ và backup hằng ngày đã chốt; IT vận hành còn cung cấp môi trường, nơi/lịch lưu, retention backup và người hỗ trợ. | Trước P9.1–P9.2; diễn tập chứng minh trước pilot. |
| O-16 — Retention/quét file/audit nâng cao | Giới hạn tệp/bình luận đã chốt; chủ dữ liệu/IT còn chốt thời hạn lưu dữ liệu, quét malware và nhu cầu audit nâng cao. | Trước dùng dữ liệu thật. |
| O-01 — Bộ phiên bản | Backend theo pom đã được người dùng xác nhận đã test, không kiểm chứng lại (C-15). Còn chọn/khóa frontend, bpmn-js và validator. | Trước tích hợp các thành phần tương ứng. |
| O-08 — Schema/form binding | Chính sách form đã chốt; kỹ thuật hoàn thiện schema dialect, validator cần đánh giá, field ID/schema/UI schema/binding mẫu. | Trước P3.1 và binding/publish tại P4. |

Phần thảo luận phạm vi/nghiệp vụ/trải nghiệm Phase 0 đã chốt, gồm ngưỡng mua sắm và quy mô/tải. Có thể chuẩn bị Phase 1 theo các đầu vào đã đủ; các đầu ra kỹ thuật/vận hành trong bảng tiếp tục có mốc bắt buộc, không được coi là đã triển khai hoặc kiểm thử đạt.

### Công việc và đầu ra Phase 0

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P0.1 — Chuẩn hóa phạm vi** | Chuyển đặc tả thành backlog MVP, phần hoãn và thuật ngữ thống nhất: quy trình, phiên bản, hồ sơ, task, action, outcome. Phân biệt quyết định C, phương án B và vấn đề O. | Có bảng phạm vi và danh sách O-01–O-17 với trạng thái, người cần xác nhận, mốc chốt. Không tự nâng phương án đề xuất thành quyết định đã duyệt. |
| **P0.2 — Chốt mẫu mua sắm** | Thống nhất thứ tự trưởng phòng/kế toán/giám đốc/mua hàng, ngưỡng và toán tử tại biên, tiền tệ, hết ngân sách, vòng bổ sung, lý do bắt buộc, self-approval và bước kết thúc. Đối chiếu khác biệt giữa ảnh và docs. | Có sơ đồ nghiệp vụ và bảng điều kiện/hành động được xác nhận; có expected result cho dưới/bằng/trên ngưỡng, từ chối, bổ sung làm đổi ngưỡng và thiếu người duyệt. Ngưỡng minh họa không trở thành hạn mức thật. |
| **P0.3 — Chốt quyền và giao việc** | Xác định ai được tạo/sửa/publish/start/giám sát, quyền requester/người từng tham gia/Owner/Admin/Auditor; chọn direct assignment, queue/claim và chính sách đổi nhân sự, thay thế, ngoại lệ. | Có ma trận quyền chức năng + phạm vi + task, bảng 4 chiến lược assignment và chính sách self-approval. Reassign/unclaim/ủy quyền có quyết định đưa vào hoặc hoãn rõ ràng; có cách xử lý task kẹt trước pilot. |
| **P0.4 — Chốt biểu mẫu và version** | Chọn field types, JSON Schema dialect, validation library cần đánh giá, cấu trúc form chung/section hoặc nhiều form, quyền trường và chính sách nháp gặp version hết hiệu lực. | Có mẫu schema/UI schema, field ID ổn định, bảng HIDDEN/READ_ONLY/EDITABLE theo bước/action và hợp đồng form binding. Có quy tắc version pinning và xử lý nháp cũ không làm mất dữ liệu. |
| **P0.5 — Chốt chính sách nền và lập đầu vào vận hành** | Chọn session/token, vòng đời tài khoản, thông báo trong ứng dụng/email, cách tính due date/timezone, giới hạn tệp/bình luận/audit. Xác định người cung cấp quy mô tải và mục tiêu phục hồi. | Authentication được chốt trước P2.1. Có chính sách deadline trước P4.3/P6.5, notification trước P6.6, file trước P6.1; O-12/O-17 có mốc chốt trước kiểm thử tương ứng. Hạng mục hoãn được ghi rõ. |
| **P0.6 — Lập bản đồ UI và kịch bản nghiệm thu** | Chuyển 4 concept thành danh sách màn hình, route, điều hướng theo vai trò và trạng thái loading/empty/error/conflict/forbidden. Bổ sung các màn chưa có concept và nối yêu cầu vào AC-01–AC-38. | Có screen map, luồng thao tác theo từng vai trò, bảng đối chiếu concept/MVP và acceptance matrix. Mỗi kịch bản có dữ liệu đầu vào, actor, thao tác và kết quả mong đợi; giao diện không hứa tính năng ngoài phạm vi. |

**Điều kiện chuyển phase:** Đủ quy tắc để làm nền kỹ thuật và thiết kế hợp đồng. Những quyết định nghiệp vụ còn treo được gắn vào bước sẽ bị ảnh hưởng, không bị bỏ quên trong backlog chung.

## 5. Phase 1 — Nền kỹ thuật và kiểm chứng Flowable

**Mục tiêu:** Dựng môi trường Docker và kiểm chứng transaction/engine. Bộ backend đã được người dùng xác nhận đã test, không kiểm chứng lại tương thích. Các quy trình cố định ở phase này chỉ là fixture kỹ thuật, không phải cơ chế cấu hình production.

**Cập nhật đã chốt ngày 01/11/2026 (C-15–C-17):** Giữ nguyên bộ phiên bản backend trong pom; project chạy trong Docker; đang dev nên chưa dùng database migration. Các kiểm thử giao việc, rollback, concurrency và restart vẫn giữ trong phạm vi.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P1.1 — Ghi nhận bộ phiên bản đã test** | Giữ Java 25/Boot 4.1.2/Flowable 8.0.0 trong pom, không kiểm chứng lại theo C-15. Chọn/khóa frontend/bpmn-js/validator khi tích hợp; bổ sung dependency test theo nhu cầu. | Bộ backend được ghi nhận đã test theo xác nhận của người dùng; còn khóa các thư viện khác trước khi dùng. Không đánh đồng với việc đã chạy các test nghiệp vụ/transaction. |
| **P1.2 — Hoàn thiện môi trường Docker dev/test** | Dockerfile cho backend, Compose cho backend/PostgreSQL, network, cổng, named volume, health check, datasource và profile dev/test. Khóa image PostgreSQL thay `latest`, đưa cấu hình từng máy ra ngoài source. Storage S3-compatible bổ sung trước chức năng tệp; quản lý schema dev/test không dùng migration theo C-17. | Dựng môi trường theo hướng dẫn; app kết nối đúng PostgreSQL trong Docker; dữ liệu dev còn sau recreate container với volume; test cách ly dev. Có lệnh build/start/stop/log/test và cách reset có chủ đích. Không dùng H2 làm bằng chứng transaction/locking PostgreSQL. |
| **P1.3 — Tạo ranh giới module và hợp đồng API nền** | Tổ chức các module identity, organization, process, form, businessrecord, task, notification, audit; phân trách nhiệm controller/use case/policy/adapter. Chuẩn hóa DTO, lỗi, phân trang, revision, thời gian và correlation ID. | Có cấu trúc module đủ dùng, API conventions và logging nền. Quyền/luật nghiệp vụ không nằm trong controller; API không trả object nội bộ Flowable; không thêm microservice hoặc abstraction không phục vụ use case. |
| **P1.4 — Schema dev/test và kiểm tra tự động** | Cấu hình cơ chế tạo/cập nhật schema ứng dụng và engine trong dev/test; chưa dùng migration. Dựng test harness PostgreSQL trong Docker, dữ liệu test và pipeline build/test tối thiểu. | Database dev/test trống dựng lại được bằng cấu hình và seed; test không sửa/xóa DB dev. Có lệnh/pipeline kiểm tra lặp lại được; chưa thêm Flyway/Liquibase hoặc yêu cầu migration cho mỗi thay đổi schema khi dev. |
| **P1.5 — Chạy process tối thiểu qua adapter** | Tích hợp adapter deploy/start/query/complete/history với fixture Start → User Task → XOR → End. Liên kết business key và các ID ứng dụng/engine; kiểm tra cả assignee và candidate queue. | Có integration test chạy bằng Flowable thật, quan sát được task và history. Start theo engine definition ID cụ thể; lõi nghiệp vụ trao đổi DTO/kiểu ứng dụng thay vì giữ object engine. |
| **P1.6 — Chứng minh transaction, concurrency và restart** | Kiểm chứng JPA và Flowable thực sự dùng chung transaction phù hợp; cố ý gây lỗi sau cập nhật nghiệp vụ/engine. Thử claim/complete cạnh tranh và restart khi task đang chờ. | Có bằng chứng cả dữ liệu thử và engine cùng rollback, mỗi task chỉ có một kết quả chuyển bước, và sau restart tiếp tục được. Nếu thất bại phải xử lý cấu hình trước khi xây runtime nghiệp vụ. |

**Điều kiện chuyển phase:** Có môi trường chạy lại được và bằng chứng nền engine/transaction. Khung dự án hiện tại chỉ là đầu vào, không thay cho kết quả kiểm chứng này.

### Chi tiết P1.2 — đầu ra môi trường Docker dev/test

**Tiến độ ngày 01/11/2026:** Đã chuẩn bị `Dockerfile`, `.dockerignore`, `.env.example`, Compose dev/test độc lập, profile dev/test, health check và smoke test PostgreSQL/Flowable. Đã rà soát cấu hình tĩnh; **build image, khởi động stack và chạy test do người dùng thực hiện theo yêu cầu**, chưa đánh dấu P1.2 đạt. Hướng dẫn/lệnh và checklist xác nhận tại [README](../README.md). PostgreSQL 18.6 được giữ theo cấu hình người dùng đã chọn; chưa bổ sung storage.

**Cập nhật theo xác nhận người dùng:** Đã chạy xong Docker. Chưa có xác nhận riêng về test stack hoặc persistence nên chưa đánh dấu các kiểm tra này đạt. Có thể tiếp tục P1.3 — cấu trúc module/hợp đồng API nền; build/test vẫn do người dùng thực hiện.

1. **Backend trong container:** Dockerfile build/chạy đúng bộ Java trong pom; có `.dockerignore`; cấu hình ứng dụng phù hợp profile Docker, Compose bên ngoài điều phối các container.
2. **PostgreSQL:** image cố định, database/user/password theo cấu hình từng máy; app kết nối bằng tên service trong network Docker; phân biệt cổng nội bộ với cổng máy host phục vụ công cụ dev.
3. **Dữ liệu dev:** named volume có tên rõ ràng để giữ DB sau khi container được tạo lại; hướng dẫn tách thao tác dừng môi trường khỏi thao tác reset/xóa dữ liệu.
4. **Khởi động và quan sát:** health check DB, chờ dependency sẵn sàng trước khi app kết nối; log app/DB đọc được từ Compose. Docker Compose là nơi điều phối, không để app trong container tự quản lý thêm một stack Compose.
5. **Cấu hình:** `application-dev.yaml`/`application-test.yaml` hoặc profile tương đương; `.env.example` cho tham số không nhạy cảm và cách cấp cấu hình bí mật riêng không commit. Chưa dùng migration; chốt cấu hình quản lý schema dev/test trong P1.4.
6. **Test cách ly:** cấu hình PostgreSQL và Compose project/volume riêng cho test hoặc container test dùng tạm; chạy test không kết nối nhầm DB dev. Khi cần cô lập hoàn toàn, build/test runner cũng chạy trong container.
7. **Storage:** đưa dịch vụ S3-compatible, bucket và volume vào môi trường trước triển khai upload/download ở P6.1; không chặn bước backend + PostgreSQL hiện tại vì storage chưa dùng.
8. **Hướng dẫn và kiểm tra:** tài liệu lệnh build/start/stop/log/test, kiểm tra kết nối app/DB, recreate container vẫn còn dữ liệu dev, test cách ly và reset dữ liệu có chủ đích. Các lệnh là đầu ra cần bổ sung, chưa được ghi là đã chạy.

### P1.3 — Ranh giới module và hợp đồng API

**Khung ban đầu:** Theo yêu cầu người dùng, chỉ tạo file, comment, chữ ký hàm và `// TODO:`; người dùng tự viết implementation. Có tài liệu ranh giới của 8 module, khung hợp đồng dùng chung và lát cắt đọc danh mục process làm mẫu. [Hướng dẫn tự triển khai](P1.3-Manual-Implementation-Guide.md) mô tả từng file, luồng gọi, thứ tự code và kiểm tra. Chưa build/test, chưa hoàn tất implementation và chưa đánh dấu P1.3 đạt.

**Cập nhật theo yêu cầu mới nhất (C-18):** Đã bổ sung security JWT Bearer stateless do BPM tự phát bằng HS256/Nimbus; có secret dev mặc định theo yêu cầu. Repository trước đó chưa triển khai session auth. Phần nền gồm CORS allowlist, global exception handler, base response, Clock và correlation/logging. Token issuer/decoder và test source JWT thật đã có; login/refresh/logout/revoke/ACTIVE check còn thuộc P2.1. Nghiệp vụ/process/helper còn TODO vẫn do người dùng tự viết. Chưa chạy build/test, chưa đánh dấu P1.3/P2.1 đạt. Chi tiết tại [nền HTTP/security](P1.3-API-Foundation.md).

**Tiến độ code ngày 02/11/2026:** Theo yêu cầu sửa và commit P1.3, đã hoàn thiện phân trang, revision guard, DTO mapper, policy và body controller/config cho ví dụ đọc danh mục; use case điều phối qua port, không nhận actor từ client. Lỗi query/expected revision sai dùng INVALID_REQUEST, revision cũ dùng REVISION_CONFLICT; policy ném lỗi và dừng trước query khi thiếu danh tính/quyền. Có test source cho phân trang biên lớn, lỗi HTTP 400, policy/use case và mapping response; chưa chạy build/test theo AGENTS.md, chưa đánh dấu P1.3 đạt.

**Ranh giới để dành:** Controller/config process chưa kích hoạt bean/route; persistence adapter danh mục và CurrentActorProvider thật còn chờ schema/tài khoản/quyền ở các bước tương ứng. Permission `Admin`/`SubAdmin` hiện chỉ là giá trị của bài thực hành, cần chuẩn hóa RBAC tại P2.3. Đây không phải API danh mục đã nghiệm thu. Người dùng chạy stack dev/test theo README để xác nhận trước khi chốt P1.3; P1.4 tập trung schema dev/test và test harness, chưa thêm migration.

### P1.4 — Schema dev/test và kiểm tra tự động

**Tiến độ code ngày 02/11/2026:** Theo yêu cầu thực hiện cả P1.4/P1.5, đã giữ cấu
hình schema dev/test không migration và bổ sung harness `@IsolatedPostgresTest`.
Guard kiểm tra profile/URL/user trước schema và xác nhận metadata PostgreSQL của
DataSource; chỉ chấp nhận DB/user `bpm_test`. Entity/SQL seed kỹ thuật chỉ nằm trong
test source để kiểm chứng tạo schema JPA khi chưa có bảng nghiệp vụ. Đã bổ sung
workflow GitHub Actions chạy test PostgreSQL Docker, thu báo cáo và build image.
Không tạo schema nghiệp vụ hoặc tài khoản giả vào dev. **P1.4 đã được người dùng
xác nhận đạt ngày 02/11/2026:** test và build/kiểm tra dev thành công sau khi sửa
dependency Boot security test. Agent không chạy lại; chưa có kết quả GitHub Actions.
Lệnh và kết quả mong đợi tại
[hướng dẫn P1.4/P1.5](P1.4-P1.5-Verification.md).

### P1.5 — Chạy process tối thiểu qua adapter

**Tiến độ code ngày 02/11/2026:** Đã hiện thực application port/DTO và adapter
Flowable deploy/start/query/history, query task/claim/complete. Start bằng definition
ID cụ thể; business key đại diện ID hồ sơ giả lập trong test, chưa lưu mapping ứng
dụng khi chưa có bảng nghiệp vụ. Kiểu Flowable chỉ ở adapter; không bật API runtime
hoặc thay query danh mục bằng query engine. Hai fixture Start → User Task → XOR →
End kiểm tra direct assignee và candidate user/group queue; có test source cho cả
hai nhánh, claim/complete đúng người, history, version pinning và lỗi đầu vào.
**P1.5 đã được người dùng xác nhận đạt ngày 02/11/2026:** test và build/kiểm tra
dev thành công; chốt để chuyển sang P1.6. Agent không chạy lại. RBAC/assignment nghiệp vụ ở P2/
Phase 5; rollback JPA + engine, concurrency và restart vẫn thuộc P1.6. Chi tiết và
lệnh nghiệm thu tại [hướng dẫn P1.4/P1.5](P1.4-P1.5-Verification.md).

### P1.6 — Transaction, concurrency và restart

**Tiến độ code ngày 02/11/2026:** Đã bổ sung kiểm tra wiring cùng JPA transaction
manager/DataSource; test rollback sau JPA flush + start/claim/complete và khi engine
lỗi. Hai worker có transaction/connection PostgreSQL riêng kiểm tra claim/complete
cạnh tranh, chỉ kết quả thắng và bản ghi JPA tương ứng được commit. Stack restart
riêng giữ named volume test; script chạy hai JVM prepare/resume, recreate DB và
runner giữa hai lượt, kiểm tra direct assignee, queue chưa claim/đã claim và tiếp
tục hoàn thành task/history. Fixture/entity chỉ trong test, không thêm API runtime
hoặc schema nghiệp vụ vào dev.

**P1.6 đã chốt ngày 02/11/2026 theo xác nhận của người dùng:** P1.6 đã build/test
thành công, chuyển sang Phase 2; bước tiếp theo là P2.1 — đăng nhập và vòng đời JWT.
Agent không chạy lại build/test; chưa có kết quả GitHub Actions. Các lệnh và tiêu
chí kiểm chứng được giữ tại [hướng dẫn P1.6](P1.6-Verification.md) để chạy lại khi cần.

## 6. Phase 2 — Danh tính, tổ chức, phân quyền và khung UI

**Mục tiêu:** Có nền quyền và giao diện nhất quán trước khi thêm designer và hồ sơ.

**P2.1a–c đã được xác nhận hoàn thành ngày 03/11/2026:** [Hướng dẫn đăng nhập và vòng đời JWT](P2.1-Manual-Implementation-Guide.md) ghi hợp đồng endpoint, token, refresh rotation/replay, password policy, code map và checklist kiểm chứng. P2.1c đã nối login, JWT `sid`/`ver`, identity lookup từ DB, `/me` và gate mật khẩu tạm. P2.1d–e đã có implementation/test source cho refresh/logout, `change-password`, reset/khóa/mở khóa nội bộ và thu hồi toàn bộ grant. P2.1f đã bổ sung integration-test source và [biên bản kiểm chứng](P2.1-Verification.md). **P2.1d–f chưa chạy build/test/dev, chờ người dùng kiểm tra nên chưa chốt mốc.** Toàn bộ code/cấu hình/test bootstrap tài khoản dev đã bị xóa; API quản trị tạo tài khoản thuộc P2.2. Chưa nghiệm thu toàn P2.1.

**Business model/schema ngày 03/11/2026:** Đã bổ sung model JPA cho 21 bảng nghiệp vụ, chú thích tiếng Việt và test source. Người dùng xác nhận build, test và dev thành công; chốt bước model/schema. Agent không chạy lại. Xem [mô hình dữ liệu](Business-Data-Model.md); các use case/API P2.1 và các phase sau vẫn triển khai riêng.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P2.1 — Đăng nhập và vòng đời JWT** | Xây API login/refresh/logout, danh tính hiện tại và thu hồi token theo C-18. Dùng token issuer/decoder nền đã có; kiểm tra ACTIVE/INACTIVE, mật khẩu tạm, trạng thái/quyền hiện tại và cơ chế chặn token cũ. Không dựng HttpSession security. | Có API xác thực để nối màn đăng nhập P2.5; token hết hạn, logout hoặc tài khoản bị khóa không tiếp tục thao tác. Không coi xóa token ở UI là server revoke. Mật khẩu/secret không trả về; token chỉ trả ở auth response được phép và không xuất hiện trong log/API danh sách. |
| **P2.2 — Người dùng và cơ cấu tổ chức** | Xây users, cây phòng ban, phòng ban chính, trưởng phòng, chức danh, role membership và trạng thái hoạt động. Chặn vòng cây và thay đổi dữ liệu phá tham chiếu lịch sử. | Có schema dev/test, API CRUD có validation và dữ liệu test nhiều phòng ban. ID ổn định; không xóa cứng danh tính đã được tham chiếu; xác định được requester manager hoặc lý do không xác định được. |
| **P2.3 — Policy phân quyền dùng chung** | Hiện thực RBAC và policy kiểm tra scope, quan hệ hồ sơ/task, trạng thái tài khoản. Xác định cách lọc danh sách và cắt dữ liệu nhạy cảm; bổ sung từng resource khi module tương ứng xuất hiện. | Có dịch vụ quyền dùng chung và test ma trận cho Employee/Designer/Owner/Admin/Auditor. Deny by default; Admin/Auditor không tự có quyền duyệt; API quản trị bị chặn với người không đủ quyền. |
| **P2.4 — Bộ phân giải assignment** | Hiện thực SPECIFIC_USER, DEPARTMENT, ROLE và REQUESTER_MANAGER theo P0.3; kiểm tra ACTIVE, scope và self-approval. Định nghĩa kết quả resolve thành assignee/candidates hoặc ngoại lệ có lý do. | Có bộ policy và test cho đủ 4 chiến lược, thiếu trưởng phòng, nhóm rỗng, user inactive và đổi nhân sự. Giao tiếp với runtime qua dữ liệu ứng dụng; không bỏ qua bước khi resolve thất bại. |
| **P2.5 — Design tokens và khung frontend** | Dựng React + TypeScript theo phương án cơ sở; tạo màu, typography, khoảng cách, button/input/table/badge, header/sidebar, route và màn đăng nhập. Kết nối API client, lỗi phiên, loading/empty/error và định dạng thời gian. | Có ứng dụng đăng nhập được với shell gần hướng concept: nền sáng, xanh chủ đạo, cấp bậc nội dung rõ. Menu theo quyền, bàn phím/focus dùng được; kích thước màn hình tối thiểu được ghi nhận; không có đổi tenant. |
| **P2.6 — Màn quản trị và kiểm tra quyền xuyên UI/API** | Hoàn thiện trang tài khoản, cây phòng ban, trưởng phòng, vai trò/quyền và hồ sơ người dùng trong phạm vi đã chốt. Hiển thị lỗi thiếu quyền và xung đột cập nhật rõ ràng. | Người quản trị đủ quyền cấu hình được tổ chức test; nhân viên không truy cập được dữ liệu/chức năng bằng route hoặc API trực tiếp. Thay đổi quản trị quan trọng có audit actor/thời điểm/nội dung. |

**Điều kiện chuyển phase:** Đăng nhập, tổ chức, policy và shell hoạt động với API thật. Nền authorization phải được tái sử dụng cho form/process/record/task ở các phase sau.

**Tiến độ P2.2 ngày 03/11/2026:** P2.2a–d đã có contract, domain/application/persistence, API/Postman và test source. Xem [hướng dẫn](P2.2-Organization-Implementation-Guide.md) và [biên bản kiểm chứng](P2.2-Verification.md). **Toàn bộ P2.2 chưa chạy build/test/dev; chưa nghiệm thu.**

**Tiến độ P2.3 ngày 03/11/2026:** P2.3a–d đã có contract, shared policy, RBAC persistence/API, Postman và test source. Xem [hướng dẫn](P2.3-Authorization-Implementation-Guide.md) và [biên bản](P2.3-Verification.md). **Toàn bộ P2.3 chưa chạy build/test/dev; chưa nghiệm thu.**

**Tiến độ P2.4 ngày 03/11/2026:** P2.4a–d đã có contract, resolver Java thuần, directory adapter identity/organization và test source. Xem [hướng dẫn](P2.4-Assignment-Resolver-Implementation-Guide.md) và [biên bản](P2.4-Verification.md). **Toàn bộ P2.4 chưa chạy build/test/dev; chưa nghiệm thu.**

**Tiến độ P2.5 ngày 03/11/2026:** frontend đã có shell, auth/session, state nền và đã chuyển sang source component gốc shadcn/ui. Build/lint/test/browser/backend thật chưa chạy; chưa nghiệm thu.

**Tiến độ P2.6 ngày 03/11/2026:** đã có source màn quản trị tài khoản/hồ sơ, role/permission, phòng ban/trưởng phòng/chức danh; route nối permission và API P2.2/P2.3. Backend đã có recorder audit transaction cho thay đổi quản trị và test source. Xem [hướng dẫn](P2.6-Administration-Implementation-Guide.md) và [biên bản](P2.6-Verification.md). **Toàn bộ P2.6 chưa chạy build/test/dev/Postman/browser; chưa nghiệm thu.**

## 7. Phase 3 — Nền tảng biểu mẫu động

**Mục tiêu:** Tạo form bằng cấu hình, render được trên UI và kiểm tra được ở server; form version có thể được workflow tham chiếu an toàn.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P3.1 — Mô hình và hợp đồng form** | Xây form definitions/versions, schema, UI schema và các field type đã chốt. Xác định tham chiếu attachment, số tiền chính xác, ngày và field ID; tách schema dữ liệu khỏi quyền theo bước. | Có schema dev/test, DTO/API và schema mẫu mua sắm. Hợp đồng FE/BE thống nhất kiểu dữ liệu; không lưu hai nguồn quyền trường mâu thuẫn trong form và step config. |
| **P3.2 — Validation và quyền trường phía server** | Kiểm tra kiểu, required, giới hạn giá trị và điều kiện theo action. Áp dụng HIDDEN/READ_ONLY/EDITABLE cho read và patch; ghép patch vào dữ liệu hiện có trước khi validate theo ngữ cảnh. | Có validator/policy dùng chung và test field tampering. Reject trường không được cấp, không trả trường HIDDEN, không mất dữ liệu bước trước khi chỉ cập nhật một phần form. |
| **P3.3 — Form renderer** | Xây bộ hiển thị form từ schema/UI schema, hỗ trợ mode tạo nháp, xử lý task và chỉ đọc. Hiển thị required, lỗi theo field, dữ liệu cũ và quyền trường do backend cấp. | Một schema render được ở nhiều ngữ cảnh mà không viết màn riêng theo loại đơn. UI và server thống nhất format; lỗi validation hiển thị đúng field; READ_ONLY không trở thành quyền ghi. |
| **P3.4 — Form builder cơ bản** | Cho người thiết kế thêm/sắp xếp/xóa trường, sửa nhãn, kiểu, lựa chọn, required, validation và bố cục trong tập hỗ trợ. Cung cấp preview bằng renderer thật. | Lưu/mở lại form nháp và preview không mất cấu hình. Field ID không đổi khi chỉ đổi nhãn; schema sai hoặc tham chiếu field bị xóa có lỗi rõ ràng. Không mặc định hỗ trợ mọi tính năng JSON Schema. |
| **P3.5 — Công bố và bất biến form version** | Xây save draft có revision, validate, publish và nhân bản thành nháp mới. Chặn sửa bản công bố và kiểm soát thao tác đồng thời. | Có form version bất biến dùng được cho bindings. Hai người lưu revision cũ nhận conflict; retry publish không tạo bản công bố trùng; schema của hồ sơ cũ không bị thay tại chỗ. |
| **P3.6 — Quản lý biểu mẫu và gắn thử ngữ cảnh bước** | Làm danh sách/tìm/lọc/chi tiết/lịch sử phiên bản form, quyền quản lý và chọn form version. Dùng fixture ngữ cảnh requester/kế toán để kiểm tra read/write trước khi nối designer. | Người đủ quyền tạo và công bố form mua sắm; preview thấy đúng trường theo ngữ cảnh. API lựa chọn binding không trả form draft hoặc form ngoài phạm vi quyền. |

**Cập nhật P3.6 ngày 04/11/2026:** Đã triển khai backend/frontend/Postman, danh mục và lịch sử phân trang, lựa chọn ACTIVE/PUBLISHED theo quyền `process.design`, preview fixture requester/kế toán với quyền trường từ backend. 94 backend form tests, 78 frontend tests và ba Postman lint đạt; Chrome smoke nối API thật với PostgreSQL test đạt. Xem [hợp đồng và bằng chứng P3.6](P3.6-Form-Management.md). Binding lưu vào process version thuộc P4; chưa coi các phase tiếp theo hoặc UAT đã hoàn thành.

**Điều kiện chuyển phase:** Có một form version mua sắm hợp lệ, renderer tái sử dụng và server validation. Binding thật vào BPMN được hoàn thiện ở Phase 4.

**Tiến độ P3.1 ngày 04/11/2026:** Đã bổ sung hợp đồng `bpm-form-v1`, domain/application/persistence/API tạo danh mục kèm draft version 1 và đọc version; fixture mua sắm và Postman. Tái sử dụng hai bảng form hiện có. **8 kiểm thử P3.1 đạt trên Docker/PostgreSQL test cách ly; Postman collection/environment/workspace lint không error/warning.** Xem [hợp đồng](P3.1-Form-Contract.md) và [biên bản](P3.1-Verification.md). Chưa chạy full regression/dev smoke; server validation dữ liệu/action và quyền trường tiếp tục tại P3.2.

**Tiến độ P3.2 ngày 04/11/2026:** Đã có validator 10 kiểu trường, policy HIDDEN/READ_ONLY/EDITABLE, service lọc read view và merge/validate patch theo action, required theo bước và lý do từ chối/bổ sung. **74 kiểm thử đạt trên Docker/PostgreSQL test cách ly (66 mới P3.2 + 8 hồi quy P3.1), không failure/error/skipped.** Xem [hướng dẫn](P3.2-Form-Validation-Guide.md) và [biên bản](P3.2-Verification.md). Đây là service nội bộ để nối renderer/record/task; resource authorization, runtime persistence và attachment ownership tiếp tục ở các bước tích hợp tương ứng.

**Tiến độ P3.3 ngày 04/11/2026:** Frontend đã có renderer dùng chung 10 kiểu trường, ba mode nháp/task/chỉ xem, required/lỗi theo field và quyền backend cấp; patch chỉ gồm trường EDITABLE thay đổi, giữ số chính xác và hỗ trợ adapter attachment. `/admin/forms` mở fixture preview cho `form.design`. Build/lint đạt (warning cũ); **47 test frontend đạt**, đã kiểm tra tương tác Chrome với fixture. Xem `bpm-frontend/docs/P3.3-Form-Renderer.md` và `P3.3-Verification.md`. API runtime hồ sơ/task và tệp được nối ở P5/P6; chưa kiểm tra mobile/full regression backend.

**Tiến độ P3.4 ngày 04/11/2026:** Đã có builder 10 kiểu trường, bố cục nhóm, ID ổn định, validation cấu hình và preview renderer P3.3. Bổ sung PUT draft có revision, chặn sửa PUBLISHED/RETIRED và audit transaction để lưu/mở lại qua API thật; publish/clone vẫn thuộc P3.5. **68 test frontend, 75 test backend form đạt; Postman 3 loại lint đạt; Chrome kiểm tra harness local.** Xem [hướng dẫn/biên bản backend](P3.4-Form-Builder.md) và `bpm-frontend/docs/P3.4-Verification.md`. Chưa smoke frontend → backend dev hoặc kiểm tra đồng thời/full regression.

**Tiến độ P3.5 ngày 04/11/2026:** Đã có validate/publish/clone, khóa definition + revision, biên nhận Idempotency-Key cùng transaction/audit và guard bất biến published. Frontend có xác nhận công bố, editor chỉ xem, clone nháp mới và permission độc lập. **87 backend form tests, 74 frontend tests đạt; Postman 3 loại lint đạt; Chrome kiểm tra retry mất phản hồi và clone độc lập qua harness.** Xem [hợp đồng/kiểm chứng](P3.5-Form-Version-Lifecycle.md) và `bpm-frontend/docs/P3.5-Form-Version-Lifecycle.md`. Chưa full dev smoke/restart-persistence/full regression toàn repo.

## 8. Phase 4 — Workflow Designer, validate và quản lý phiên bản

**Mục tiêu:** Người có quyền tự thiết kế và công bố một quy trình chạy được trong tập BPMN MVP.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P4.1 — Danh mục và dữ liệu cấu hình quy trình** | Xây process definitions/versions, key, Owner, quyền design/publish/start/monitor, XML, step config và form bindings. Tách business version khỏi engine version. | Có schema dev/test và API cho danh mục, tạo nháp, tải cấu hình theo version, danh sách version. Process key duy nhất; người không có quyền không đọc được metadata quản trị qua ID/danh sách. |
| **P4.2 — Canvas bpmn-js giới hạn MVP** | Tích hợp modeler trong bố cục concept: palette, canvas, property panel; cung cấp Start/User Task/XOR/End/Sequence Flow, zoom, undo/redo. Hạn chế cả palette, context pad và thao tác tạo/thay node. | Vẽ, lưu và mở lại được BPMN chuẩn với DI. Không tạo được node ngoài phạm vi qua các thao tác UI thông thường; chưa coi giới hạn UI là validation bảo mật. |
| **P4.3 — Property panel nghiệp vụ** | Xây cấu hình tên/mô tả bước, 4 chiến lược phân công, action, form version, quyền trường và hạn xử lý. Cấu hình XOR bằng field/toán tử/giá trị có kiểu và outcome hợp lệ. | Có metadata keyed theo element ID và giao diện kiểm tra tham chiếu. Người thiết kế không nhập script/class/bean/expression tùy ý; đường đi APPROVE/REJECT/REQUEST_CHANGES thể hiện đúng trong BPMN. |
| **P4.4 — Bảo toàn XML, metadata và bố cục** | Đăng ký moddle extensions được hỗ trợ; quy định nguồn có thẩm quyền cho từng thuộc tính và cách tạo artifact triển khai. Xử lý đổi tên, xóa/thêm node, binding và round-trip. | Test load → edit → save → reload giữ element IDs, DI, assignment/action/form metadata. Node bị xóa không để config mồ côi; artifact cuối nhất quán, không thêm DSL/graph điều phối thứ hai. |
| **P4.5 — Validator server và XML an toàn** | Validate cấu trúc, tập BPMN/extension/expression cho phép, đường đi, vòng có wait state/đường thoát, gateway, assignment và form binding. Chặn DTD/external entity, tài nguyên ngoài và model vượt giới hạn. | API trả lỗi theo element ID/field; dữ liệu gửi trực tiếp bỏ qua UI vẫn bị kiểm tra. Model lỗi và XML nguy hiểm không đến engine; chính XML cuối sẽ deploy được validate. |
| **P4.6 — Lưu nháp và trải nghiệm kiểm tra lỗi** | Nối save/load/revision với canvas và panel, hiển thị trạng thái chưa lưu, lỗi validation và conflict. Cho lưu mô hình nháp chưa hoàn chỉnh trong giới hạn an toàn nhưng chặn publish. | Nháp tải lại đúng; chọn lỗi đưa người thiết kế tới bước tương ứng. Save revision cũ không ghi đè âm thầm; nút Kiểm tra gọi validator, không ngầm chạy mô phỏng quy trình. |
| **P4.7 — Publish nguyên tử và chống lặp** | Kiểm tra quyền/revision, validate artifact, deploy Flowable và đóng băng XML + metadata + form bindings + tham số trong transaction. Lưu checksum, engine deployment/definition/version mapping và audit. | Publish thành công tạo đúng một version/deployment cho cùng request; failure rollback cả mapping/trạng thái/engine. Người thiếu quyền bị chặn; artifact PUBLISHED không sửa được tại chỗ. |
| **P4.8 — Quản lý quy trình và vòng đời version** | Dựng trang theo concept quản lý: tìm/lọc, tab nháp/công bố/ngừng khởi tạo, chi tiết, tạo nháp kế tiếp và nhân bản trong tập hỗ trợ. Quản lý version được phép start và retire có audit. | Có danh mục riêng theo quyền khởi tạo/quản lý; sửa published tạo draft mới. Retire chặn start mới, không suspend/xóa instance cũ; tên tab không khiến người dùng hiểu nhầm là đã xóa lịch sử. |

**Cập nhật P4.1 ngày 04/11/2026:** Đã nối hai bảng process hiện có với API danh mục theo scope, tạo definition + draft v1 + audit nguyên tử, metadata, lịch sử phân trang và đọc cấu hình đúng version. Bảo vệ cấu hình bằng `process.design`/`process.publish`; danh mục start chỉ có ACTIVE/PUBLISHED và không lộ XML/metadata quản trị. **114 test đạt** (10 tích hợp P4.1 + 10 process hồi quy + 94 form), ba Postman lint đạt 0 error/warning. Xem [hợp đồng và kiểm chứng P4.1](P4.1-Process-Catalog.md). Chưa có canvas/save/publish/lifecycle P4.2–P4.8; chưa chạy full regression/dev smoke/UAT.

**Điều kiện chuyển phase:** Publish được mẫu mua sắm bằng designer, giữ đầy đủ form bindings và artifact. Có bằng chứng chặn model sai, xử lý conflict và không publish trùng.

## 9. Phase 5 — Hồ sơ, inbox và xử lý công việc xuyên suốt

**Mục tiêu:** Có luồng người dùng thật từ chọn quy trình đến hoàn tất/từ chối/bổ sung. Chưa cần đủ dashboard và các khối cộng tác để chứng minh luồng này.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P5.1 — Tạo và quản lý hồ sơ nháp** | Xây business record, business key, dữ liệu JSONB, revision, trạng thái và trang tạo/sửa nháp bằng renderer. Xây Hồ sơ của tôi với phân trang/tìm/lọc theo quyền. | Nháp chưa có engine instance; requester do server gán. Chủ hồ sơ lưu/mở lại được; người ngoài phạm vi không đọc/sửa được. Nháp pin schema/version và không tự mất dữ liệu khi version đó ngừng start. |
| **P5.2 — Submit và khởi tạo đúng version** | Validate dữ liệu/quyền khởi tạo/revision, xác nhận version startable, snapshot dữ liệu cần thiết và start bằng engine definition ID đã pin. Áp dụng idempotency và ràng buộc mỗi hồ sơ một instance. | Submit tạo một hồ sơ đang chạy gắn đúng process/form version. Double-click/retry hoặc hai request khác khóa cùng nộp một nháp không tạo instance thứ hai; lỗi trước commit không để kết quả nửa chừng. |
| **P5.3 — Assignment thật và ngoại lệ runtime** | Nối policy P2.4 vào lúc engine tạo task bằng hook nội bộ được kiểm soát. Kiểm tra thành viên/scope, trạng thái user và chính sách snapshot/dynamic; ghi nhận ngoại lệ phân công với quyền bảo vệ. | Task có assignee hoặc candidate hợp lệ; nếu không resolve được thì vẫn chờ, có lý do/actor hệ thống và hiển thị cho người hỗ trợ đủ quyền. Không tự complete, không cho toàn công ty claim. |
| **P5.4 — Inbox và claim** | Xây API và UI Việc của tôi/Việc có thể nhận, task detail, phân trang/lọc và claim. Kiểm tra candidate hiện tại trên backend và xử lý hai người nhận đồng thời. | Task được giao/đã claim nằm đúng inbox; chỉ candidate hợp lệ claim được. Khi cạnh tranh chỉ một assignee thắng; UI còn lại nhận conflict và tải lại dữ liệu. Candidate chưa claim không complete được. |
| **P5.5 — Complete task nguyên tử** | Xây use case đọc lại state/revision, kiểm tra assignee/action/quyền trường, patch form, cập nhật variables, ghi task action log, complete engine và đồng bộ record outcome/audit trong một transaction. | Một chuyển bước hợp lệ chỉ có một kết quả và một bộ log thành công; retry cùng khóa trả kết quả cũ, đổi payload cùng khóa bị conflict. API không nhận actor/assignee/trạng thái cuối tùy ý từ client. |
| **P5.6 — Từ chối và vòng bổ sung** | Nối APPROVE/REJECT/REQUEST_CHANGES/RESUBMIT/COMPLETE vào BPMN. Giao bổ sung cho requester đã xác thực, yêu cầu lý do theo chính sách, reset/quản lý decision đúng từng lượt và đồng bộ dữ liệu ảnh hưởng nhánh. | REJECT kết thúc đúng outcome và hết task hoạt động. Bổ sung giữ cùng instance, lưu từng lượt, duyệt lại theo chính sách; sửa amount có thể đổi nhánh mà không dùng nhầm quyết định cũ. |
| **P5.7 — Tích hợp quy trình mua sắm đủ nhánh** | Dùng cấu hình đã publish để chạy các vai trò requester/trưởng phòng/kế toán/giám đốc/mua hàng. Kiểm tra biên ngưỡng, ngân sách, self-approval, thay đổi nhân sự và thiếu người duyệt theo kết luận Phase 0. | Có bộ kịch bản end-to-end và dữ liệu kiểm tra cho dưới/bằng/trên ngưỡng, reject và bổ sung. Luật riêng của mẫu nằm trong cấu hình/policy đã xác định, không tạo endpoint duyệt mua sắm riêng. |
| **P5.8 — Màn chi tiết và hành động tối thiểu** | Ghép form được lọc quyền, trạng thái hồ sơ, task/người xử lý hiện tại và nút hành động hợp lệ. Có modal lý do, phản hồi thành công/conflict và refresh dữ liệu sau hành động. | Người dùng thực hiện được toàn luồng mua sắm trên UI có API thật. Requester không sửa tự do sau submit; người chỉ xem không có quyền xử lý qua API; APPROVE một bước không hiển thị cả hồ sơ đã hoàn tất. |

**Điều kiện chuyển phase:** Luồng mua sắm chạy được từ giao diện qua đủ nhánh đã thống nhất; authorization, idempotency và rollback đã có kiểm thử ngay trong runtime.

## 10. Phase 6 — Tệp, cộng tác, tiến trình và màn chi tiết hoàn chỉnh

**Mục tiêu:** Hoàn thiện trải nghiệm concept chi tiết yêu cầu trên dữ liệu engine và nghiệp vụ thật.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P6.1 — Tệp đính kèm có kiểm soát** | Xây storage adapter và metadata attachment, upload tạm/xác nhận liên kết, download kiểm tra quyền, giới hạn loại/kích thước và dọn tệp mồ côi. Chốt xử lý tệp ở nháp/bổ sung theo quyền trường. | Upload/download được trong form và chi tiết; bucket không public, object key không dựa vào tên gốc. Đoán ID/URL không vượt quyền; DB rollback hoặc storage lỗi không làm file được coi đã liên kết thành công sai thực tế. |
| **P6.2 — Bình luận theo hồ sơ** | Xây API và khối trao đổi, phân trang/thứ tự, actor từ JWT đã xác thực/trạng thái tài khoản hiện tại và xử lý nội dung an toàn. Chỉ cung cấp sửa/xóa nếu chính sách đã chọn cho phép. | Người có quyền đọc/trao đổi trên hồ sơ dùng được; tải lại trang vẫn còn nội dung. Không giả tác giả qua payload, không đọc bình luận ngoài scope; nội dung hiển thị không thực thi mã. |
| **P6.3 — Timeline nghiệp vụ và history** | Tổng hợp action log/audit/engine history thành tiến trình đọc được: ai, lúc nào, action, lý do và thay đổi dữ liệu được phép xem. Phân biệt task ID với activity ID. | Có API và timeline thể hiện từng lượt duyệt/bổ sung, cả hành động hệ thống. Một activity chạy lại không bị gộp mất lượt; history không lộ payload/trường ngoài quyền. |
| **P6.4 — Viewer của đúng phiên bản instance** | Tích hợp bpmn-js viewer chỉ đọc; tải artifact của process version hồ sơ đang chạy và overlay runtime/history theo IDs. Biểu diễn bước hiện tại, đã qua và các nhánh chưa diễn ra có chú giải. | Hồ sơ v1 vẫn xem sơ đồ v1 sau khi publish v2. Node đang hoạt động lại sau bổ sung hiển thị đúng; bước tương lai không bị trình bày như task đã tồn tại hoặc deadline đã cam kết. |
| **P6.5 — Due date và quá hạn** | Tính deadline từ chính sách ngày lịch/ngày làm việc/timezone đã chốt, lưu vào task phù hợp, bổ sung badge và lọc quá hạn. Kiểm tra mốc biên giờ/ngày nghỉ nếu thuộc chính sách. | Inbox/chi tiết hiển thị cùng một deadline đúng timezone. Task quá hạn được đánh dấu và lọc chính xác; quá hạn không tự approve/reject/complete hoặc tự sinh escalation ngoài scope. |
| **P6.6 — Thông báo trong ứng dụng** | Tạo notification task mới, yêu cầu bổ sung, từ chối và hoàn tất trong transaction phù hợp; xây danh sách, badge chưa đọc, đánh dấu đã đọc và liên kết tới hồ sơ/task. Email/outbox chỉ làm nếu được chọn. | Chỉ người nhận được đọc/đánh dấu thông báo của mình. Rollback không để thông báo thành công; retry không tạo thông báo nghiệp vụ trùng; mở link vẫn kiểm tra lại quyền hiện tại. |
| **P6.7 — Hoàn thiện màn chi tiết theo concept** | Tổ chức thông tin, form, tệp, trao đổi, timeline, người xử lý, deadline, viewer và thanh hành động thành bố cục thống nhất. Tối ưu bảng/khối nội dung dài, focus và thao tác trên kích thước màn hình đã chọn. | Màn chi tiết sát hướng concept nhưng phản ánh đúng quyền/state. Không có nút giả; trạng thái trống/lỗi/tải dữ liệu và hồ sơ kết thúc đều có UI rõ ràng; thanh hành động không che nội dung thiết yếu. |

**Điều kiện chuyển phase:** Người tạo và người xử lý biết hồ sơ ở đâu, ai đang giữ, vì sao bị trả/từ chối và có thể làm gì tiếp theo; tệp, trao đổi, lịch sử tuân cùng phạm vi quyền.

## 11. Phase 7 — Tổng quan, giám sát và chứng minh khả năng tái sử dụng

**Mục tiêu:** Có trải nghiệm vận hành hằng ngày cho nhân viên, Owner và Auditor; chứng minh nền tảng không chỉ phục vụ mua sắm.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P7.1 — Định nghĩa số liệu và API tổng hợp** | Chốt cách đếm hồ sơ đang chạy, việc chờ, cần bổ sung, quá hạn và các chỉ số cơ bản được giữ từ concept. Xác định bộ lọc thời gian, scope và mẫu số nếu hiển thị tỷ lệ. | Có định nghĩa metric và API trả dữ liệu thật trong phạm vi người xem. Đối chiếu được số trên dashboard với danh sách nguồn; không lộ tổng số của phòng ban ngoài quyền. |
| **P7.2 — Dashboard tổng quan** | Dựng màn theo concept tổng quan: việc cần làm, hồ sơ của tôi, quy trình được phép khởi tạo và yêu cầu gần đây. Chỉ thêm biểu đồ/tỷ lệ có định nghĩa và dữ liệu đủ; liên kết từng khối vào danh sách tương ứng. | Dashboard dùng API thật, có loading/empty/error và hành động mở task/tạo hồ sơ. Không giữ số demo, ưu tiên hoặc biểu đồ chưa có logic nghiệp vụ; mỗi chỉ số truy được danh sách phù hợp. |
| **P7.3 — Giám sát Owner và xử lý ngoại lệ** | Làm danh sách hồ sơ theo quy trình được giao, lọc trạng thái/người xử lý/quá hạn và danh sách ngoại lệ phân công. Thêm reassign có lý do nếu được chốt; kiểm soát người nhận mới và phiên bản quyền hiện tại. | Owner chỉ thấy quy trình thuộc phạm vi. Người được cấp quyền xử lý được task kẹt theo quy trình hỗ trợ đã chọn; thao tác có actor, lý do, assignee cũ/mới và không mạo danh người phê duyệt. |
| **P7.4 — Tra cứu audit theo quyền** | Dựng màn tra cứu và API audit với bộ lọc actor/entity/action/thời gian; cho xem chi tiết phù hợp mức nhạy cảm. Kiểm tra audit đã được phát sinh ở các module trước. | Auditor/nhân sự hỗ trợ đủ quyền tra được sự kiện theo scope. Không có thao tác sửa/xóa qua quyền ứng dụng thông thường; audit xem được không đồng nghĩa được claim/complete task. |
| **P7.5 — Cấu hình workflow thứ hai** | Tạo nghỉ phép đơn giản bằng cùng form builder/designer: gửi → trưởng phòng duyệt → hoàn tất/từ chối/bổ sung. Dùng nhân sự/quyền/notification/tracking chung; không thêm tính số dư phép nếu ngoài scope. | Tạo và chạy được workflow thứ hai chỉ bằng tính năng cấu hình đã xây. Không thêm bảng runtime, engine, state machine hoặc endpoint duyệt riêng cho nghỉ phép; có kịch bản xác nhận AC-37. |

**Điều kiện chuyển phase:** Các vai trò dùng được sản phẩm hằng ngày; có hai loại quy trình chạy trên cùng nền tảng và số liệu tổng quan nhất quán với dữ liệu được phép xem.

## 12. Phase 8 — Kiểm thử tích hợp, lỗi cạnh tranh và hoàn thiện MVP

**Mục tiêu:** Kiểm tra các phần đã tích hợp trên cấu hình gần môi trường pilot, sửa lỗi và có bằng chứng nghiệm thu. Các bài kiểm tra đơn vị/tích hợp đã có từ phase trước được tái sử dụng.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P8.1 — Kiểm tra quyền trên toàn bộ bề mặt truy cập** | Chạy ma trận vai trò/scope cho list, detail, search/filter, form fields, files, comments, history, audit, notification, design/publish và task actions. Thử gọi trực tiếp bằng ID và payload sửa actor/assignee/state. | Có báo cáo positive/negative cases; không có đường API vượt quyền hoặc rò metadata. Inactive, requester, candidate chưa claim, Owner/Admin/Auditor đều bị giới hạn đúng ngữ cảnh. |
| **P8.2 — Retry, đồng thời và mất response** | Chạy request đồng thời cho save/publish/submit/claim/complete; thử cùng khóa khác payload, khóa khác cùng task, dữ liệu revision cũ và response mất sau commit. Kiểm tra cả DB/engine/action logs. | Mỗi thao tác hợp lệ có đúng một kết quả; request thua có conflict hoặc kết quả retry phù hợp. Không có deployment/instance/task kế tiếp/log thành công trùng, không ghi đè nháp/hồ sơ âm thầm. |
| **P8.3 — Vòng đời version và thay đổi tổ chức** | Chạy v1 rồi publish/start v2, retire v1, đổi form/ngưỡng, nộp nháp cũ và đổi trưởng phòng/phòng ban/user status khi đang chờ. Kiểm tra vòng bổ sung lặp nhiều lần. | Hồ sơ cũ pin đúng artifact/form/tham số; retire không dừng instance. Task cũ/mới theo chính sách nhân sự; timeline và viewer đúng từng lượt, dữ liệu cũ không bị diễn giải lại bởi schema mới. |
| **P8.4 — Sự cố và dữ liệu không hợp lệ** | Kiểm tra XML ngoài allowlist/DTD/expression nguy hiểm, lỗi ghi DB hoặc engine, lỗi storage/upload, lỗi thông báo và restart với hồ sơ đang chạy. Dùng fault injection ở ranh giới có thể gây kết quả một phần. | Publish/submit/complete rollback đúng phạm vi; không có thông báo thành công giả, tệp mồ côi được xử lý theo thiết kế. Sau restart xử lý tiếp được; lỗi có correlation ID và hướng khắc phục, không lộ stack trace/secrets. |
| **P8.5 — Hiệu năng theo quy mô đã chốt** | Chạy kịch bản tải và kích thước dữ liệu O-17 trên danh sách/dashboard/inbox, submit/claim/complete, history và file. Đo truy vấn, index, phân trang, pool và giới hạn tài nguyên khi cần. | Có workload, môi trường, chỉ tiêu và kết quả đo tái lập được. Sửa điểm nghẽn có bằng chứng; không giảm authorization, độ chính xác dữ liệu hoặc chống xử lý lặp để đạt tốc độ. |
| **P8.6 — E2E, kiểm tra UI và gói nghiệm thu** | Chạy designer → publish → tạo hồ sơ → claim/xử lý → tracking qua các vai trò; reload giữa luồng. Đối chiếu 4 concept, các màn bổ sung và loading/empty/error/conflict. Tổng hợp AC-01–AC-38 với bằng chứng và lỗi còn lại. | Có báo cáo E2E/UI, checklist acceptance và bản build ứng viên. Không còn lỗi chặn nghiệp vụ, vượt quyền, mất dữ liệu, xử lý lặp hoặc sai version đã biết; tiêu chí chỉ đạt ở pilot không bị đánh dấu sớm. |

**Điều kiện chuyển phase:** Có bản build đủ điều kiện UAT, báo cáo kiểm tra và danh sách giới hạn đã thống nhất. Hoàn thành giao diện không thay cho đạt acceptance criteria.

## 13. Phase 9 — Vận hành, UAT và pilot

**Mục tiêu:** Chứng minh hệ thống có thể được sử dụng và hỗ trợ trong điều kiện doanh nghiệp thực tế trước khi mở rộng.

| Bước | Mô tả việc cần làm | Yêu cầu đầu ra |
| --- | --- | --- |
| **P9.1 — Chuẩn bị môi trường pilot và runbook** | Thiết lập cấu hình môi trường, secrets, TLS, quyền DB/storage, migration, health checks, logging và theo dõi lỗi/ngoại lệ/quá hạn. Mô tả triển khai, restart và phục hồi bản ứng dụng tương thích dữ liệu. | Có môi trường pilot và runbook lặp lại được; artifact/dependency được khóa. Không public engine admin/DB/bucket; người hỗ trợ có đúng quyền và biết cách truy vết bằng correlation ID. |
| **P9.2 — Backup và diễn tập restore** | Chốt RPO/RTO/retention, backup DB và file theo bộ dữ liệu tương ứng. Restore thử vào môi trường cách ly, mở hồ sơ/tệp và tiếp tục task; ghi nhận thời gian và dữ liệu có thể mất. | Có bản backup kiểm tra được, biên bản restore thực tế và kết quả so với mục tiêu. Không chỉ xác nhận job backup chạy; phải chứng minh liên kết DB/file và trạng thái engine sử dụng tiếp được. |
| **P9.3 — Dữ liệu ban đầu và tài liệu sử dụng** | Chuẩn bị tài khoản/phòng ban/quyền thật trong phạm vi pilot, Owner và bộ quy trình đã được xác nhận. Soạn hướng dẫn requester/approver/designer/Owner/Admin/Auditor và xử lý task kẹt. | Có dữ liệu seed/import được rà soát, hướng dẫn theo vai trò và kênh hỗ trợ. Không dùng tên/ngưỡng trong ảnh hoặc fixture kỹ thuật như dữ liệu/chính sách thật khi chưa được xác nhận. |
| **P9.4 — UAT với các phòng ban** | Chạy nghiệp vụ mua sắm và workflow thứ hai với requester, quản lý, kế toán, mua hàng, Owner và Auditor. Kiểm tra lý do, bổ sung, quyền dữ liệu, deadline và thao tác lỗi thực tế. | Có biên bản UAT với expected/actual result, người xác nhận và lỗi cần sửa. Chính sách ảnh hưởng luồng được chốt; các mục không áp dụng có lý do, không bị đánh dấu đạt giả. |
| **P9.5 — Pilot giới hạn và sửa lỗi** | Đưa nhóm được chọn dùng hồ sơ thật theo phạm vi thống nhất; theo dõi task kẹt, lỗi quyền, retry, tốc độ, tệp và phản hồi UX. Sửa lỗi, chạy lại kiểm tra bị ảnh hưởng và cập nhật hướng dẫn. | Có báo cáo pilot, số liệu thực tế và kết quả khắc phục. Không còn lỗi chặn đã biết; các ngoại lệ xử lý qua chức năng/runbook có audit, không sửa bảng engine như thao tác nghiệp vụ thường ngày. |
| **P9.6 — Nghiệm thu và quyết định mở rộng** | Tổng hợp 38 AC theo phạm vi cuối cùng, kết quả UAT/tải/phục hồi/pilot, quyền hỗ trợ và kế hoạch mở rộng người dùng. Xác định trách nhiệm vận hành và backlog sau MVP. | Có hồ sơ nghiệm thu được người phụ trách nghiệp vụ/kỹ thuật xác nhận, checklist phát hành và điều kiện mở rộng. Hạng mục chưa đạt có quyết định xử lý rõ ràng; không coi deploy thành công là hoàn tất MVP. |

**Điều kiện hoàn thành MVP:** Hai workflow dùng được trên cùng nền tảng; các AC áp dụng có bằng chứng; không còn lỗi chặn về quyền/dữ liệu/version/concurrency; backup/restore đã thử; có người vận hành và pilot được xác nhận.

## 14. Mốc cần chốt các quyết định còn mở

Các mã giữ nguyên từ mục 16 của đặc tả. Bước trong bảng là nơi ghi nhận và kiểm tra quyết định; lựa chọn chưa được xác nhận không được tự biến thành chính sách production.

| Quyết định | Ghi nhận/chốt tại | Phải xong trước |
| --- | --- | --- |
| O-01 — Bộ phiên bản | P1.1 | Tích hợp engine, validator và frontend tương ứng. |
| O-02 — Mô hình giao việc | P0.3 | P2.4; hoàn chỉnh trước P5.3–P5.4. |
| O-03 — Quyền/quy trình publish | P0.3 | P4.1 và P4.7. |
| O-04 — Snapshot/dynamic tổ chức | P0.3 | P2.4 và P5.3. |
| O-05 — Ủy quyền/thay thế | P0.3; xác nhận phạm vi trước P7.3 | Xử lý ngoại lệ và pilot. Có thể hoãn ủy quyền tự phục vụ nhưng phải có phương án task kẹt. |
| O-06 — Quay lại sau bổ sung | P0.2 | Chốt BPMN mẫu ở P4 và xử lý P5.6. |
| O-07 — Hủy/thu hồi/mở lại/unclaim | P0.1 và P0.3 | Chốt scope runtime; baseline không thêm nếu chưa chọn. |
| O-08 — Form/schema/field types/nháp cũ | P0.4 | P3.1; hoàn chỉnh trước P5.1–P5.2. |
| O-09 — Authentication | P0.5 | P2.1. |
| O-10 — Notification/email | P0.5 | P6.6; thêm outbox/retry khi kênh đã chọn đòi hỏi. |
| O-11 — Deadline/lịch/timezone | P0.5 | Cấu hình P4.3 và tính hạn P6.5. |
| O-12 — Hạ tầng/backup/RPO/RTO | P0.5 ghi đầu vào; chốt trước Phase 9 | P9.1–P9.2, trước pilot. |
| O-13 — Quy chế mua sắm | P0.2 | P4 thiết kế mẫu và P5.7 kiểm tra nghiệp vụ. |
| O-14 — Self-approval | P0.2–P0.3 | P2.4 và P5.7; không chờ hồ sơ thật để quyết định. |
| O-15 — Phạm vi xem/giám sát | P0.3 | P2.3 và các API resource/danh sách. |
| O-16 — Retention/tệp/bình luận/audit | P0.5 | Tính năng P6.1–P6.3/P7.4 và phục hồi P9.2. |
| O-17 — Quy mô/hiệu năng | P0.5 xác định đầu mối; chốt trước P8.5 | Lập workload, mục tiêu tải và cấu hình pilot. |

## 15. Đối chiếu acceptance criteria với các bước

Đây là bản đồ trách nhiệm, **không phải kết quả kiểm thử**. Bằng chứng được bổ sung khi thực hiện từng bước và tổng hợp lại tại P8.6/P9.6.

| Acceptance criteria | Bước triển khai/chứng minh chính | Kiểm tra tổng hợp |
| --- | --- | --- |
| AC-01–AC-02 — Tài khoản và quyền quản trị | P2.1–P2.3, P2.6 | P8.1 |
| AC-03–AC-04 — Designer và round-trip | P4.2–P4.4, P4.6 | P8.6 |
| AC-05–AC-07 — Validator, allowlist và XML an toàn | P4.5 | P8.4 |
| AC-08–AC-10 — Publish, chống lặp và revision | P4.6–P4.7 | P8.1–P8.2 |
| AC-11–AC-12 — Nháp/submit và một instance | P5.1–P5.2 | P8.2 |
| AC-13 — Schema/quyền trường | P3.2–P3.3, P5.5 | P8.1 |
| AC-14–AC-18 — Nhánh mua sắm, reject và bổ sung | P0.2, P5.6–P5.7 | P8.6, P9.4 |
| AC-19–AC-22 — Claim/complete, cạnh tranh và retry | P5.4–P5.5 | P8.1–P8.2 |
| AC-23 — Transaction DB/engine | P1.6, P5.2, P5.5 | P8.4 |
| AC-24–AC-27 — Assignment, ngoại lệ, nhân sự và self-approval | P2.4, P5.3, P5.7, P7.3 | P8.1, P8.3 |
| AC-28–AC-29 — Phạm vi đọc và không vượt quyền nghiệp vụ | P2.3, P5.8, P6.1–P6.4, P7.3–P7.4 | P8.1 |
| AC-30–AC-31 — Pin version và retire | P3.5, P4.7–P4.8, P5.2, P6.4 | P8.3 |
| AC-32 — Timeline/viewer khi bước lặp | P5.6, P6.3–P6.4 | P8.3 |
| AC-33 — Deadline/quá hạn | P6.5 | P8.6, P9.4 |
| AC-34 — Notification và rollback | P6.6 | P8.4 |
| AC-35 — Restart | P1.6 | P8.4, P9.1 |
| AC-36 — Backup/restore DB và file | P9.2 | P9.6 |
| AC-37 — Workflow thứ hai bằng cấu hình | P7.5 | P8.6, P9.4 |
| AC-38 — Tải theo quy mô đã thống nhất | P8.5 | P9.5–P9.6 |

AC-25 và các nhánh phụ thuộc chính sách phải ghi rõ điều kiện áp dụng; nếu bị hoãn theo scope đã xác nhận thì đánh dấu không áp dụng kèm lý do, không ghi là đã kiểm thử đạt. Phương án hỗ trợ tối thiểu cho task kẹt vẫn phải có trước pilot.

## 16. Quy ước hoàn thành một bước

Một bước triển khai chỉ được đóng khi:

1. Đã có đầu ra đúng mô tả, nằm trong repository hoặc môi trường/tài liệu được chỉ rõ; các quyết định liên quan đã được ghi nhận.
2. Chức năng có API/UI theo phạm vi bước, dùng dữ liệu thật của môi trường kiểm tra; trạng thái lỗi/thiếu quyền/conflict có phản hồi phù hợp.
3. Có kiểm tra đúng mức rủi ro: policy quan trọng có unit test; engine/DB/locking dùng integration test thực; luồng người dùng có E2E/UAT phù hợp. Việc thuần tài liệu/UI nhỏ dùng review hoặc kiểm tra trực tiếp, không viết test chỉ để đủ số lượng.
4. Thay đổi schema cập nhật cấu hình/seed dev/test và cách dựng lại dữ liệu, chưa yêu cầu migration trong dev theo C-17. Chính sách nâng cấp schema cho dữ liệu thật xử lý trước mốc vận hành liên quan. Thay đổi config/quyền có audit cần thiết; hợp đồng API và hướng dẫn chạy được cập nhật trong cùng bước.
5. Không còn lỗi chặn đối với đầu ra của bước và không phá đầu ra các bước trước; nợ kỹ thuật hoặc phạm vi hoãn có nội dung và ảnh hưởng rõ ràng.

Mẫu ghi nhận khi bắt đầu thực hiện từng bước:

| Trường | Nội dung cần ghi |
| --- | --- |
| Mã bước | Ví dụ `P5.4`. |
| Trạng thái | Chưa làm / Đang làm / Chờ quyết định / Cần sửa / Hoàn thành. |
| Đầu vào | Bước phụ thuộc, quyết định O liên quan, schema/API/version đang dùng. |
| Đầu ra thực tế | File, endpoint, màn hình hoặc artifact cụ thể đã tạo. |
| Bằng chứng | Lệnh kiểm tra, kết quả test, ảnh màn hình hoặc biên bản xác nhận. |
| Vấn đề còn lại | Lỗi, giới hạn, người xử lý và bước bị ảnh hưởng. |

## 17. Phần sau MVP

Chỉ lập kế hoạch chi tiết khi có use case mới được xác nhận: SSO/LDAP/AD, tích hợp ERP/HRM, email tin cậy nếu hoãn, parallel/quorum/multi-instance, escalation/timer phức tạp, DMN, báo cáo nâng cao, import BPMN bên ngoài, ủy quyền mở rộng, mobile native và AI/OCR.

Không đưa React Flow, Workflow DSL/compiler riêng hoặc migration instance đang chạy vào backlog bắt buộc của MVP. Mỗi mở rộng cần phạm vi, mô hình quyền, ảnh hưởng dữ liệu/version và tiêu chí nghiệm thu riêng.
