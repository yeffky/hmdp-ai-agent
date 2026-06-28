package com.hmdp.rag;

import com.hmdp.rag.ingestion.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 启动时自动将商家类型映射文档摄入 RAG 知识库。
 */
@Component
public class ShopTypeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ShopTypeBootstrap.class);

    @Resource
    private IngestionService ingestionService;

    private static final String SHOP_TYPE_DOC =
            "# 黑马点评商家类型ID映射表\n\n" +
            "## 美食类\n" +
            "- 美食 | 餐厅 | 饭店 | 小吃 | 快餐 | 中餐 | 西餐 | 日料 | 韩餐 | 火锅 | 烧烤 | 烤肉 | 川菜 | 湘菜 | 自助餐 | typeId: 1\n\n" +
            "## 娱乐类\n" +
            "- KTV | 卡拉OK | 唱歌 | typeId: 2\n\n" +
            "## 酒店类\n" +
            "- 酒店 | 宾馆 | 旅馆 | 民宿 | 住宿 | typeId: 3\n\n" +
            "## 购物类\n" +
            "- 购物 | 商场 | 超市 | 便利店 | 百货 | typeId: 4\n\n" +
            "## 生活服务\n" +
            "- 理发 | 美发 | 美容 | 按摩 | 足疗 | 洗浴 | typeId: 5\n\n" +
            "## 运动健身\n" +
            "- 健身 | 瑜伽 | 游泳 | 篮球 | 羽毛球 | 台球 | typeId: 6\n\n" +
            "## 旅游景点\n" +
            "- 景点 | 公园 | 游乐园 | 动物园 | 植物园 | typeId: 9\n\n" +
            "## 汽车服务\n" +
            "- 洗车 | 加油 | 充电 | 停车 | 4S店 | typeId: 10\n\n" +
            "## 规则\n" +
            "调用 geoSearch 前必须用 searchKnowledge 查询 typeId。\n" +
            "例如用户说'找烤肉店' → searchKnowledge('烤肉 typeId') → 得到 typeId=1 → geoSearch(typeId=1, x, y, radius)";

    @EventListener(ApplicationReadyEvent.class)
    public void ingestShopTypeMapping() {
        try {
            int count = ingestionService.ingest(SHOP_TYPE_DOC,
                    "system/shop-type-mapping", "商家类型ID映射表");
            log.info("Shop type mapping ingested: {} chunks", count);
        } catch (Exception e) {
            log.warn("Shop type bootstrap skipped (RAG may not be available): {}", e.getMessage());
        }
    }
}
