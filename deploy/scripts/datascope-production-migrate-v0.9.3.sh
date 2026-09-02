#!/usr/bin/env sh
# 只执行 V0.9.3：账号范围 Expand Schema。禁止用于其他 Migration。
set -eu

: "${DATASCOPE_PRODUCTION_MIGRATION_CONFIRM:?请设置 DATASCOPE_PRODUCTION_MIGRATION_CONFIRM=EXECUTE_V0_9_3}"
if [ "$DATASCOPE_PRODUCTION_MIGRATION_CONFIRM" != "EXECUTE_V0_9_3" ]; then
  echo "拒绝执行：未完成 V0.9.3 生产变更确认" >&2
  exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
MIGRATION_SQL="$PROJECT_ROOT/backend/src/main/resources/db/mysql/V0.9.3__datascope_user_scope.sql"
EXPECTED_SHA256='05efcf0a44388439149b5d17d990581a7db5aea2fe9c8c35f313d8ef4e106730'
ENV_FILE="${DATASCOPE_ENV_FILE:-/etc/material-pull/material-pull.env}"

[ -r "$ENV_FILE" ] || { echo "拒绝执行：无法读取生产环境文件 $ENV_FILE；请通过 sudo 运行。" >&2; exit 2; }
[ -r "$MIGRATION_SQL" ] || { echo "拒绝执行：找不到 V0.9.3 SQL 文件" >&2; exit 2; }
actual_sha256=$(sha256sum "$MIGRATION_SQL" | awk '{print $1}')
[ "$actual_sha256" = "$EXPECTED_SHA256" ] || { echo "拒绝执行：V0.9.3 SQL 校验值不匹配" >&2; exit 2; }

set -a
. "$ENV_FILE"
set +a

MYSQL_USER="${MYSQL_USER:-${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}}"
MYSQL_HOST="${MYSQL_HOST:-${DB_HOST:-${DATABASE_HOST:-}}}"
MYSQL_PORT="${MYSQL_PORT:-${DB_PORT:-${DATABASE_PORT:-3306}}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-${DB_DATABASE:-${DATABASE_NAME:-}}}}"
if [ -z "$MYSQL_HOST" ] || [ -z "$MYSQL_DATABASE" ]; then
  jdbc_url="${SPRING_DATASOURCE_URL:-${JDBC_DATABASE_URL:-${DATABASE_URL:-${DB_URL:-}}}}"
  case "$jdbc_url" in
    jdbc:mysql://*/*) endpoint="${jdbc_url#jdbc:mysql://}" ;;
    mysql://*/*) endpoint="${jdbc_url#mysql://}" ;;
    *) endpoint="" ;;
  esac
  if [ -n "$endpoint" ]; then
    authority="${endpoint%%/*}"
    database_part="${endpoint#*/}"
    [ -n "$MYSQL_HOST" ] || MYSQL_HOST="${authority%%:*}"
    case "$authority" in *:*) MYSQL_PORT="${authority##*:}" ;; esac
    [ -n "$MYSQL_DATABASE" ] || MYSQL_DATABASE="${database_part%%\?*}"
  fi
fi
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_DATABASE="${MYSQL_DATABASE:-material_pull}"
: "${MYSQL_USER:?环境文件未提供 MySQL 用户名}"
: "${MYSQL_PASSWORD:?环境文件未提供 MySQL 密码}"
command -v mysql >/dev/null 2>&1 || { echo "缺少 mysql 客户端" >&2; exit 127; }

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD MYSQL_PASSWORD' EXIT HUP INT TERM
mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --batch --raw "$@"
}

# V0.9.3 的 DDL 不可整体事务回滚；先确认当前应用数据库用户具备所需权限，避免执行到一半才失败。
grant_text=$(mysql_cmd --skip-column-names -e "SHOW GRANTS FOR CURRENT_USER()")
case "$grant_text" in
  *"ALL PRIVILEGES"*) ;;
  *)
    for privilege in ALTER CREATE INSERT; do
      if ! printf '%s\n' "$grant_text" | grep -Eqi "(^|[ ,])$privilege([ ,]|$)"; then
        echo "拒绝执行：当前数据库用户缺少 V0.9.3 所需的 $privilege 权限。" >&2
        exit 3
      fi
    done
    ;;
esac

# 此版本的 ALTER 不是幂等 SQL；任一对象已存在时拒绝，避免半执行状态被再次扩大。
existing=$(mysql_cmd --skip-column-names -e "
SELECT COALESCE(GROUP_CONCAT(item ORDER BY item SEPARATOR ','), '') FROM (
  SELECT 'sys_user.factory' AS item FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='factory'
  UNION ALL SELECT 'sys_user.version' FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='version'
  UNION ALL SELECT table_name FROM information_schema.tables
   WHERE table_schema=DATABASE() AND table_name IN ('sys_factory','sys_delivery_area','sys_user_delivery_area')
) x")
if [ -n "$existing" ]; then
  echo "拒绝执行：V0.9.3 前置状态不是全新状态，已存在：$existing。请人工核对，禁止重跑。" >&2
  exit 4
fi

echo "开始执行 V0.9.3：数据库=$MYSQL_DATABASE；仅新增可空账号范围字段和字典表。"
mysql_cmd < "$MIGRATION_SQL"

post_missing=$(mysql_cmd --skip-column-names -e "
WITH expected(table_name,column_name) AS (
 SELECT 'sys_user','factory' UNION ALL SELECT 'sys_user','version' UNION ALL
 SELECT 'sys_factory','factory_code' UNION ALL
 SELECT 'sys_delivery_area','factory_code' UNION ALL SELECT 'sys_delivery_area','area_code' UNION ALL
 SELECT 'sys_user_delivery_area','user_id' UNION ALL SELECT 'sys_user_delivery_area','delivery_area'
)
SELECT COALESCE(GROUP_CONCAT(CONCAT(e.table_name,'.',e.column_name) ORDER BY e.table_name,e.column_name SEPARATOR ','), '')
FROM expected e LEFT JOIN information_schema.columns c
 ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
WHERE c.column_name IS NULL")
[ -z "$post_missing" ] || { echo "执行后验证失败，缺少：$post_missing；请立即停止后续 Migration。" >&2; exit 5; }

factory_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_factory WHERE factory_code='弋江'")
area_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_delivery_area WHERE factory_code='弋江'")
account_count=$(mysql_cmd --skip-column-names -e "SELECT COUNT(*) FROM sys_user WHERE username IN ('500001','500002','500003','500004','500005')")
if [ "$factory_count" != "1" ] || [ "$area_count" != "17" ] || [ "$account_count" != "0" ]; then
  echo "执行后验证失败：factory=$factory_count areas=$area_count new_accounts=$account_count；请立即停止后续 Migration。" >&2
  exit 5
fi

backup_dir="${DATASCOPE_BACKUP_DIR:-/opt/apps/material-pull/db-backups}"
receipt="$backup_dir/datascope-v0.9.3-$(date +%Y%m%d-%H%M%S).receipt.txt"
umask 077
{
  echo "migration=V0.9.3__datascope_user_scope.sql"
  echo "sha256=$actual_sha256"
  echo "database=$MYSQL_DATABASE"
  echo "factory_count=$factory_count"
  echo "delivery_area_count=$area_count"
  echo "new_account_count=$account_count"
  echo "completed_at=$(date -Is)"
} > "$receipt"
chmod 600 "$receipt"

echo "V0.9.3 执行并验证通过：弋江工厂=1，配送区域=17，新增账号=0。"
echo "执行回执：$receipt"
