---
name: general
description: 通用查询兜底，处理确定性 skill 覆盖不到的长尾/跨表/组合查询。其他 skill 未命中、用户确实要查数据时使用。触发词：（无，仅兜底）
---

# 通用兜底 Skill

## 何时使用
当 shop / order / queue / content / knowledge / history **均未命中**，且用户确实需要查询数据时，作为最后兜底。

## 工具
- `query(description)` —— text2Sql：LLM 根据自然语言动态生成 SQL 查询。**本技能仅此一个工具**。

## 使用规则（仅在兜底时）
- **店铺/评价/笔记/订单/排队等有确定性技能的任务，应先命中对应技能，本兜底不重复它们**——确定性工具（searchShops/recommendShops/queryShopComments 等）属于 shop/content 等技能，本技能不含，不要在兜底时假装能调用。
- 只有确定性技能覆盖不到的长尾/组合/跨表查询才走 `query`（text2sql）。
- 商家类型：具体菜系归入美食（type_id=1），**优先 food_category 细分**，不搜 tb_shop_type 表；菜系→food_category 映射见 `references/food-categories.md`。
- 生成 SQL 前先看表结构，**禁止编造列名**；JOIN 只在 schema 允许范围内。
- 只做只读 SELECT；任何写操作不得经 query 执行。
- 兜底查到的裸行数据可能无法渲染卡片——如实展示文本结果，不强行编卡片。

## 护栏
- query 是兜底，命中确定性 skill 时绝不使用。
- 不向用户暴露 SQL、表名、工具名、内部 ID。
- 空结果时换条件重查，不轻易下"没有"结论。
