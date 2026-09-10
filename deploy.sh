#!/bin/bash

# 포트폴리오 배포 스크립트
# 사용법: ./deploy.sh

set -e

# ============================================
# 설정
#  경로를 고정하지 않습니다. 이 스크립트가 놓인 폴더를 프로젝트 폴더로 보므로
#  저장소를 어디에 두든 그대로 동작합니다.
#  키 위치나 서버가 다르면 환경 변수로 덮어쓸 수 있습니다.
#    예) PEM_KEY=~/keys/my.pem ./deploy.sh
# ============================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="${SERVER:-ubuntu@13.54.153.158}"
JAR_NAME="portfolio-backend-1.0.0.jar"

# SSH 키: 환경 변수가 없으면 흔한 위치를 순서대로 찾습니다
if [ -z "${PEM_KEY:-}" ]; then
    for candidate in \
        "$HOME/Downloads/profileJ.pem" \
        "$HOME/.ssh/profileJ.pem" \
        "$HOME/keys/profileJ.pem" \
        "$PROJECT_DIR/profileJ.pem"; do
        if [ -f "$candidate" ]; then
            PEM_KEY="$candidate"
            break
        fi
    done
fi

if [ -z "${PEM_KEY:-}" ] || [ ! -f "$PEM_KEY" ]; then
    echo "[오류] SSH 키 파일(profileJ.pem)을 찾지 못했습니다."
    echo "       키 위치를 직접 알려주세요:"
    echo "         PEM_KEY=/키/파일/경로.pem ./deploy.sh"
    exit 1
fi

# 권한이 느슨하면 ssh 가 키를 거부합니다
chmod 400 "$PEM_KEY" 2>/dev/null || true

echo "프로젝트 폴더: $PROJECT_DIR"
echo "SSH 키:        $PEM_KEY"
echo "서버:          $SERVER"

# Java 17 설정 (brew 로 설치된 경우에만 적용)
if command -v brew >/dev/null 2>&1 && brew --prefix openjdk@17 >/dev/null 2>&1; then
    export JAVA_HOME="$(brew --prefix openjdk@17)"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "[오류] mvn(Maven)을 찾을 수 없습니다. 설치 후 다시 실행해주세요."
    exit 1
fi


echo "========================================="
echo "  포트폴리오 배포 시작"
echo "========================================="

# 1. 빌드
echo ""
echo "[1/3] Maven 빌드 중..."
cd "$PROJECT_DIR"
mvn clean package -DskipTests -q
echo "  -> 빌드 완료!"

# 2. JAR 전송
echo ""
echo "[2/3] JAR 서버 전송 중..."
scp -i "$PEM_KEY" -o StrictHostKeyChecking=no "target/$JAR_NAME" "$SERVER:/tmp/"
echo "  -> 전송 완료!"

# 3. 서버 배포 & 재시작
echo ""
echo "[3/3] 서버 배포 및 재시작 중..."
ssh -i "$PEM_KEY" -o StrictHostKeyChecking=no "$SERVER" "bash -s" <<REMOTE
set -e
STAMP=\$(date +%Y%m%d_%H%M%S)
sudo mkdir -p /opt/portfolio/backups

# 1) 기존 JAR 백업 (문제가 생기면 되돌릴 수 있도록)
if [ -f /opt/portfolio/$JAR_NAME ]; then
  sudo cp /opt/portfolio/$JAR_NAME /opt/portfolio/backups/$JAR_NAME.\$STAMP
  echo "  백업: /opt/portfolio/backups/$JAR_NAME.\$STAMP"
fi

# 2) 서버에 남은 예전 정적 파일이 JAR 안의 새 화면을 덮어쓰지 않도록 치웁니다.
#    (application-prod 의 static-locations 가 file:/opt/portfolio/static/ 를 먼저 봅니다)
if [ -d /opt/portfolio/static ] && [ -n "\$(ls -A /opt/portfolio/static 2>/dev/null)" ]; then
  sudo mv /opt/portfolio/static /opt/portfolio/backups/static.\$STAMP
  echo "  예전 정적 파일을 backups/static.\$STAMP 로 옮김"
fi

# 3) 새 JAR 설치 후 재시작
sudo cp /tmp/$JAR_NAME /opt/portfolio/
sudo systemctl restart portfolio

# 4) 헬스체크 (최대 90초)
for i in \$(seq 1 30); do
  sleep 3
  if curl -sf http://localhost:8080/api/health | grep -q '"status":"UP"'; then
    echo "  헬스체크 통과 (\$((i*3))초)"
    exit 0
  fi
done
echo "  헬스체크 실패. 최근 로그:"
sudo journalctl -u portfolio -n 60 --no-pager
exit 1
REMOTE
echo "  -> 재시작 완료!"

echo ""
echo "========================================="
echo "  배포 완료! https://jaehoonjeong.com"
echo "========================================="
