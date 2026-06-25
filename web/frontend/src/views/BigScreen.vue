<template>
  <div class="screen-page">
    <section class="hero-panel">
      <div class="hero-left">
        
        <h1>物料拉动现场大屏</h1>
      
      </div>
      <div class="hero-right">
        <div class="live-pill">
          <span class="live-dot"></span>
          {{ realtimeState }}
        </div>
        <div class="clock">{{ nowText }}</div>
      </div>
    </section>

    <section class="metrics-grid">
      <div class="metric-card normal">
        <span>正常任务</span>
        <b>{{ normal.length }}</b>
        <small>按计划配送</small>
      </div>
      <div class="metric-card urgent">
        <span>紧急配送</span>
        <b>{{ urgent.length }}</b>
        <small>需优先处理</small>
      </div>
      <div class="metric-card danger">
        <span>超时任务</span>
        <b>{{ timeout.length }}</b>
        <small>需立即跟进</small>
      </div>
      <div class="metric-card warn">
        <span>缺料/低库存</span>
        <b>{{ shortage.length }}</b>
        <small>库存风险预警</small>
      </div>
      <div class="metric-card total">
        <span>任务总览</span>
        <b>{{ totalTasks }}</b>
        <small>当前待关注项</small>
      </div>
    </section>

    <section class="flow-panel">
      <div class="flow-step active"><i>01</i><span>扫码触发</span></div>
      <div class="flow-line"></div>
      <div class="flow-step"><i>02</i><span>仓库接单</span></div>
      <div class="flow-line"></div>
      <div class="flow-step"><i>03</i><span>标签打印</span></div>
      <div class="flow-line"></div>
      <div class="flow-step"><i>04</i><span>AGV配送</span></div>
      <div class="flow-line"></div>
      <div class="flow-step"><i>05</i><span>工位签收</span></div>
    </section>

    <section class="screen-layout">
      <div class="board-card large">
        <div class="board-head">
          <div>
            <h2>配送任务队列</h2>
            <p>按正常、紧急、超时分区展示，便于仓库现场快速分拣处理。</p>
          </div>
          <el-button type="primary" round @click="load">刷新</el-button>
        </div>

        <div class="task-zones">
          <div class="task-zone zone-normal">
            <div class="zone-title"><span>正常配送</span><b>{{ normal.length }}</b></div>
            <TaskTable :rows="normal" />
          </div>
          <div class="task-zone zone-urgent">
            <div class="zone-title"><span>紧急配送</span><b>{{ urgent.length }}</b></div>
            <TaskTable :rows="urgent" />
          </div>
          <div class="task-zone zone-danger">
            <div class="zone-title"><span>超时异常</span><b>{{ timeout.length }}</b></div>
            <TaskTable :rows="timeout" />
          </div>
        </div>
      </div>

      <aside class="side-stack">
        <div class="board-card warning-card">
          <div class="board-head compact">
            <div>
              <h2>库存预警</h2>
              <p>低于安全库存的物料</p>
            </div>
          </div>
          <el-table :data="shortage" height="360" empty-text="暂无库存预警">
            <el-table-column prop="warehouseMaterialCode" label="仓库料号" min-width="120" />
            <el-table-column prop="materialName" label="物料" min-width="120" />
            <el-table-column prop="availableQty" label="可用" width="76" />
            <el-table-column prop="safetyStock" label="安全" width="76" />
          </el-table>
        </div>

        <div class="board-card command-card">
          <h2>现场提示</h2>
          <ul>
            <li><span></span>紧急任务优先于正常配送进入拣料队列。</li>
            <li><span></span>超时任务需确认库存、打印与 AGV 状态。</li>
            <li><span></span>缺料预警请及时同步计划与仓库补货。</li>
          </ul>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, onUnmounted, ref } from 'vue'
import { ElTable, ElTableColumn, ElTag } from 'element-plus'
import { get, tagType } from '../api'
import { connectRealtime } from '../realtime'

const normal = ref<any[]>([])
const timeout = ref<any[]>([])
const urgent = ref<any[]>([])
const shortage = ref<any[]>([])
const realtimeState = ref('轮询 + 实时推送')
const nowText = ref(new Date().toLocaleString())
const totalTasks = computed(() => normal.value.length + timeout.value.length + urgent.value.length)
let timer:number | undefined
let clockTimer:number | undefined
let closeRealtime:undefined | (() => void)

const TaskTable = defineComponent({
  props: { rows: { type: Array, default: () => [] } },
  setup(props){
    return () => h(ElTable, { data: props.rows, height: 280, emptyText: '暂无任务' }, () => [
      h(ElTableColumn, { prop:'taskNo', label:'任务号', minWidth: 190 }),
      h(ElTableColumn, { prop:'stationCode', label:'工位', width: 92 }),
      h(ElTableColumn, { prop:'materialName', label:'物料', minWidth: 150, showOverflowTooltip: true }),
      h(ElTableColumn, { prop:'requestQty', label:'数量', width: 74 }),
      h(ElTableColumn, { prop:'deliveryMode', label:'配送', width: 92 }),
      h(ElTableColumn, { prop:'status', label:'状态', width: 108, default: ({row}:any) => h(ElTag, { type: tagType(row.status) as any, effect: 'dark' }, () => row.status) })
    ])
  }
})

async function load(){
 const d:any = await get('/dashboard')
 normal.value = d.normalTasks || []
 timeout.value = d.timeoutTasks || []
 urgent.value = d.urgentTasks || []
 shortage.value = d.shortageItems || d.warnings || []
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
</script>

<style scoped>
.screen-page {
  min-height: calc(100vh - 114px);
  padding: 22px;
  border-radius: 24px;
  color: #e5f0ff;
  background:
    radial-gradient(circle at 10% 0%, rgba(59, 130, 246, .38), transparent 30%),
    radial-gradient(circle at 100% 10%, rgba(14, 165, 233, .24), transparent 34%),
    linear-gradient(135deg, #08111f 0%, #0d1728 52%, #101827 100%);
  overflow: hidden;
}

.hero-panel,
.flow-panel,
.board-card,
.metric-card {
  border: 1px solid rgba(148, 163, 184, .22);
  background: rgba(15, 23, 42, .66);
  box-shadow: 0 24px 60px rgba(0, 0, 0, .22);
  backdrop-filter: blur(18px);
}

.hero-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  border-radius: 24px;
  margin-bottom: 18px;
}

.eyebrow {
  color: #67e8f9;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 1.8px;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.hero-panel h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.15;
  letter-spacing: .4px;
}

.hero-panel p {
  max-width: 780px;
  margin: 12px 0 0;
  color: #b8c7dd;
  font-size: 16px;
  line-height: 1.8;
}

.hero-right {
  min-width: 240px;
  text-align: right;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  color: #bbf7d0;
  background: rgba(34, 197, 94, .12);
  border: 1px solid rgba(34, 197, 94, .28);
  font-weight: 700;
}

.live-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 6px rgba(34, 197, 94, .14);
  animation: pulse 1.8s infinite;
}

.clock {
  margin-top: 14px;
  font-size: 20px;
  font-weight: 800;
  color: #ffffff;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  min-height: 142px;
  padding: 20px;
  border-radius: 22px;
}

.metric-card::after {
  content: "";
  position: absolute;
  right: -34px;
  top: -34px;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .08);
}

.metric-card span {
  color: #b8c7dd;
  font-weight: 700;
}

.metric-card b {
  display: block;
  margin-top: 12px;
  color: #fff;
  font-size: 46px;
  line-height: 1;
  letter-spacing: -1px;
}

.metric-card small {
  display: block;
  margin-top: 12px;
  color: #8ea2bd;
  font-weight: 600;
}

.metric-card.normal { border-color: rgba(96, 165, 250, .32); }
.metric-card.urgent { border-color: rgba(251, 146, 60, .46); }
.metric-card.danger { border-color: rgba(248, 113, 113, .52); }
.metric-card.warn { border-color: rgba(250, 204, 21, .46); }
.metric-card.total { border-color: rgba(45, 212, 191, .42); }

.flow-panel {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr auto 1fr auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 20px;
  margin-bottom: 18px;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #c7d2e5;
  font-weight: 800;
  white-space: nowrap;
}

.flow-step i {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-style: normal;
  color: #93c5fd;
  background: rgba(37, 99, 235, .14);
  border: 1px solid rgba(147, 197, 253, .22);
}

.flow-step.active i {
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
}

.flow-line {
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(59, 130, 246, .7), rgba(148, 163, 184, .18));
}

.screen-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(360px, .75fr);
  gap: 18px;
}

.board-card {
  border-radius: 22px;
  padding: 20px;
}

.board-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.board-head.compact {
  margin-bottom: 14px;
}

.board-head h2,
.command-card h2 {
  margin: 0;
  color: #ffffff;
  font-size: 22px;
}

.board-head p {
  margin: 8px 0 0;
  color: #9eb0c8;
  line-height: 1.6;
}

.task-zones {
  display: grid;
  gap: 16px;
}

.task-zone {
  padding: 16px;
  border-radius: 18px;
  background: rgba(2, 6, 23, .28);
  border: 1px solid rgba(148, 163, 184, .18);
}

.zone-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 900;
  color: #ffffff;
  font-size: 18px;
}

.zone-title b {
  min-width: 36px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, .10);
}

.zone-normal { border-color: rgba(96, 165, 250, .26); }
.zone-urgent { border-color: rgba(251, 146, 60, .38); }
.zone-danger { border-color: rgba(248, 113, 113, .42); }

.side-stack {
  display: grid;
  gap: 18px;
  align-content: start;
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

@media (max-width: 1400px) {
  .metrics-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .screen-layout { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .hero-panel { flex-direction: column; align-items: flex-start; }
  .hero-right { text-align: left; }
  .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .flow-panel { grid-template-columns: 1fr; }
  .flow-line { display: none; }
}
</style>
