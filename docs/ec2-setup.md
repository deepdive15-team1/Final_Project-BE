# EC2 서버 초기 설정 가이드 (Ubuntu, ARM t4g.micro)

## 1. JDK 17 설치

```bash
sudo apt update
sudo apt install -y wget gnupg software-properties-common

# Amazon Corretto 17 (ARM 지원)
wget -O - https://apt.corretto.aws/corretto.key | sudo gpg --dearmor -o /usr/share/keyrings/corretto.gpg
echo "deb [signed-by=/usr/share/keyrings/corretto.gpg] https://apt.corretto.aws stable main" | sudo tee /etc/apt/sources.list.d/corretto.list
sudo apt update
sudo apt install -y java-17-amazon-corretto-jdk

# 설치 확인
java -version
```

## 2. 앱 디렉토리 생성

```bash
mkdir -p ~/app/secrets
chmod 700 ~/app/secrets
```

FCM 서비스 계정 복구에는 `jq`가 필요하다.

```bash
sudo apt update
sudo apt install -y jq
```

## 3. systemd 서비스 등록

```bash
sudo tee /etc/systemd/system/runspot.service > /dev/null << 'EOF'
[Unit]
Description=Runspot Spring Boot Application
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/app
EnvironmentFile=/home/ubuntu/app/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -jar /home/ubuntu/app/server.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

위 예시의 `ubuntu`는 GitHub Secret `EC2_USERNAME`의 실제 값으로 일관되게 바꾼다. 서비스 사용자, `WorkingDirectory`, `EnvironmentFile`, `ExecStart` 경로가 모두 같은 사용자 홈 디렉터리를 가리켜야 한다.

## 4. 서비스 활성화

```bash
sudo systemctl daemon-reload
sudo systemctl enable runspot
```

## 5. GitHub Secrets 설정

GitHub 레포 → Settings → Secrets and variables → Actions 에서 아래 항목 등록:

| Secret 이름 | 값 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USERNAME` | `ubuntu` |
| `EC2_SSH_KEY` | EC2 접속용 PEM 키 파일 내용 (-----BEGIN ~ -----END 포함) |
| `DB_URL` | `jdbc:mysql://host:3306/runspot` |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JJWT 서명에 사용하는 Base64 인코딩 시크릿 |
| `SENTRY_DSN` | Sentry 프로젝트 DSN |
| `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` | 줄바꿈 없는 Firebase 서비스 계정 JSON의 Base64 값 |

`FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`는 서비스 계정 JSON 자체가 아니다. JSON 파일은 저장소, `.env`, CI 로그에 넣지 않는다. 안전한 관리 단말에서 GitHub CLI로 줄바꿈 없는 값을 바로 등록한다.

```bash
base64 -w 0 /secure/path/firebase-service-account.json | gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_BASE64
```

배포는 이 Secret을 출력하지 않고 EC2의 `/home/<EC2_USERNAME>/app/secrets/firebase-service-account.json`에만 복구한다. 복구 헬퍼는 같은 디렉터리에 임시 파일을 만들고, Base64 디코드와 `jq` 서비스 계정 스키마 검증을 통과한 뒤 `600` 권한으로 원자적으로 이름을 바꾼다. 따라서 잘못된 Secret, `jq` 누락, 디코드 실패, JSON 검증 실패 시 재시작하지 않으며 기존 정상 파일은 유지된다.

FCM에 필요한 `.env` 항목은 다음 두 개뿐이다. JSON, Base64, 개인 키를 `.env`에 넣지 않는다.

```properties
GOOGLE_APPLICATION_CREDENTIALS=/home/<EC2_USERNAME>/app/secrets/firebase-service-account.json
PUSH_FCM_ENABLED=true
```

`GOOGLE_APPLICATION_CREDENTIALS`는 Firebase Admin SDK가 Application Default Credentials(ADC) 경로로 서비스 계정 파일을 읽게 한다.

### 서비스 계정 회전, 폐기, 롤백

1. 새 서비스 계정 키를 발급하고 이전 명령으로 `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`를 교체한다. Secret 값이나 JSON을 터미널 출력, 커밋, 이슈에 붙이지 않는다.
2. `main` 배포를 실행해 새 파일 복구와 서비스 시작을 확인한다. EC2에서는 파일 소유자가 서비스 사용자이고 권한이 `600`인지 확인한다.
3. 정상 배포를 확인한 뒤에만 Firebase 또는 Google Cloud Console에서 이전 키를 폐기한다.
4. 새 Secret이 잘못되었으면 이전의 검증된 Base64 Secret을 다시 등록하고 배포를 다시 실행한다. 배포 중 검증 실패했다면 이전 EC2 파일은 그대로이므로 수동 삭제나 덮어쓰기를 하지 않는다.

### FCM 배포 문제 해결

- 재시작 전에 GitHub Actions 로그에서 복구 단계가 실패했는지 확인한다. Secret 원문은 로그에 남지 않아야 한다.
- EC2에서 `jq --version`, `sudo systemctl status runspot`, `sudo journalctl -u runspot -n 100`으로 의존성 및 ADC 초기화 오류를 확인한다.
- `GOOGLE_APPLICATION_CREDENTIALS`가 `/home/<EC2_USERNAME>/app/secrets/firebase-service-account.json`을 가리키는지, 디렉터리가 `700`, 파일이 `600`인지 확인한다.
- 배포 실패 시 임시 `.firebase-service-account.json.*` 파일이나 기존 정상 파일을 지우지 않는다. GitHub Secret을 바로잡은 뒤 다시 배포하면 검증 완료 후에만 교체된다.

## 6. 유용한 운영 명령어

```bash
# 서비스 상태 확인
sudo systemctl status runspot

# 로그 실시간 확인
sudo journalctl -u runspot -f

# 수동 재시작
sudo systemctl restart runspot

# 수동 중지
sudo systemctl stop runspot
```
