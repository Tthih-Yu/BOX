# 物料拉动系统启动与部署说明

本文档适用于 Ubuntu 系统从零配置环境、启动本地开发服务，以及进行生产部署。

项目根目录：

```text
/home/wan/IE
```

项目包含：

```text
backend   Spring Boot 后端
frontend  Vue 3 + Vite 前端
deploy    Nginx 与 systemd 部署配置
docs      SQL 脚本、导入模板、文档资源
```

---

## 一、从零安装基础环境

### 1. 更新系统

```bash
sudo apt update
sudo apt upgrade -y
```

### 2. 安装 Java 17

```bash
sudo apt install -y openjdk-17-jdk
```

检查版本：

```bash
java -version
javac -version
```

要求 Java 版本为 17。

### 3. 安装 Maven

```bash
sudo apt install -y maven
```

检查版本：

```bash
mvn -version
```

### 4. 安装 Node.js 20 和 npm

```bash
sudo apt install -y curl ca-certificates
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

检查版本：

```bash
node -v
npm -v
```

建议 Node.js 为 20.x。

---

## 二、本地开发模式启动

本地开发模式最简单，后端默认使用 H2 文件数据库，不需要先安装 MySQL。

### 1. 启动后端

打开第一个终端：

```bash
cd /home/wan/IE/backend
```

设置首次管理员密码：

```bash
export MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD='Admin123456'
```

密码要求：不少于 10 位，并且同时包含字母和数字。

启动后端：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端默认地址：

```text
http://localhost:8080/api
```

Swagger 地址：

```text
http://localhost:8080/api/swagger-ui.html
```

H2 控制台地址：

```text
http://localhost:8080/api/h2-console
```

### 2. 启动前端

打开第二个终端：

```bash
cd /home/wan/IE/frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

登录信息：

```text
用户名：admin
密码：Admin123456
```

如果你修改了 `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD`，则使用你自己设置的密码。

---

## 三、局域网访问开发服务

如果需要让局域网其他电脑访问前端，使用：

```bash
cd /home/wan/IE/frontend
npm run dev:lan
```

查看本机 IP：

```bash
hostname -I
```

假设 IP 是 `192.168.1.50`，局域网访问地址为：

```text
http://192.168.1.50:5173
```

如果防火墙阻止访问，可以放行端口：

```bash
sudo ufw allow 5173/tcp
sudo ufw allow 8080/tcp
```

---

## 四、常见加速配置

### 1. Maven 下载慢

创建 Maven 配置目录：

```bash
mkdir -p ~/.m2
```

编辑配置：

```bash
nano ~/.m2/settings.xml
```

写入：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### 2. npm 下载慢

```bash
npm config set registry https://registry.npmmirror.com
```

然后重新执行：

```bash
cd /home/wan/IE/frontend
npm install
```

---

## 五、本地打包运行

### 1. 后端打包

```bash
cd /home/wan/IE/backend
mvn clean package -DskipTests
```

打包结果：

```text
/home/wan/IE/backend/target/material-pull-system-backend-0.8.4.jar
```

运行 jar：

```bash
java -jar target/material-pull-system-backend-0.8.4.jar
```

使用开发模式运行 jar：

```bash
java -jar target/material-pull-system-backend-0.8.4.jar --spring.profiles.active=dev
```

### 2. 前端打包

```bash
cd /home/wan/IE/frontend
npm install
npm run build
```

打包结果：

```text
/home/wan/IE/frontend/dist
```

本地预览：

```bash
npm run preview
```

默认预览地址：

```text
http://localhost:4173
```

---

## 六、生产部署环境安装

生产部署建议使用：

- MySQL 作为数据库
- systemd 托管后端服务
- Nginx 部署前端静态文件并反向代理后端 API

安装 MySQL 和 Nginx：

```bash
sudo apt install -y mysql-server nginx
```

启动服务：

```bash
sudo systemctl enable mysql
sudo systemctl start mysql
sudo systemctl enable nginx
sudo systemctl start nginx
```

---

## 七、配置 MySQL 数据库

进入 MySQL：

```bash
sudo mysql
```

执行：

```sql
CREATE DATABASE material_pull DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'material_pull'@'localhost' IDENTIFIED BY 'MaterialPull123456';
GRANT ALL PRIVILEGES ON material_pull.* TO 'material_pull'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

生产环境请把 `MaterialPull123456` 替换为更强密码。

如果需要导入升级 SQL：

```bash
mysql -u material_pull -p material_pull < /home/wan/IE/docs/sql/V0.8.4__production_upgrade.sql
```

---

## 八、生产部署后端

### 1. 打包后端

```bash
cd /home/wan/IE/backend
mvn clean package -DskipTests
```

### 2. 创建部署目录和用户

```bash
sudo mkdir -p /opt/material-pull-system/backend
sudo useradd -r -s /usr/sbin/nologin materialpull || true
```

### 3. 复制 jar

```bash
sudo cp /home/wan/IE/backend/target/material-pull-system-backend-0.8.4.jar /opt/material-pull-system/backend/
sudo ln -sfn /opt/material-pull-system/backend/material-pull-system-backend-0.8.4.jar /opt/material-pull-system/backend/app.jar
sudo chown -R materialpull:materialpull /opt/material-pull-system
```

### 4. 安装 systemd 服务

```bash
sudo cp /home/wan/IE/deploy/systemd/material-pull-backend.service /etc/systemd/system/material-pull-backend.service
```

编辑服务文件：

```bash
sudo nano /etc/systemd/system/material-pull-backend.service
```

在 `[Service]` 部分确认或添加：

```ini
Environment=SPRING_PROFILES_ACTIVE=mysql
Environment=MYSQL_USER=material_pull
Environment=MYSQL_PASSWORD=MaterialPull123456
Environment=MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD=Admin123456
Environment=MATERIAL_PULL_EXTERNAL_API_KEY=ChangeMeToStrongRandomKey123456
```

说明：

- `MYSQL_PASSWORD` 要和 MySQL 用户密码一致。
- `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD` 只建议首次部署时保留，用于创建初始管理员。
- 初始管理员创建成功并登录后，建议删除 `MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD`。

### 5. 启动后端服务

```bash
sudo systemctl daemon-reload
sudo systemctl enable material-pull-backend
sudo systemctl start material-pull-backend
```

查看状态：

```bash
sudo systemctl status material-pull-backend
```

查看日志：

```bash
sudo journalctl -u material-pull-backend -f
```

---

## 九、生产部署前端

### 1. 打包前端

```bash
cd /home/wan/IE/frontend
npm install
npm run build
```

### 2. 复制静态文件

```bash
sudo mkdir -p /var/www/material-pull-system
sudo rm -rf /var/www/material-pull-system/*
sudo cp -r /home/wan/IE/frontend/dist/* /var/www/material-pull-system/
```

---

## 十、配置 Nginx

复制项目自带配置：

```bash
sudo cp /home/wan/IE/deploy/nginx/material-pull-system.conf /etc/nginx/sites-available/material-pull-system.conf
```

启用站点：

```bash
sudo ln -sfn /etc/nginx/sites-available/material-pull-system.conf /etc/nginx/sites-enabled/material-pull-system.conf
```

禁用默认站点：

```bash
sudo rm -f /etc/nginx/sites-enabled/default
```

测试配置：

```bash
sudo nginx -t
```

重载 Nginx：

```bash
sudo systemctl reload nginx
```

访问地址：

```text
http://localhost
```

或：

```text
http://服务器IP
```

---

## 十一、生产部署后建议操作

首次管理员创建成功后，编辑服务文件：

```bash
sudo nano /etc/systemd/system/material-pull-backend.service
```

删除或注释：

```ini
Environment=MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD=Admin123456
```

然后执行：

```bash
sudo systemctl daemon-reload
sudo systemctl restart material-pull-backend
```

---

## 十二、常用命令

### 启动后端开发环境

```bash
cd /home/wan/IE/backend
export MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD='Admin123456'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 启动前端开发环境

```bash
cd /home/wan/IE/frontend
npm install
npm run dev
```

### 重启生产后端

```bash
sudo systemctl restart material-pull-backend
```

### 查看生产后端日志

```bash
sudo journalctl -u material-pull-backend -f
```

### 重载 Nginx

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 查看端口占用

```bash
sudo apt install -y lsof
sudo lsof -i:8080
sudo lsof -i:5173
sudo lsof -i:80
```

---

## 十三、快速启动总结

如果只想先把系统跑起来，执行下面步骤即可。

第一个终端启动后端：

```bash
cd /home/wan/IE/backend
export MATERIAL_PULL_BOOTSTRAP_ADMIN_PASSWORD='Admin123456'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

第二个终端启动前端：

```bash
cd /home/wan/IE/frontend
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

登录：

```text
用户名：admin
密码：Admin123456
```
