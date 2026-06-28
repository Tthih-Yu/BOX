<template>
  <div class="screen-page">
    <section class="hero-panel">
      <div class="hero-left">
        <h1>物料拉动现场大屏</h1>
      </div>
      <div class="hero-right">
        <div class="live-pill" role="button" tabindex="0" @click="openDetail('realtime')" @keyup.enter="openDetail('realtime')">
          <span class="live-dot"></span>
          {{ realtimeState }}
        </div>
        <div class="clock" role="button" tabindex="0" @click="openDetail('clock')" @keyup.enter="openDetail('clock')">{{ nowText }}</div>
      </div>
    </section>

    <section class="metrics-grid">
      <div class="metric-card normal" role="button" tabindex="0" @click="openDetail('metric-normal')" @keyup.enter="openDetail('metric-normal')">
        <span>正常任务</span>
        <b>{{ normal.length }}</b>
        <small>按计划配送 · 点击查看</small>
      </div>
      <div class="metric-card urgent" role="button" tabindex="0" @click="openDetail('metric-urgent')" @keyup.enter="openDetail('metric-urgent')">
        <span>紧急配送</span>
        <b>{{ urgent.length }}</b>
        <small>需优先处理 · 点击查看</small>
      </div>
      <div class="metric-card danger" role="button" tabindex="0" @click="openDetail('metric-timeout')" @keyup.enter="openDetail('metric-timeout')">
        <span>超时任务</span>
        <b>{{ timeout.length }}</b>
        <small>需立即跟进 · 点击查看</small>
      </div>
      <div class="metric-card warn" role="button" tabindex="0" @click="openDetail('metric-shortage')" @keyup.enter="openDetail('metric-shortage')">
        <span>缺料/低库存</span>
        <b>{{ shortage.length }}</b>
        <small>库存风险预警 · 点击查看</small>
      </div>
      <div class="metric-card total" role="button" tabindex="0" @click="openDetail('metric-total')" @keyup.enter="openDetail('metric-total')">
        <span>任务总览</span>
        <b>{{ totalTasks }}</b>
        <small>当前待关注项 · 点击查看</small>
      </div>
    </section>

    <section class="screen-layout">
      <div class="board-card large">
        <div class="board-head">
          <div class="board-head-title" role="button" tabindex="0" @click="openDetail('queue-overview')" @keyup.enter="openDetail('queue-overview')">
            <h2>配送任务队列</h2>
            <p>按正常、紧急、超时分区展示 · 点击任意任务卡可查看详情</p>
          </div>
          <div class="board-head-actions">
            <el-button type="primary" plain round @click.stop="goPage('/tasks')">完整工单</el-button>
            <el-button type="primary" round :loading="loading" @click.stop="load">刷新</el-button>
          </div>
        </div>

        <div class="task-zones">
          <div class="task-zone zone-normal">
            <div class="zone-title" role="button" tabindex="0" @click="openDetail('zone-normal')" @keyup.enter="openDetail('zone-normal')">
              <span>正常配送</span><b>{{ normal.length }}</b>
            </div>
            <TaskLane :rows="normal" @pick="openTask" />
          </div>
          <div class="task-zone zone-urgent">
            <div class="zone-title" role="button" tabindex="0" @click="openDetail('zone-urgent')" @keyup.enter="openDetail('zone-urgent')">
              <span>紧急配送</span><b>{{ urgent.length }}</b>
            </div>
            <TaskLane :rows="urgent" @pick="openTask" />
          </div>
          <div class="task-zone zone-danger">
            <div class="zone-title" role="button" tabindex="0" @click="openDetail('zone-timeout')" @keyup.enter="openDetail('zone-timeout')">
              <span>超时异常</span><b>{{ timeout.length }}</b>
            </div>
            <TaskLane :rows="timeout" @pick="openTask" />
          </div>
          <div class="task-zone zone-warn">
            <div class="zone-title" role="button" tabindex="0" @click="openDetail('zone-shortage')" @keyup.enter="openDetail('zone-shortage')">
              <span>库存预警</span><b>{{ shortage.length }}</b>
            </div>
            <WarningLane :rows="shortage" @pick="openShortage" />
          </div>
        </div>
      </div>

      <aside class="side-stack">
        <div class="board-card chart-card">
          <div class="board-head compact">
            <div class="board-head-title" role="button" tabindex="0" @click="openDetail('chart')" @keyup.enter="openDetail('chart')">
              <h2>配送任务可视化</h2>
              <p>按任务类型与库存风险实时统计 · 点击查看分布</p>
            </div>
          </div>
          <div class="donut-wrap">
            <div class="donut" :style="donutStyle" role="button" tabindex="0" @click="openDetail('chart')" @keyup.enter="openDetail('chart')">
              <span>{{ totalVisual }}</span><small>总计</small>
            </div>
          </div>
          <div class="chart-legend">
            <div role="button" tabindex="0" @click="openDetail('metric-normal')" @keyup.enter="openDetail('metric-normal')"><i class="c-normal"></i><span>正常配送</span><b>{{ normal.length }}</b></div>
            <div role="button" tabindex="0" @click="openDetail('metric-urgent')" @keyup.enter="openDetail('metric-urgent')"><i class="c-urgent"></i><span>紧急配送</span><b>{{ urgent.length }}</b></div>
            <div role="button" tabindex="0" @click="openDetail('metric-timeout')" @keyup.enter="openDetail('metric-timeout')"><i class="c-danger"></i><span>超时异常</span><b>{{ timeout.length }}</b></div>
            <div role="button" tabindex="0" @click="openDetail('metric-shortage')" @keyup.enter="openDetail('metric-shortage')"><i class="c-warn"></i><span>库存预警</span><b>{{ shortage.length }}</b></div>
          </div>
        </div>

        <div class="board-card trend-card">
          <h2>现场处理优先级</h2>
          <div class="priority-row danger" role="button" tabindex="0" @click="openDetail('priority-timeout')" @keyup.enter="openDetail('priority-timeout')"><span>1</span><b>超时异常</b><em>立即确认库存、打印与配送状态</em></div>
          <div class="priority-row urgent" role="button" tabindex="0" @click="openDetail('priority-urgent')" @keyup.enter="openDetail('priority-urgent')"><span>2</span><b>紧急配送</b><em>优先拣料并打印仓库条形码</em></div>
          <div class="priority-row normal" role="button" tabindex="0" @click="openDetail('priority-normal')" @keyup.enter="openDetail('priority-normal')"><span>3</span><b>正常配送</b><em>按队列顺序处理</em></div>
          <div class="priority-row warn" role="button" tabindex="0" @click="openDetail('priority-shortage')" @keyup.enter="openDetail('priority-shortage')"><span>4</span><b>库存预警</b><em>同步计划与补货</em></div>
        </div>
      </aside>
    </section>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      :size="drawerSize"
      direction="rtl"
      destroy-on-close
      class="screen-drawer"
    >
      <component :is="drawerContent" v-if="drawerContent" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, markRaw, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElTag, ElButton, ElDescriptions, ElDescriptionsItem, ElEmpty } from 'element-plus'
import { get, tagType } from '../api'
import { connectRealtime } from '../realtime'

const router = useRouter()
const loading = ref(false)
const normal = ref<any[]>([])
const timeout = ref<any[]>([])
const urgent = ref<any[]>([])
const shortage = ref<any[]>([])
const realtimeState = ref('轮询 + 实时推送')
const nowText = ref(new Date().toLocaleString())
const totalTasks = computed(() => normal.value.length + timeout.value.length + urgent.value.length)
const totalVisual = computed(() => normal.value.length + timeout.value.length + urgent.value.length + shortage.value.length)
const donutStyle = computed(() => {
  const total = Math.max(totalVisual.value, 1)
  const n = normal.value.length / total * 100
  const u = urgent.value.length / total * 100
  const t = timeout.value.length / total * 100
  const s = shortage.value.length / total * 100
  return {
    background: `conic-gradient(#60a5fa 0 ${n}%, #fb923c ${n}% ${n + u}%, #f87171 ${n + u}% ${n + u + t}%, #facc15 ${n + u + t}% ${n + u + t + s}%, rgba(148,163,184,.18) ${n + u + t + s}% 100%)`
  }
})

let timer:number | undefined
let clockTimer:number | undefined
let closeRealtime:undefined | (() => void)

function statusInfo(row:any){
  return `${row.stationCode || row.stationName || '-'} ｜ ${row.materialName || row.materialCode || '-'}`
}

const TaskLane = defineComponent({
  props: { rows: { type: Array, default: () => [] } },
  emits: ['pick'],
  setup(props, { emit }){
    return () => h('div', { class: 'lane-list' }, [
      props.rows.length ? props.rows.map((row:any) => h('div', {
        class: 'lane-task',
        key: row.taskNo,
        role: 'button',
        tabindex: 0,
        onClick: () => emit('pick', row),
        onKeyup: (e:KeyboardEvent) => { if(e.key === 'Enter') emit('pick', row) }
      }, [
        h('div', { class: 'task-main' }, [
          h('b', row.taskNo || '-'),
          h('span', statusInfo(row))
        ]),
        h('div', { class: 'task-meta' }, [
          h('span', `数量 ${row.requestQty || 0}`),
          h('span', row.deliveryMode || '-'),
          h(ElTag, { type: tagType(row.status) as any, effect: 'dark', size: 'small' }, () => row.status || '-')
        ])
      ])) : h('div', { class: 'empty-lane' }, '暂无任务')
    ])
  }
})

const WarningLane = defineComponent({
  props: { rows: { type: Array, default: () => [] } },
  emits: ['pick'],
  setup(props, { emit }){
    return () => h('div', { class: 'lane-list' }, [
      props.rows.length ? props.rows.map((row:any, idx:number) => h('div', {
        class: 'lane-task warning-task',
        key: row.warehouseMaterialCode || idx,
        role: 'button',
        tabindex: 0,
        onClick: () => emit('pick', row),
        onKeyup: (e:KeyboardEvent) => { if(e.key === 'Enter') emit('pick', row) }
      }, [
        h('div', { class: 'task-main' }, [
          h('b', row.warehouseMaterialCode || row.materialCode || '-'),
          h('span', row.materialName || '库存低于安全线')
        ]),
        h('div', { class: 'task-meta' }, [
          h('span', `可用 ${row.availableQty ?? '-'}`),
          h('span', `安全 ${row.safetyStock ?? '-'}`),
          h(ElTag, { type: 'warning', effect: 'dark', size: 'small' }, () => 'WARN')
        ])
      ])) : h('div', { class: 'empty-lane' }, '暂无任务')
    ])
  }
})

async function load(){
 loading.value = true
 try {
   const d:any = await get('/dashboard')
   normal.value = d.normalTasks || []
   timeout.value = d.timeoutTasks || []
   urgent.value = d.urgentTasks || []
   shortage.value = d.shortageItems || d.warnings || []
 } finally {
   loading.value = false
 }
}

onMounted(()=>{
  load()
  timer = window.setInterval(load, 10000)
  clockTimer = window.setInterval(() => { nowText.value = new Date().toLocaleString() }, 1000)
  closeRealtime = connectRealtime(['tasks','taskWarnings','boxes','boxPool','agvJobs'], () => {
    realtimeState.value = `实时刷新 ${new Date().toLocaleTimeString()}`
    load()
  })
})

onUnmounted(()=>{
  if(timer) window.clearInterval(timer)
  if(clockTimer) window.clearInterval(clockTimer)
  closeRealtime?.()
})

const drawerVisible = ref(false)
const drawerTitle = ref('')
const drawerSize = ref<string|number>('560px')
const drawerContent = shallowRef<any>(null)

function goPage(path:string){
  drawerVisible.value = false
  router.push(path)
}

function fmt(v:any){ return v == null || v === '' ? '-' : v }

function buildTaskListView(rows:any[], emptyText:string, headerTip:string, jumpPath:string){
  return defineComponent({
    setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, headerTip),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => goPage(jumpPath) }, () => '进入完整页面')
        ]),
        rows.length ? h('div', { class: 'drawer-task-list' }, rows.map((r:any) => h('div', {
          class: 'drawer-task-item',
          key: r.taskNo,
          onClick: () => openTask(r)
        }, [
          h('div', { class: 'drawer-task-head' }, [
            h('b', fmt(r.taskNo)),
            h(ElTag, { type: tagType(r.status) as any, effect: 'dark', size: 'small' }, () => fmt(r.status))
          ]),
          h('div', { class: 'drawer-task-row' }, `${fmt(r.stationCode || r.stationName)} ｜ ${fmt(r.materialName || r.materialCode)}`),
          h('div', { class: 'drawer-task-meta' }, [
            h('span', `数量 ${fmt(r.requestQty)}`),
            h('span', `优先级 ${fmt(r.priority)}`),
            h('span', `截止 ${fmt(r.deadlineAt)}`)
          ])
        ]))) : h(ElEmpty, { description: emptyText, imageSize: 80 })
      ])
    }
  })
}

function buildShortageListView(rows:any[], headerTip:string){
  return defineComponent({
    setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, headerTip),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => goPage('/inventory') }, () => '查看库存'),
          h(ElButton, { type: 'warning', plain: true, onClick: () => goPage('/planning') }, () => '生产计划/补货')
        ]),
        rows.length ? h('div', { class: 'drawer-task-list' }, rows.map((r:any, i:number) => h('div', {
          class: 'drawer-task-item',
          key: r.warehouseMaterialCode || i,
          onClick: () => openShortage(r)
        }, [
          h('div', { class: 'drawer-task-head' }, [
            h('b', fmt(r.warehouseMaterialCode || r.materialCode)),
            h(ElTag, { type: 'warning', effect: 'dark', size: 'small' }, () => 'WARN')
          ]),
          h('div', { class: 'drawer-task-row' }, fmt(r.materialName)),
          h('div', { class: 'drawer-task-meta' }, [
            h('span', `可用 ${fmt(r.availableQty)}`),
            h('span', `安全 ${fmt(r.safetyStock)}`),
            h('span', `仓库 ${fmt(r.warehouseCode)}`)
          ])
        ]))) : h(ElEmpty, { description: '暂无库存预警项', imageSize: 80 })
      ])
    }
  })
}

function openTask(row:any){
  drawerTitle.value = `任务详情 · ${row.taskNo || '-'}`
  drawerSize.value = '620px'
  drawerContent.value = markRaw(defineComponent({
    setup(){
      const fields:[string, any][] = [
        ['任务号', row.taskNo],
        ['状态', row.status],
        ['优先级', row.priority],
        ['来源标签', row.sourceLabelCode],
        ['条码', row.barcodeValue],
        ['仓库代号', row.warehouseCode],
        ['仓库地址', row.warehouseAddress || row.warehouseLocation],
        ['发送工位地址', row.sendStationAddress || row.deliveryAddress],
        ['工位', `${row.stationCode || ''} ${row.stationName || ''}`.trim()],
        ['物料编码', row.materialCode],
        ['物料名称', row.materialName],
        ['仓库料号', row.warehouseMaterialCode],
        ['需求数量', row.requestQty],
        ['盒号', row.boxCode || row.containerNo],
        ['盒序', row.boxSeq && row.boxTotal ? `${row.boxSeq}/${row.boxTotal}` : ''],
        ['盒子大小', row.boxSize],
        ['配送方式', row.deliveryMode],
        ['标签类型', row.labelUsageType],
        ['送料人工号', row.delivererEmployeeNo],
        ['接单人', row.acceptedBy],
        ['拣料人', row.picker],
        ['配送人', row.deliverer],
        ['打印任务', row.printJobNo],
        ['AGV任务', row.agvJobNo || row.deliveryAgvJobNo],
        ['截止时间', row.deadlineAt],
        ['创建时间', row.createdAt],
        ['更新时间', row.updatedAt],
        ['异常原因', row.exceptionReason]
      ]
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, '点击下方按钮可在工单管理页继续操作（接单、拣料、打印、配送、签收等）。'),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => goPage('/tasks') }, () => '前往工单管理'),
          h(ElButton, { plain: true, onClick: () => goPage('/print-jobs') }, () => '查看打印任务'),
          h(ElButton, { plain: true, onClick: () => goPage('/agv-jobs') }, () => '查看 AGV 作业')
        ]),
        h(ElDescriptions, { border: true, column: 2, size: 'small', class: 'drawer-desc' },
          () => fields.map(([label, val]) => h(ElDescriptionsItem, { label }, () => fmt(val)))
        )
      ])
    }
  }))
  drawerVisible.value = true
}

function openShortage(row:any){
  drawerTitle.value = `库存预警 · ${row.warehouseMaterialCode || row.materialCode || '-'}`
  drawerSize.value = '560px'
  drawerContent.value = markRaw(defineComponent({
    setup(){
      const fields:[string, any][] = [
        ['仓库料号', row.warehouseMaterialCode],
        ['物料编码', row.materialCode],
        ['物料名称', row.materialName],
        ['可用库存', row.availableQty],
        ['安全库存', row.safetyStock],
        ['在途/锁定', row.lockedQty],
        ['仓库代号', row.warehouseCode],
        ['更新时间', row.updatedAt]
      ]
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, '建议优先确认在途订单与生产计划，必要时触发补货。'),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => goPage('/inventory') }, () => '查看库存'),
          h(ElButton, { plain: true, onClick: () => goPage('/planning') }, () => '生产计划'),
          h(ElButton, { plain: true, onClick: () => goPage('/materials') }, () => '物料主数据')
        ]),
        h(ElDescriptions, { border: true, column: 1, size: 'small', class: 'drawer-desc' },
          () => fields.map(([label, val]) => h(ElDescriptionsItem, { label }, () => fmt(val)))
        )
      ])
    }
  }))
  drawerVisible.value = true
}

function openDetail(key:string){
  drawerSize.value = '560px'
  if (key === 'realtime') {
    drawerTitle.value = '实时连接状态'
    drawerContent.value = markRaw(defineComponent({ setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, '大屏通过 WebSocket 接收 tasks/boxPool/agvJobs 等频道推送，并以 10 秒轮询兜底。'),
        h(ElDescriptions, { border: true, column: 1, size: 'default', class: 'drawer-desc' }, () => [
          h(ElDescriptionsItem, { label: '当前状态' }, () => realtimeState.value),
          h(ElDescriptionsItem, { label: '推送频道' }, () => 'tasks, taskWarnings, boxes, boxPool, agvJobs'),
          h(ElDescriptionsItem, { label: '轮询周期' }, () => '10 秒'),
          h(ElDescriptionsItem, { label: '最近刷新' }, () => nowText.value)
        ]),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => { load(); } }, () => '立即刷新'),
          h(ElButton, { plain: true, onClick: () => goPage('/health') }, () => '查看系统健康')
        ])
      ])
    }}))
  } else if (key === 'clock') {
    drawerTitle.value = '现场时钟'
    drawerContent.value = markRaw(defineComponent({ setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, '系统时间为浏览器本地时间，用于现场看板对齐排班与节拍。'),
        h(ElDescriptions, { border: true, column: 1, size: 'default', class: 'drawer-desc' }, () => [
          h(ElDescriptionsItem, { label: '当前时间' }, () => nowText.value),
          h(ElDescriptionsItem, { label: '时区' }, () => Intl.DateTimeFormat().resolvedOptions().timeZone || '-')
        ])
      ])
    }}))
  } else if (['metric-normal','zone-normal','priority-normal'].includes(key)) {
    drawerTitle.value = '正常任务'
    drawerContent.value = markRaw(buildTaskListView(normal.value, '暂无正常任务', '按计划节奏配送，可点击单条任务查看完整流水。', '/tasks'))
  } else if (['metric-urgent','zone-urgent','priority-urgent'].includes(key)) {
    drawerTitle.value = '紧急配送'
    drawerContent.value = markRaw(buildTaskListView(urgent.value, '暂无紧急任务', '紧急任务请优先拣料并触发打印/AGV。', '/tasks'))
  } else if (['metric-timeout','zone-timeout','priority-timeout'].includes(key)) {
    drawerTitle.value = '超时任务'
    drawerContent.value = markRaw(buildTaskListView(timeout.value, '暂无超时任务', '超时任务需立即确认库存、打印与配送状态。', '/tasks'))
  } else if (['metric-shortage','zone-shortage','priority-shortage'].includes(key)) {
    drawerTitle.value = '库存预警'
    drawerContent.value = markRaw(buildShortageListView(shortage.value, '低于安全库存的物料清单，可点击查看物料明细。'))
  } else if (key === 'metric-total' || key === 'queue-overview') {
    drawerTitle.value = '任务总览'
    drawerSize.value = '620px'
    drawerContent.value = markRaw(defineComponent({ setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, `当前待关注任务共 ${totalTasks.value} 条，库存预警 ${shortage.value.length} 条。`),
        h('div', { class: 'drawer-grid-4' }, [
          h('div', { class: 'drawer-stat normal' }, [h('span', '正常'), h('b', String(normal.value.length))]),
          h('div', { class: 'drawer-stat urgent' }, [h('span', '紧急'), h('b', String(urgent.value.length))]),
          h('div', { class: 'drawer-stat danger' }, [h('span', '超时'), h('b', String(timeout.value.length))]),
          h('div', { class: 'drawer-stat warn' }, [h('span', '预警'), h('b', String(shortage.value.length))])
        ]),
        h('div', { class: 'drawer-actions' }, [
          h(ElButton, { type: 'primary', onClick: () => goPage('/tasks') }, () => '工单管理'),
          h(ElButton, { plain: true, onClick: () => goPage('/dashboard') }, () => '驾驶舱看板'),
          h(ElButton, { plain: true, onClick: () => goPage('/inventory') }, () => '库存'),
          h(ElButton, { plain: true, onClick: () => goPage('/print-jobs') }, () => '打印任务'),
          h(ElButton, { plain: true, onClick: () => goPage('/agv-jobs') }, () => 'AGV 作业')
        ])
      ])
    }}))
  } else if (key === 'chart') {
    drawerTitle.value = '任务分布'
    drawerContent.value = markRaw(defineComponent({ setup(){
      return () => h('div', { class: 'drawer-body' }, [
        h('div', { class: 'drawer-tip' }, `按任务类型与库存风险实时统计，合计 ${totalVisual.value} 项。`),
        h(ElDescriptions, { border: true, column: 1, size: 'small', class: 'drawer-desc' }, () => [
          h(ElDescriptionsItem, { label: '正常配送' }, () => String(normal.value.length)),
          h(ElDescriptionsItem, { label: '紧急配送' }, () => String(urgent.value.length)),
          h(ElDescriptionsItem, { label: '超时异常' }, () => String(timeout.value.length)),
          h(ElDescriptionsItem, { label: '库存预警' }, () => String(shortage.value.length))
        ])
      ])
    }}))
  } else {
    return
  }
  drawerVisible.value = true
}
</script>

<style scoped>
.screen-page {
  min-height: calc(100vh - 96px);
  padding: 14px 16px 16px;
  border-radius: 20px;
  color: #e5f0ff;
  background:
    radial-gradient(circle at 10% 0%, rgba(59, 130, 246, .32), transparent 30%),
    radial-gradient(circle at 100% 10%, rgba(14, 165, 233, .22), transparent 34%),
    linear-gradient(135deg, #08111f 0%, #0d1728 52%, #101827 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-panel,
.board-card,
.metric-card {
  border: 1px solid rgba(148, 163, 184, .22);
  background: rgba(15, 23, 42, .66);
  box-shadow: 0 16px 40px rgba(0, 0, 0, .22);
  backdrop-filter: blur(18px);
}

.hero-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 22px;
  border-radius: 16px;
}

.hero-left { display: flex; align-items: center; gap: 14px; }

.hero-panel h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
  letter-spacing: .4px;
}

.hero-right {
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: right;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  color: #bbf7d0;
  background: rgba(34, 197, 94, .12);
  border: 1px solid rgba(34, 197, 94, .28);
  font-weight: 700;
  font-size: 13px;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 5px rgba(34, 197, 94, .14);
  animation: pulse 1.8s infinite;
}

.clock {
  font-size: 16px;
  font-weight: 800;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  min-height: 132px;
  padding: 20px 22px;
  border-radius: 18px;
}

.metric-card::after {
  content: "";
  position: absolute;
  right: -28px;
  top: -28px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .06);
}

.metric-card span {
  color: #b8c7dd;
  font-weight: 800;
  font-size: 18px;
  letter-spacing: .5px;
}

.metric-card b {
  display: block;
  margin-top: 10px;
  color: #fff;
  font-size: 52px;
  line-height: 1;
  letter-spacing: -1.5px;
  font-variant-numeric: tabular-nums;
}

.metric-card small {
  display: block;
  margin-top: 10px;
  color: #9eb0c8;
  font-weight: 700;
  font-size: 14px;
}

.metric-card.normal { border-color: rgba(96, 165, 250, .32); }
.metric-card.urgent { border-color: rgba(251, 146, 60, .46); }
.metric-card.danger { border-color: rgba(248, 113, 113, .52); }
.metric-card.warn { border-color: rgba(250, 204, 21, .46); }
.metric-card.total { border-color: rgba(45, 212, 191, .42); }

.metric-card,
.live-pill,
.clock,
.chart-legend div,
.priority-row,
.board-head-title,
.zone-title,
.donut,
.lane-task {
  cursor: pointer;
  transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease, background .18s ease;
}

.metric-card:hover,
.chart-legend div:hover,
.priority-row:hover,
.lane-task:hover,
.donut:hover {
  transform: translateY(-2px);
  border-color: rgba(96, 165, 250, .55);
  box-shadow: 0 16px 32px rgba(56, 189, 248, .18);
}

.zone-title:hover,
.board-head-title:hover h2 { color: #93c5fd; }

.metric-card:focus-visible,
.live-pill:focus-visible,
.clock:focus-visible,
.chart-legend div:focus-visible,
.priority-row:focus-visible,
.zone-title:focus-visible,
.board-head-title:focus-visible,
.donut:focus-visible,
.lane-task:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
}

.board-head-actions { display: flex; gap: 8px; }

.screen-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(320px, .55fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.board-card {
  border-radius: 18px;
  padding: 18px 20px;
}

.board-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(148, 163, 184, .14);
}

.board-head.compact {
  margin-bottom: 10px;
  padding-bottom: 0;
  border-bottom: none;
}

.board-head h2,
.command-card h2 {
  margin: 0;
  color: #ffffff;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: .3px;
}

.board-head.compact h2 {
  font-size: 18px;
}

.board-head p {
  margin: 6px 0 0;
  color: #9eb0c8;
  line-height: 1.5;
  font-size: 13px;
}

.task-zones {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 14px;
  align-items: stretch;
}

.task-zone {
  min-height: 0;
  padding: 14px 14px 12px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(15, 23, 42, .58) 0%, rgba(2, 6, 23, .42) 100%);
  border: 1px solid rgba(148, 163, 184, .2);
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .04), 0 8px 20px rgba(0, 0, 0, .18);
  position: relative;
}

.task-zone::before {
  content: "";
  position: absolute;
  top: 0;
  left: 14px;
  right: 14px;
  height: 3px;
  border-radius: 0 0 4px 4px;
  background: var(--zone-accent, rgba(96, 165, 250, .6));
}

.zone-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(148, 163, 184, .16);
  font-weight: 800;
  color: #ffffff;
  font-size: 16px;
}

.zone-title b {
  min-width: 32px;
  height: 24px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--zone-accent, rgba(96, 165, 250, .22));
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.zone-normal { --zone-accent: #60a5fa; border-color: rgba(96, 165, 250, .34); }
.zone-urgent { --zone-accent: #fb923c; border-color: rgba(251, 146, 60, .42); }
.zone-danger { --zone-accent: #f87171; border-color: rgba(248, 113, 113, .48); }
.zone-warn   { --zone-accent: #facc15; border-color: rgba(250, 204, 21, .46); }

.lane-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.lane-task {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(15, 23, 42, .72);
  border: 1px solid rgba(148, 163, 184, .18);
}

.lane-task.warning-task {
  background: rgba(113, 63, 18, .24);
  border-color: rgba(250, 204, 21, .22);
}

.task-main b {
  display: block;
  color: #fff;
  font-size: 12.5px;
  word-break: break-all;
  margin-bottom: 4px;
}

.task-main span {
  color: #b8c7dd;
  font-size: 12.5px;
  line-height: 1.5;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.task-meta span {
  color: #dbeafe;
  background: rgba(59, 130, 246, .12);
  border: 1px solid rgba(147, 197, 253, .14);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11.5px;
  font-weight: 700;
}

.empty-lane {
  flex: 1;
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8ea2bd;
  border: 1px dashed rgba(148, 163, 184, .22);
  border-radius: 12px;
  font-size: 13px;
}

.donut-wrap {
  display: flex;
  justify-content: center;
  padding: 4px 0 10px;
}

.donut {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  box-shadow: inset 0 0 24px rgba(0,0,0,.18), 0 12px 28px rgba(0,0,0,.24);
}

.donut::after {
  content: "";
  position: absolute;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: #0f172a;
  border: 1px solid rgba(148, 163, 184, .2);
}

.donut span,
.donut small {
  position: relative;
  z-index: 1;
}

.donut span {
  color: #fff;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
}

.donut small {
  color: #9eb0c8;
  font-weight: 800;
  font-size: 11px;
  margin-top: 4px;
}

.chart-legend {
  display: grid;
  gap: 6px;
}

.chart-legend div,
.priority-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(2, 6, 23, .28);
  border: 1px solid rgba(148, 163, 184, .16);
}

.chart-legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.chart-legend span { color: #c7d2e5; font-weight: 800; font-size: 12.5px; }
.chart-legend b { color: #fff; font-size: 16px; }
.c-normal { background: #60a5fa; }
.c-urgent { background: #fb923c; }
.c-danger { background: #f87171; }
.c-warn { background: #facc15; }

.trend-card h2 { margin: 0 0 10px; color: #fff; font-size: 18px; }
.priority-row { grid-template-columns: 26px 80px 1fr; margin-bottom: 6px; padding: 8px 10px; }
.priority-row span { width: 22px; height: 22px; display:inline-flex; align-items:center; justify-content:center; border-radius:8px; color:#fff; font-weight:900; font-size: 12px; }
.priority-row b { color:#fff; font-size: 13px; }
.priority-row em { color:#9eb0c8; font-style:normal; line-height:1.45; font-size: 12px; }
.priority-row.danger span { background:#ef4444; }
.priority-row.urgent span { background:#f97316; }
.priority-row.normal span { background:#3b82f6; }
.priority-row.warn span { background:#eab308; }

.side-stack {
  display: grid;
  gap: 12px;
  align-content: start;
  min-height: 0;
}

.board-card.large {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.board-card.large .task-zones {
  flex: 1;
  min-height: 0;
}

.command-card ul {
  list-style: none;
  padding: 0;
  margin: 16px 0 0;
}

.command-card li {
  display: flex;
  gap: 10px;
  color: #b8c7dd;
  line-height: 1.8;
  margin-bottom: 12px;
}

.command-card li span {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  margin-top: 10px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 0 5px rgba(56, 189, 248, .12);
}

.screen-page :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(15, 23, 42, .92);
  --el-table-row-hover-bg-color: rgba(37, 99, 235, .18);
  --el-table-border-color: rgba(148, 163, 184, .16);
  color: #dce8f8;
  background: transparent;
  border-radius: 14px;
}

.screen-page :deep(.el-table th.el-table__cell) {
  background: rgba(15, 23, 42, .92) !important;
  color: #93c5fd;
  font-size: 14px;
}

.screen-page :deep(.el-table td.el-table__cell) {
  color: #e5f0ff;
  background: transparent;
}

.screen-page :deep(.el-table__empty-text) {
  color: #8ea2bd;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  70% { transform: scale(1.18); opacity: .72; }
  100% { transform: scale(1); opacity: 1; }
}

.screen-page :deep(.screen-drawer .el-drawer__header) {
  margin-bottom: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-light);
  color: #0f172a;
  font-weight: 800;
}

.screen-page :deep(.screen-drawer .el-drawer__body) {
  padding: 0;
}

.drawer-body {
  padding: 14px 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: #1f2937;
}

.drawer-tip {
  padding: 10px 12px;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  line-height: 1.6;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 280px);
  overflow: auto;
}

.drawer-task-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.drawer-task-item:hover {
  border-color: #60a5fa;
  box-shadow: 0 6px 14px rgba(59, 130, 246, .12);
}

.drawer-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.drawer-task-head b {
  color: #0f172a;
  font-size: 13.5px;
  word-break: break-all;
}

.drawer-task-row {
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.drawer-task-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.drawer-task-meta span {
  font-size: 12px;
  color: #1e3a8a;
  background: #eff6ff;
  border-radius: 999px;
  padding: 2px 8px;
}

.drawer-grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.drawer-stat {
  border-radius: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-stat span { color: #64748b; font-size: 12px; font-weight: 700; }
.drawer-stat b { color: #0f172a; font-size: 24px; font-variant-numeric: tabular-nums; }
.drawer-stat.normal { border-color: #bfdbfe; background: #eff6ff; }
.drawer-stat.urgent { border-color: #fed7aa; background: #fff7ed; }
.drawer-stat.danger { border-color: #fecaca; background: #fef2f2; }
.drawer-stat.warn { border-color: #fde68a; background: #fefce8; }

.drawer-desc :deep(.el-descriptions__label) { color: #475569; font-weight: 700; }
.drawer-desc :deep(.el-descriptions__content) { color: #0f172a; word-break: break-all; }

@media (max-width: 1400px) {
  .screen-layout { grid-template-columns: minmax(0, 1.6fr) minmax(280px, .6fr); }
  .task-zones { grid-template-columns: repeat(2, minmax(200px, 1fr)); }
}

@media (max-width: 1100px) {
  .metrics-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .screen-layout { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .hero-panel { flex-direction: column; align-items: flex-start; }
  .hero-right { text-align: left; }
  .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .task-zones { grid-template-columns: 1fr; }
}
</style>
