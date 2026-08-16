# 商家类型 → typeId 映射（geoSearch / searchShops 用）

> `geoSearch(typeId, radius)` 必须传**有效 typeId**；`searchShops(typeId)` 的 typeId 可选（不传=全类型按名称搜）。

## 大类映射（tb_shop_type，10 类）

| 用户说的 | typeId | 说明 |
|---|---|---|
| 美食/餐厅/火锅/咖啡（任何菜系） | 1 | 美食：细分用 `food_category`（见 food-categories.md），不要再用 typeId 区分 |
| 唱歌/KTV/量贩 | 2 | |
| 理发/美发/造型 | 3 | |
| 健身/运动/游泳 | 4 | |
| 按摩/推拿/足浴/采耳 | 5 | |
| SPA/水疗 | 6 | |
| 亲子/儿童乐园/游乐 | 7 | |
| 酒吧/清吧/夜店 | 8 | |
| 轰趴/桌游/剧本杀 | 9 | |
| 美容/美甲/纹绣 | 10 | |

## 未收录类型（关键规则）

**不在上表的类型（电影院/影院/密室逃脱/游戏厅/棋牌/宠物店/花店/药店等），数据库没有对应 typeId——禁止猜一个数字传 geoSearch，那必然报「商家类型ID无效」并浪费工具调用。**

正确做法：用 **`searchShops(name="关键词")` 按名称搜索，且不传 typeId**（传了会把结果锁死在错误大类）：

```
用户说「推荐电影院」→ searchShops(name="影院")（不传 typeId；也可换"电影院"再试）
用户说「推荐密室逃脱」→ searchShops(name="密室")（不传 typeId）
```

- 名称搜不到就换同义词（影院/电影院/影城）再试一次；仍无结果再如实告知用户，不要反复乱猜。
- geoSearch 只用于上表 10 类中的明确类型。
