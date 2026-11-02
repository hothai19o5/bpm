# BPM — môi trường Docker dev/test

Cấu hình dùng Java 25, Spring Boot 4.1.2, Flowable 8.0.0 theo `pom.xml` và PostgreSQL 18.6. Backend, build và test chạy trong container; máy host cần Docker Desktop ở chế độ Linux containers và Docker Compose v2 có hỗ trợ `up --wait`.

**Trạng thái ngày 03/11/2026:** Người dùng xác nhận P1.4/P1.5, P1.6 và business model/schema đã build/test thành công. P2.1a đã chốt hợp đồng đăng nhập và vòng đời JWT; bước tiếp theo là P2.1b — persistence port/adapter, password hashing và fixture dev/test. Agent không chạy lại; chưa có kết quả GitHub Actions. Các lần build/test tiếp theo tiếp tục do người dùng chạy.

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

Kết quả mong đợi là `status: UP`. Nếu đổi `APP_PORT`, dùng cổng đã cấu hình. Health endpoint kiểm tra trạng thái ứng dụng và DB; chưa phải bằng chứng nghiệp vụ/transaction/concurrency đã đúng. Security dùng JWT Bearer stateless; API bảo vệ trả JSON 401 khi thiếu token. Chưa có màn hình, login nghiệp vụ hoặc tài khoản mặc định.

Xem log:

```powershell
docker compose logs -f backend postgres
```

Sau khi sửa source, chạy lại `docker compose up --build -d --wait --wait-timeout 180`. Source được đóng vào image, chưa cấu hình hot reload/bind mount source.

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
dùng xác nhận chạy thành công ngày 02/11/2026. Controller/config process đã có body nhưng chưa đăng ký route/
bean; adapter danh mục và danh tính thật tiếp tục ở các bước phụ thuộc schema/P2/P4.

- [Hướng dẫn từng phần](docs/P1.3-Manual-Implementation-Guide.md): code map, mục đích file, trạng thái code và phần để dành cho các bước sau.
- [Nền HTTP/security đã triển khai](docs/P1.3-API-Foundation.md): response, secret/issuer/audience/TTL, cách gửi Bearer token và lệnh kiểm tra.

Dev CORS mặc định cho `http://localhost:5173` và `http://127.0.0.1:5173`; thay bằng
`CORS_ALLOWED_ORIGINS` trong `.env` khi frontend chạy origin khác. Client gửi
`Authorization: Bearer <accessToken>`, không gửi cookie phiên. Dev có secret HS256
mặc định theo yêu cầu người dùng; thay `JWT_SECRET_KEY` trong `.env` khi cần.
`JWT_ISSUER=https://bpm.local`, `JWT_AUDIENCE=bpm-api`, `JWT_ACCESS_TOKEN_TTL=30m` là
baseline dev. Ngoài dev/test phải cấp secret riêng. Service phát token đã có;
API login/refresh/logout và trạng thái tài khoản sẽ được nối ở P2.1.

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
liệu nên không có build/test mới. Bước tiếp theo là P2.1b; xem
[hướng dẫn P2.1](docs/P2.1-Manual-Implementation-Guide.md).

Tham chiếu: [Docker Compose health/dependency](https://docs.docker.com/compose/how-tos/startup-order/), [PostgreSQL official image — volume PostgreSQL 18](https://hub.docker.com/_/postgres), [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Flowable schema configuration](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot).
