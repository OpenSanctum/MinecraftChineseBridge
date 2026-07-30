package com.spensanctum.tcb;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.github.houbb.opencc4j.util.ZhTwConverterUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaiwaneseLocalizer {
    private static final Pattern PROTECTED = Pattern.compile(
            "https?://\\S+"
                    + "|[a-z0-9_.-]+:[a-z0-9_./-]+"
                    + "|/(?:[a-z0-9_.-]+)(?:\\s+[a-z0-9_~^@.=+-]+)*"
                    + "|%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]"
                    + "|\\$\\{[^}\\r\\n]+}"
                    + "|\\{\\d+(?:,[^}\\r\\n]*)?}"
                    + "|§[0-9A-FK-ORa-fk-or]"
                    + "|\\\\[nrt]"
                    + "|<[^<>\\r\\n]+>");

    private static final String[][] DEFAULT_TERMS = {
            // Minecraft dimensions, creatures and terminology
            {"远古守卫者", "遠古深海守衛"},
            {"僵尸猪灵", "殭屍豬布林"},
            {"岩浆立方怪", "岩漿立方怪"},
            {"凋零骷髅", "凋零骷髏"},
            {"红石比较器", "紅石比較器"},
            {"红石中继器", "紅石中繼器"},
            {"生物群系", "生態域"},
            {"命令方块", "指令方塊"},
            {"方块实体", "方塊實體"},
            {"游戏规则", "遊戲規則"},
            {"旁观模式", "旁觀者模式"},
            {"冒险模式", "冒險模式"},
            {"创造模式", "創造模式"},
            {"生存模式", "生存模式"},
            {"死亡不掉落", "死亡不掉落"},
            {"战利品表", "戰利品表"},
            {"资源位置", "資源位置"},
            {"命名空间", "命名空間"},
            {"数据生成器", "資料產生器"},
            {"数据组件", "資料元件"},
            {"数据包", "資料包"},
            {"资源包", "資源包"},
            {"材质包", "材質包"},
            {"光影包", "光影包"},
            {"合成配方", "合成配方"},
            {"锻造模板", "鍛造模板"},
            {"锻造台", "鍛造台"},
            {"制图台", "製圖台"},
            {"切石机", "切石機"},
            {"堆肥桶", "堆肥箱"},
            {"刷怪笼", "生怪磚"},
            {"物品栏", "物品欄"},
            {"快捷栏", "快捷欄"},
            {"聊天栏", "聊天欄"},
            {"经验条", "經驗條"},
            {"经验值", "經驗值"},
            {"饱食度", "飽食度"},
            {"生命值", "生命值"},
            {"护甲值", "護甲值"},
            {"攻击伤害", "攻擊傷害"},
            {"攻击速度", "攻擊速度"},
            {"移动速度", "移動速度"},
            {"挖掘速度", "挖掘速度"},
            {"掉落物", "掉落物"},
            {"附魔等级", "附魔等級"},
            {"药水效果", "藥水效果"},
            {"状态效果", "狀態效果"},
            {"村民交易", "村民交易"},
            {"袭击事件", "突襲事件"},
            {"掠夺者", "掠奪者"},
            {"劫掠兽", "劫毀獸"},
            {"卫道士", "衛道士"},
            {"唤魔者", "喚魔者"},
            {"潜影贝", "界伏蚌"},
            {"末影螨", "終界蟎"},
            {"末影龙", "終界龍"},
            {"末影人", "終界使者"},
            {"末影珍珠", "終界珍珠"},
            {"末影之眼", "終界之眼"},
            {"末地传送门", "終界傳送門"},
            {"末地折跃门", "終界折躍門"},
            {"末地城", "終界城"},
            {"末地船", "終界船"},
            {"末地", "終界"},
            {"下界传送门", "地獄傳送門"},
            {"下界要塞", "地獄要塞"},
            {"下界合金", "獄髓"},
            {"下界疣", "地獄疙瘩"},
            {"下界", "地獄"},
            {"恶魂", "地獄幽靈"},
            {"烈焰人", "烈焰使者"},
            {"僵尸", "殭屍"},
            {"溺尸", "沉屍"},
            {"尸壳", "屍殼"},
            {"骷髅", "骷髏"},
            {"流浪者", "流浪者"},
            {"蠹虫", "蠹魚"},
            {"守卫者", "深海守衛"},
            {"幻翼", "夜魅"},
            {"恼鬼", "惱鬼"},
            {"监守者", "伏守者"},
            {"幽匿", "伏聆"},
            {"悦灵", "悅靈"},
            {"猪灵", "豬布林"},
            {"疣猪兽", "豬布獸"},
            {"炽足兽", "熾足獸"},
            {"旋风人", "旋風使者"},
            {"史莱姆", "史萊姆"},
            {"苦力怕", "苦力怕"},
            {"红石粉", "紅石粉"},
            {"红石火把", "紅石火把"},
            {"红石", "紅石"},
            {"粘性活塞", "黏性活塞"},
            {"活塞", "活塞"},
            {"发射器", "發射器"},
            {"投掷器", "投擲器"},
            {"漏斗", "漏斗"},
            {"铁砧", "鐵砧"},
            {"砂轮", "砂輪"},
            {"酿造台", "釀造台"},
            {"附魔台", "附魔台"},
            {"工作台", "工作台"},
            {"熔炉", "熔爐"},
            {"烟熏炉", "煙燻爐"},
            {"高炉", "高爐"},
            {"潜行", "潛行"},
            {"疾跑", "疾跑"},
            {"跳跃", "跳躍"},
            {"拾取", "撿取"},
            {"丢弃", "丟棄"},
            {"生成点", "重生點"},
            {"出生点", "出生點"},
            {"重生锚", "重生錨"},
            {"世界种子", "世界種子碼"},
            {"种子码", "種子碼"},
            {"区块加载", "區塊載入"},
            {"区块", "區塊"},
            {"渲染距离", "繪製距離"},
            {"模拟距离", "模擬距離"},
            {"平滑光照", "平滑光源"},
            {"粒子效果", "粒子效果"},
            {"实体阴影", "實體陰影"},
            {"全屏", "全螢幕"},
            {"垂直同步", "垂直同步"},
            {"视野", "視野"},
            {"亮度", "亮度"},
            {"语言", "語言"},
            {"字幕", "字幕"},
            {"自动跳跃", "自動跳躍"},
            {"触屏模式", "觸控螢幕模式"},
            {"无障碍", "無障礙"},
            {"难度", "難度"},
            {"和平", "和平"},
            {"简单", "簡單"},
            {"普通", "普通"},
            {"困难", "困難"},

            // Taiwan software and user-interface terminology
            {"崩溃报告", "當機報告"},
            {"错误报告", "錯誤報告"},
            {"服务器列表", "伺服器清單"},
            {"服务器地址", "伺服器位址"},
            {"服务器", "伺服器"},
            {"客户端", "用戶端"},
            {"用户名", "使用者名稱"},
            {"用户界面", "使用者介面"},
            {"用户", "使用者"},
            {"账户", "帳號"},
            {"登录", "登入"},
            {"登出", "登出"},
            {"注销", "登出"},
            {"互联网", "網際網路"},
            {"网络", "網路"},
            {"文件夹", "資料夾"},
            {"文件名", "檔案名稱"},
            {"文件", "檔案"},
            {"保存", "儲存"},
            {"另存为", "另存新檔"},
            {"加载", "載入"},
            {"卸载", "卸載"},
            {"下载", "下載"},
            {"上传", "上傳"},
            {"默认值", "預設值"},
            {"默认", "預設"},
            {"设置", "設定"},
            {"选项", "選項"},
            {"菜单", "選單"},
            {"搜索", "搜尋"},
            {"查找", "尋找"},
            {"复制", "複製"},
            {"粘贴", "貼上"},
            {"剪切", "剪下"},
            {"撤销", "復原"},
            {"快捷键", "快速鍵"},
            {"左键", "滑鼠左鍵"},
            {"右键", "滑鼠右鍵"},
            {"鼠标", "滑鼠"},
            {"光标", "游標"},
            {"滚动", "捲動"},
            {"屏幕", "螢幕"},
            {"分辨率", "解析度"},
            {"帧率", "幀率"},
            {"视频", "影片"},
            {"音频", "音訊"},
            {"高清", "高畫質"},
            {"质量", "品質"},
            {"内存", "記憶體"},
            {"硬盘", "硬碟"},
            {"显卡", "顯示卡"},
            {"驱动程序", "驅動程式"},
            {"缓存", "快取"},
            {"队列", "佇列"},
            {"在线", "線上"},
            {"离线", "離線"},
            {"激活", "啟用"},
            {"禁用", "停用"},
            {"支持", "支援"},
            {"兼容", "相容"},
            {"自定义", "自訂"},
            {"反馈", "意見回饋"},
            {"日志文件", "紀錄檔"},
            {"日志", "紀錄"},
            {"访问", "存取"},
            {"权限", "權限"},
            {"数据", "資料"},
            {"信息", "資訊"},
            {"消息", "訊息"},
            {"文本", "文字"},
            {"字符串", "字串"},
            {"源代码", "原始碼"},
            {"代码", "程式碼"},
            {"运行", "執行"},
            {"程序", "程式"},
            {"应用程序", "應用程式"},
            {"插件", "外掛"},
            {"模组", "模組"},
            {"概率", "機率"},
            {"几率", "機率"},
            {"随机", "隨機"},
            {"创建", "建立"},
            {"删除", "刪除"},
            {"添加", "新增"},
            {"导入", "匯入"},
            {"导出", "匯出"},
            {"注册表", "註冊表"},
            {"标签", "標籤"}
    };

    private final List<Term> terms;
    private final List<Term> reverseTerms;

    TaiwaneseLocalizer(Path gameDirectory) throws IOException {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String[] term : DEFAULT_TERMS) merged.put(term[0], term[1]);
        merged.putAll(loadUserTerms(gameDirectory));
        terms = merged.entrySet().stream()
                .map(entry -> new Term(entry.getKey(),
                        ZhTwConverterUtil.toTraditional(entry.getKey()),
                        ZhConverterUtil.toTraditional(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt((Term term) -> term.source().length()).reversed())
                .toList();
        reverseTerms = terms.stream()
                .sorted(Comparator.comparingInt((Term term) -> term.target().length()).reversed())
                .toList();
    }

    String localize(String source) {
        ProtectedText protectedText = protect(source);
        String translated = protectedText.text();
        for (Term term : terms) {
            translated = translated.replace(term.source(), term.target());
        }
        translated = ZhTwConverterUtil.toTraditional(translated);
        for (Term term : terms) {
            translated = translated.replace(term.traditionalSource(), term.target());
            translated = translated.replace(term.hongKongSource(), term.target());
        }
        return restore(translated, protectedText.values());
    }

    String toSimplified(String source) {
        ProtectedText protectedText = protect(source);
        String translated = protectedText.text();
        for (Term term : reverseTerms) {
            translated = translated.replace(term.target(), term.source());
        }
        translated = ZhTwConverterUtil.toSimple(translated);
        return restore(translated, protectedText.values());
    }

    String toHongKong(String source) {
        ProtectedText protectedText = protect(source);
        String translated = protectedText.text();
        for (Term term : reverseTerms) {
            translated = translated.replace(term.target(), term.hongKongSource());
        }
        translated = ZhConverterUtil.toTraditional(translated);
        return restore(translated, protectedText.values());
    }

    private Map<String, String> loadUserTerms(Path gameDirectory) throws IOException {
        Path config = gameDirectory.resolve("config").resolve("ChineseBridge-terms.json");
        Files.createDirectories(config.getParent());
        if (!Files.exists(config)) writeDefaultConfig(config);
        Map<String, String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return result;
            JsonObject termsObject = root.getAsJsonObject().has("詞彙")
                    ? root.getAsJsonObject().getAsJsonObject("詞彙")
                    : root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : termsObject.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception exception) {
            System.err.println("[ChineseBridge] 無法讀取自訂詞彙表，將使用內建詞彙。");
        }
        return result;
    }

    private void writeDefaultConfig(Path config) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("說明", "在「詞彙」中加入「原文\": \"台灣用語」即可覆寫或新增翻譯。");
        root.add("詞彙", new JsonObject());
        try (Writer raw = Files.newBufferedWriter(config, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(raw)) {
            writer.setIndent("  ");
            com.google.gson.internal.Streams.write(root, writer);
        }
    }

    private ProtectedText protect(String source) {
        List<String> values = new ArrayList<>();
        Matcher matcher = PROTECTED.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            int index = values.size();
            values.add(matcher.group());
            matcher.appendReplacement(output, Matcher.quoteReplacement("\uE000" + index + "\uE001"));
        }
        matcher.appendTail(output);
        return new ProtectedText(output.toString(), values);
    }

    private String restore(String source, List<String> values) {
        String restored = source;
        for (int index = 0; index < values.size(); index++) {
            restored = restored.replace("\uE000" + index + "\uE001", values.get(index));
        }
        return restored;
    }

    private record ProtectedText(String text, List<String> values) { }

    private record Term(String source, String traditionalSource,
                        String hongKongSource, String target) { }
}
