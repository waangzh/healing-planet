package com.healingplanet.ai.query;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Shared deterministic vocabulary for query analysis and explicit constraints. */
public final class QueryLexicon {
    public static final Set<String> PERSONAL_CONTEXT = Set.of(
            "我的", "我这盆", "这盆", "这株", "这棵", "家里的", "传感器", "它");
    public static final Set<String> CURRENT = Set.of(
            "当前", "现在", "今日", "今天", "当前状态", "当前土壤", "当前湿度", "当前温度", "当前读数", "当前数据");
    public static final Set<String> HISTORY = Set.of(
            "过去24小时", "过去一天", "过去一周", "过去7天", "近24", "近7", "趋势", "变化", "波动", "最近");
    public static final Set<String> FRESHNESS = Set.of(
            "实时数据", "还能当", "太旧", "数据时效", "还靠谱吗", "可靠吗", "新鲜吗", "新不新鲜");
    public static final Set<String> WATERING_DECISION = Set.of(
            "需要浇水", "要不要浇水", "要不要补水", "可以马上浇水", "缺水", "有点渴", "渴了");
    public static final Set<String> STATE_DECISION = Set.of(
            "状态异常", "有异常吗", "异常吗", "需要处理吗", "是不是");
    public static final Set<String> STATE_SIGNALS = Set.of(
            "土壤湿度", "最近温度", "最近湿度", "环境异常", "下降这么快", "状态", "湿度", "温度", "缺水", "渴");
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
