package com.xiaohan.xhsnotegen.domain

import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts

/** Defaults for the built-in modes and for new custom modes. */
object BuiltInModes {

    const val FOOD = "food"
    const val TRAVEL = "travel"

    val food = WritingMode(
        key = FOOD,
        name = "Food",
        rootTag = "美食",
        promptHeading = "这次吃的",
        fields = mapOf(
            FieldSlot.SUBJECT to FieldSpec("What did you eat", "红烧肉, 糖醋里脊", "菜"),
            FieldSlot.PLACE to FieldSpec("Where", "Restaurant name", "店"),
            FieldSlot.FEELING to FieldSpec("How was it", "汤有点咸，但面很筋道", "味道"),
            FieldSlot.COST to FieldSpec("Price or rating", "两个人150", "价格"),
            FieldSlot.SCENE to FieldSpec("The place", "排了40分钟，店里很吵", "环境"),
            FieldSlot.OTHER to FieldSpec("Anything else", "和谁、为什么来、下次想点什么", "其他想说的"),
        ),
        instructions = FoodPrompts.DEFAULT_SYSTEM_PROMPT,
        styles = NoteStyle.entries.associate { it.key to FoodPrompts.defaultStyleInstruction(it) },
        builtIn = true,
    )

    val travel = WritingMode(
        key = TRAVEL,
        name = "Travel",
        rootTag = "旅行",
        promptHeading = "这次去的",
        fields = mapOf(
            FieldSlot.SUBJECT to FieldSpec("What did you see or do", "爬了伏见稻荷、逛了锦市场", "做了什么"),
            FieldSlot.PLACE to FieldSpec("Where", "Place, sight or trip name", "地方"),
            FieldSlot.FEELING to FieldSpec("How was it", "人太多了，但山顶很安静", "感受"),
            FieldSlot.COST to FieldSpec("Cost / tickets", "门票免费，打车60", "花费"),
            FieldSlot.SCENE to FieldSpec("Weather & crowds", "下小雨，早上人少", "天气和人"),
            FieldSlot.OTHER to FieldSpec("Anything else", "和谁、怎么去的、有什么小插曲", "其他想说的"),
        ),
        instructions = TRAVEL_PROMPT,
        styles = mapOf(
            NoteStyle.CASUAL_STORY.key to "随手记：像给朋友讲这一天——怎么去的、和谁、印象最深的一两个瞬间。80到200字，分成几小段。",
            NoteStyle.PRACTICAL.key to "实用记录：写给以后的自己和想去的人看的。怎么去、花了多少、几点去合适、值不值得专门去。可以分行，每行是完整的口语句子。100到200字。",
            NoteStyle.PUNCHY.key to "清单式：几行短句，每行开头一个小标签（比如 去了／交通／花费／人多吗／建议，只写有信息的项），标签后面是一句大白话。最后可以加一句自己的碎碎念。",
            NoteStyle.CLEAN.key to "极简：除日期行外只写两到四句话。去了哪，加一个最具体的画面或感受。",
        ),
        builtIn = true,
    )

    // ---- General modes for Xiaohongshu's biggest categories ----

    val outfit = category(
        key = "outfit", name = "Outfit", rootTag = "穿搭", heading = "这次穿的",
        subject = FieldSpec("What did you wear", "米色风衣 + 直筒牛仔裤 + 乐福鞋", "穿了什么"),
        place = FieldSpec("Occasion / where", "周末逛街、上班通勤", "场合"),
        feeling = FieldSpec("How did it feel", "风衣有点压个子，但很暖", "感受"),
        cost = FieldSpec("Price / brand", "风衣优衣库，裤子穿了三年", "价格和牌子"),
        scene = FieldSpec("Height, size & weather", "163/50kg，穿M；15度有风", "身材尺码和天气"),
        spec = CategorySpec(
            diary = "穿搭日记", moment = "出门前在镜子前拍完照、随手记一下今天穿了什么",
            smallDetails = "袖子有点长卷了两折、风一吹下摆很好看、走了一天鞋有点磨脚",
            concrete = "\"腰线正好在最细的地方\" 比 \"版型很显瘦\"",
            cliches = "氛围感拉满、高级感、显瘦显高、温柔又飒、一衣多穿、谁穿谁好看、闭眼冲",
            facts = "不要编面料成分、牌子、价格和尺码；我没说的就不写。",
            titles = "\"降温后的第一件风衣\"、\"上班穿了一周的牛仔裤\"",
            tags = "\"通勤穿搭\"、\"小个子穿搭\"、单品名",
            example1 = "10.8 周三 早上\n降温了，翻出去年的米色风衣。\n配直筒牛仔裤有点压个子，换了双带跟的乐福鞋好一点。\n同事说像要去出差。",
            example2 = "5.20 周一\n新买的白T洗了一次就有点缩。\n还是穿出门了，外面套件衬衫看不出来。",
        ),
    )

    val beauty = category(
        key = "beauty", name = "Beauty", rootTag = "美妆护肤", heading = "这次用的",
        subject = FieldSpec("What did you use", "某某精华、某某粉底液 02色", "用了什么"),
        place = FieldSpec("Routine / occasion", "早晚护肤、上班妆", "场景"),
        feeling = FieldSpec("How did it work on you", "上脸不黏，但下午T区有点出油", "感受"),
        cost = FieldSpec("Price / where bought", "专柜 480，用了两周", "价格和购买"),
        scene = FieldSpec("Skin type & season", "混油皮，换季容易泛红", "肤质和季节"),
        spec = CategorySpec(
            diary = "美妆护肤日记", moment = "卸完妆、洗完脸随手记一下最近用的东西",
            smallDetails = "瓶口设计不好容易洒、味道有点冲、第三天下巴冒了个痘",
            concrete = "\"下午三点鼻翼开始卡粉\" 比 \"持妆一般\"",
            cliches = "绝绝子、素颜神器、空瓶级别、谁用谁夸、平价替代天花板、一抹化水、黄皮亲妈、闭眼入",
            facts = "不要编成分、功效、价格和使用时长；不要写夸大或医疗效果（治痘、美白几度）；我只说感受，你就只写感受。",
            titles = "\"换季泛红那两周用的\"、\"这支粉底我用完了\"",
            tags = "产品名、\"混油皮\"、\"日常妆\"",
            example1 = "3.2 周六 晚上\n这瓶精华用了快一个月。\n上脸很快就吸收了，但味道有点冲，第一周不太习惯。\n泛红好像少了点，也可能是天气暖了。",
            example2 = "11.15 周五\n新粉底 02 色对我来说还是白了一点。\n混了点之前那瓶用，下午鼻翼还是有点卡。",
        ),
    )

    val home = category(
        key = "home", name = "Home", rootTag = "家居", heading = "这次家里的",
        subject = FieldSpec("What changed at home", "换了窗帘、新买的落地灯", "改了什么"),
        place = FieldSpec("Which room", "客厅、出租屋卧室", "房间"),
        feeling = FieldSpec("How is it", "晚上开灯很暖，但开关位置不顺手", "感受"),
        cost = FieldSpec("Cost / where from", "灯 199，窗帘找人定做 600", "花费"),
        scene = FieldSpec("Size & before/after", "12平，以前很暗", "大小和变化"),
        spec = CategorySpec(
            diary = "家居生活日记", moment = "收拾完屋子、坐在沙发上随手记一下",
            smallDetails = "装完才发现柜门挡开关、猫第一天就占了新垫子、快递盒还没扔",
            concrete = "\"晚上开这盏灯像在咖啡店\" 比 \"氛围感很强\"",
            cliches = "氛围感拉满、治愈系小窝、出租屋改造天花板、低成本高颜值、好物推荐、幸福感爆棚",
            facts = "不要编尺寸、价格、品牌和材质；我没说的就不写。",
            titles = "\"客厅终于换了窗帘\"、\"出租屋的第一盏落地灯\"",
            tags = "\"出租屋改造\"、房间名、物件名",
            example1 = "6.9 周日\n拖了半年，终于把窗帘换了。\n遮光比以前好很多，早上不会被晒醒了。\n颜色比网上看的深一点，也还行。",
            example2 = "1.14 周二 晚上\n落地灯到了，自己装了二十分钟。\n开关在线上，要弯腰按，有点烦。",
        ),
    )

    val fitness = category(
        key = "fitness", name = "Fitness", rootTag = "运动", heading = "这次练的",
        subject = FieldSpec("What did you do", "跑了5公里、爬了香山", "做了什么"),
        place = FieldSpec("Where", "小区跑道、健身房、某某山", "地点"),
        feeling = FieldSpec("How did it feel", "第三公里开始腿很沉", "感受"),
        cost = FieldSpec("Time / pace / distance", "38分钟，配速7分半", "时长和数据"),
        scene = FieldSpec("Weather & gear", "闷热，新跑鞋第一次穿", "天气和装备"),
        spec = CategorySpec(
            diary = "运动日记", moment = "刚运动完、喘着气拿手机随手记一下",
            smallDetails = "耳机没电了只能听自己喘气、半路鞋带开了、下山的时候膝盖有点疼",
            concrete = "\"最后一公里是咬着牙跑完的\" 比 \"突破了自我\"",
            cliches = "自律给我自由、燃脂、突破自我、汗水不会骗人、每一滴汗都值得、打卡第N天、闭眼冲",
            facts = "不要编距离、配速、心率、消耗的卡路里和训练动作；不要给健康或减肥建议。",
            titles = "\"下雨前跑完的五公里\"、\"第一次爬香山\"",
            tags = "运动项目、地点、\"跑步日记\"",
            example1 = "7.18 周四 早上\n六点出门还是很闷。\n第三公里开始腿很沉，最后一公里基本是走跑。\n回来喝了一整瓶水。",
            example2 = "10.2 周三\n爬了香山，人比想象的多。\n下山的时候膝盖有点疼，下次要带护膝。",
        ),
    )

    val parenting = category(
        key = "parenting", name = "Parenting", rootTag = "育儿", heading = "这次的",
        subject = FieldSpec("What happened", "第一次自己吃饭、打了疫苗", "发生了什么"),
        place = FieldSpec("Where", "家里、小区公园", "地点"),
        feeling = FieldSpec("How was it", "吃得满脸都是，但很认真", "感受"),
        cost = FieldSpec("Age / stage", "1岁2个月", "月龄"),
        scene = FieldSpec("Products or tips used", "用了吸盘碗", "用到的东西"),
        spec = CategorySpec(
            diary = "育儿日记", moment = "孩子睡着以后、随手记一下今天的小事",
            smallDetails = "勺子拿反了还很骄傲、打完针哭了三秒就去看狗了、午觉只睡了四十分钟",
            concrete = "\"把勺子递给我让我也吃一口\" 比 \"特别懂事\"",
            cliches = "神仙宝宝、人类幼崽、带娃天花板、育儿干货、宝妈必看、闭眼入",
            facts = "不要编孩子的发育情况、医疗或育儿建议；不要写孩子的全名、学校、住址等隐私信息。",
            titles = "\"第一次自己拿勺子\"、\"打疫苗那天\"",
            tags = "月龄、\"宝宝辅食\"、\"育儿日常\"",
            example1 = "4.3 周四 晚上\n今天第一次自己拿勺子吃饭。\n一半吃到嘴里，一半在桌上，还有一点在头发上。\n但吃得特别认真，没让我喂。",
            example2 = "9.12 周五\n打了疫苗，哭了三秒。\n看到楼下的狗就忘了，回家还一直学狗叫。",
        ),
    )

    val booksFilms = category(
        key = "books_films", name = "Books & Films", rootTag = "书影音", heading = "这次看的",
        subject = FieldSpec("What did you read / watch", "《某某》、某某展", "看了什么"),
        place = FieldSpec("Where / how", "电影院、kindle、美术馆", "在哪看的"),
        feeling = FieldSpec("What stuck with you", "结尾那场戏看哭了", "感受"),
        cost = FieldSpec("Tickets / price", "电影票 45", "花费"),
        scene = FieldSpec("Favorite line or part", "有一句话一直记着", "印象深的地方"),
        spec = CategorySpec(
            diary = "书影音日记", moment = "看完/读完以后、趁印象还在随手记一下",
            smallDetails = "中间走神去看了手机、旁边的人一直在吃爆米花、有一句话截图存下来了",
            concrete = "\"结尾那顿饭看得我想给家里打电话\" 比 \"很有感染力\"",
            cliches = "神作、封神、后劲太大、不愧是豆瓣高分、强烈安利、人生必看、泪目",
            facts = "不要编剧情、作者、导演、演员和评分；不要剧透我没提到的情节。",
            titles = "\"周末看的这部有点后劲\"、\"读了一半的书\"",
            tags = "书名/片名、\"读书笔记\"、\"观影记录\"",
            example1 = "2.24 周六 晚上\n一个人去看的，影院只有七八个人。\n前半段有点慢，差点睡着。\n结尾那顿饭看得我想给家里打个电话。",
            example2 = "8.7 周三\n这本书读了一半。\n有些章节很啰嗦，但写外婆的那段反复看了两遍。",
        ),
    )

    val all: List<WritingMode> = listOf(food, travel, outfit, beauty, home, fitness, parenting, booksFilms)

    fun builtIn(key: String): WritingMode? = all.firstOrNull { it.key == key }

    /** Starting point for a new custom mode: travel-style wording, generic labels. */
    fun blank(key: String, name: String) = WritingMode(
        key = key,
        name = name,
        rootTag = name,
        promptHeading = "这次的",
        fields = FieldSlot.entries.associateWith { genericField(it) },
        instructions = GENERIC_PROMPT,
        styles = NoteStyle.entries.associate { it.key to genericStyle(it) },
        builtIn = false,
    )

    fun genericField(slot: FieldSlot): FieldSpec = when (slot) {
        FieldSlot.SUBJECT -> FieldSpec("What was it", "", "内容")
        FieldSlot.PLACE -> FieldSpec("Where", "", "地点")
        FieldSlot.FEELING -> FieldSpec("How was it", "", "感受")
        FieldSlot.COST -> FieldSpec("Cost", "", "花费")
        FieldSlot.SCENE -> FieldSpec("Details", "", "细节")
        FieldSlot.OTHER -> FieldSpec("Anything else", "", "其他想说的")
    }

    fun genericStyle(style: NoteStyle): String = when (style) {
        NoteStyle.CASUAL_STORY -> "随手记：像给朋友发消息那样讲这件事，印象最深的一两个细节。80到200字，分成几小段。"
        NoteStyle.PRACTICAL -> "实用记录：写给以后的自己查的。关键信息、花费、值不值得。可以分行，每行是完整的口语句子。100到200字。"
        NoteStyle.PUNCHY -> "清单式：几行短句，每行开头一个小标签，只写有信息的项，标签后面是一句大白话。"
        NoteStyle.CLEAN -> "极简：除日期行外只写两到四句话，只留最具体的一个细节。"
    }
}

private val TRAVEL_PROMPT = """
你在帮我写我自己的旅行日记。写好后我会发到小红书，但它首先是我的日记：写给自己看，顺便给刷到的人看看。写出来要像我本人走完一天、晚上在酒店拿手机随手打的字，不能让人一眼看出是AI写的。

## 真人随手记是什么样的
- 不完整。不会把去过的地方挨个介绍一遍，常常只记得一两个瞬间：台阶比想象中多、下山碰上下雨、路边一只猫。
- 具体的小事比笼统的评价真实。"走到一半腿开始抖" 比 "登山体验很棒" 像人话。
- 句子长短不一，可以有半句、口语、语气词（挺、蛮、有点、还行、说实话、其实、就是）。
- 感受是个人的，可以含糊、可以矛盾："人多到想走，但拍出来是真好看"。
- 结尾很随意，可以停在一个画面上，不用总结，不用升华。
- 信息少就写短，不要凑字数。

## 一看就是AI写的，不要这样
- 报流水账式开头："X年X月X日，我来到了……"、"今天我们来到了美丽的……"
- 总结和升华：总的来说、整体而言、此行不虚、下次还会再来、值得一去、治愈了我、找到了内心的平静
- 排比、对仗、"不仅……而且……"、"既……又……"、"首先……其次……最后"
- 旅游宣传腔：人间仙境、美不胜收、宛如画卷、令人叹为观止、历史的厚重感、诗和远方、打卡圣地
- 营销腔：绝了、yyds、天花板、宝藏、必打卡、冲、姐妹们、家人们、强烈推荐、闭眼入
- 每个景点一句评价、每段一样长的整齐感（清单风格除外）
- emoji、连着用感叹号

## 事实
只写我给的信息和照片里确实看得到的东西。没给价格就不提价格，没说排队就不写排队，不要编景点的历史、传说、开放时间和路线。照片里看不清的不要猜。
标着"我的原话"的内容最有我自己的味道：尽量保留我的说法和用词，最多理顺一下，不要改成书面语。

## 标题
严格不超过20个字。像日记的小标题，平实具体，比如"下雨天的伏见稻荷"、"第一次一个人去海边"。不要用"｜"拼接，不要标题党，不要感叹号。

## 话题标签
3到5个，不带#号。具体实用：城市、景点名、玩法，比如"京都旅行"、"伏见稻荷大社"。少用"旅行分享"这种泛标签。

## warnings
如果我给的信息和照片对不上，或者有你拿不准的事实，在 warnings 里用一句中文说明。没有就返回空数组。

## 语气示例（只学语气和节奏，不要照抄内容）
示例一：
4.12 周六 上午
七点就出门了，想着能避开人，结果到的时候已经排起来了。
鸟居一直往上，走到一半腿开始抖，后面基本没人了，反而最好看。
下山碰上小雨，躲在小卖部门口吃了个团子。

示例二：
8.3 周日 傍晚
临时决定去海边，坐了一个半小时车。
风很大，头发一直往脸上糊。
太阳落下去那几分钟大家都安静了，挺难得的。
""".trimIndent()

private val GENERIC_PROMPT = """
你在帮我写我自己的日记。写好后我会发到小红书，但它首先是我的日记：写给自己看，顺便给刷到的人看看。写出来要像我本人拿手机随手打的字，不能让人一眼看出是AI写的。

## 真人随手记是什么样的
- 不完整，常常只记得一两个具体细节。
- 具体的小事比笼统的评价真实。
- 句子长短不一，可以有口语和语气词（挺、蛮、有点、还行、说实话、其实）。
- 结尾很随意，不用总结，不用升华。
- 信息少就写短，不要凑字数。

## 一看就是AI写的，不要这样
- 报流水账式开头："X年X月X日，我……"
- 总结和升华：总的来说、整体而言、值得一试、不虚此行
- 排比、对仗、"不仅……而且……"、"首先……其次……最后"
- 营销腔：绝了、yyds、天花板、宝藏、必打卡、姐妹们、强烈推荐
- emoji、连着用感叹号

## 事实
只写我给的信息和照片里确实看得到的东西，不要编造。标着"我的原话"的内容尽量保留我的说法。

## 标题
严格不超过20个字，平实具体，不要标题党，不要感叹号。

## 话题标签
3到5个，不带#号，具体实用。

## warnings
如果信息和照片对不上，或者有拿不准的事实，在 warnings 里用一句中文说明。没有就返回空数组。
""".trimIndent()

/** What differs between the general category prompts; the rest is shared. */
private data class CategorySpec(
    val diary: String,          // "穿搭日记"
    val moment: String,         // when you'd jot it down
    val smallDetails: String,   // examples of small, real details
    val concrete: String,       // a concrete-vs-vague example pair
    val cliches: String,        // this category's typical AI / marketing phrases
    val facts: String,          // what must not be invented
    val titles: String,
    val tags: String,
    val example1: String,
    val example2: String,
)

private fun category(
    key: String, name: String, rootTag: String, heading: String,
    subject: FieldSpec, place: FieldSpec, feeling: FieldSpec, cost: FieldSpec, scene: FieldSpec,
    spec: CategorySpec,
) = WritingMode(
    key = key, name = name, rootTag = rootTag, promptHeading = heading,
    fields = mapOf(
        FieldSlot.SUBJECT to subject, FieldSlot.PLACE to place, FieldSlot.FEELING to feeling,
        FieldSlot.COST to cost, FieldSlot.SCENE to scene,
        FieldSlot.OTHER to FieldSpec("Anything else", "", "其他想说的"),
    ),
    instructions = categoryPrompt(spec),
    styles = NoteStyle.entries.associate { it.key to BuiltInModes.genericStyle(it) },
    builtIn = true,
)

private fun categoryPrompt(s: CategorySpec) = """
你在帮我写我自己的${s.diary}。写好后我会发到小红书，但它首先是我的日记：写给自己看，顺便给刷到的人看看。写出来要像我本人${s.moment}，不能让人一眼看出是AI写的。

## 真人随手记是什么样的
- 不完整。不会面面俱到，常常只记得一两个小细节：${s.smallDetails}。
- 具体的小事比笼统的评价真实。${s.concrete} 像人话。
- 句子长短不一，可以有半句、口语、语气词（挺、蛮、有点、还行、说实话、其实、就是）。
- 感受是个人的，可以含糊、可以矛盾，好的坏的都可以写。
- 结尾很随意，可以停在一个细节上，不用总结，不用升华。
- 信息少就写短，不要凑字数。

## 一看就是AI写的，不要这样
- 报流水账式开头："X年X月X日，我……"、"今天给大家分享……"
- 总结和升华：总的来说、整体而言、值得一试、不踩雷、强烈推荐
- 排比、对仗、"不仅……而且……"、"既……又……"、"首先……其次……最后"
- 这一类笔记常见的套话：${s.cliches}
- 称呼读者：姐妹们、家人们、宝子们
- emoji、连着用感叹号

## 事实
只写我给的信息和照片里确实看得到的东西。${s.facts}照片里看不清的不要猜。
标着"我的原话"的内容最有我自己的味道：尽量保留我的说法和用词，最多理顺一下，不要改成书面语。

## 标题
严格不超过20个字。像日记的小标题，平实具体，比如${s.titles}。不要用"｜"拼接，不要标题党，不要感叹号。

## 话题标签
3到5个，不带#号。具体实用，比如${s.tags}。少用泛标签。

## warnings
如果我给的信息和照片对不上，或者有你拿不准的事实，在 warnings 里用一句中文说明。没有就返回空数组。

## 语气示例（只学语气和节奏，不要照抄内容）
示例一：
${s.example1}

示例二：
${s.example2}
""".trimIndent()
