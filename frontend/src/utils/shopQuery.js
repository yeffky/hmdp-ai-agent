// 店铺列表页：把路由 query + 排序状态 + 当前定位翻译成后端接口参数
// loc = { districtId, x, y }（x/y 为当前地区 GEO 圆心，距离排序时使用）
// 分类浏览：type 存在时按分类 ofType；默认(综合)不带坐标 → shopType 自然序
// 距离排序：sortBy=distance 时带圆心坐标 → 后端 geo 距离序
// 人气/评分：sortBy=comments/score 传给后端 ORDER BY，保证分页与排序一致
// 搜索：q 存在时按名称 ofName；带 type/district 时限定范围
// 美食细分：fc 存在且为美食分类时传 foodCategory 过滤
export function buildShopParams(routeQuery, types = [], page = 1, sortBy = '', loc = {}) {
  const params = { current: page }
  const isRankSort = sortBy === 'comments' || sortBy === 'score'
  const fc = routeQuery.fc
  if (routeQuery.q !== undefined) {
    const keyword = routeQuery.q || ''
    if (!keyword) return null
    params.name = keyword
    const typeId = Number(routeQuery.type) || 0
    if (typeId) params.typeId = typeId
    if (fc) params.foodCategory = fc
    if (loc.districtId) params.districtId = loc.districtId
    if (isRankSort) params.sortBy = sortBy
    return params
  }
  params.typeId = Number(routeQuery.type) || types[0]?.id || 0
  if (fc) params.foodCategory = fc
  if (loc.districtId) params.districtId = loc.districtId
  if (isRankSort) params.sortBy = sortBy
  if (sortBy === 'distance' && loc.x != null && loc.y != null) {
    params.x = loc.x
    params.y = loc.y
  }
  return params
}
