package com.healingplanet.ai.query;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Shared deterministic vocabulary for query analysis and explicit constraints. */
public final class QueryLexicon {
    public static final Set<String> PERSONAL_CONTEXT = Set.of(
            "我的", "我这盆", "这盆", "这株", "这棵", "家里的", "传感器", "它");
    public static final Set<String> PERSONAL_INSTANCE_CONTEXT = Set.of(
            "我的", "我这盆", "这盆", "这株", "这棵", "家里的");
    public static final Set<String> CURRENT = Set.of(
            "当前", "现在", "今日", "今天", "当前状态", "当前土壤", "当前湿度", "当前温度", "当前读数", "当前数据");
    public static final Set<String> HISTORY = Set.of(
            "过去24小时", "过去一天", "过去一周", "过去7天", "近24", "近7", "趋势", "变化", "波动", "最近");
    public static final Set<String> HISTORY_MEASUREMENT = Set.of(
            "过去24小时", "过去一天", "过去一周", "过去7天", "近24", "近7", "趋势", "变化", "波动",
            "下降", "升高", "降低", "上升");
    public static final Set<String> FRESHNESS = Set.of(
            "实时数据", "还能当", "太旧", "数据时效", "还靠谱吗", "可靠吗", "新鲜吗", "新不新鲜");
    public static final Set<String> WATERING_DECISION = Set.of(
            "需要浇水", "要不要浇水", "要不要补水", "可以马上浇水", "缺水", "有点渴", "渴了");
    public static final Set<String> STATE_DECISION = Set.of(
            "状态异常", "有异常吗", "异常吗", "需要处理吗", "是不是");
    public static final Set<String> STATE_METRICS = Set.of(
            "土壤湿度", "空气湿度", "湿度", "温度", "光照强度", "光照", "co2", "二氧化碳", "传感器读数");
    public static final Set<String> SENSOR_CONTEXT = Set.of(
            "传感器", "读数", "监测值", "实测", "测得", "状态数据", "实时数据", "设备数据");
    public static final Set<String> MEASUREMENT_REQUEST = Set.of(
            "多少", "读数", "数值", "数据", "测得", "监测", "变化", "趋势", "波动");
    public static final Set<String> STATE_ASSESSMENT = Set.of(
            "偏低", "偏高", "正常吗", "正常不", "超出", "超阈值", "越界", "异常吗", "有没有异常");
    public static final Set<String> STATE_SIGNALS = Set.of(
            "环境异常", "下降这么快", "状态异常", "有异常", "缺水", "有点渴", "渴了");
    public static final Set<String> GENERIC_CONCEPT = Set.of(
            "是什么意思", "有什么区别", "区别是什么", "怎么理解", "原理是什么",
            "光合作用", "蒸腾作用", "呼吸作用");
    /** Plant-name morphology used only to keep an unresolved named plant from being swallowed by a concept marker. */
    public static final Set<String> PLANT_NAME_SUFFIX = Set.of(
            "苔藓", "兰", "榕", "蕨", "竹", "藤", "菊", "莲", "掌", "草", "花", "树", "木");
    public static final Set<String> PLANT_DOMAIN = Set.of(
            "植物", "绿植", "盆栽", "花卉", "花盆", "花草", "植株", "园艺", "种植", "栽培", "多肉",
            "养花", "养植物", "养绿植", "盆土", "根系", "叶片", "叶子", "浇水", "补水", "施肥", "肥料",
            "光照", "阳光", "温度", "湿度", "土壤", "修剪", "养护", "黄叶", "发黄", "枯黄", "耐阴",
            "喜阴", "弱光", "强光", "直射", "光合作用");
    public static final Set<String> CLEAR_NON_PLANT = Set.of(
            "计算机", "网络", "tcp", "编程", "算法", "软件", "硬件", "量子", "物理", "股票", "金融", "篮球", "足球");
    public static final Set<String> COMMUNITY = Set.of("社区", "大家", "网友", "花友", "帖子", "社区经验", "网友经验");
    public static final Set<String> FORMAL = Set.of("官方", "正式", "指南", "规范", "标准");

    private QueryLexicon() {
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static boolean containsAny(String text, Set<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }
}
