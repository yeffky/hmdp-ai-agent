const fs = require('fs')
const path = require('path')
const files = []
function walk(d) {
  for (const e of fs.readdirSync(d)) {
    const p = path.join(d, e)
    const st = fs.statSync(p)
    if (st.isDirectory() && !/node_modules/.test(p)) walk(p)
    else if (/\.vue$/.test(p)) files.push(p)
  }
}
walk('src')
const voidTags = new Set(['img', 'input', 'br', 'hr', 'meta', 'link', 'source', 'wbr', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon'])
let bad = 0
for (const f of files) {
  const s = fs.readFileSync(f, 'utf8')
  const m = s.match(/<template>([\s\S]*)<\/template>/)
  if (!m) { console.log('NO TEMPLATE:', f); bad++; continue }
  const tpl = m[1].replace(/<[a-zA-Z][^>]*\/\s*>/g, '') // 先剔除自闭合标签
  const stack = []
  let err = null
  const re = /<(\/?)([a-zA-Z][\w-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g
  let mm
  while ((mm = re.exec(tpl))) {
    const closing = !!mm[1]
    const tag = mm[2].toLowerCase()
    if (closing) {
      if (stack.length === 0 || stack[stack.length - 1] !== tag) {
        err = 'mismatch </' + tag + '> expected </' + (stack[stack.length - 1] || '?') + '>'
        break
      }
      stack.pop()
    } else if (!voidTags.has(tag)) {
      stack.push(tag)
    }
  }
  if (err) { console.log('FAIL', f, err); bad++ }
  else if (stack.length) { console.log('FAIL', f, 'unclosed:', stack.join(',')); bad++ }
  else console.log('OK  ', f)
}
process.exit(bad ? 1 : 0)
