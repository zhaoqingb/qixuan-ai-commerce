<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">秒杀活动</h1>
      <div>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建活动</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="activities" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="活动名称" min-width="180" />
      <el-table-column label="开始时间" width="160">
        <template #default="{ row }">{{ formatTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="160">
        <template #default="{ row }">{{ formatTime(row.endAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="onPrecheck(row)">预检查</el-button>
          <el-button
            v-if="row.status !== 'ONLINE'"
            size="small"
            type="success"
            @click="onOnline(row)"
          >
            上线
          </el-button>
          <el-button v-else size="small" type="warning" @click="onOffline(row)">
            下线
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建秒杀活动" width="460px">
      <el-form label-position="top">
        <el-form-item label="活动名称">
          <el-input v-model="form.name" placeholder="例如：双11 限时秒杀" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createActivity,
  fetchActivities,
  offlineActivity,
  onlineActivity,
  precheckActivity,
  type SeckillActivity,
} from '../../api/adminSeckill'

const activities = ref<SeckillActivity[]>([])
const loading = ref(false)

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ name: '', startAt: '', endAt: '' })

async function load() {
  loading.value = true
  try {
    activities.value = await fetchActivities()
  } catch {
    // 错误提示由 http.ts 的响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 后端返回 2026-11-20T00:52:08，展示成 11-20 00:52 */
function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.slice(5, 16).replace('T', ' ')
}

function statusText(status: string): string {
  if (status === 'DRAFT') return '草稿'
  if (status === 'ONLINE') return '已上线'
  if (status === 'OFFLINE') return '已下线'
  return status
}

function statusType(status: string): 'info' | 'success' | 'warning' {
  if (status === 'ONLINE') return 'success'
  if (status === 'OFFLINE') return 'warning'
  return 'info'
}

async function onPrecheck(row: SeckillActivity) {
  try {
    const issues = await precheckActivity(row.id)
    if (issues.length === 0) {
      ElMessage.success('检查通过，可以上线')
      return
    }
    const html = issues.map((issue) => `· ${issue.message}`).join('<br>')
    await ElMessageBox.alert(html, `发现 ${issues.length} 个问题`, {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '知道了',
    })
  } catch {
    // 拦截器已提示
  }
}

async function onOnline(row: SeckillActivity) {
  try {
    await ElMessageBox.confirm(
      `上线后将锁定活动商品的库存，确定上线「${row.name}」吗？`,
      '确认上线',
      { type: 'warning' },
    )
  } catch {
    return // 用户点了取消
  }

  try {
    await onlineActivity(row.id)
    ElMessage.success('已上线')
    await load()
  } catch {
    // 拦截器已提示（库存不足 / 时间太近 / 状态不对等）
  }
}

async function onOffline(row: SeckillActivity) {
  try {
    await ElMessageBox.confirm(
      `下线会释放未售出的库存，确定下线「${row.name}」吗？`,
      '确认下线',
      { type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await offlineActivity(row.id)
    ElMessage.success('已下线')
    await load()
  } catch {
    // 拦截器已提示
  }
}

function openCreate() {
  form.name = ''
  form.startAt = ''
  form.endAt = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!form.name || !form.startAt || !form.endAt) {
    ElMessage.warning('请填写活动名称和起止时间')
    return
  }

  creating.value = true
  try {
    await createActivity({
      name: form.name,
      startAt: form.startAt,
      endAt: form.endAt,
    })
    ElMessage.success('已创建，记得先预检查再上线')
    createVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: 24px;
}

.page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page__title {
  margin: 0;
  font-size: 18px;
}
</style>
