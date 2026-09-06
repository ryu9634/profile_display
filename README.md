# Portfolio Display

작가 포트폴리오 웹사이트와 관리자 페이지를 함께 제공하는 Spring Boot 애플리케이션입니다.
작품 이미지·영상·PDF를 카테고리별로 등록하고, 사이트의 색상·폰트·연락처까지 관리자 화면에서 직접 수정할 수 있습니다.

- 공개 사이트: `/` (메인 소개 + 최근 작품, 사이드바 카테고리, 작품 목록·상세 팝업)
  - 해시 라우팅: `#/카테고리ID`, `#/카테고리ID/게시글ID` — 새로고침·뒤로가기·링크 공유 가능
- 관리자 페이지: `/admin` (로그인 페이지 `/login.html`, `ROLE_ADMIN` 필요)
- 운영 도메인: https://jaehoonjeong.com

---

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어/런타임 | Java 11 (컴파일 타깃), 빌드·배포 스크립트는 JDK 17 사용 |
| 프레임워크 | Spring Boot 2.7.18 (Web, Data JPA, Security, Validation, Actuator) |
| 데이터베이스 | MariaDB (HikariCP 커넥션 풀) |
| 빌드 | Maven (`spring-boot-maven-plugin`), Lombok |
| 프론트엔드 | 정적 HTML/CSS/Vanilla JS (`src/main/resources/static`) |
| 외부 라이브러리 | Quill.js(리치 텍스트), PDF.js(CV 렌더링), YouTube 임베드 |
| 인프라 | AWS EC2 + Nginx 리버스 프록시 + systemd |

---

## 주요 기능

**콘텐츠 관리**
- 카테고리 CRUD — `PHOTO` / `ARTICLE` / `HTML` 3가지 타입, 기본 카테고리(`main`, `artwork`, `cv`)는 최초 실행 시 자동 생성
- 게시글 CRUD — 제목(필수)·연도·재료·크기·설명 및 다중 이미지 관리 (PHOTO는 미디어 목록의 첫 이미지가 썸네일)
- 게시글·카테고리 순서 변경(`PUT /reorder`), 미디어 순서 변경, 이미지별 설명/표시 위치 옵션
- 상세 팝업에서 이전/다음 작품 이동(← → 키), 라이트박스 스와이프, 키보드 접근성
- YouTube 영상 URL 임베드, CV 카테고리의 PDF 페이지 내 직접 렌더링
- 낙관적 락(`@Version`)으로 동시 수정 충돌 방지

**파일 업로드**
- 허용 확장자: `.jpg .jpeg .png .gif .webp .svg .pdf`
- 확장자 + MIME 타입 이중 검증, 최대 10MB, UUID 파일명으로 저장
- JPG/PNG 업로드 시 목록용 축소본(`<이름>_thumb.<확장자>`, 최대 900px, EXIF 회전 보정) 자동 생성 — 없으면 프론트가 원본으로 폴백
- 게시글 수정 시 여전히 참조되는 파일은 삭제하지 않음, 삭제 시 축소본도 함께 정리
- 경로 탈출(`..`) 차단 및 삭제 시 저장 경로 밖 접근 차단

**사이트 설정** (`/api/settings`)
- 사이트 제목/부제, 메인 페이지 소개 문구·최근 작품 표시 여부, 배경 색상·이미지·반복·고정 방식, 텍스트/사이드바 색상
- 관리자 화면에서 색상·폰트 실시간 미리보기, Google Fonts 프리셋
- 커스텀 웹폰트 URL(제목/본문), 로고·파비콘
- 연락처, 소셜 링크(Instagram / Behance / Email / Website / LinkedIn), 푸터 문구

**보안**
- Spring Security 폼 로그인(`/login.html`) + HTTP Basic(API 클라이언트용)
- 관리자 계정은 DB(`admin_account`)에 BCrypt 해시로 저장. 최초 실행 시 `admin.username`/`admin.password`로 생성되고, 관리자 페이지 **계정 · 비밀번호** 메뉴에서 아이디/비밀번호 변경 (`POST /api/account/change`)
- 비밀번호 분실 시 `ADMIN_RESET_PASSWORD=true`로 한 번 재시작하면 설정값으로 초기화 (초기화 후 다시 `false`)
- 관리자 화면의 XHR(`X-Requested-With`) 요청은 브라우저 팝업 없이 401 JSON을 받고 로그인 페이지로 안내
- 읽기(GET) API와 정적 리소스는 공개, 쓰기(POST/PUT/DELETE)는 `ROLE_ADMIN` 전용
- CSP 헤더 설정, 프로파일별 CORS 허용 도메인 분리
- `GlobalExceptionHandler`로 예외를 `ErrorResponse` 형식으로 통일

---

## 프로젝트 구조

```
.
├── pom.xml
├── deploy.sh                   # 로컬 → EC2 원격 배포 (빌드 + scp + systemctl restart)
├── deploy/
│   ├── deploy.sh               # EC2 내부 배포 스크립트 (백업 + 재시작 + 상태 확인)
│   ├── nginx.conf              # Nginx 리버스 프록시 / uploads 직접 서빙 설정
│   └── portfolio.service       # systemd 유닛 파일
├── docs/                       # 배포·보안 가이드 (아래 문서 목록 참고)
└── src/main/
    ├── java/com/portfolio/
    │   ├── PortfolioApplication.java
    │   ├── config/             # SecurityConfig, WebConfig, DataLoader, GlobalExceptionHandler
    │   ├── controller/         # Category, Post, File, SiteSettings, Health, Home
    │   ├── dto/                # 요청 DTO + ErrorResponse
    │   ├── model/              # Category, Post, PostImage, SiteSettings, ContentType
    │   ├── repository/         # Spring Data JPA 리포지토리
    │   └── service/            # Category, Post, SiteSettings, FileStorage
    └── resources/
        ├── application.properties          # 공통 설정
        ├── application-local.properties    # 로컬 개발 (portfolio_dev)
        ├── application-prod.properties     # 운영 (portfolio_prod, 환경 변수 사용)
        └── static/                         # index.html, admin.html, scripts/, styles/
```

---

## 실행 방법

### 사전 준비

- JDK 11 이상 (운영 서버는 17)
- Maven 3.6 이상
- MariaDB 10.x — 로컬 DB `portfolio_dev` (스키마는 `ddl-auto=update`로 자동 생성)

### 로컬 실행

```bash
mvn spring-boot:run
```

기본 프로파일은 `local`이며 포트는 **8081**입니다.

- 사이트: http://localhost:8081
- 관리자: http://localhost:8081/admin
- 헬스체크: http://localhost:8081/api/health

DB 접속 정보는 [application-local.properties](src/main/resources/application-local.properties)에서 수정하세요 (기본값 `root` / `1234`).

### JAR 빌드 및 실행

```bash
mvn clean package -DskipTests
```

```bash
java -jar -Dspring.profiles.active=prod target/portfolio-backend-1.0.0.jar
```

---

## 설정 값

| 항목 | 키 | 기본값 |
| --- | --- | --- |
| 서버 포트 | `server.port` | local `8081` / prod `8080` |
| 업로드 경로 | `file.upload-dir` | local `./uploads` / prod `/opt/portfolio/uploads` |
| 업로드 최대 크기 | `spring.servlet.multipart.max-file-size` | `10MB` |
| CORS 허용 도메인 | `cors.allowed-origins` | 프로파일별 지정 |
| 관리자 계정 초기값 | `admin.username` / `admin.password` | 최초 실행 시 DB에 계정 생성용. 이후엔 관리자 페이지에서 변경 |
| 비밀번호 초기화 | `admin.reset-password` (`ADMIN_RESET_PASSWORD`) | `true`로 한 번 재시작하면 위 값으로 초기화. 평소엔 `false` |

운영 환경에서는 다음 환경 변수를 반드시 설정해야 합니다.

```bash
export DB_USERNAME=portfolio
export DB_PASSWORD=your_db_password
export ADMIN_USERNAME=your_admin_id
export ADMIN_PASSWORD=your_admin_password
```

> ⚠️ `application.properties`에는 개발 편의를 위한 기본 계정(`admin` / `admin123`)이 들어 있습니다.
> 이 값은 공개 저장소에 노출된 상태이므로 운영 환경에서는 반드시 환경 변수로 덮어써서 사용하세요.

---

## API

인증이 필요한 요청은 HTTP Basic 헤더 또는 폼 로그인 세션 쿠키를 사용합니다. 오류는 `{status, message, timestamp}` 형식으로 응답합니다.

### 카테고리 `/api/categories`

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/categories` | - | 전체 카테고리 조회 |
| GET | `/api/categories/{id}` | - | 단일 카테고리 조회 |
| GET | `/api/categories/custom` | - | 사용자 생성 카테고리만 조회 |
| POST | `/api/categories` | ADMIN | 카테고리 생성 (id는 소문자·숫자·하이픈만) |
| GET | `/api/categories/{id}/post-count` | ADMIN | 카테고리 내 게시글 수 |
| PUT | `/api/categories/reorder` | ADMIN | 순서 변경 (`{"ids": [...]}`) |
| PUT | `/api/categories/{id}` | ADMIN | 카테고리 수정 |
| DELETE | `/api/categories/{id}` | ADMIN | 카테고리 삭제 |

### 게시글 `/api/posts`

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/posts` | - | 전체 게시글 조회 |
| GET | `/api/posts/{id}` | - | 단일 게시글 조회 |
| PUT | `/api/posts/reorder` | ADMIN | 순서 변경 (`{"ids": [...]}`) |

### 관리자 계정 `/api/account`

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/account` | ADMIN | 현재 아이디, 비밀번호 변경 시각, 초기 비밀번호 사용 여부 |
| POST | `/api/account/change` | ADMIN | `{currentPassword, newUsername?, newPassword?}` — 아이디 변경 시 재로그인 필요 |
| GET | `/api/posts/category/{categoryId}` | - | 카테고리별 게시글 조회 |
| POST | `/api/posts` | ADMIN | 게시글 생성 |
| PUT | `/api/posts/{id}` | ADMIN | 게시글 수정 |
| DELETE | `/api/posts/{id}` | ADMIN | 게시글 삭제 |

### 파일 `/api/files`

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/files/upload` | ADMIN | 파일 업로드 (`multipart/form-data`, 필드명 `file`) |
| GET | `/api/files/{fileName}` | - | 파일 조회 |
| DELETE | `/api/files/{fileName}` | ADMIN | 파일 삭제 |

업로드 응답 예시:

```json
{ "fileName": "e2c1...c9.jpg", "fileUrl": "/api/files/e2c1...c9.jpg" }
```

### 사이트 설정 · 헬스체크

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/settings` | - | 사이트 설정 조회 |
| POST | `/api/settings` | ADMIN | 사이트 설정 저장 |
| GET | `/api/health` | - | 앱 및 DB 연결 상태 (`UP` / `DEGRADED`) |

---

## 배포

로컬에서 빌드 후 EC2로 한 번에 배포합니다.

```bash
./deploy.sh
```

스크립트 내부의 `PROJECT_DIR`, `PEM_KEY`, `SERVER` 값은 각자 환경에 맞게 수정해야 합니다.
서버에서는 systemd 서비스(`portfolio`)가 `prod` 프로파일, 8080 포트로 애플리케이션을 실행하고,
Nginx가 80/443을 받아 프록시하며 `/uploads/`와 `/api/files/`는 파일 시스템에서 직접 서빙합니다.

```bash
sudo systemctl status portfolio
```

```bash
sudo journalctl -u portfolio -f
```

---

## 문서

| 문서 | 내용 |
| --- | --- |
| [QUICK_START_AWS.md](docs/QUICK_START_AWS.md) | AWS EC2 10분 빠른 배포 |
| [AWS_EC2_DEPLOYMENT_GUIDE.md](docs/AWS_EC2_DEPLOYMENT_GUIDE.md) | EC2 배포 상세 가이드 |
| [DEPLOY_GUIDE.md](docs/DEPLOY_GUIDE.md) | 서버 접속 및 유지보수 |
| [SECURITY_SETUP.md](docs/SECURITY_SETUP.md) | Spring Security 설정 |
| [INTEGRATION_SUMMARY.md](docs/INTEGRATION_SUMMARY.md) | 프로젝트 통합·개선 요약 |
| [UPGRADE_SUMMARY.md](docs/UPGRADE_SUMMARY.md) | 업그레이드 작업 요약 |
| [CLEANUP_SUMMARY.md](docs/CLEANUP_SUMMARY.md) | 프로젝트 정리 내역 |
