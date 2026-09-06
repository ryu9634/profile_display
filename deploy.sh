#!/bin/bash

# 포트폴리오 배포 스크립트
# 사용법: ./deploy.sh

set -e

# 설정
PROJECT_DIR="/Users/ryu/Desktop/PJ_jeung/printPP"
PEM_KEY="/Users/ryu/Downloads/profileJ.pem"
SERVER="ubuntu@13.54.153.158"
JAR_NAME="portfolio-backend-1.0.0.jar"

# Java 17 설정
export JAVA_HOME=$(brew --prefix openjdk@17)
export PATH="$JAVA_HOME/bin:$PATH"

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
