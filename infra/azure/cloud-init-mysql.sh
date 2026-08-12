#cloud-config
# =====================================================================
# CareQ — MySQL VM cloud-init (Day 15)
#
# Runs once when the Standard_B1s VM first boots:
#   1. Installs MySQL 8 (Ubuntu 24.04 LTS package)
#   2. Binds mysqld to 0.0.0.0 (network access is then restricted by the
#      NSG on this subnet — ONLY the Container Apps subnet 10.0.1.0/24 is
#      allowed to reach port 3306; nothing is exposed to the internet)
#   3. Creates the shared schema `careq_db` and the `careq` user
#
# The __MYSQL_PASSWORD__ token is replaced at deploy time by the Bicep
# template (secure parameter) — never hardcode it here.
#
# NOTE on root: Ubuntu's MySQL root uses auth_socket (sudo-only, no
# password, no network login). The `careq` user is the only network login
# and is scoped to careq_db.* — the app services never need root.
# =====================================================================
#cloud-config
package_update: true
packages:
  - mysql-server-8.0

runcmd:
  # Bind MySQL to all interfaces so the Container Apps subnet can reach it.
  - sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
  - systemctl restart mysql
  - mysql -e "CREATE DATABASE IF NOT EXISTS careq_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  - mysql -e "CREATE USER IF NOT EXISTS 'careq'@'%' IDENTIFIED BY '__MYSQL_PASSWORD__';"
  - mysql -e "GRANT ALL PRIVILEGES ON careq_db.* TO 'careq'@'%'; FLUSH PRIVILEGES;"
  - mysql -e "SELECT 'careq_db ready' AS status;" > /var/log/careq-mysql-init.log
