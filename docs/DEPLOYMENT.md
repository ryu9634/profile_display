# 배포 가이드

AWS EC2에 배포하는 방법입니다. Nginx를 앞에 두고 Spring Boot 애플리케이션을
systemd 서비스로 운영하는 구성입니다.

```
인터넷 → Nginx (80/443) → Spring Boot (8080) → MariaDB (3306)
                ↓
         업로드 파일 직접 서빙
```

---

## 1. 서버 준비

Ubuntu 기준입니다.

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mariadb-server nginx
```

데이터베이스와 계정을 만듭니다.

```bash
sudo mysql -e "CREATE DATABASE portfolio_prod CHARACTER SET utf8mb4"
sudo mysql -e "CREATE USER 'portfolio'@'localhost' IDENTIFIED BY '원하는_비밀번호'"
sudo mysql -e "GRANT ALL ON portfolio_prod.* TO 'portfolio'@'localhost'"
```

배포 디렉토리를 만듭니다.

```bash
sudo mkdir -p /opt/portfolio/uploads /opt/portfolio/backups
sudo chown -R ubuntu:ubuntu /opt/portfolio
```

---

## 2. systemd 서비스 등록

`deploy/portfolio.service` 를 서버로 복사합니다.

```bash
sudo cp portfolio.service /etc/systemd/system/
```

비밀번호는 유닛 파일에 직접 적지 말고 override 파일에 넣습니다. 이 파일은
root만 읽을 수 있게 권한을 제한합니다.

```bash
sudo systemctl edit portfolio
```

```ini
[Service]
Environment="DB_PASSWORD=실제_DB_비밀번호"
Environment="ADMIN_USERNAME=원하는_관리자_아이디"
Environment="ADMIN_PASSWORD=원하는_초기_비밀번호"
Environment="ADMIN_RESET_PASSWORD=false"
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable portfolio
```

`ADMIN_USERNAME` 과 `ADMIN_PASSWORD` 는 최초 실행 시 관리자 계정을 만들 때만
쓰입니다. 그 뒤로는 관리자 페이지에서 변경한 값이 DB에 저장됩니다.

---

## 3. Nginx 설정

`deploy/nginx.conf` 를 참고해 작성합니다.

```bash
sudo cp nginx.conf /etc/nginx/sites-available/portfolio
sudo ln -s /etc/nginx/sites-available/portfolio /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

`server_name` 을 실제 도메인으로 바꿔야 합니다.

HTTPS는 Certbot으로 발급합니다.

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d example.com -d www.example.com
```

> **HTTPS는 선택이 아닙니다.** 운영 설정은 세션 쿠키에 `Secure` 를 붙이므로,
> 평문 HTTP로 접속하면 쿠키가 전달되지 않아 로그인이 유지되지 않습니다.
> `deploy/nginx.conf` 에 HTTP → HTTPS 리다이렉트가 포함되어 있습니다.

`proxy_set_header X-Forwarded-For` 와 `X-Forwarded-Proto` 를 반드시 넘겨야 합니다.
애플리케이션이 이 헤더로 원래 클라이언트 IP를 판단하는데, 없으면 로그인 시도 제한이
모든 접속을 Nginx의 IP 하나로 묶어 버립니다.

---

## 4. 배포

### GitHub Actions

저장소 Settings → Secrets and variables → Actions 에 `EC2_HOST` 와 `EC2_SSH_KEY`
를 등록하면, `main` 브랜치에 push할 때마다 자동 배포됩니다.

GitHub 러너의 IP는 고정되어 있지 않습니다. 보안 그룹에서 22번 포트를 특정 IP로
제한하고 있으면 접속이 막히므로, 그런 경우에는 아래 로컬 스크립트를 사용하세요.

### 로컬 스크립트

```bash
cp deploy.env.example deploy.env
# deploy.env 에 SERVER 와 PEM_KEY 입력 (이 파일은 git에 올라가지 않습니다)

./deploy.sh
```

두 방식 모두 같은 순서로 동작합니다.

1. Maven 빌드
2. JAR을 서버로 전송
3. 기존 JAR을 `backups/` 에 백업
4. `/opt/portfolio/static` 에 남은 예전 화면 파일 정리
5. 서비스 재시작
6. `/api/health` 확인, 실패하면 서버 로그 출력

4번이 필요한 이유는 운영 설정이 `file:/opt/portfolio/static/` 을 JAR 안의
정적 파일보다 먼저 참조하기 때문입니다. 이 폴더에 예전 파일이 남아 있으면
새 화면이 배포돼도 반영되지 않습니다.

---

## 문제가 생겼을 때

```bash
# 서비스 상태
sudo systemctl status portfolio

# 실시간 로그
sudo journalctl -u portfolio -f

# 헬스체크
curl http://localhost:8080/api/health
```

`{"status":"UP","database":"UP"}` 가 나오면 정상입니다. `database` 가 `DOWN` 이면
DB가 떠 있는지, `DB_PASSWORD` 가 맞는지 확인하세요.

### 관리자 비밀번호를 잊었을 때

```bash
sudo systemctl edit portfolio     # ADMIN_RESET_PASSWORD=true 로 변경
sudo systemctl restart portfolio  # 이때 ADMIN_PASSWORD 값으로 초기화됨
```

초기화된 뒤에는 반드시 `false` 로 되돌리고 다시 시작하세요. `true` 로 두면
재시작할 때마다 계정이 초기값으로 덮어써집니다.

### 이전 버전으로 되돌리기

```bash
ls /opt/portfolio/backups
sudo cp /opt/portfolio/backups/portfolio-backend-1.0.0.jar.20260101_120000 \
        /opt/portfolio/portfolio-backend-1.0.0.jar
sudo systemctl restart portfolio
```
