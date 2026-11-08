package com.bazi.interpret

import com.bazi.engine.TenGodCategory
import com.bazi.engine.TianGan
import com.bazi.engine.WuXing

/**
 * 文案库。
 *
 * 设计原则：宁可少而准，不要多而空。
 * 每条文案都必须落在具体的干支五行特征上，禁止「你是一个很有潜力的人」这类通用话术。
 */
internal object TextBank {

    val DAY_MASTER: Map<TianGan, String> = mapOf(
        TianGan.JIA to "甲木为栋梁之木，性直而仁。做事讲章法、重原则，认准方向便一路向上，不轻易低头；不足之处在于不够圆转，遇阻碍时容易硬顶。",
        TianGan.YI to "乙木为藤萝之木，柔韧善附。处事圆融，能屈能伸，最擅长借势成事；但依附性偏强，环境一变，动摇也快。",
        TianGan.BING to "丙火为太阳之火，光明外放。热情坦率，乐于表现，做事来得快去得也快；优点是感染力强，短处是耐心不足。",
        TianGan.DING to "丁火为灯烛之火，内敛细腻。思虑周密，善照近处，重感受与细节；长处是体贴入微，短处是容易内耗。",
        TianGan.WU to "戊土为城墙之土，厚重沉稳。能承载、守信诺，守成有余；变通稍缓，遇到急剧变化时转身不快。",
        TianGan.JI to "己土为田园之土，温润包容。善调和、重实际，愿意替他人操心；但也容易把事情都揽到自己身上。",
        TianGan.GENG to "庚金为刀剑之金，刚锐果决。讲义气，遇事直取，不喜拖泥带水；锋芒太露时容易伤人伤己。",
        TianGan.XIN to "辛金为珠玉之金，精致讲品味。重细节与体面，敏感而有韧性；对人对己要求都高，容易因小事耿耿于怀。",
        TianGan.REN to "壬水为江河之水，气量大而流动。思路开阔，善变通，能容纳不同意见；不耐静守，容易心猿意马。",
        TianGan.GUI to "癸水为雨露之水，细腻内秀。感知敏锐，心思深沉而不轻易外露；长处是洞察力，短处是思虑过重。"
    )

    val ELEMENT_TRAIT: Map<WuXing, String> = mapOf(
        WuXing.MU to "主生发与仁厚，行事重成长性与长远布局。",
        WuXing.HUO to "主礼与显扬，行事重表达、速度与影响力。",
        WuXing.TU to "主信与承载，行事重稳定、信用与落地。",
        WuXing.JIN to "主义与决断，行事重规则、效率与边界。",
        WuXing.SHUI to "主智与流通，行事重变通、信息与人脉。"
    )

    val TEN_GOD_MEANING: Map<TenGodCategory, String> = mapOf(
        TenGodCategory.SELF to "比劫当令，自主与竞争意识强。适合独立作战或以合伙方式放大力量，但需防平辈之间的分夺与消耗。",
        TenGodCategory.OUTPUT to "食伤当令，表达与创造力是主要出口。适合技艺、内容、设计、教学一类需要持续输出的领域，但忌泄身太过而致精力透支。",
        TenGodCategory.WEALTH to "财星当令，对资源与实务敏感，重视可衡量的结果。适合经营、交易、项目管理，身弱时须防因财耗身。",
        TenGodCategory.OFFICER to "官杀当令，重规则与责任，能承担压力。适合体制内、管理岗或需要专业资质的行业，唯需留意压力累积。",
        TenGodCategory.RESOURCE to "印星当令，重学习与积累，善于借助既有体系。适合文教、研究、需要长期沉淀的方向，忌因求稳而错失时机。"
    )
}
