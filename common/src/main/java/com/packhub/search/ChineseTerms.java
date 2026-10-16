package com.packhub.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 中文搜索支持：把中文关键词映射成 Modrinth / CurseForge 上真正会被匹配到的英文词。
 *
 * <p>两个站点的索引基本是英文的，直接拿「光影」「小地图」去搜往往什么都搜不到，
 * 所以这里做三件事：</p>
 * <ol>
 *     <li><b>词表翻译</b>：内置常用 MC 术语的中英对照（光影 → shader、小地图 → minimap …）；</li>
 *     <li><b>分词组合</b>：把「光影优化」拆成 shader + optimization，组合成一条英文查询；</li>
 *     <li><b>多查询兜底</b>：翻译不出来时，退回原来的中文词（有些项目标题/简介就是中文）。</li>
 * </ol>
 *
 * <p>纯逻辑、不依赖 Minecraft，可以脱离游戏做单元测试。</p>
 */
public final class ChineseTerms {

    /** 中文词 -> 英文搜索词（多个词用空格分隔，代表英文里常用的说法）。 */
    private static final Map<String, String> DICTIONARY = new LinkedHashMap<>();

    /** 内容类型类。 */
    private static final Map<String, String> TYPE_WORDS = new LinkedHashMap<>();

    static {
        // ---------------- 内容类型
        TYPE_WORDS.put("光影", "shader");
        TYPE_WORDS.put("光影包", "shader");
        TYPE_WORDS.put("着色器", "shader");
        TYPE_WORDS.put("资源包", "resource pack");
        TYPE_WORDS.put("材质包", "resource pack");
        TYPE_WORDS.put("材质", "texture");
        TYPE_WORDS.put("纹理", "texture");
        TYPE_WORDS.put("模组", "mod");
        TYPE_WORDS.put("模组包", "modpack");
        TYPE_WORDS.put("整合包", "modpack");
        TYPE_WORDS.put("数据包", "datapack");
        TYPE_WORDS.put("插件", "plugin");
        TYPE_WORDS.put("汉化", "chinese translation");
        TYPE_WORDS.put("中文", "chinese");
        TYPE_WORDS.put("简体中文", "chinese simplified");

        // ---------------- 优化 / 性能
        DICTIONARY.put("优化", "optimization");
        DICTIONARY.put("性能", "performance");
        DICTIONARY.put("流畅", "fps boost");
        DICTIONARY.put("帧率", "fps");
        DICTIONARY.put("帧数", "fps");
        DICTIONARY.put("卡顿", "lag fix");
        DICTIONARY.put("内存", "memory");
        DICTIONARY.put("加速", "fast");
        DICTIONARY.put("轻量", "lightweight");
        DICTIONARY.put("渲染", "render");
        DICTIONARY.put("清晰", "hd");
        DICTIONARY.put("高清", "hd");
        DICTIONARY.put("写实", "realistic");
        DICTIONARY.put("卡通", "cartoon");
        DICTIONARY.put("像素", "pixel");
        DICTIONARY.put("风格", "style");

        // ---------------- 常用功能
        DICTIONARY.put("小地图", "minimap");
        DICTIONARY.put("地图", "map");
        DICTIONARY.put("背包", "inventory");
        DICTIONARY.put("物品栏", "inventory");
        DICTIONARY.put("整理", "sorting");
        DICTIONARY.put("箱子", "chest");
        DICTIONARY.put("存储", "storage");
        DICTIONARY.put("仓库", "storage");
        DICTIONARY.put("合成", "crafting");
        DICTIONARY.put("配方", "recipe");
        DICTIONARY.put("合成表", "jei recipe");
        DICTIONARY.put("任务", "quest");
        DICTIONARY.put("成就", "advancement");
        DICTIONARY.put("进度", "advancement");
        DICTIONARY.put("界面", "ui");
        DICTIONARY.put("菜单", "menu");
        DICTIONARY.put("按钮", "button");
        DICTIONARY.put("提示", "tooltip");
        DICTIONARY.put("信息", "info");
        DICTIONARY.put("血条", "health bar");
        DICTIONARY.put("状态栏", "hud");
        DICTIONARY.put("冷却", "cooldown");
        DICTIONARY.put("按键", "keybind");
        DICTIONARY.put("设置", "config");
        DICTIONARY.put("配置", "config");
        DICTIONARY.put("音效", "sound");
        DICTIONARY.put("音乐", "music");
        DICTIONARY.put("动画", "animation");
        DICTIONARY.put("粒子", "particles");
        DICTIONARY.put("字体", "font");
        DICTIONARY.put("天空", "sky");
        DICTIONARY.put("云", "clouds");
        DICTIONARY.put("水面", "water");
        DICTIONARY.put("水反", "water reflection");
        DICTIONARY.put("反射", "reflection");
        DICTIONARY.put("阴影", "shadow");
        DICTIONARY.put("光照", "lighting");
        DICTIONARY.put("雾", "fog");
        DICTIONARY.put("雨", "rain");
        DICTIONARY.put("天气", "weather");
        DICTIONARY.put("季节", "seasons");
        DICTIONARY.put("昼夜", "day night");

        // ---------------- 玩法
        DICTIONARY.put("科技", "tech");
        DICTIONARY.put("机械", "machinery");
        DICTIONARY.put("能源", "energy");
        DICTIONARY.put("电力", "power");
        DICTIONARY.put("自动化", "automation");
        DICTIONARY.put("自动", "automatic");
        DICTIONARY.put("工业", "industrial");
        DICTIONARY.put("魔法", "magic");
        DICTIONARY.put("法术", "spell");
        DICTIONARY.put("符文", "rune");
        DICTIONARY.put("附魔", "enchantment");
        DICTIONARY.put("药水", "potion");
        DICTIONARY.put("酿造", "brewing");
        DICTIONARY.put("食物", "food");
        DICTIONARY.put("农业", "farming");
        DICTIONARY.put("种植", "farming");
        DICTIONARY.put("作物", "crops");
        DICTIONARY.put("钓鱼", "fishing");
        DICTIONARY.put("烹饪", "cooking");
        DICTIONARY.put("建筑", "building");
        DICTIONARY.put("家具", "furniture");
        DICTIONARY.put("装饰", "decoration");
        DICTIONARY.put("灯具", "light");
        DICTIONARY.put("城镇", "town");
        DICTIONARY.put("村庄", "village");
        DICTIONARY.put("村民", "villager");
        DICTIONARY.put("交易", "trade");
        DICTIONARY.put("经济", "economy");
        DICTIONARY.put("商店", "shop");
        DICTIONARY.put("货币", "currency");
        DICTIONARY.put("生存", "survival");
        DICTIONARY.put("创造", "creative");
        DICTIONARY.put("冒险", "adventure");
        DICTIONARY.put("地牢", "dungeon");
        DICTIONARY.put("遗迹", "ruins");
        DICTIONARY.put("结构", "structure");
        DICTIONARY.put("洞穴", "cave");
        DICTIONARY.put("地形", "terrain");
        DICTIONARY.put("生物群系", "biome");
        DICTIONARY.put("生态", "biome");
        DICTIONARY.put("世界", "world");
        DICTIONARY.put("维度", "dimension");
        DICTIONARY.put("下界", "nether");
        DICTIONARY.put("地狱", "nether");
        DICTIONARY.put("末地", "end");
        DICTIONARY.put("天堂", "aether");
        DICTIONARY.put("太空", "space");
        DICTIONARY.put("月球", "moon");
        DICTIONARY.put("星球", "planet");
        DICTIONARY.put("红石", "redstone");
        DICTIONARY.put("漏斗", "hopper");
        DICTIONARY.put("活塞", "piston");
        DICTIONARY.put("轨道", "rail");
        DICTIONARY.put("火车", "train");
        DICTIONARY.put("汽车", "car");
        DICTIONARY.put("飞机", "plane");
        DICTIONARY.put("船", "boat");
        DICTIONARY.put("载具", "vehicle");
        DICTIONARY.put("飞行", "flight");
        DICTIONARY.put("翅膀", "wings");
        DICTIONARY.put("坐骑", "mount");
        DICTIONARY.put("宠物", "pet");
        DICTIONARY.put("龙", "dragon");
        DICTIONARY.put("怪物", "mob");
        DICTIONARY.put("生物", "mob");
        DICTIONARY.put("boss", "boss");
        DICTIONARY.put("战斗", "combat");
        DICTIONARY.put("武器", "weapon");
        DICTIONARY.put("剑", "sword");
        DICTIONARY.put("弓", "bow");
        DICTIONARY.put("枪", "gun");
        DICTIONARY.put("炮", "cannon");
        DICTIONARY.put("盔甲", "armor");
        DICTIONARY.put("护甲", "armor");
        DICTIONARY.put("盾", "shield");
        DICTIONARY.put("血量", "health");
        DICTIONARY.put("伤害", "damage");
        DICTIONARY.put("技能", "skill");
        DICTIONARY.put("等级", "level");
        DICTIONARY.put("经验", "experience");
        DICTIONARY.put("职业", "class");
        DICTIONARY.put("天赋", "talent");
        DICTIONARY.put("掉落", "drops");
        DICTIONARY.put("工具", "tools");
        DICTIONARY.put("背包整理", "inventory sorting");

        // ---------------- 联机 / 服务端
        DICTIONARY.put("联机", "multiplayer");
        DICTIONARY.put("多人", "multiplayer");
        DICTIONARY.put("服务器", "server");
        DICTIONARY.put("客户端", "client");
        DICTIONARY.put("服务端", "server side");
        DICTIONARY.put("皮肤", "skin");
        DICTIONARY.put("披风", "cape");
        DICTIONARY.put("聊天", "chat");
        DICTIONARY.put("语音", "voice chat");
        DICTIONARY.put("好友", "friends");
        DICTIONARY.put("领地", "claim");
        DICTIONARY.put("保护", "protection");
        DICTIONARY.put("权限", "permission");
        DICTIONARY.put("反作弊", "anticheat");
        DICTIONARY.put("备份", "backup");
        DICTIONARY.put("死亡", "death");
        DICTIONARY.put("复活", "respawn");
    }

    private ChineseTerms() {
    }

    /** 是否是中日韩字符（用来判断「用户输入了中文」）。 */
    public static boolean hasChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)     // 基本汉字
                || (c >= 0x3400 && c <= 0x4DBF)  // 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF); // 兼容汉字
    }

    /** 单个词条查询（先查专用词表，再查类型词表）。 */
    public static String translate(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String direct = TYPE_WORDS.get(word);
        if (direct == null) {
            direct = DICTIONARY.get(word);
        }
        if (direct != null) {
            return direct;
        }
        // 长词里包含短词：「材质包推荐」→ 命中「材质包」
        String best = "";
        for (Map.Entry<String, String> entry : TYPE_WORDS.entrySet()) {
            if (word.contains(entry.getKey()) && entry.getKey().length() > best.length()) {
                best = entry.getKey();
            }
        }
        for (Map.Entry<String, String> entry : DICTIONARY.entrySet()) {
            if (word.contains(entry.getKey()) && entry.getKey().length() > best.length()) {
                best = entry.getKey();
            }
        }
        if (!best.isEmpty()) {
            return translate(best);
        }
        return "";
    }

    /**
     * 把一句中文查询扩展成若干条可用的英文查询（按优先级排序）。
     *
     * <p>例：「光影优化」→ [「shader optimization」, 「shader」, 「optimization」]。</p>
     */
    public static List<String> expand(String rawQuery) {
        List<String> result = new ArrayList<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        String query = rawQuery.trim();

        // 1) 从左到右扫描每个片段：命中词表就换成英文，其余原样保留（保持原句语序）
        String[] segments = query.split("[\\s,，、/|+]+");
        List<String> primaryPieces = new ArrayList<>();
        List<String> fallbackWords = new ArrayList<>();
        List<String> unmatchedChinese = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            StringBuilder raw = new StringBuilder();
            int pos = 0;
            while (pos < segment.length()) {
                String key = longestKeyAt(segment, pos);
                if (key != null) {
                    if (raw.length() > 0) {
                        primaryPieces.add(raw.toString());
                        if (hasChinese(raw.toString())) {
                            unmatchedChinese.add(raw.toString());
                        }
                        raw.setLength(0);
                    }
                    String english = translate(key);
                    primaryPieces.add(english);
                    fallbackWords.add(english);
                    pos += key.length();
                } else {
                    raw.append(segment.charAt(pos));
                    pos++;
                }
            }
            if (raw.length() > 0) {
                primaryPieces.add(raw.toString());
                if (hasChinese(raw.toString())) {
                    unmatchedChinese.add(raw.toString());
                }
            }
        }

        LinkedHashSet<String> combos = new LinkedHashSet<>();
        String primary = String.join(" ", primaryPieces).replaceAll("\\s+", " ").trim();
        if (!primary.isEmpty()) {
            combos.add(primary);
        }
        // 单个英文词也各搜一次：多词组合可能太窄，单词能捞到更多相关项目
        if (new LinkedHashSet<>(fallbackWords).size() > 1) {
            combos.addAll(new LinkedHashSet<>(fallbackWords));
        }
        // 2) 原样查询（有些项目标题/简介就是中文）
        combos.add(query);
        // 3) 没翻译出来的中文片段原样再试一次
        combos.addAll(unmatchedChinese);

        List<String> planned = new ArrayList<>();
        for (String combo : combos) {
            String trimmed = combo.trim();
            if (!trimmed.isEmpty() && !planned.contains(trimmed)) {
                planned.add(trimmed);
            }
        }
        result.addAll(planned);
        return result;
    }

    /** 在 {@code text} 的 {@code pos} 位置，找出词表里能匹配上的最长词；没有则返回 null。 */
    private static String longestKeyAt(String text, int pos) {
        String best = null;
        for (String key : allKeysByLengthDesc()) {
            if (key.length() > (best == null ? 0 : best.length()) && text.startsWith(key, pos)) {
                best = key;
            }
        }
        return best;
    }

    private static List<String> allKeysByLengthDesc() {
        List<String> keys = new ArrayList<>(DICTIONARY.keySet());
        keys.addAll(TYPE_WORDS.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keys;
    }
}
