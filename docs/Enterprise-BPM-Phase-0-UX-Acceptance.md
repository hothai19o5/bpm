# Enterprise BPM — Bản đồ UI và nghiệm thu Phase 0

**Ngày:** 30/10/2026.  
**Trạng thái:** Người dùng đã xác nhận P0.6 trong thảo luận ngày 30/10/2026 (C-12); chưa phải tính năng hoặc kết quả kiểm thử đã hoàn thành.  
**Nguồn:** [Đặc tả MVP](Enterprise-BPM-MVP-Specification.md), C-01–C-14; [kế hoạch triển khai](Enterprise-BPM-Implementation-Plan.md).

## 1. Điều hướng và màn hình đã xác nhận

Nhân viên dùng menu nghiệp vụ. Admin có thêm khu vực quản trị; khi xử lý task, Admin vẫn phải đáp ứng quyền và phân công nghiệp vụ như các actor khác. Không thêm nhóm người dùng thiết kế riêng. Routes dưới đây là bản đồ điều hướng đã được xác nhận, không phải API hay các trang đã được dựng.

| Màn hình | Route | Người dùng và thao tác |
| --- | --- | --- |
| Đăng nhập | `/login` | Tài khoản nội bộ; xử lý sai thông tin, inactive và hết phiên. |
| Đổi mật khẩu | `/account/password` | Bắt buộc đổi mật khẩu tạm trước khi dùng nghiệp vụ; sau đó cho tự đổi. |
| Tổng quan | `/` | Việc cần làm, cần bổ sung, quá hạn và hồ sơ gần đây theo quyền. Không dùng số liệu demo như số vận hành. |
| Danh mục quy trình | `/processes` | Quy trình được phép khởi tạo; tìm/lọc và tạo hồ sơ. |
| Tạo hồ sơ | `/processes/:processId/start` | Form đúng version được phép start; lưu nháp/gửi. Backend xác định requester, phòng ban và version hợp lệ. |
| Hồ sơ của tôi | `/my-records` | Nháp/đã gửi/cần bổ sung/hoàn tất/từ chối; xóa nháp của mình. |
| Công việc | `/tasks` | Hai tab Việc của tôi và Việc có thể nhận; lọc hạn/trạng thái; claim trước khi xử lý task nhóm. |
| Chi tiết hồ sơ và xử lý | `/records/:recordId` | Một trang dùng chung cho requester, assignee, người từng xử lý và Admin; trường/nút theo backend. Có thông tin, tệp, bình luận, timeline và sơ đồ đúng version. |
| Thông báo | `/notifications` | Đã đọc/chưa đọc; mở hồ sơ/task nếu vẫn đủ quyền. Liên kết không tự cấp quyền. |
| Quản trị người dùng/quyền | `/admin/users` | Admin tạo/khóa/reset tài khoản, quản lý quyền. Không xóa danh tính đã có lịch sử. |
| Quản trị tổ chức | `/admin/organization` | Admin quản lý cây phòng ban, thành viên chính và trưởng phòng. |
| Quản lý quy trình | `/admin/processes` | Admin tạo/sửa nháp, quản lý phiên bản, validate/publish/retire và giám sát hồ sơ theo quy trình. |
| Thiết kế quy trình | `/admin/processes/:processId/versions/:versionId/designer` | bpmn-js với palette MVP, property panel assignment/form/action/deadline; bản publish chỉ đọc. |
| Quản lý/thiết kế form | `/admin/forms`, `/admin/forms/:formId/versions/:versionId/designer` | Admin thiết kế trường/nhóm, preview, validation, quyền theo bước và version. |
| Giám sát hồ sơ/ngoại lệ | `/admin/records`, `/admin/assignment-exceptions` | Admin xem toàn bộ, tìm task thiếu người/không hợp lệ và phân công lại có lý do. |
| Tra cứu audit | `/admin/audit` | Admin tra cứu theo actor/hồ sơ/thời gian; chỉ đọc, không chỉnh lịch sử. |

Giao diện tiếng Việt; ưu tiên desktop/laptop. Các trang nghiệp vụ hỗ trợ màn hình nhỏ ở mức đọc/nhập/xử lý; không cam kết thiết kế BPMN trên điện thoại hoặc ứng dụng mobile native. Trải nghiệm này đã được xác nhận cùng P0.6.

## 2. Bốn luồng thao tác

1. **Admin thiết lập:** tạo người dùng/phòng ban → tạo form → tạo workflow → gắn form/assignment/action/deadline → kiểm tra → công bố → theo dõi ngoại lệ.
2. **Nhân viên gửi:** chọn quy trình → điền form → lưu nháp hoặc gửi → theo dõi chi tiết → bổ sung khi được giao → nhận thông báo kết quả.
3. **Người xử lý:** mở công việc/thông báo → nhận việc nếu là task nhóm → xem dữ liệu → quyết định/hoàn thành → thấy lịch sử và trạng thái đã cập nhật.
4. **Admin hỗ trợ:** mở ngoại lệ → xem lý do → chọn người đủ thẩm quyền → nhập lý do phân công lại → ghi audit. Không có nút duyệt thay bằng quyền Admin.

Không hiện nút sửa sơ đồ cho nhân viên; không hiện nút thu hồi/hủy/mở lại sau gửi, unclaim, ủy quyền, email hoặc chức năng ngoài MVP.

## 3. Trạng thái và phản hồi UI

| Tình huống | Hành vi |
| --- | --- |
| Đang tải | Hiện trạng thái tải tại vùng dữ liệu; không hiển thị số 0 giả hoặc nút thao tác dựa trên dữ liệu cũ. |
| Chưa có dữ liệu | Nói rõ chưa có hồ sơ/việc; có hành động tạo hồ sơ nếu đủ quyền. Bộ lọc không có kết quả được phân biệt với hệ thống chưa có dữ liệu. |
| Lỗi tải/mất mạng | Giữ phần nhập đang có trong bộ nhớ trang, báo lỗi và cho thử lại; không báo thành công khi chưa có kết quả xác nhận. Không hứa khôi phục nội dung chưa lưu sau đóng/tải lại trang. |
| Hết phiên | Yêu cầu đăng nhập lại, giữ đường dẫn quay về an toàn; mọi dữ liệu phải tải lại theo quyền hiện tại. Nháp đã lưu còn nguyên; phần chưa lưu không có cam kết phục hồi. |
| Không đủ quyền/không tìm thấy | Hồ sơ ngoài phạm vi dùng thông báo không tiết lộ sự tồn tại/nội dung, phù hợp chính sách API; trang quản trị có thể báo không đủ quyền. |
| Validation | Chỉ rõ trường, bước hoặc BPMN element bị lỗi; giữ dữ liệu người dùng vừa nhập. |
| Conflict khi claim/complete/lưu | Nói rõ công việc/cấu hình đã thay đổi; tải trạng thái mới, không tự ghi đè hoặc tự gửi lại quyết định. |
| Retry sau mất response | Submit/complete/publish dùng lại khóa thao tác ban đầu; hiển thị kết quả đã commit nếu có, không tạo thao tác nghiệp vụ thứ hai. |
| Task tương lai | Chỉ là đường đi có thể xảy ra trên sơ đồ; không tạo assignee/deadline/task giả trước khi gateway chọn nhánh. |
| Task quá hạn | Có nhãn/lọc quá hạn; vẫn theo đúng quyền và hành động được cấu hình, không tự chuyển bước. |

## 4. Đối chiếu concept với MVP

| Concept | Giữ trong MVP | Điều chỉnh |
| --- | --- | --- |
| Tổng quan — `02_19_38 PM-1.png` | Sidebar/header, việc cần làm, quy trình, hồ sơ gần đây | Số liệu theo quyền, có empty state; chưa có BI hoặc tỷ lệ tăng giảm thiếu dữ liệu. |
| Thiết kế — `02_19_44 PM-2.png` | Palette/canvas/property panel, lưu/validate/publish | bpmn-js và ký hiệu BPMN chuẩn; chỉ Admin, tập node MVP; không hứa import tự do, auto layout hoặc service/script task. |
| Quản lý quy trình — `02_19_47 PM-3.png` | Tìm/lọc, vòng đời, phiên bản | Nhân viên dùng danh mục để khởi tạo; Admin dùng quản lý cấu hình; published bất biến. |
| Chi tiết — `02_19_49 PM-4.png` | Form, tệp, trao đổi, timeline, sơ đồ và hành động | Luồng nghiệp vụ theo C-08, quyền theo C-09/C-10; không sửa tự do hồ sơ đã gửi hoặc hiển thị task tương lai như đã giao. |

Ảnh nguồn nằm tại [concept-ui](../concept-ui/); không lấy các nút hoặc số demo trong ảnh làm cam kết tính năng.

## 5. Dữ liệu và actor nghiệm thu

- `AD`: Admin; `NV1/NV2`: hai nhân viên; `TP1`: trưởng phòng của NV1; `KT1/KT2`: hai candidate kế toán; `MH1`: candidate mua hàng; `GD1`: giám đốc. Có thêm user inactive, user chỉ được cấp quyền đọc audit và user không có quyền thiết kế/publish để thử từ chối truy cập. Các actor phủ định quyền không tạo thêm nhóm người dùng bắt buộc trong sản phẩm.
- Phòng ban requester có TP1; kế toán có KT1/KT2; mua hàng có MH1. Có fixture thiếu trưởng phòng/nhóm rỗng/assignee inactive. Mỗi quyền/phạm vi được khai báo rõ trong dữ liệu test.
- `T = 50.000.000 VND` là ngưỡng người dùng đã xác nhận, được cấu hình trên version mua sắm. Các giá trị biên `T-1/T/T+1` lần lượt là 49.999.999/50.000.000/50.000.001 VND; bằng hoặc trên T phải qua giám đốc.
- Mua sắm v1 có form v1 và tham số riêng; v2 thay đổi form/ngưỡng hoặc bước để kiểm tra pinning. Có nháp v1, hồ sơ v1 đang chạy và hồ sơ mới sau khi công bố v2.
- Dùng fixture hạn 1 giờ, tệp hợp lệ/sai loại/quá giới hạn và bình luận 2.000/2.001 ký tự. Đây là tham số test; không mặc định deadline mọi quy trình là 1 giờ.
- Các bài concurrency dùng hai request thực sự đồng thời; bài rollback cố ý gây lỗi. Cần đối chiếu dữ liệu ứng dụng/engine/audit, không chỉ ảnh giao diện hoặc HTTP status.

## 6. Acceptance matrix đã xác nhận làm kế hoạch kiểm thử

Mọi dòng dưới đây là kịch bản phải thực hiện sau khi có tính năng. Chưa có dòng nào được đánh dấu đạt. Mô tả chi tiết của AC-01–AC-38 tại mục 14 đặc tả vẫn áp dụng; bảng này bổ sung actor, đầu vào và đường thao tác.

| AC | Actor/đầu vào | Thao tác | Kết quả cần kiểm chứng |
| --- | --- | --- | --- |
| AC-01–AC-02 | NV1 ACTIVE; user INACTIVE; tài khoản có mật khẩu tạm | Login, đổi mật khẩu tạm, gọi quản trị bằng NV1; thử lại sau khóa, JWT exp hoặc thu hồi theo C-18 | Chỉ ACTIVE đúng quyền thao tác được; bắt buộc đổi mật khẩu tạm; token cũ bị chặn sau khóa/hết hạn/thu hồi; nhân viên không dùng API quản trị. |
| AC-03–AC-04 | AD, mua sắm có XML/DI và metadata | Thiết kế → lưu → tải lại → sửa → lưu | Sơ đồ, IDs, assignment/action/form metadata không mất; form preview giữ kiểu/quyền/bố cục. |
| AC-05–AC-07 | AD hoặc request bỏ qua UI; mô hình sai, script/service task, expression ngoài tập và XML có DTD/entity | Gọi validate/publish trực tiếp | Backend chặn trước deploy, chỉ ra lỗi; không thực thi mã hoặc truy cập tài nguyên ngoài. |
| AC-08–AC-10 | AD và NV1; draft revision cũ, khóa publish lặp | NV1 thử publish; AD publish/retry; hai tab AD lưu/publish trên revision cũ | Chỉ AD công bố; retry một deployment; conflict không ghi đè. Hai tab cùng Admin kiểm tra được concurrency dù MVP chỉ có một người thiết kế. |
| AC-11–AC-12 | NV1, form mua sắm hợp lệ, cùng khóa submit | Lưu nháp, gửi hai lần; mất response sau commit rồi retry | Một hồ sơ/instance, pin đúng version; không tạo hồ sơ hoặc instance thứ hai. |
| AC-13 | NV1 thiếu trường/sai kiểu; KT1 sửa amount; requester sửa ngân sách | Lưu nháp, submit và gửi payload ngoài quyền; REJECT/REQUEST_CHANGES với/không lý do | Nháp được thiếu trường bắt buộc; submit kiểm tra đầy đủ; payload sai quyền/kiểu bị từ chối nguyên tử; từ chối/bổ sung bắt buộc lý do và không đòi dữ liệu approve. |
| AC-14–AC-15 | NV1; amount lần lượt 49.999.999/50.000.000/50.000.001 VND; TP1, KT1, GD1, MH1 | Chạy đủ nhánh APPROVE; kế toán và mua hàng claim trước xử lý | 49.999.999 VND bỏ bước giám đốc; 50.000.000 và 50.000.001 VND phải qua giám đốc; đủ ngân sách mới đi tiếp; nhận đủ hàng kèm chứng từ mới COMPLETED. |
| AC-16 | TP1/KT1/GD1 ở các fixture riêng | REJECT có lý do | REJECTED, không còn task hoạt động; không có mở lại/thu hồi/hủy sau gửi. |
| AC-17–AC-18 | Amount ban đầu T-1; KT1 yêu cầu bổ sung; NV1 đổi thành T+1 | REQUEST_CHANGES → NV1 sửa trường được phép → RESUBMIT → duyệt lại | Giữ instance/lịch sử, quay trưởng phòng, kế toán kiểm tra mới, phải qua GD1; không tái dùng kết quả ngân sách cũ để bỏ bước. |
| AC-19–AC-20 | KT1/KT2 cùng candidate; NV2 ngoài queue | Claim đồng thời; complete trước claim hoặc bằng NV2 | Một assignee; request còn lại conflict; người chưa claim/ngoài phạm vi không complete. |
| AC-21–AC-23 | Assignee, task đang mở; các khóa/payload giống và khác; lỗi trước commit | Complete đồng thời/retry, tái dùng khóa với payload khác, cố ý làm app/engine lỗi | Một kết quả chuyển bước; retry đúng trả kết quả cũ, sai payload conflict; app/engine/action log cùng rollback khi giao dịch thất bại. |
| AC-24–AC-27 | Thiếu TP, inactive, đổi trưởng phòng; TP1 tạo hồ sơ cho chính mình | Tạo/chuyển bước; AD xem ngoại lệ và phân công lại có lý do | Không bỏ bước/tự duyệt. Phòng ban giữ lúc gửi, người resolve khi tạo task; task cũ không tự chuyển. AD chỉ giao người đủ thẩm quyền, có audit; chưa có người hợp lệ thì giữ chờ. |
| AC-28–AC-29 | NV2 không liên quan, người từng trực tiếp xử lý, AD, user chỉ đọc audit | Truy cập danh sách/ID/file/history và thử complete | Không lộ dữ liệu ngoài phạm vi; người từng xử lý đọc theo quyền trường; AD xem toàn bộ nhưng không tự duyệt; quyền audit không cấp quyền xử lý. |
| AC-30–AC-31 | Hồ sơ/nháp v1, form v1; AD publish v2 rồi retire v1 | Xử lý hồ sơ cũ, tạo mới, submit nháp v1 | Hồ sơ đang chạy/viewer/ngưỡng/form giữ v1; v1 retired chặn start mới. Tạo nháp theo v2 sao chép trường tương thích, giữ nháp nguồn và yêu cầu kiểm tra trước gửi. |
| AC-32 | Cùng activity qua nhiều vòng bổ sung | Mở timeline/viewer sau mỗi vòng | Mỗi lượt task/action riêng; phân biệt task hiện tại và lịch sử, không coi bước tương lai là task đã giao. |
| AC-33 | Task hạn 1 giờ; thời điểm trước/đúng/sau hạn, cuối tuần; reassign và lượt duyệt mới | Xem/lọc quá hạn, phân công lại, bổ sung/gửi lại | Ngày lịch/UTC+7; reassign giữ hạn, task lượt mới có hạn mới. Không tự complete/approve/escalate. Quy ước so sánh tại đúng hạn cần thể hiện nhất quán trong hợp đồng kỹ thuật. |
| AC-34 | Giao dịch tạo task/bổ sung/từ chối/hoàn tất hoặc ngoại lệ; một fixture rollback | Thực hiện action và mở thông báo; đánh dấu đã đọc | Đúng người nhận/phạm vi, có read/unread; không có thông báo thành công cho rollback; retry không gây nghiệp vụ lặp; không có email/browser push. |
| AC-35–AC-36 | Hồ sơ có task chờ và tệp; backup DB/storage tương ứng | Restart; restore ở môi trường cách ly, tải tệp và xử lý task tiếp | Dữ liệu/engine còn đúng, không start lại; đo và đối chiếu RPO ≤24 giờ/RTO ≤4 giờ. Chưa có kết quả trước diễn tập. |
| AC-37 | AD dùng cùng form builder/designer | Cấu hình nghỉ phép đơn giản, publish, nhân viên gửi và trưởng phòng xử lý | Quy trình thứ hai chạy bằng cấu hình; không thêm runtime/state machine/endpoint duyệt riêng, không tính số dư phép. |
| AC-38 | Đầu vào 50 nhân sự, cao điểm khoảng 500 request/giây (C-14); kỹ thuật hoàn thiện workload/môi trường/thời gian và tiêu chí đo tại P8.5 | Chạy tải danh sách/inbox/submit/claim/complete/file theo workload đã ghi nhận trước khi chạy | Đo throughput thực đạt, độ trễ/lỗi và tính đúng đắn; không coi mục tiêu 500 request/giây là kết quả đã chứng minh. Không giảm quyền/tính đúng đắn để đạt tốc độ. |

**Kiểm tra UX bổ sung:** loading/empty/error/conflict/forbidden/hết phiên trên các màn liên quan; file sai loại/>20 MB hoặc vượt 10 tệp bị chặn; bình luận 2.001 ký tự bị chặn, bình luận đã gửi không sửa/xóa; nhập trên màn hình nhỏ theo phạm vi; mọi thao tác trái quyền phải kiểm tra bằng API.

## 7. Phần còn mở và điều kiện chuyển bước

- P0.6 đã được xác nhận về bản đồ/trải nghiệm và kế hoạch nghiệm thu; các AC chỉ đánh dấu đạt khi có bằng chứng chạy kiểm thử.
- Ngưỡng T đã chốt 50.000.000 VND (C-13), áp dụng `>=`; không còn treo giá trị ngưỡng.
- O-17 đã có đầu vào 50 nhân sự/cao điểm khoảng 500 request/giây. Theo yêu cầu người dùng, không thảo luận sâu thêm về tải tại Phase 0; kỹ thuật hoàn thiện workload/môi trường/tiêu chí đo trước khi thực hiện P8.5.
- O-12/O-16 còn hạ tầng, runbook, retention backup/dữ liệu và quét malware/audit nâng cao theo mốc tại mục 13.4 đặc tả, trước pilot hoặc dữ liệu thật tương ứng.
- Cập nhật 01/11/2026: backend Java/Boot/Flowable trong pom đã được người dùng xác nhận đã test, không kiểm chứng lại (C-15); chạy project trong Docker (C-16), dev chưa dùng migration DB (C-17). O-01 còn chọn/khóa frontend/bpmn-js/validator, O-08 còn hợp đồng form/schema/binding. Kiểm thử nghiệp vụ/transaction vẫn theo acceptance matrix.

Tài liệu đã xác nhận này là đầu vào để triển khai và viết kiểm thử; không có nghĩa AC đã chạy hoặc được nghiệm thu.
