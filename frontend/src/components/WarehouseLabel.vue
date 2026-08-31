<template>
  <div class="warehouse-label">
    <div class="wl-top">
      <div class="wl-usage">{{ urgent ? '紧急配送(备用)' : '正常配送(使用)' }}</div>
      <div class="wl-barcode" v-html="barcodeSvg"></div>
    </div>
    <div class="wl-mid">
      <div class="wl-cell"><span>物料名称</span><b>{{ field('materialCode', 'materialName') }}</b></div>
      <div class="wl-cell"><span>仓储地址</span><b>{{ field('warehouseAddress', 'warehouseLocation') }}</b></div>
      <div class="wl-cell"><span>货架工位地址</span><b class="small">{{ field('sendStationAddress', 'deliveryAddress', 'stationName', 'stationCode') }}</b></div>
    </div>
    <div class="wl-bottom">
      <div class="wl-cell"><span>盒子大小</span><b>{{ field('boxSize') }}</b></div>
      <div class="wl-cell"><span>数量</span><b>{{ field('requestQty') }}</b></div>
      <div class="wl-cell"><span>配送区域</span><b class="small">{{ field('deliveryArea') }}</b></div>
      <div class="wl-cell"><span>任务号</span><b class="tiny">{{ field('taskNo') }}</b></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ row:any; barcodeSvg?:string }>()
const urgent = computed(() => {
  const mode = String(props.row?.deliveryMode || '').toUpperCase()
  const priority = String(props.row?.priority || '').toUpperCase()
  const usage = String(props.row?.labelUsageType || '').toUpperCase()
  return mode === 'URGENT' || priority === 'URGENT' || usage === 'SPARE'
})
function field(...keys:string[]){
  for (const key of keys) {
    const value = props.row?.[key]
    if (value !== undefined && value !== null && String(value).trim()) return value
  }
  return '-'
}
</script>

<style>
.warehouse-label{width:480px;height:300px;border:2px solid #111;background:#fff;color:#111;font-family:Arial,"Microsoft YaHei",sans-serif;box-sizing:border-box;display:flex;flex-direction:column;overflow:hidden}
.warehouse-label .wl-top{display:flex;align-items:center;gap:10px;height:96px;flex:none;border-bottom:2px solid #111;padding:0 14px}
.warehouse-label .wl-usage{flex:none;background:#111;color:#fff;border-radius:22px;padding:10px 16px;font-size:18px;font-weight:800;white-space:nowrap}
.warehouse-label .wl-barcode{flex:1;display:flex;align-items:center;justify-content:center;overflow:hidden}
.warehouse-label .wl-barcode svg{width:100%;height:82px;display:block}
.warehouse-label .wl-mid{display:flex;flex:1;border-bottom:2px solid #111;min-height:0}
.warehouse-label .wl-mid>.wl-cell{flex:1;border-right:2px solid #111}
.warehouse-label .wl-mid>.wl-cell:last-child{border-right:0}
.warehouse-label .wl-bottom{display:flex;flex:1;min-height:0}
.warehouse-label .wl-bottom>.wl-cell{flex:1;border-right:2px solid #111}
.warehouse-label .wl-bottom>.wl-cell:last-child{border-right:0}
.warehouse-label .wl-cell{display:flex;flex-direction:column;justify-content:center;gap:3px;min-height:0;overflow:hidden;padding:6px 10px}
.warehouse-label .wl-cell span{font-size:12px;font-weight:600;color:#333;line-height:1}
.warehouse-label .wl-cell b{font-size:22px;font-weight:800;word-break:break-all;line-height:1.1}
.warehouse-label .wl-cell b.small{font-size:17px}
.warehouse-label .wl-cell b.tiny{font-size:12px;letter-spacing:.2px}
.warehouse-label-print-root{display:none}
@media print{
  @page{size:80mm 50mm;margin:0}
  html,body,#app{width:80mm!important;height:auto!important;margin:0!important;padding:0!important;background:#fff!important}
  body *{visibility:hidden!important}
  .warehouse-label-print-root,.warehouse-label-print-root *{visibility:visible!important}
  .warehouse-label-print-root{display:block!important;position:absolute!important;inset:0 auto auto 0!important;width:80mm!important;margin:0!important;padding:0!important}
  .warehouse-label-print-page{width:80mm!important;height:50mm!important;margin:0!important;padding:0!important;overflow:hidden!important;break-after:page!important;page-break-after:always!important}
  .warehouse-label-print-page:last-child{break-after:auto!important;page-break-after:auto!important}
  .warehouse-label{width:80mm!important;height:50mm!important;border-width:.3333mm!important}
  .warehouse-label .wl-top{height:16mm!important;gap:1.6667mm!important;border-bottom-width:.3333mm!important;padding:0 2.3333mm!important}
  .warehouse-label .wl-usage{border-radius:3.6667mm!important;padding:1.6667mm 2.6667mm!important;font-size:3mm!important}
  .warehouse-label .wl-barcode svg{height:13.6667mm!important}
  .warehouse-label .wl-mid{border-bottom-width:.3333mm!important}
  .warehouse-label .wl-mid>.wl-cell,.warehouse-label .wl-bottom>.wl-cell{border-right-width:.3333mm!important}
  .warehouse-label .wl-cell{gap:.5mm!important;padding:1mm 1.6667mm!important}
  .warehouse-label .wl-cell span{font-size:2mm!important}
  .warehouse-label .wl-cell b{font-size:3.6667mm!important}
  .warehouse-label .wl-cell b.small{font-size:2.8333mm!important}
  .warehouse-label .wl-cell b.tiny{font-size:2mm!important;letter-spacing:.0333mm!important}
}

</style>
