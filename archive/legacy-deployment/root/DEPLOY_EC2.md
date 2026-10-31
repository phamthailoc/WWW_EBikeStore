# Deploy Backend Len EC2

Tai lieu nay tom tat cac buoc can lam de deploy backend len EC2 bang GitHub Actions va Docker Compose.

## 1. File dang duoc dung de deploy

Workflow deploy hien tai:

```text
.github/workflows/deploy-ec2.yml
```

Workflow nay se deploy bang file compose:

```text
backend/docker-compose.ec2.yml
```

Luu y: `backend/docker-compose.scale.yml` la cau hinh scale nhieu backend qua nginx, nhung hien dang hardcode password/JWT nen chua nen dung cho production neu chua sua sang doc tu `.env`.

## 2. Chuan bi EC2

SSH vao EC2 Ubuntu:

```bash
ssh ubuntu@EC2_PUBLIC_IP
```

Cai Docker va Docker Compose plugin:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker
docker --version
docker compose version
```

Mo Security Group tren AWS:

```text
SSH: 22
Backend API: 8080
```

Neu muon public backend o port khac, dat bien `BACKEND_PUBLIC_PORT` trong env file.

## 3. Tao SSH key cho GitHub Actions

Tren may local:

```bash
ssh-keygen -t ed25519 -C "github-actions-ebike" -f ebike_ec2_key
```

Copy public key len EC2:

```bash
ssh-copy-id -i ebike_ec2_key.pub ubuntu@EC2_PUBLIC_IP
```

Neu khong co `ssh-copy-id`, copy noi dung file `ebike_ec2_key.pub` vao EC2:

```text
~/.ssh/authorized_keys
```

Private key `ebike_ec2_key` se duoc luu vao GitHub Secret `EC2_SSH_KEY`.

## 4. Them GitHub Secrets

Vao GitHub repository:

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

Can them cac secret sau:

```text
EC2_HOST=public IP hoac domain cua EC2
EC2_USER=ubuntu
EC2_SSH_PORT=22
EC2_SSH_KEY=noi dung private key ebike_ec2_key
EC2_BACKEND_ENV_FILE=noi dung file env production cho backend
```

Vi du noi dung `EC2_BACKEND_ENV_FILE`:

```env
POSTGRES_USER=ebike_user
POSTGRES_PASSWORD=doi-mat-khau-manh
POSTGRES_DB=ebike_db

REDIS_PASSWORD=doi-redis-password-manh

APP_JWT_SECRET=chuoi-random-dai-toi-thieu-32-ky-tu
SPRING_PROFILES_ACTIVE=prod
BACKEND_PUBLIC_PORT=8080
```

Khong dung password mac dinh trong production.

## 5. Chay deploy

Vao GitHub:

```text
Actions -> Deploy Backend to EC2 -> Run workflow
```

Workflow se tu dong:

```text
1. Checkout source code
2. Nen source thanh archive
3. Upload source va env file len EC2
4. Giai nen vao ~/e-bike-multiplatform
5. Ghi file backend/.env
6. Chay docker compose -f docker-compose.ec2.yml up -d --build
7. Kiem tra health endpoint
```

## 6. Kiem tra sau deploy

SSH vao EC2:

```bash
ssh ubuntu@EC2_PUBLIC_IP
cd ~/e-bike-multiplatform/backend
docker compose -f docker-compose.ec2.yml ps
docker compose -f docker-compose.ec2.yml logs -f backend
```

Kiem tra API tu may local:

```bash
curl http://EC2_PUBLIC_IP:8080/api/v1/health/ready
```

Neu endpoint tra ve HTTP 200 thi backend da deploy thanh cong.

## 7. Lenh restart/update thu cong tren EC2

Neu can restart:

```bash
cd ~/e-bike-multiplatform/backend
docker compose -f docker-compose.ec2.yml restart
```

Neu can build lai thu cong:

```bash
cd ~/e-bike-multiplatform/backend
docker compose -f docker-compose.ec2.yml up -d --build
```

Neu can xem log loi:

```bash
cd ~/e-bike-multiplatform/backend
docker compose -f docker-compose.ec2.yml logs --tail=200 backend
docker compose -f docker-compose.ec2.yml logs --tail=200 postgres
docker compose -f docker-compose.ec2.yml logs --tail=200 redis
```

## 8. Thu tu viec can lam tiep

```text
1. Tao hoac chuan bi EC2.
2. Cai Docker tren EC2.
3. Mo port 22 va 8080 trong Security Group.
4. Tao SSH key deploy.
5. Them GitHub Secrets.
6. Chay workflow Deploy Backend to EC2.
7. Test health endpoint.
```
