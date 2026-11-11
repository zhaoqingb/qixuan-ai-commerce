/**
 * spec_json 在库里是一段 JSON，例如 {"颜色": "曜石黑"}。
 * 页面需要把它变成「颜色 曜石黑」这样的可读文本。
 */
export function parseSpec(specJson: string | null | undefined): Record<string, string> {
  if (!specJson) return {}
  try {
    const raw: unknown = JSON.parse(specJson)
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}

    const obj = raw as Record<string, unknown>
    // 兼容被包装过一层的写法：{"spec": {...}}
    const inner =
      obj.spec && typeof obj.spec === 'object' && !Array.isArray(obj.spec)
        ? (obj.spec as Record<string, unknown>)
        : obj

    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(inner)) {
      result[key] = String(value)
    }
    return result
  } catch {
    return {}
  }
}

export function formatSpec(specJson: string | null | undefined): string {
  const entries = Object.entries(parseSpec(specJson))
  if (entries.length === 0) return '默认规格'
  return entries.map(([key, value]) => `${key} ${value}`).join(' · ')
}

/** 金额展示，统一两位小数 */
export function formatMoney(value: number | null | undefined): string {
  const num = Number(value ?? 0)
  return num.toFixed(2)
}
