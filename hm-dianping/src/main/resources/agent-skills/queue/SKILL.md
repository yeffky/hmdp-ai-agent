---
name: queue
description: 取号排队、查询排队进度与某店排队情况、取消排队。用户要排队、问前面还有几桌、取消等位时使用。触发词：排队,取号,叫号,等位,等待,前面还有几桌
---

# 排队 Skill

## 何时使用
用户要：取号排队、查排队进度、查某店排队情况、取消排队。

## 工具决策
| 用户需求 | 工具 | 前置 |
|---|---|---|
| 取号 | `takeQueueNumber(shopId, peopleCount)` | 需 shopId（shop 技能获取） |
| 查我的排队进度 | `queryMyQueueStatus()` | — |
| 查某店排队情况 | `queryShopQueueStatus(shopId)` | 需 shopId（shop 技能获取） |
| 取消排队 | `cancelMyQueue(ticketId)` | 需排队记录 id |

## 流程
1. 取号/查某店需要 shopId：**本技能不含店铺搜索工具**（搜索属 shop 技能）。shopId 由 Planner 规划的 **shop** 技能获取——取号/查某店场景应把 shop 与 queue 一并选入 plan.skills。若运行时无 shopId 且无搜索工具可用，说明规划遗漏，应触发 replan 让 Planner 补 shop。
2. **用户明确要取号时**：
   - 先调用 `queryShopQueueStatus(shopId)` 获取该店**真实排队情况**（当前排队桌数/等位时长），供确认提示使用；
   - 再调用 `takeQueueNumber(shopId, peopleCount)`——系统会自动暂停等待用户确认，**不要再发文字问"需要取号吗"**。人数默认 2，用户说了按用户。
3. 排队进度用队列号 + 前方桌数如实告知。
4. **取消排队**：先调用 `queryMyQueueStatus()` 确认当前排队记录（哪家店/几号/几人），再调用 `cancelMyQueue(ticketId)`——确认提示展示真实排队信息，请用户确认取消。确认提示怎么写，见 `references/queue-confirmation.md`。

## 护栏
- **取号 / 取消排队是写操作**：一旦执行无法撤回，所以调用后系统会暂停要求用户确认（回复"确认"才真正执行），无需自行文字确认，也不要跳过工具去声称已完成。
- 排队人数 ≤ 合理上限；商家不开放排队的（queue_enabled=0）要如实告知。
- 确认提示只用真实工具结果里的排队数据，规则见 `references/queue-confirmation.md`。
- 不暴露内部排队记录 ID，用"您的排队号"表达。
- 取号前必须有真实 shopId（由 shop 技能获取），不得编造。

## 回答规则（Answer 阶段）
- **写操作确认由系统确认卡片完成**（取号/取消排队的确认/取消按钮，见上节）：Answer 阶段**禁止再输出"确认取号吗/需要确认吗/回复确认即执行"等二次确认文字**——确认卡片已展示，Answer 只需复述执行结果（成功/失败/排队号）。
- 回答涉及排队信息时用「排队号 + 前方桌数」如实告知，不暴露内部 ticketId。
- 引用排队数据一律来自工具结果，禁止编造桌数/等位时长（见 `references/queue-confirmation.md`）。

## 写操作确认卡片（UI 展示规范）
取号 / 取消排队调用后，系统自动暂停并生成**确认卡片 + 确认/取消按钮**（用户点「确认」才真正执行，按钮交互由系统保证，不要用文字代替确认）：
- **取号（takeQueueNumber）**：卡片 `kind=queue`，展示 店名（shopName）+ 用餐人数（peopleCount，未指定默认 2 人）。
- **取消排队（cancelMyQueue）**：卡片 `kind=queue-cancel`，展示 店名（shopName）+ 排队号（queueNumber）。
- 卡片字段来源、数据真实性与红线见 `references/queue-confirmation.md`；卡片由系统按写操作参数生成，LLM 只负责把参数调对（shopId/ticketId 来自真实查询），不自行拼卡片内容。
