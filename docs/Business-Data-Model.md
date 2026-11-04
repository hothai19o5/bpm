# Business data model

**Ngày:** 02/11/2026. **Cập nhật:** 03/11/2026. **Trạng thái:** Người dùng xác nhận build, test và dev thành công; chốt bước bổ sung business model/schema. Agent không chạy lại build/test. P2.1 và các phase nghiệp vụ tiếp theo chưa được chốt bởi kết quả này.

Mô hình dựa trên mục 8 của [đặc tả MVP](Enterprise-BPM-MVP-Specification.md) và phần schema của [hướng dẫn P2.1](P2.1-Manual-Implementation-Guide.md). P2.1a đã chốt dùng trực tiếp các bảng số nhiều `users`, `login_grants`, `refresh_credentials`; không tạo bảng auth song song.

## 1. Danh sách bảng

Entity nằm trong `src/main/java/com/x/bpm/<module>/adapter/persistence/`, tên lớp có hậu tố `Entity`. JPA/Hibernate được giữ ngoài domain/application. Quan hệ giữa các entity là mapping hạ tầng; module khác vẫn phải dùng application port và DTO để truy cập nghiệp vụ.

| Module | Bảng | Dữ liệu chính |
| --- | --- | --- |
| identity | `users` | Username chuẩn hóa, password hash, ACTIVE/INACTIVE, bắt buộc đổi mật khẩu, security version. |
| identity | `roles`, `permissions` | Mã duy nhất, tên, mô tả; role có trạng thái. |
| identity | `user_roles`, `role_permissions` | RBAC, unique theo cặp liên kết. |
| identity | `login_grants`, `refresh_credentials` | Lần đăng nhập, thu hồi, hạn dùng, hash refresh token, thời điểm tiêu thụ. |
| organization | `departments`, `positions`, `department_members` | Cây phòng ban, trưởng phòng, chức danh, phòng ban chính. |
| form | `form_definitions`, `form_versions` | Danh mục, schema/UI schema JSONB, version, trạng thái, công bố. |
| form | `form_version_operations` | P3.5 (04/11/2026): biên nhận publish/clone, unique actor/key, source revision và target version; cùng transaction với audit. |
| process | `process_definitions`, `process_versions` | Danh mục, active version, BPMN XML, cấu hình bước/binding JSONB, form version chung, engine mapping, checksum. |
| businessrecord | `business_records` | Mã hồ sơ, requester/phòng ban snapshot, process/form version, engine instance, dữ liệu JSONB, trạng thái và thời gian. |
| businessrecord | `record_attachments`, `record_comments` | Metadata object storage, liên kết file tạm/chính thức, bình luận và tác giả. |
| businessrecord | `record_access_grants` | Quyền bổ sung theo user/hồ sơ/action, lý do, người cấp, hiệu lực và thu hồi. |
| task | `task_action_logs` | Engine task/activity, actor, action, comment, payload JSONB, idempotency key. |
| audit | `audit_events` | Actor tùy chọn, source, action, tài nguyên, kết quả, correlation ID, diff JSONB. |
| notification | `notifications` | Người nhận, hồ sơ/task tùy chọn, loại, tiêu đề, nội dung, thời điểm đã đọc. |

Tổng cộng **22 bảng** (21 bảng nền và một bảng biên nhận P3.5). Mốc model nền được xác nhận ngày 03/11 vẫn là 21 bảng; phần mở rộng P3.5 được kiểm chứng riêng tại [vòng đời form version](P3.5-Form-Version-Lifecycle.md). Không tạo bảng runtime thay Flowable hoặc FK tới `ACT_*`. Engine IDs là tham chiếu ngoài. Các bảng mở như outbox, delegation, assignment exception và record revision chưa được tạo.

## 2. Quy ước và ràng buộc

- `CreatedEntity`: UUID do JPA sinh và `created_at` do Hibernate ghi. `MutableEntity` bổ sung `updated_at` và `@Version long revision`. Timestamp dùng `Instant`; version nghiệp vụ, revision và security version độc lập.
- Các quan hệ dùng FK, tải LAZY, không cascade xóa. API sau này map UUID thành chuỗi cho DTO hiện có và không trả entity trực tiếp.
- Username được strip và lowercase bằng `Locale.ROOT` ở callback persist/update; DB enforce unique `normalized_username`. User mới mặc định ACTIVE và cần đổi mật khẩu. Adapter phải lấy password hash từ `PasswordEncoder`.
- Refresh credential chỉ có SHA-256 hash dạng hex lowercase 64 ký tự; không có raw token. CHECK kiểm tra định dạng/hạn dùng; chưa triển khai hashing, rotation, lock hoặc replay detection.
- MVP có tối đa một membership cho mỗi user, `is_primary` luôn true. Mở rộng nhiều phòng ban cần thay ràng buộc có chủ đích.
- Version dương, unique trong definition; engine definition/instance IDs unique khi có giá trị. PUBLISHED/RETIRED cần metadata công bố; process version còn cần BPMN, form và engine mapping.
- CHECK phân biệt hồ sơ DRAFT chưa có instance với hồ sơ đã gửi/có instance và hồ sơ kết thúc/có thời điểm hoàn tất. Trạng thái: DRAFT, IN_PROGRESS, CHANGES_REQUESTED, COMPLETED, REJECTED.
- File TEMPORARY chưa liên kết hồ sơ; LINKED bắt buộc có hồ sơ/thời điểm liên kết. Unique theo bucket/object key; CHECK dung lượng 1–20.971.520 byte (20 MiB).
- Comment tối đa 2.000 ký tự, không rỗng. Comment, task action log và audit dùng `@Immutable`, không dirty-update qua Hibernate. Đây không phải bảo vệ chống sửa/xóa ở mức database; không mở API update/delete.
- Task action log unique `(business_record_id, actor_id, idempotency_key)`. Đây là nền lưu kết quả, chưa có replay response/fingerprint request hoặc bảo đảm task chỉ complete một lần.
- Có index cho FK, inbox thông báo, hồ sơ theo requester/process/trạng thái và lịch sử theo hồ sơ/tài nguyên/thời gian. Chưa thêm GIN index rộng khi chưa có truy vấn cụ thể.

Không thêm tài khoản dev, quyền mặc định, repository/CRUD/endpoint hoặc kích hoạt adapter danh mục đang để TODO.

## 3. Phần cần hoàn thiện trong use case

Các mapping này chưa thay thế policy, validation và transaction của các phase tiếp theo:

- Active version phải thuộc đúng process definition, đã publish và được phép khởi tạo; form version của hồ sơ phải khớp version đã pin trong process/binding. FK hiện tại kiểm tra sự tồn tại, chưa kiểm tra chéo các điều kiện này.
- Không sửa artifact đã publish; thay đổi tạo version mới. Entity version vẫn mutable để lưu draft và publish/retire; API không được gọi save tùy ý.
- Kiểm tra chu kỳ phòng ban nhiều cấp, trưởng phòng hợp lệ, user ACTIVE, RBAC và quyền hồ sơ.
- Giữ snapshot phòng ban khi gửi, phối hợp update hồ sơ với Flowable trong một transaction. CHECK trạng thái không phải engine điều phối.
- Kiểm tra JSON Schema/quyền trường, tối đa 10 tệp/hồ sơ, loại tệp, upload/cleanup thực tế và download có quyền.
- P2.1b đã thêm persistence boundary; P2.1c đã nối login và security pipeline
  để chặn account/grant/version không còn hợp lệ, đọc quyền hiện tại từ DB.
  P2.1d đã có code/test source cho refresh rotation/replay và logout. P2.1e đã
  có code/test source cho đổi/reset mật khẩu, khóa và thu hồi toàn bộ phiên.
  P2.1f đã bổ sung integration-test source và hướng dẫn nghiệm thu.
  **P2.1d–f chưa chạy build/test/dev; người dùng sẽ kiểm tra sau.**
- Notification chỉ trả cho người nhận; audit không lưu password/token hay toàn bộ dữ liệu nhạy cảm; dữ liệu thành công theo transaction đã commit.

## 4. Build/test do người dùng chạy

Không thêm migration hoặc dependency. Spring scan các entity; dev dùng `ddl-auto=update`, test dùng `create-drop` trên DB cách ly `bpm_test`. Bảng ứng dụng nằm trong schema `business`, tách khỏi các bảng Flowable trong schema `flowable`. Ngoài dev/test giữ `ddl-auto=none`.

```powershell
docker compose -p bpm-test -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from tests
```

Kỳ vọng mã thoát `0`. Test source `BusinessModelPersistenceTest` kiểm tra schema ứng dụng cùng engine, JSONB round-trip/tham chiếu version, unique username/version/membership, FK, optimistic locking tách security version, hash refresh, CHECK hồ sơ/file và mapping comment bất biến. Các test mới rollback dữ liệu trong DB test. Thu thập Surefire trước khi dọn stack theo README.

Sau khi test thành công, dựng lại dev:

```powershell
docker compose up --build -d --wait --wait-timeout 180
Invoke-RestMethod http://localhost:8080/actuator/health
```

Kỳ vọng backend/PostgreSQL healthy, health `UP`, có 21 bảng ứng dụng cùng các bảng engine. Nếu đổi APP_PORT, dùng cổng thực tế. Kiểm tra log không có lỗi tạo constraint/FK và xem bảng bằng công cụ PostgreSQL của bạn. Hibernate update không thay thế chính sách migration cho dữ liệu thật.

Ngày 03/11/2026, người dùng xác nhận build, test và dev thành công. Agent đã rà soát source/cấu hình và whitespace, không chạy lại các lệnh trên. Bước model/schema đủ điều kiện commit trước khi tiếp tục nghiệp vụ. Commit message: `feat: add BPM business persistence models`.
