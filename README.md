# BPM — môi trường Docker dev/test

Cấu hình dùng Java 25, Spring Boot 4.1.2, Flowable 8.0.0 theo `pom.xml` và PostgreSQL 18.6. Backend, build và test chạy trong container; máy host cần Docker Desktop ở chế độ Linux containers và Docker Compose v2 có hỗ trợ `up --wait`.

**Trạng thái cập nhật ngày 04/11/2026:** Người dùng xác nhận P1.4/P1.5, P1.6, business model/schema và P2.1a–c đã hoàn thành. P2.1d–f, P2.2, P2.3 và P2.4 đã chạy full automated suite trong Docker với PostgreSQL test riêng ngày 03/11: **159 test, 0 failure, 0 error, 1 skipped; exit code 0**. Ba test fixture được chỉnh trước lượt chạy đạt. P2.1 restart persistence và các luồng dev/Postman nghiệp vụ chưa chạy đầy đủ; các mốc chưa nghiệm thu. Xem [biên bản P2.1](docs/P2.1-Verification.md), [P2.2](docs/P2.2-Verification.md), [P2.3](docs/P2.3-Verification.md) và [P2.4](docs/P2.4-Verification.md). Chưa có kết quả GitHub Actions.

P2.2a–d đã có [contract và implementation quản trị người dùng/tổ chức](docs/P2.2-Organization-Implementation-Guide.md), Postman và [test source/biên bản](docs/P2.2-Verification.md). Automated tests chạy đạt; CRUD smoke trên dev/Postman và một số trường hợp trong ma trận vẫn cần kiểm tra.

P2.3a–d đã có [permission catalog/shared policy/RBAC API](docs/P2.3-Authorization-Implementation-Guide.md), Postman và [ma trận test source](docs/P2.3-Verification.md). Automated policy/RBAC tests chạy đạt; actor/resource-scope matrix và Postman smoke còn lại chưa nghiệm thu.

P2.4a–d đã có [assignment resolver contract/implementation](docs/P2.4-Assignment-Resolver-Implementation-Guide.md) và [ma trận test source](docs/P2.4-Verification.md). Resolver unit và một luồng PostgreSQL đã chạy đạt; các trường hợp directory còn lại và restart vẫn cần kiểm tra.

P3.1 đã có [hợp đồng schema/UI schema và API form](docs/P3.1-Form-Contract.md), fixture mua sắm và Postman. [8 kiểm thử P3.1 đạt trên Docker/PostgreSQL test riêng](docs/P3.1-Verification.md); các bước tiếp theo bổ sung validation, renderer/builder, vòng đời và quản lý biểu mẫu như bên dưới.

P3.2 đã có [validator dữ liệu và policy quyền trường dùng chung](docs/P3.2-Form-Validation-Guide.md), lọc read view, merge patch và validation theo action. [74 kiểm thử P3.1/P3.2 đạt](docs/P3.2-Verification.md), gồm 66 test mới P3.2. Service nội bộ được đăng ký Spring bean để các bước renderer/record/task tái sử dụng.

P3.4 đã bổ sung [API lưu draft theo revision cho form builder](docs/P3.4-Form-Builder.md), cập nhật Postman; 75 backend form tests và 68 frontend form tests đạt. Builder tại frontend dùng renderer P3.3 để preview; backend dev cần rebuild/restart để nạp endpoint mới.

P3.5 đã có [validate/publish/clone và bất biến form version](docs/P3.5-Form-Version-Lifecycle.md), Idempotency-Key với biên nhận DB và test concurrency/rollback. 87 backend form tests, 74 frontend tests và Postman lint đạt. Dev cần rebuild/restart để nạp API và bảng form_version_operations mới; frontend đã nối thao tác lifecycle trong designer.

P3.6 đã có [danh mục/tìm/lọc, lịch sử, lựa chọn published version và preview requester/kế toán](docs/P3.6-Form-Management.md). 94 backend form tests, 78 frontend tests và ba Postman lint đạt; Chrome smoke nối API thật trên PostgreSQL test cách ly đạt. Binding vào process version tiếp tục ở P4; dev cần rebuild backend để nạp API mới.

P4.1 đã có [danh mục quy trình theo scope, tạo draft v1, metadata/lịch sử và đọc cấu hình theo version](docs/P4.1-Process-Catalog.md), tái sử dụng hai bảng process hiện có. 114 backend tests (10 mới P4.1), ba Postman lint đạt; metadata quản trị chỉ dành cho `process.design`/`process.publish`, phiên bản nghiệp vụ độc lập với engine version. Canvas/save/publish/lifecycle tiếp tục ở P4.2–P4.8; cần rebuild/restart backend để dùng API mới trên dev.

P4.2 đã có [canvas bpmn-js giới hạn MVP ở frontend](docs/P4.2-Workflow-Canvas.md), lưu/mở tệp BPMN giữ DI, zoom/undo/redo và giới hạn công cụ/menu/modeling. 13 test Chrome và 78 test frontend form đạt; build/lint đạt. Chưa lưu nháp quy trình lên server/publish; không thay API/schema/Postman ở bước này.

## 1. Chuẩn bị lần đầu

Trong PowerShell tại thư mục repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Chỉnh `.env` theo máy của bạn; giá trị mật khẩu trong file mẫu chỉ phục vụ dev. `.env` được Git và Docker build context bỏ qua. Các tham số:

| Biến | Giá trị mẫu | Ý nghĩa |
| --- | --- | --- |
| `APP_PORT` | `8080` | Cổng backend trên máy host. |
| `POSTGRES_PORT` | `5432` | Cổng PostgreSQL trên máy host, phục vụ IDE/công cụ DB. |
| `POSTGRES_DB` | `bpm` | Database dev. |
| `POSTGRES_USER` | `bpm` | Tài khoản DB dev. |
| `POSTGRES_PASSWORD` | Giá trị mẫu trong `.env.example` | Mật khẩu DB dev; không để trống. |

Các cổng được bind vào `127.0.0.1`. Nếu cổng đang được dùng, đổi `APP_PORT`/`POSTGRES_PORT` trong `.env`. Backend luôn kết nối nội bộ tới service `postgres:5432`; đổi cổng host không đổi địa chỉ kết nối giữa container.

## 2. Build và chạy dev

```powershell
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
```

Lệnh đầu build image backend, chờ PostgreSQL healthy rồi chạy ứng dụng với profile `dev`; cuối cùng chờ health check của backend. Image build dùng `-Dmaven.test.skip=true`, không chạy hoặc compile test. Bộ kiểm thử có lệnh riêng ở mục 4.

Với cổng mặc định, kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Kết quả mong đợi là `status: UP`. Nếu đổi `APP_PORT`, dùng cổng đã cấu hình. Health endpoint kiểm tra trạng thái ứng dụng và DB; chưa phải bằng chứng nghiệp vụ/transaction/concurrency đã đúng. Security dùng JWT Bearer stateless; API bảo vệ trả JSON 401 khi thiếu token. Ngày 03/11/2026, dev health trả `UP` và `GET /api/v1/auth/me` không token trả `401`; login/refresh/change-password/logout trên dev chưa smoke test. Toàn bộ code, cấu hình và test bootstrap tài khoản dev đã bị xóa khỏi repository; test authentication tự tạo fixture trong DB test, còn tài khoản runtime sẽ được tạo qua API quản trị P2.2.

Xem log:

```powershell
docker compose logs -f backend postgres
```

Sau khi sửa source, chạy lại `docker compose up --build -d --wait --wait-timeout 180`. Source được đóng vào image, chưa cấu hình hot reload/bind mount source.

### 2.1. Tạo tài khoản fixture dev thủ công

Runtime không có bootstrap hoặc self-registration. Khi cần kiểm tra login thủ
công, kết nối PostgreSQL dev và chạy đoạn SQL sau khi đã thay password mẫu:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

INSERT INTO business.users (
    id, created_at, updated_at, revision,
    username, normalized_username, display_name, password_hash,
    status, must_change_password, security_version
) VALUES (
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'employee.one', 'employee.one', 'Employee One',
    '{bcrypt}' || public.crypt(
        'REPLACE_WITH_DEV_PASSWORD_12_TO_128_CHARS',
        public.gen_salt('bf', 12)
    ),
    'ACTIVE', false, 0
);
```

Chỉ dùng cho DB dev; không commit password thật vào repository. Đặt
`must_change_password=true` khi cần kiểm tra gate mật khẩu tạm. Username
phải được strip/lowercase nhất quán ở cả `username` và
`normalized_username`. API quản trị tạo tài khoản chính thức vẫn thuộc P2.2.

## 3. Dừng và giữ dữ liệu dev

```powershell
docker compose stop
docker compose up -d --wait --wait-timeout 180
```

Hoặc xóa container/network và dựng lại, vẫn giữ named volume:

```powershell
docker compose down
docker compose up -d --wait --wait-timeout 180
```

Với project mặc định `bpm-dev`, dữ liệu nằm trong volume `bpm-dev_postgres-data`, mount tại `/var/lib/postgresql` phù hợp image PostgreSQL 18. Dữ liệu ở container/volume của cấu hình Compose cũ không tự chuyển sang volume mới. Các lệnh trong tài liệu này chưa được chạy tự động.

`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` được PostgreSQL áp dụng khi khởi tạo volume trống. Đổi các biến đó trong `.env` không tự sửa tài khoản/database đã tồn tại trong volume; cần điều chỉnh DB hiện có hoặc reset dev có chủ đích.

Khi khởi tạo database trống, script `docker/postgres/init/01-create-schemas.sql`
tạo hai schema thuộc database user hiện tại: `flowable` chứa bảng engine và
`business` chứa bảng JPA của ứng dụng. Script init chỉ chạy với data directory
trống; volume dev cũ cần được reset bằng lệnh bên dưới để nhận cấu trúc này.

**Chỉ khi muốn xóa toàn bộ database dev để tạo lại từ đầu:**

```powershell
docker compose -p bpm-dev -f compose.yaml down --volumes
docker compose -p bpm-dev -f compose.yaml up --build -d --wait --wait-timeout 180
```

Đây là thao tác xóa dữ liệu dev, tách riêng với lệnh dừng thông thường. Nếu chủ động dùng project name khác, thay `bpm-dev` đúng project đó.

## 4. Chạy test cách ly trong Docker

`compose.test.yaml` là file độc lập; không ghép bằng `-f compose.yaml -f compose.test.yaml`.

```powershell
docker compose -p bpm-test -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from tests
$testExitCode = $LASTEXITCODE
```

Test runner dùng JDK 25 và Maven Wrapper của repository, chạy `clean test`. PostgreSQL test có database/user `bpm_test`, không mở cổng host và không mount volume dev. Dữ liệu test nằm trên tmpfs, mất khi container PostgreSQL test dừng. Dev có thể tiếp tục chạy song song.

Mã thoát từ lệnh Compose là kết quả của service `tests`; `0` khi thành công. Nếu dùng trong script CI/PowerShell, lưu `$LASTEXITCODE` ngay sau lệnh `up` như trên và trả lại `$testExitCode` sau các bước thu thập báo cáo/dọn dẹp.

Thu thập báo cáo **trước khi xóa container test**:

```powershell
New-Item -ItemType Directory -Force target | Out-Null
docker compose -p bpm-test -f compose.test.yaml cp tests:/workspace/target/surefire-reports ./target/test-results
docker compose -p bpm-test -f compose.test.yaml down
```

Nếu Maven lỗi trước khi chạy Surefire, có thể chưa có thư mục báo cáo; xem output hoặc `docker compose -p bpm-test -f compose.test.yaml logs tests` để biết lỗi. Volume `bpm-test_maven-cache` chỉ giữ dependency Maven, không chứa DB; có thể giữ cho lần chạy sau.

Đã có test source cho smoke test PostgreSQL/Flowable, tạo schema JPA + seed kỹ thuật
và quy trình Flowable tối thiểu qua adapter (P1.4/P1.5). Người dùng đã xác nhận
chạy test thành công ngày 02/11/2026 sau khi bổ sung Boot security test starter.
Guard yêu cầu chỉ profile `test`, DB/user `bpm_test`, URL dạng
`jdbc:postgresql://<host>:<port>/bpm_test?currentSchema=flowable,business,public`;
không chấp nhận query parameter khác và kiểm tra lại
metadata DataSource trước schema. Test DB dùng chung annotation `@IsolatedPostgresTest`.
Đã bổ sung test source rollback JPA + engine và claim/complete cạnh tranh của
P1.6; restart dùng stack riêng có volume bền vững. Người dùng đã xác nhận P1.6
build/test thành công ngày 02/11/2026; xem [hướng dẫn P1.6](docs/P1.6-Verification.md).

Nếu chạy test từ IDE thay vì Docker runner, cần tự cung cấp `TEST_DATABASE_URL`,
`TEST_DATABASE_USERNAME=bpm_test`, `TEST_DATABASE_PASSWORD` trỏ tới DB PostgreSQL
test riêng tên `bpm_test`; URL phải có đúng `currentSchema` như trên và database
phải có sẵn hai schema. Profile test không có fallback về DB dev. Không chạy
nhiều test runner trên cùng DB; xem [hướng dẫn P1.4/P1.5](docs/P1.4-P1.5-Verification.md).

## 5. Cấu hình ứng dụng và schema

| File | Chức năng |
| --- | --- |
| `application.yaml` | Cấu hình chung, ánh xạ JPA vào schema `business`; Flowable dùng schema đầu tiên trong search path là `flowable`; tắt auto-update ngoài profile được chọn, graceful shutdown và health endpoint. Không đặt `flowable.database-schema` vì Flowable sẽ thu hẹp schema trên connection transaction dùng chung. |
| `application-dev.yaml` | Datasource từ các biến `DB_*` với search path `flowable,business,public`; JPA `ddl-auto=update`, Flowable `database-schema-update=true`. |
| `src/test/resources/application-test.yaml` | Datasource riêng từ `TEST_DATABASE_*` với cùng search path; JPA `create-drop` trong `business`, Flowable tạo/cập nhật bảng trong `flowable`. Không đóng profile test vào JAR runtime. |

Không thêm Flyway/Liquibase hoặc migration script trong giai đoạn dev. Tự cập nhật schema dev hỗ trợ vòng lặp phát triển; đổi tên/xóa trường hoặc thay đổi dữ liệu vẫn có thể cần reset dev hoặc xử lý dữ liệu có chủ đích. Chính sách nâng cấp schema cho dữ liệu thật hoàn thiện trước pilot/production.

Compose trực tiếp điều phối container. Đã bỏ dependency `spring-boot-docker-compose` để ứng dụng không tự dựng stack khác; bỏ H2/H2 console để dev/test dùng PostgreSQL. Giữ nguyên phiên bản Java/Spring Boot/Flowable. Thêm Actuator cho health check và starter test cho bộ kiểm thử.

Storage S3-compatible sẽ được bổ sung trước chức năng đính kèm; cấu hình hiện tại gồm backend và PostgreSQL.

## 6. Checklist xác nhận sau khi bạn chạy

- Compose dev build/khởi động thành công, backend và PostgreSQL healthy.
- Health endpoint trả `UP` và log có Flowable khởi tạo thành công.
- Dữ liệu DB/engine vẫn còn khi `down` rồi `up` không kèm `--volumes`.
- Test stack chạy thành công trong DB `bpm_test`; dữ liệu dev không thay đổi.
- Báo cáo Surefire được lấy ra trước khi xóa container test.

Người dùng đã xác nhận chạy xong Docker; kết quả test stack và kiểm tra persistence
sẽ được ghi nhận riêng tại [P1.2 trong kế hoạch](docs/Enterprise-BPM-Implementation-Plan.md).
Người dùng xác nhận P1.4/P1.5 và sau đó P1.6 đã build/test thành công ngày
02/11/2026; P1.6 đã chốt theo xác nhận này, bước tiếp theo là P2.1.

## 7. P1.3 — Module và hợp đồng API

Đã có ranh giới 8 module, hợp đồng phân trang/revision, ActorContext, DTO mapper,
policy và luồng đọc danh mục process qua controller/use case/port. Ngày 02/11/2026
đã sửa xử lý lỗi đầu vào, policy từ chối truy cập và tràn số phân trang; bổ sung test
source cho helper, use case và controller. JWT Bearer tự phát HS256, CORS, global
exception handler, response chung, Clock và correlation/logging đã được bổ sung ở
commit security trước đó.

Phần code P1.3 đã được rà soát tĩnh; bộ test hiện tại và build/dev đã được người
dùng xác nhận chạy thành công ngày 02/11/2026. P4.1 đã đăng ký route/bean process và nối adapter danh mục với persistence/danh tính thật; xem hợp đồng P4.1 ở trên.

- [Hướng dẫn từng phần](docs/P1.3-Manual-Implementation-Guide.md): code map, mục đích file, trạng thái code và phần để dành cho các bước sau.
- [Nền HTTP/security đã triển khai](docs/P1.3-API-Foundation.md): response, secret/issuer/audience/TTL, cách gửi Bearer token và lệnh kiểm tra.

Dev CORS mặc định cho `http://localhost:5173` và `http://127.0.0.1:5173`; thay bằng
`CORS_ALLOWED_ORIGINS` trong `.env` khi frontend chạy origin khác. Client gửi
`Authorization: Bearer <accessToken>`, không gửi cookie phiên. Dev có secret HS256
mặc định theo yêu cầu người dùng; thay `JWT_SECRET_KEY` trong `.env` khi cần.
`JWT_ISSUER=https://bpm.local`, `JWT_AUDIENCE=bpm-api`, `JWT_ACCESS_TOKEN_TTL=30m` là
baseline dev; `REFRESH_TOKEN_TTL=7d` là hạn tuyệt đối của login grant. Ngoài
dev/test phải cấp secret riêng. Source P2.1 đã nối login, `/me`, refresh/logout
và change-password; automated suite đã đạt ngày 03/11/2026. Full dev flow và restart
revocation smoke test vẫn đang chờ.

## 8. P1.4/P1.5 — schema, adapter và integration test

Đã bổ sung schema guard, fixture JPA/SQL và pipeline GitHub Actions tại
`.github/workflows/backend.yml`. Port/DTO ứng dụng và adapter Flowable hỗ trợ
deploy/start/query/claim/complete/history; fixture chỉ nằm trong test resources.
Test source bao phủ hai nhánh XOR, direct assignee, candidate user/group queue,
definition ID pinning và lịch sử. Chưa bật API runtime. Tại thời điểm chốt
P1.4/P1.5 chưa có bảng nghiệp vụ; model bổ sung sau đó được mô tả ở mục 10.

**P1.4/P1.5 đã được người dùng xác nhận đạt ngày 02/11/2026:** test và build/kiểm
tra dev thành công sau khi sửa dependency `spring-boot-starter-security-test`.
Agent không chạy lại; chưa có kết quả GitHub Actions. Xem [hướng dẫn nghiệm thu và code map](docs/P1.4-P1.5-Verification.md)
để chạy lại khi cần. P1.6 kiểm chứng rollback JPA + engine, claim/complete cạnh
tranh và restart khi task đang chờ; bước này cũng đã chốt theo xác nhận của người dùng.

## 9. P1.6 — transaction, concurrency và restart

Suite ở mục 4 bao gồm test rollback JPA + Flowable và claim/complete cạnh tranh.
Kiểm chứng restart chạy riêng trên stack PostgreSQL cách ly với dev:

```powershell
./scripts/Verify-P16Restart.ps1
```

**P1.6 đã chốt ngày 02/11/2026 theo xác nhận build/test thành công của người dùng.**
Agent không chạy lại; chưa có kết quả GitHub Actions. Bước tiếp theo là P2.1 — đăng
nhập và vòng đời JWT. [Hướng dẫn nghiệm thu P1.6](docs/P1.6-Verification.md) giữ các
lệnh, kết quả mong đợi và cách kiểm tra báo cáo để chạy lại khi cần.

## 10. Business data model

Đã thêm mapping JPA cho 21 bảng identity/RBAC, phiên đăng nhập/refresh, tổ chức,
process/form version, hồ sơ/tệp/bình luận/quyền bổ sung, task action log, audit
và thông báo. Dev tiếp tục tự tạo/cập nhật schema bằng Hibernate; không thêm
migration. Chưa thêm API nghiệp vụ hoặc nối authentication với các bảng mới.

Đã bổ sung test source cho schema, JSONB, unique/FK, revision và các CHECK.
**Ngày 03/11/2026, người dùng xác nhận build, test và dev thành công; chốt bước
business model/schema.** Agent không chạy lại; các lệnh ở mục 2 và 4 dùng khi
cần kiểm chứng lại. Kết quả này chưa chốt nghiệp vụ đăng nhập/vòng đời JWT P2.1.
Xem [mô hình dữ liệu và hướng dẫn kiểm chứng](docs/Business-Data-Model.md).

## 11. P2.1 — Hợp đồng xác thực

**P2.1a đã chốt ngày 03/11/2026:** access token dùng `sid`/`ver`, refresh token
opaque có hạn tuyệt đối 7 ngày và rotation/replay protection, quyền được đọc từ
DB hiện tại, cùng policy mật khẩu 12–128 Unicode code point. Đây là thay đổi tài
liệu nên không có build/test mới.

**P2.1b/P2.1c đã được người dùng xác nhận hoàn thành:** login tạo login grant và refresh credential hash, access JWT có
`sid`/`ver`, Bearer request đối chiếu ACTIVE/grant/version và đọc quyền hiện tại
từ DB, principal ứng dụng cấp `/me`, và gate mật khẩu tạm. Code,
cấu hình và test bootstrap tài khoản dev đã được xóa hoàn toàn. P2.1d–e đã có
implementation/test source cho refresh/logout, change-password và account revoke.
P2.1f đã hoàn thiện integration-test source cùng hướng dẫn nghiệm thu. Full
automated suite đã đạt ngày 03/11/2026; **P2.1d–f chưa nghiệm thu** vì restart
persistence và full dev flow còn chờ. Xem
[hướng dẫn P2.1](docs/P2.1-Manual-Implementation-Guide.md) và
[biên bản kiểm chứng](docs/P2.1-Verification.md).

Tham chiếu: [Docker Compose health/dependency](https://docs.docker.com/compose/how-tos/startup-order/), [PostgreSQL official image — volume PostgreSQL 18](https://hub.docker.com/_/postgres), [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Flowable schema configuration](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot).

P4.4 đã có [XML/metadata/DI round-trip](docs/P4.4-Artifact-Round-Trip.md) tại frontend, extension moddle, projection thống nhất, undo/cleanup metadata, mở BPMN/JSON và reject dữ liệu mâu thuẫn. Chưa validator server hoặc deploy engine.

P4.5 đã có [validator server/XML an toàn](docs/P4.5-Server-Validation.md), API validate, kiểm tra references/graph và compiler biểu thức nội bộ. Full backend 295 đạt + 1 skipped restart opt-in; full frontend 103 đạt, ba Postman lint đạt. Save/publish/lifecycle tiếp tục P4.6–P4.8.

P4.6 đã có [save draft theo revision và kiểm tra lỗi trên designer](docs/P4.6-Draft-Save.md). Full backend 296 đạt + 1 skipped restart opt-in, frontend 104 đạt; build/lint và Postman lint đạt.
