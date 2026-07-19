package com.example.materialpull.config;

import com.example.materialpull.entity.SystemConfigEntity;
import com.example.materialpull.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class SystemConfigBootstrapRunner implements CommandLineRunner {
    private final SystemConfigRepository configRepository;

    @Override
    public void run(String... args) {
        ensure("task.dedup.window-minutes", "3", "补货任务去重时间窗(分钟)",
                "同一仓库代号在该时间窗内重复扫码将被判为重复申请并拦截；超过该时间窗再次扫码视为新一轮用料，正常生成任务。设为0表示不做时间窗拦截。");
        ensure("task.auto-urgent.minutes", "0", "正常任务自动升级紧急的超时(分钟)",
                "一条正常配送任务从创建起超过该分钟数仍未完成，系统将自动把它升级为紧急任务。设为0表示关闭自动升级。");
        ensure("print.auto.enabled", "false", "定时自动打印标签开关",
                "开启后系统按设定的时间间隔自动为未打印的补货任务提交标签打印（斑马打印机）。关闭则只能手动打印。");
        ensure("print.auto.interval-minutes", "60", "定时自动打印间隔(分钟)",
                "自动打印的触发间隔，按整点对齐。例如 10=每10分钟一次，60=每个整点一次。最小1分钟。");
        ensure("print.auto.delay-minutes", "0", "任务出现后延时自动提交打印(分钟)",
                "仅提交“创建时间已超过该分钟数”的任务，实现“任务出来后等 N 分钟再自动提交到代理打印队列”。0=不延时，任务一出现即可被下一轮自动打印提交。分区域模式下可在各区域单独设置延时，留空则用此全局值。");
        ensure("print.auto.printer-name", "", "定时自动打印使用的打印机名称",
                "留空则使用系统默认打印机（app.factory.default-printer-name）。生产环境请填真实斑马打印机名，例如 ZDesigner GT800 (EPL)。");
        ensure("print.auto.delivery-areas", "", "定时自动打印的配送区域(逗号分隔)",
                "仅用于“全局模式”：只自动打印这些配送区域的任务；留空表示所有区域。区域值对应“料号映射/仓库任务”中的“配送区域”字段，例如 1,2。若配置了下面的“分区域定时”，则以分区域配置为准。");
        ensure("print.auto.print-type", "WAREHOUSE_BARCODE_LABEL", "定时自动打印的标签类型",
                "定时自动打印生成的打印作业类型，默认仓库条形码标签。");
        ensure("print.auto.area-schedules", "",
                "分区域定时打印配置(JSON)",
                "按区域分别设定打印节奏，优先于全局间隔。请在“出货标签打印”页的“定时打印设置(按区域)”里编辑，勿手改。留空则走全局模式。");
        ensureNonEditable("print.auto.last-run", "", "定时自动打印上次执行时间",
                "系统内部记录的上次自动打印时间，用于按间隔判断是否触发；请勿手动修改。");
        ensureNonEditable("print.auto.area-last-run", "", "分区域定时打印各区域上次执行时间(JSON)",
                "系统内部记录的各区域上次自动打印时间，用于分区域按间隔判断是否触发；请勿手动修改。");
        ensureNonEditable("menu.role-permissions",
                "{\"VIEWER\":[\"/tasks\",\"/print-jobs\",\"/mappings\"]}",
                "各角色可见菜单配置(JSON)",
                "按角色设置登录后可见的菜单路径。默认普通用户(只读)仅可见仓库补货任务、出货标签打印、料号映射。请在“系统管理-菜单权限”页编辑，勿手改。");
        ensure("print.label.dpi", "203", "标签打印机分辨率(dpi)",
                "斑马打印机分辨率，决定 ZPL 标签排版的点数换算。常见 203 或 300。换成 300dpi 打印机时改为 300，否则标签会被放大导致内容显示不全。改后下一张自动打印即生效，无需重启。");
        ensure("print.label.width-mm", "35", "标签物理宽度(mm)",
                "标签纸的实际宽度(毫米)，竖版标签一般 35。与 dpi 一起决定打印宽度点数。");
        ensure("print.label.height-mm", "95", "标签物理高度(mm)",
                "标签纸的实际高度(毫米)，竖版标签一般 95。与 dpi 一起决定标签长度点数。");
    }

    private void ensure(String key, String defaultValue, String name, String remark) {
        if (configRepository.findByConfigKey(key).isPresent()) return;
        SystemConfigEntity cfg = new SystemConfigEntity();
        cfg.setConfigKey(key);
        cfg.setConfigValue(defaultValue);
        cfg.setConfigName(name);
        cfg.setRemark(remark);
        cfg.setEditable(true);
        configRepository.save(cfg);
    }

    private void ensureNonEditable(String key, String defaultValue, String name, String remark) {
        if (configRepository.findByConfigKey(key).isPresent()) return;
        SystemConfigEntity cfg = new SystemConfigEntity();
        cfg.setConfigKey(key);
        cfg.setConfigValue(defaultValue);
        cfg.setConfigName(name);
        cfg.setRemark(remark);
        cfg.setEditable(false);
        configRepository.save(cfg);
    }
}
