// 地图 marker：按店铺类型着色（低饱和），dot 小且半透明
export const TYPE_COLORS = {
  1: '#E2482D', // 美食
  2: '#7A4ED0', // KTV
  3: '#E87A9A', // 丽人·美发
  4: '#2E9E6B', // 健身运动
  5: '#C98A3D', // 按摩·足疗
  6: '#B05CE0', // 美容SPA
  7: '#3D8FBF', // 亲子游乐
  8: '#5A5AE0', // 酒吧
  9: '#D0862E', // 轰趴馆
  10: '#E05AA0' // 美睫·美甲
}
export const DOT_COLOR = '#8a8f98'

export function shopMarkerColor(typeId) {
  return TYPE_COLORS[typeId] || DOT_COLOR
}

// 14px 半透明圆点 + 白描边
export function shopMarkerContent(s) {
  const c = shopMarkerColor(s.typeId)
  return (
    `<div style="width:14px;height:14px;border-radius:50%;` +
    `background:${c};opacity:.55;` +
    `border:2px solid rgba(255,255,255,.95);` +
    `box-shadow:0 1px 3px rgba(0,0,0,.25);cursor:pointer;"></div>`
  )
}

// hover 店名标签
export function shopMarkerLabel(s) {
  return (
    `<div style="background:#fff;border:1px solid #eee;border-radius:6px;` +
    `padding:2px 8px;font-size:12px;color:#333;white-space:nowrap;` +
    `box-shadow:0 1px 4px rgba(0,0,0,.2);">${s.name}</div>`
  )
}
