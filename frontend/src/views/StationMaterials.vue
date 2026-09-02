<template>
  <div class="usage-dashboard" v-loading="loading">
    <section class="hero">
      <div><p class="eyebrow">MATERIAL ANALYTICS</p><h1>物料用量看板</h1><p>按现场扫码成功时间统计，关联料号映射与任务历史快照</p></div>
      <div class="hero-actions"><el-button @click="resetFilters">重置筛选</el-button><el-button type="primary" :loading="loading" @click="load">刷新数据</el-button></div>
    </section>

    <section class="filters">
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :shortcuts="dateShortcuts" @change="load" />
      <el-select v-model="factoryFilter" multiple collapse-tags clearable placeholder="工厂" @change="renderCharts"><el-option v-for="x in factories" :key="x" :label="x" :value="x" /></el-select>
      <el-select v-model="materialFilter" multiple filterable collapse-tags clearable placeholder="物料编码/名称" @change="renderCharts"><el-option v-for="x in materialOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select>
      <el-select v-model="stationFilter" multiple filterable collapse-tags clearable placeholder="工位" @change="renderCharts"><el-option v-for="x in stations" :key="x" :label="x" :value="x" /></el-select>
      <el-select v-model="areaFilter" multiple collapse-tags clearable placeholder="配送区域" @change="renderCharts"><el-option v-for="x in areas" :key="x" :label="x" :value="x" /></el-select>
      <el-select v-model="statusFilter" multiple collapse-tags clearable placeholder="任务状态" @change="renderCharts"><el-option v-for="x in statuses" :key="x" :label="statusCn(x)" :value="x" /></el-select>
    </section>

    <section class="metric-grid">
      <article><span>扫码申请次数</span><b>{{ filteredTasks.length }}</b><small>成功落库，不去重</small></article>
      <article><span>有效申请次数</span><b>{{ validTasks.length }}</b><small>自动排除已取消</small></article>
      <article><span>涉及物料种数</span><b>{{ activeMaterialCount }}</b><small>当前筛选范围</small></article>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16"><section class="panel"><header><div><h3>扫码申请次数趋势</h3><p>申请 / 有效 / 取消按扫码日期统计</p></div></header><div ref="trendEl" class="chart tall"></div></section></el-col>
      <el-col :xs="24" :lg="8"><section class="panel"><header><div><h3>工厂分布</h3><p>有效申请次数</p></div></header><div ref="factoryEl" class="chart tall"></div></section></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12"><section class="panel"><header><div><h3>活跃物料排行</h3><p>可切换扫码次数或同物料累计数量</p></div><el-radio-group v-model="materialRankMode" size="small" @change="renderCharts"><el-radio-button value="count">次数</el-radio-button><el-radio-button value="qty">数量</el-radio-button></el-radio-group></header><div ref="materialEl" class="chart"></div></section></el-col>
      <el-col :xs="24" :lg="12"><section class="panel"><header><div><h3>工位排行</h3><p>有效申请次数 TOP 10</p></div></header><div ref="stationEl" class="chart"></div></section></el-col>
      <el-col :xs="24" :lg="12"><section class="panel"><header><div><h3>配送区域分布</h3><p>有效申请次数</p></div></header><div ref="areaEl" class="chart"></div></section></el-col>
      <el-col :xs="24" :lg="12"><section class="panel"><header><div><h3>每日任务量趋势</h3><p>筛选时间区间内每日有效任务数，柱顶直接显示数量</p></div></header><div ref="cancelEl" class="chart"></div></section></el-col>
    </el-row>

    <section class="panel table-panel">
      <header><div><h3>所有物料信息</h3><p>料号映射全集；零扫码物料仍显示，历史快照与当前映射变化会标记</p></div><el-input v-model="tableKeyword" clearable placeholder="搜索物料/仓库代号/地址" style="width:260px" /></header>
      <el-table :data="pagedMaterialRows" border stripe height="520">
        <el-table-column prop="lineMaterialCode" label="物料编码" min-width="150" fixed />
        <el-table-column prop="warehouseCode" label="仓库代号" min-width="135" />
        <el-table-column prop="factory" label="当前工厂" width="110" />
        <el-table-column prop="stationCode" label="当前工位" min-width="150" />
        <el-table-column prop="warehouseLocation" label="当前仓储地址" min-width="150" />
        <el-table-column prop="deliveryAddress" label="当前总装地址" min-width="170" />
        <el-table-column prop="deliveryArea" label="配送区域" width="100" />
        <el-table-column prop="requestCount" label="扫码次数" width="100" sortable />
        <el-table-column prop="validCount" label="有效次数" width="100" sortable />
        <el-table-column prop="cancelCount" label="取消次数" width="100" sortable />
        <el-table-column label="同单位有效用量" min-width="155"><template #default="{row}">{{ qtyText(row.qtyByUnit) }}</template></el-table-column>
        <el-table-column prop="lastScanAt" label="最近扫码" width="175" />
        <el-table-column label="映射对照" width="120" fixed="right"><template #default="{row}"><el-tag v-if="row.mappingChanged" type="warning">映射已变更</el-tag><el-tag v-else type="success" plain>一致</el-tag></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="materialRowsFiltered.length" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" />
    </section>
    <el-alert type="info" :closable="false" show-icon :title="historyNote || '已物理删除的旧任务无法恢复；统计从当前数据库保留的最早扫码记录开始。'" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { get } from '../api'
import { ElMessage } from 'element-plus'

const pad=(n:number)=>String(n).padStart(2,'0')
const day=(d:Date)=>`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`
const addDays=(d:Date,n:number)=>{const x=new Date(d);x.setDate(x.getDate()+n);return x}
const today=new Date()
const dateRange=ref<[string,string]>([day(addDays(today,-29)),day(today)])
const dateShortcuts=[
  {text:'今天',value:()=>[today,today]},{text:'近 7 天',value:()=>[addDays(today,-6),today]},{text:'近 30 天',value:()=>[addDays(today,-29),today]},
  {text:'本月',value:()=>[new Date(today.getFullYear(),today.getMonth(),1),today]},
  {text:'上月',value:()=>[new Date(today.getFullYear(),today.getMonth()-1,1),new Date(today.getFullYear(),today.getMonth(),0)]}
]
const loading=ref(false), tasks=ref<any[]>([]), mappings=ref<any[]>([]), historyNote=ref('')
const factoryFilter=ref<string[]>([]), materialFilter=ref<string[]>([]), stationFilter=ref<string[]>([]), areaFilter=ref<string[]>([]), statusFilter=ref<string[]>([])
const materialRankMode=ref<'count'|'qty'>('count'), tableKeyword=ref(''), page=ref(1), pageSize=ref(20)
const trendEl=ref(),factoryEl=ref(),materialEl=ref(),stationEl=ref(),areaEl=ref(),cancelEl=ref()
const charts:echarts.ECharts[]=[]
const norm=(v:any,fallback='未维护')=>String(v??'').trim()||fallback
const factoryOf=(r:any)=>['弋江','三山'].includes(norm(r.factory,''))?norm(r.factory):'未维护工厂'
const stationOf=(r:any)=>norm(r.stationCode||r.stationName||r.sendStationAddress||r.deliveryAddress,'未维护工位')
const materialOf=(r:any)=>norm(r.materialCode||r.sourceLabelCode,'未维护物料')
const areaOf=(r:any)=>norm(r.deliveryArea,'未分类').replace(/EOVA/g,'E0VA').replace(/EOY/g,'E0Y')
const statusCn=(v:string)=>({CREATED:'已创建',ACCEPTED:'已接单',PICKING:'拣料中',PICKED:'拣料完成',DELIVERING:'配送中',ARRIVED:'已到达',COMPLETED:'已完成',CANCELLED:'已取消',EXCEPTION:'异常'} as any)[v]||v
const filteredTasks=computed(()=>tasks.value.filter(r=>(!factoryFilter.value.length||factoryFilter.value.includes(factoryOf(r)))&&(!materialFilter.value.length||materialFilter.value.includes(materialOf(r)))&&(!stationFilter.value.length||stationFilter.value.includes(stationOf(r)))&&(!areaFilter.value.length||areaFilter.value.includes(areaOf(r)))&&(!statusFilter.value.length||statusFilter.value.includes(r.status))))
const validTasks=computed(()=>filteredTasks.value.filter(r=>r.status!=='CANCELLED'))
const cancelledTasks=computed(()=>filteredTasks.value.filter(r=>r.status==='CANCELLED'))
const activeMaterialCount=computed(()=>new Set(filteredTasks.value.map(materialOf)).size)
const factories=computed(()=>['弋江','三山','未维护工厂'])
const materialOptions=computed(()=>[...new Set([...mappings.value.map(m=>norm(m.lineMaterialCode)),...tasks.value.map(materialOf)])].sort().map(v=>({value:v,label:v})))
const stations=computed(()=>[...new Set([...mappings.value.map(m=>norm(m.stationCode,'未维护工位')),...tasks.value.map(stationOf)])].sort())
const areas=computed(()=>[...new Set([...mappings.value.map(m=>norm(m.deliveryArea,'未分类')),...tasks.value.map(areaOf)])].sort())
const statuses=computed(()=>[...new Set(tasks.value.map(t=>t.status).filter(Boolean))])

async function load(){
  if(!dateRange.value?.[0]||!dateRange.value?.[1])return
  loading.value=true
  try{
    let d:any
    try{d=await get('/material-usage-dashboard',{from:dateRange.value[0],to:dateRange.value[1]})}
    catch{
      const [allTasks,allMappings]:any[]=await Promise.all([get('/tasks'),get('/mappings')])
      const from=dateRange.value[0],to=dateRange.value[1]
      d={tasks:(allTasks||[]).filter((t:any)=>{const x=String(t.createdAt||'').slice(0,10);return x>=from&&x<=to}),mappings:allMappings||[],historyNote:'当前使用兼容统计模式（最多读取现有任务接口最近 1000 条）；后端统计接口发布后自动切换完整日期范围。'}
    }
    tasks.value=d.tasks||[];mappings.value=d.mappings||[];historyNote.value=d.historyNote||'';page.value=1;await nextTick();renderCharts()
  }
  catch(e:any){ElMessage.error(e?.message||'物料统计加载失败')}finally{loading.value=false}
}
function resetFilters(){dateRange.value=[day(addDays(today,-29)),day(today)];factoryFilter.value=[];materialFilter.value=[];stationFilter.value=[];areaFilter.value=[];statusFilter.value=[];tableKeyword.value='';load()}
function group(list:any[],key:(r:any)=>string,value=(_:any)=>1){const m=new Map<string,number>();list.forEach(r=>m.set(key(r),(m.get(key(r))||0)+Number(value(r)||0)));return [...m.entries()].map(([name,value])=>({name,value}))}
function chart(el:any,option:any){if(!el)return;let c=echarts.getInstanceByDom(el);if(!c){c=echarts.init(el);charts.push(c)}c.setOption(option,true)}
function renderCharts(){
  nextTick(()=>{
    const start=new Date(dateRange.value[0]+'T00:00:00'),end=new Date(dateRange.value[1]+'T00:00:00'),dates:string[]=[];for(let d=start;d<=end;d=addDays(d,1))dates.push(day(d))
    const byDate=(list:any[])=>{const m=new Map(group(list,r=>String(r.createdAt||'').slice(0,10)).map(x=>[x.name,x.value]));return dates.map(d=>m.get(d)||0)}
    chart(trendEl.value,{tooltip:{trigger:'axis'},legend:{data:['申请','有效','取消']},grid:{left:45,right:20,bottom:45},xAxis:{type:'category',data:dates},yAxis:{type:'value',minInterval:1},series:[{name:'申请',type:'line',smooth:true,data:byDate(filteredTasks.value)},{name:'有效',type:'line',smooth:true,data:byDate(validTasks.value)},{name:'取消',type:'line',smooth:true,data:byDate(cancelledTasks.value)}]})
    const pie=(el:any,data:any[])=>chart(el,{tooltip:{trigger:'item'},legend:{bottom:0},series:[{type:'pie',radius:['42%','70%'],data}]})
    pie(factoryEl.value,group(validTasks.value,factoryOf));
    chart(areaEl.value,{tooltip:{trigger:'item'},legend:{bottom:0},series:[{type:'pie',radius:['42%','70%'],data:group(validTasks.value,areaOf),label:{show:true,formatter:'{b}: {c}'},labelLine:{show:true}}]})
    const materialData=group(validTasks.value,materialOf,r=>materialRankMode.value==='qty'?Number(r.requestQty||0):1).sort((a,b)=>b.value-a.value).slice(0,10).reverse()
    const bar=(el:any,data:any[])=>chart(el,{tooltip:{trigger:'axis'},grid:{left:110,right:25,bottom:25,top:15},xAxis:{type:'value'},yAxis:{type:'category',data:data.map(x=>x.name)},series:[{type:'bar',data:data.map(x=>x.value),itemStyle:{borderRadius:[0,6,6,0]}}]})
    bar(materialEl.value,materialData);bar(stationEl.value,group(validTasks.value,stationOf).sort((a,b)=>b.value-a.value).slice(0,10).reverse())
    const densityDates:string[]=[]; for(let d=start;d<=end;d=addDays(d,1)) densityDates.push(day(d))
    const densityMap=new Map(group(validTasks.value,r=>String(r.createdAt||'').slice(0,10)).map(x=>[x.name,x.value]))
    const density=densityDates.map(d=>Number(densityMap.get(d)||0))
    chart(cancelEl.value,{tooltip:{trigger:'axis'},grid:{left:45,right:20,bottom:45,top:25},xAxis:{type:'category',data:densityDates,axisLabel:{rotate:densityDates.length>14?45:0}},yAxis:{type:'value',minInterval:1},series:[{name:'有效任务数',type:'bar',data:density,itemStyle:{color:'#0ea5e9',borderRadius:[5,5,0,0]},label:{show:true,position:'top',fontSize:11}},{name:'趋势',type:'line',data:density,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{color:'#f97316',width:2},itemStyle:{color:'#f97316'}}]})
  })
}
const mappingKey=(m:any)=>norm(m.warehouseCode||m.warehouseMaterialCode||m.lineMaterialCode)
const materialRows=computed(()=>mappings.value.map(m=>{
  const related=filteredTasks.value.filter(t=>norm(t.warehouseCode||t.warehouseMaterialCode||t.materialCode)===mappingKey(m))
  const valid=related.filter(t=>t.status!=='CANCELLED'),qtyByUnit:Record<string,number>={}
  valid.forEach(t=>{const u=norm(t.requestUnit,'个');qtyByUnit[u]=(qtyByUnit[u]||0)+Number(t.requestQty||0)})
  const latest=related[0]
  const changed=!!latest&&[factoryOf(latest),stationOf(latest),norm(latest.warehouseAddress||latest.warehouseLocation),norm(latest.deliveryAddress||latest.sendStationAddress),areaOf(latest)].join('|')!==[factoryOf(m),norm(m.stationCode,'未维护工位'),norm(m.warehouseLocation),norm(m.deliveryAddress),areaOf(m)].join('|')
  return{...m,factory:factoryOf(m),requestCount:related.length,validCount:valid.length,cancelCount:related.length-valid.length,qtyByUnit,lastScanAt:latest?.createdAt?new Date(latest.createdAt).toLocaleString():'-',mappingChanged:changed}
}))
const materialRowsFiltered=computed(()=>{const k=tableKeyword.value.trim().toLowerCase();return k?materialRows.value.filter(r=>Object.values(r).join(' ').toLowerCase().includes(k)):materialRows.value})
const pagedMaterialRows=computed(()=>materialRowsFiltered.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value))
const qtyText=(m:Record<string,number>)=>Object.entries(m||{}).map(([u,q])=>`${q} ${u}`).join(' / ')||'0'
const resize=()=>charts.forEach(c=>c.resize())
onMounted(()=>{load();window.addEventListener('resize',resize)})
onBeforeUnmount(()=>{window.removeEventListener('resize',resize);charts.forEach(c=>c.dispose())})
</script>

<style scoped>
.usage-dashboard{display:flex;flex-direction:column;gap:16px}.hero{padding:24px 28px;border-radius:18px;background:linear-gradient(120deg,#0f172a,#164e63);color:#fff;display:flex;justify-content:space-between;align-items:center}.hero h1{margin:3px 0;font-size:28px}.hero p{margin:0;color:#cbd5e1}.eyebrow{font-size:11px!important;letter-spacing:2px;color:#67e8f9!important}.hero-actions{display:flex;gap:8px}.filters{display:grid;grid-template-columns:2fr repeat(5,1fr);gap:10px;padding:14px;background:#fff;border:1px solid #e2e8f0;border-radius:14px}.filters>*{width:100%!important}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.metric-grid article{padding:18px;border:1px solid #dbeafe;border-radius:14px;background:linear-gradient(145deg,#fff,#eff6ff)}.metric-grid span,.metric-grid small{display:block;color:#64748b}.metric-grid b{display:block;margin:6px 0;font-size:30px;color:#0f172a}.metric-grid .cancel{background:linear-gradient(145deg,#fff,#fff7ed);border-color:#fed7aa}.panel{background:#fff;border:1px solid #e2e8f0;border-radius:14px;padding:16px;margin-bottom:16px;box-shadow:0 4px 18px rgba(15,23,42,.04)}.panel header{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.panel h3{margin:0;color:#0f172a}.panel header p{margin:4px 0 0;color:#64748b;font-size:12px}.chart{height:330px}.chart.tall{height:360px}.table-panel .el-pagination{justify-content:flex-end;margin-top:14px}@media(max-width:1100px){.filters{grid-template-columns:repeat(2,1fr)}.metric-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:640px){.hero{align-items:flex-start;gap:14px;flex-direction:column}.filters,.metric-grid{grid-template-columns:1fr}}
</style>
