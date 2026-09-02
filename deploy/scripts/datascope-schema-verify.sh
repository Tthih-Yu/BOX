#!/usr/bin/env sh
# 在隔离 MySQL 验证库只读核验 DataScope Expand Schema。
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
VERIFY_SQL="$PROJECT_ROOT/backend/src/main/resources/db/mysql/DATASCOPE_schema_verify.sql"

: "${DATASCOPE_SCHEMA_CONFIRM:?请设置 DATASCOPE_SCHEMA_CONFIRM=I_AM_USING_AN_ISOLATED_DATABASE}"
if [ "$DATASCOPE_SCHEMA_CONFIRM" != "I_AM_USING_AN_ISOLATED_DATABASE" ]; then
  echo "拒绝执行：必须明确确认使用隔离验证库" >&2
  exit 2
fi
: "${MYSQL_HOST:?需要设置 MYSQL_HOST}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?需要设置 MYSQL_DATABASE}"
: "${MYSQL_USER:?需要设置只读 MYSQL_USER}"
: "${MYSQL_PASSWORD:?需要设置 MYSQL_PASSWORD}"

command -v mysql >/dev/null 2>&1 || { echo "缺少 mysql 客户端" >&2; exit 127; }

export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'unset MYSQL_PWD' EXIT HUP INT TERM

mysql_cmd() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --batch --skip-column-names \
    --init-command="SET SESSION TRANSACTION READ ONLY" "$@"
}

missing=$(mysql_cmd -e "WITH expected(table_name,column_name) AS (
 SELECT 'm_material_mapping','factory' UNION ALL
 SELECT 't_replenishment_task','factory' UNION ALL
 SELECT 't_print_job','factory' UNION ALL
 SELECT 'sys_user','factory' UNION ALL SELECT 'sys_user','version'
 UNION ALL SELECT 't_inventory','factory' UNION ALL SELECT 't_inventory','delivery_area'
 UNION ALL SELECT 't_box','factory' UNION ALL SELECT 't_box','delivery_area'
 UNION ALL SELECT 't_label','factory' UNION ALL SELECT 't_label','delivery_area'
 UNION ALL SELECT 't_print_job','delivery_area'
 UNION ALL SELECT 't_production_plan','factory' UNION ALL SELECT 't_material_demand','factory'
 UNION ALL SELECT 't_purchase_requirement','factory'
 UNION ALL SELECT 'log_scan','factory' UNION ALL SELECT 'log_scan','delivery_area'
 UNION ALL SELECT 'sys_outbox_event','scope_type' UNION ALL SELECT 'sys_outbox_event','factory'
 UNION ALL SELECT 'sys_outbox_event','delivery_area')
 SELECT COUNT(*) FROM expected e LEFT JOIN information_schema.columns c
 ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
 WHERE c.column_name IS NULL")

if [ "$missing" != "0" ]; then
  echo "失败：缺少 $missing 个 DataScope 字段，详情如下" >&2
  mysql_cmd < "$VERIFY_SQL" >&2
  exit 4
fi

relations=$(mysql_cmd -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user_delivery_area'")
factories=$(mysql_cmd -e "SELECT COUNT(*) FROM sys_factory")
areas=$(mysql_cmd -e "SELECT COUNT(*) FROM sys_delivery_area")
if [ "$relations" != "1" ] || [ "$factories" -lt 1 ] || [ "$areas" -lt 17 ]; then
  echo "失败：字典/关系表不完整 relation=$relations factories=$factories areas=$areas" >&2
  exit 4
fi

echo "通过：20 个范围及前置字段（含 V0.9.1/V0.9.2）、用户区域关系表、工厂字典及配送区域数据均存在"
