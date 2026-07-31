import { h, ref } from 'vue'
import { ElMessageBox } from 'element-plus'

interface CountdownStep {
  title: string
  message: string
  seconds: number
  confirmText?: string
}

/**
 * 带倒计时的二次确认弹窗。倒计时期间“继续”按钮禁用，倒计时结束才可点击。
 * 用户取消则整个 Promise reject（携带 'cancel'）。
 */
function countdownConfirm(step: CountdownStep): Promise<void> {
  const remaining = ref(step.seconds)
  let timer: ReturnType<typeof setInterval> | null = null
  const primaryBtnSelector = '.el-message-box__btns .el-button--primary'

  const setPrimaryDisabled = (disabled: boolean) => {
    const btn = document.querySelector(primaryBtnSelector) as HTMLButtonElement | null
    if (btn) btn.disabled = disabled
  }

  const stop = () => { if (timer) { clearInterval(timer); timer = null } }

  return new Promise<void>((resolve, reject) => {
    ElMessageBox({
      title: step.title,
      type: 'warning',
      showCancelButton: true,
      cancelButtonText: '取消',
      confirmButtonText: step.confirmText || '继续',
      closeOnClickModal: false,
      closeOnPressEscape: false,
      message: () => h('div', { style: 'line-height:1.7' }, [
        h('p', { style: 'margin:0 0 6px' }, step.message),
        remaining.value > 0
          ? h('p', { style: 'margin:0;color:#e6a23c;font-weight:600' }, `请仔细确认，${remaining.value} 秒后可继续`)
          : h('p', { style: 'margin:0;color:#67c23a;font-weight:600' }, '现在可以点击“继续”')
      ]),
      beforeClose: (action, _instance, done) => {
        if (action === 'confirm' && remaining.value > 0) return
        stop()
        done()
      }
    })
      .then(() => { stop(); resolve() })
      .catch(() => { stop(); reject(new Error('cancel')) })

    setTimeout(() => setPrimaryDisabled(remaining.value > 0), 0)
    timer = setInterval(() => {
      remaining.value = Math.max(0, remaining.value - 1)
      if (remaining.value <= 0) {
        stop()
        setPrimaryDisabled(false)
      }
    }, 1000)
  })
}

/**
 * 高危操作的多重确认：第一次倒计时 5 秒确认，继续后再倒计时 10 秒二次确认。
 * 两次都点“继续”才 resolve；任一环节取消则 reject。
 */
export async function doubleCountdownConfirm(action: string, detail?: string): Promise<void> {
  const suffix = detail ? `（${detail}）` : ''
  await countdownConfirm({
    title: `${action} - 第一次确认`,
    message: `你正在执行「${action}」${suffix}，该操作不可撤销。是否继续？`,
    seconds: 5
  })
  await countdownConfirm({
    title: `${action} - 最终确认`,
    message: `再次确认执行「${action}」${suffix}。确认后将立即执行，无法恢复。`,
    seconds: 10,
    confirmText: '确认执行'
  })
}
