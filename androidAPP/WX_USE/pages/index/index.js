const api = require('../../utils/api.js')
const settingsStore = require('../../utils/settings.js')
const developerInfo = require('../../utils/developer.js')

const INITIAL_OPERATION = {
  title: '待扫码',
  detail: '点击底部“开始扫码”按钮，将条码或二维码放入扫码预览框',
  isError: false
}

Page({
  data: {
    networkMode: api.NETWORK_INTERNAL,
    serverUrl: api.DEFAULT_SERVER_BASE_URL,
    externalUrl: api.DEFAULT_EXTERNAL_BASE_URL,
    enrollmentKey: '',
    relayDeviceToken: '',
    relayRegisteredDeviceNo: '',
    submitPath: api.DEFAULT_SCAN_SUBMIT_PATH,
    deviceNo: '',
    employeeNo: '',
    deviceModel: '',
    isRelayRegistered: false,
    showSettings: false,
    isSending: false,
    pendingPreview: null,
    requestQtyText: '',
    requestUnit: api.DEFAULT_REQUEST_UNIT,
    operation: INITIAL_OPERATION,
    focusedField: '',
    keyboardHeight: 0,
    scrollIntoView: '',
    developerInfo
  },

  onLoad() {
    const settings = settingsStore.getSettings()
    const isExternal = settings.networkMode === api.NETWORK_EXTERNAL
    this.setData({
      networkMode: settings.networkMode,
      serverUrl: settings.serverBaseUrl,
      externalUrl: settings.externalBaseUrl,
      relayDeviceToken: settings.relayDeviceToken,
      relayRegisteredDeviceNo: settings.relayRegisteredDeviceNo,
      submitPath: settings.scanSubmitPath,
      deviceNo: settings.deviceNo,
      employeeNo: settings.employeeNo,
      deviceModel: api.getDeviceModel(),
      developerInfo,
      isRelayRegistered: this.computeRelayRegistered(
        settings.relayDeviceToken,
        settings.relayRegisteredDeviceNo,
        settings.deviceNo
      )
    })
    this.warmUp(settings, isExternal)
  },

  computeRelayRegistered(token, registeredDeviceNo, deviceNo) {
    return Boolean(token && registeredDeviceNo && registeredDeviceNo === String(deviceNo).trim())
  },

  warmUp(settings, isExternal) {
    const baseUrl = isExternal ? settings.externalBaseUrl : settings.serverBaseUrl
    const run = async () => {
      try {
        if (isExternal) {
          await new api.ExternalScanRelayApi(baseUrl).health()
        } else {
          await new api.MaterialPullApi(baseUrl).healthReady()
        }
        console.log('server warm-up completed')
      } catch (e) {
        console.warn('server warm-up failed', e && e.message)
      }
    }
    run()
  },

  onToggleSettings() {
    if (this.data.isSending) return
    this.setData({
      showSettings: !this.data.showSettings,
      focusedField: '',
      keyboardHeight: 0,
      scrollIntoView: ''
    })
  },

  onNetworkModeChange(e) {
    if (this.data.isSending) return
    const mode = e.currentTarget.dataset.mode
    this.setData({ networkMode: mode })
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field
    if (!field) return
    const patch = { [field]: e.detail.value }
    if (field === 'deviceNo') {
      patch.isRelayRegistered = this.computeRelayRegistered(
        this.data.relayDeviceToken,
        this.data.relayRegisteredDeviceNo,
        e.detail.value
      )
    }
    this.setData(patch)
  },

  onInputFocus(e) {
    const field = e.currentTarget.dataset.field
    const keyboardHeight = (e.detail && e.detail.height) || 0
    if (!field) return
    this.setData({
      focusedField: field,
      keyboardHeight,
      scrollIntoView: `field-${field}`
    })
  },

  onInputBlur() {
    this.setData({
      focusedField: '',
      keyboardHeight: 0,
      scrollIntoView: ''
    })
  },

  onInputConfirm() {
    if (wx.hideKeyboard) {
      wx.hideKeyboard()
    }
  },

  toast(title, icon = 'none') {
    wx.showToast({ title, icon, duration: 1800 })
  },

  onScanClick() {
    if (this.data.isSending) {
      this.toast('正在发送上一次扫码')
      return
    }
    this.setData({
      operation: {
        title: '等待扫码',
        detail: '请将条码或二维码放入扫码预览框',
        isError: false
      }
    })

    wx.navigateTo({
      url: '/pages/scanner/scanner',
      events: {
        scanned: (data) => {
          this.handleScan(data.value, data.format || 'UNKNOWN')
        },
        cancel: () => {
          this.setData({
            operation: {
              title: '已取消扫码',
              detail: '点击底部扫码按钮可重新打开摄像头',
              isError: false
            }
          })
        }
      },
      fail: () => {
        this.setData({
          operation: {
            title: '扫码失败',
            detail: '无法打开扫码页面',
            isError: true
          }
        })
      }
    })
  },

  async handleScan(rawValue, format) {
    const value = String(rawValue || '').trim()
    if (!value) {
      this.toast('未读取到条码')
      return
    }
    if (this.data.isSending) {
      this.toast('正在发送上一次扫码')
      return
    }

    this.lastScan = { value, format }
    this.setData({
      isSending: true,
      pendingPreview: null,
      operation: {
        title: '正在查询物料',
        detail: value,
        isError: false
      }
    })

    const isExternal = this.data.networkMode === api.NETWORK_EXTERNAL
    const normalizedUrl = isExternal
      ? api.normalizeExternalBaseUrl(this.data.externalUrl)
      : api.normalizeInternalBaseUrl(this.data.serverUrl)
    const normalizedPath = api.normalizePath(this.data.submitPath)

    if (isExternal) {
      this.setData({ externalUrl: normalizedUrl })
    } else {
      this.setData({ serverUrl: normalizedUrl })
    }
    this.setData({ submitPath: normalizedPath })

    settingsStore.updateSettings({
      networkMode: this.data.networkMode,
      serverBaseUrl: isExternal ? this.data.serverUrl : normalizedUrl,
      externalBaseUrl: isExternal ? normalizedUrl : this.data.externalUrl,
      scanSubmitPath: normalizedPath,
      deviceNo: this.data.deviceNo,
      employeeNo: this.data.employeeNo
    })

    try {
      const preview = await this.fetchScanPreview({
        serverUrl: normalizedUrl,
        networkMode: this.data.networkMode,
        relayDeviceToken: this.data.relayDeviceToken,
        deviceNo: this.data.deviceNo,
        deviceModel: this.data.deviceModel,
        employeeNo: this.data.employeeNo,
        scanCode: value,
        format
      })
      this.setData({
        pendingPreview: preview,
        requestQtyText: preview.defaultQty,
        requestUnit: preview.defaultUnit,
        operation: {
          title: '请确认申请',
          detail: '核对物料、数量和单位后点击发送',
          isError: false
        }
      })
    } catch (e) {
      console.error('load scan preview failed', e)
      this.setData({
        operation: {
          title: '查询失败',
          detail: e && e.message ? e.message : '无法获取物料信息',
          isError: true
        }
      })
      this.toast('查询失败')
    } finally {
      this.setData({ isSending: false })
    }
  },

  async fetchScanPreview(params) {
    const {
      serverUrl,
      networkMode,
      relayDeviceToken,
      deviceNo,
      deviceModel,
      employeeNo,
      scanCode,
      format
    } = params

    let response
    if (networkMode === api.NETWORK_EXTERNAL) {
      const relay = new api.ExternalScanRelayApi(serverUrl)
      const result = await relay.executeScan({
        action: 'preview',
        scanCode,
        format,
        requestQty: null,
        requestUnit: null,
        deviceNo,
        deviceToken: relayDeviceToken
      })
      response = result.data || result
    } else {
      const materialApi = new api.MaterialPullApi(serverUrl)
      response = await materialApi.previewBarcodeScan(
        scanCode,
        format,
        deviceNo,
        deviceModel,
        employeeNo
      )
    }

    const quantityText = api.firstText(
      response,
      'defaultQty',
      'standardQty',
      'requestQty'
    )
    const quantity = api.parseDecimal(quantityText)
    if (quantity === null || quantity <= 0) {
      throw new Error('后台未返回有效的默认申请数量')
    }

    return {
      scanCode,
      format,
      materialCode: api.firstText(response, 'materialCode'),
      materialName: api.firstText(response, 'materialName'),
      warehouseCode: api.firstText(response, 'warehouseCode'),
      stationAddress: api.firstText(
        response,
        'sendStationAddress',
        'deliveryAddress',
        'stationCode',
        'warehouseAddress',
        'warehouseLocation'
      ),
      defaultQty: api.toPlainDecimal(quantity),
      defaultUnit:
        api.firstText(response, 'defaultUnit', 'standardUnit', 'requestUnit', 'unit') ||
        api.DEFAULT_REQUEST_UNIT
    }
  },

  onCancelPreview() {
    if (this.data.isSending) return
    this.setData({
      pendingPreview: null,
      requestQtyText: '',
      requestUnit: api.DEFAULT_REQUEST_UNIT,
      operation: {
        title: '已取消',
        detail: '本次申请未发送，可以重新扫码',
        isError: false
      }
    })
  },

  async onSubmitPreview() {
    if (this.data.isSending) return
    const preview = this.data.pendingPreview
    const quantity = api.parseDecimal(this.data.requestQtyText)

    if (!preview) {
      this.toast('请先扫码获取物料信息')
      return
    }
    if (quantity === null || quantity <= 0) {
      this.toast('申请数量必须大于 0')
      return
    }

    const unit = String(this.data.requestUnit).trim() || api.DEFAULT_REQUEST_UNIT
    this.setData({
      isSending: true,
      operation: {
        title: '正在发送申请',
        detail: `${api.toPlainDecimal(quantity)} ${unit}`,
        isError: false
      }
    })

    const isExternal = this.data.networkMode === api.NETWORK_EXTERNAL
    const normalizedUrl = isExternal
      ? api.normalizeExternalBaseUrl(this.data.externalUrl)
      : api.normalizeInternalBaseUrl(this.data.serverUrl)

    const result = await this.sendScannedCode({
      serverUrl: normalizedUrl,
      networkMode: this.data.networkMode,
      relayDeviceToken: this.data.relayDeviceToken,
      submitPath: api.normalizePath(this.data.submitPath),
      deviceNo: this.data.deviceNo,
      deviceModel: this.data.deviceModel,
      employeeNo: this.data.employeeNo,
      scanCode: preview.scanCode,
      format: preview.format,
      requestQty: quantity,
      requestUnit: unit
    })

    this.setData({
      isSending: false,
      operation: result
    })
    if (!result.isError) {
      this.setData({
        pendingPreview: null,
        requestQtyText: '',
        requestUnit: api.DEFAULT_REQUEST_UNIT
      })
    }
    this.toast(result.title)
  },

  async sendScannedCode(params) {
    const {
      serverUrl,
      networkMode,
      relayDeviceToken,
      submitPath,
      deviceNo,
      deviceModel,
      employeeNo,
      scanCode,
      format,
      requestQty,
      requestUnit
    } = params

    try {
      let response
      if (networkMode === api.NETWORK_EXTERNAL) {
        const action = api.actionFromPath(submitPath)
        if (['empty', 'receive', 'exception'].indexOf(action) < 0) {
          throw new Error(`外网模式不支持业务动作：${action}`)
        }
        const relay = new api.ExternalScanRelayApi(serverUrl)
        const result = await relay.executeScan({
          action,
          scanCode,
          format,
          requestQty,
          requestUnit,
          deviceNo,
          deviceToken: relayDeviceToken
        })
        response = result.data || result
      } else {
        const materialApi = new api.MaterialPullApi(serverUrl)
        response = await materialApi.submitBarcodeScan({
          path: submitPath,
          scanCode,
          format,
          requestQty,
          requestUnit,
          deviceNo,
          deviceModel,
          employeeNo
        })
      }

      return {
        title: '发送成功',
        detail: api.summarizeResponse(response),
        isError: false
      }
    } catch (e) {
      console.error('send scanned code failed', e)
      return {
        title: '发送失败',
        detail: e && e.message ? e.message : '网络请求失败',
        isError: true
      }
    }
  },

  async onSaveSettings() {
    if (this.data.isSending) return

    const isExternal = this.data.networkMode === api.NETWORK_EXTERNAL
    const serverUrl = api.normalizeInternalBaseUrl(this.data.serverUrl)
    const externalUrl = api.normalizeExternalBaseUrl(this.data.externalUrl)
    const submitPath = api.normalizePath(this.data.submitPath)
    const deviceNo = String(this.data.deviceNo).trim()
    const employeeNo = String(this.data.employeeNo).trim()

    const patch = {
      networkMode: this.data.networkMode,
      serverBaseUrl: serverUrl,
      externalBaseUrl: externalUrl,
      scanSubmitPath: submitPath,
      deviceNo,
      employeeNo
    }
    settingsStore.updateSettings(patch)

    this.setData({
      serverUrl,
      externalUrl,
      submitPath,
      deviceNo,
      employeeNo,
      isSending: true
    })

    try {
      if (isExternal) {
        let token = this.data.relayDeviceToken
        let registeredDeviceNo = this.data.relayRegisteredDeviceNo
        if (String(this.data.enrollmentKey).trim()) {
          token = await new api.ExternalScanRelayApi(externalUrl).registerDevice(
            deviceNo,
            this.data.deviceModel,
            employeeNo,
            this.data.enrollmentKey
          )
          const updated = settingsStore.updateSettings({
            relayDeviceToken: token,
            relayRegisteredDeviceNo: deviceNo
          })
          registeredDeviceNo = updated.relayRegisteredDeviceNo
          this.setData({
            relayDeviceToken: updated.relayDeviceToken,
            relayRegisteredDeviceNo: updated.relayRegisteredDeviceNo,
            enrollmentKey: ''
          })
        }

        if (!token || String(registeredDeviceNo).trim() !== deviceNo) {
          throw new Error('当前设备编号尚未登记，请填写设备登记码后保存')
        }
        this.setData({
          isRelayRegistered: this.computeRelayRegistered(
            token,
            registeredDeviceNo,
            deviceNo
          ),
          operation: {
            title: '外网模式已就绪',
            detail: externalUrl,
            isError: false
          }
        })
      } else {
        await new api.MaterialPullApi(serverUrl).deviceLogin(
          deviceNo,
          this.data.deviceModel,
          employeeNo,
          'save_settings'
        )
        this.setData({
          operation: {
            title: '内网模式已就绪',
            detail: `${serverUrl}${submitPath}`,
            isError: false
          }
        })
      }
      this.toast('设置已保存', 'success')
    } catch (e) {
      this.setData({
        operation: {
          title: '保存失败',
          detail: e && e.message ? e.message : '设备登记失败',
          isError: true
        }
      })
      this.toast('保存失败')
    } finally {
      this.setData({ isSending: false })
    }
  },

  async onHealthCheck() {
    if (this.data.isSending) return
    const isExternal = this.data.networkMode === api.NETWORK_EXTERNAL
    const normalizedUrl = isExternal
      ? api.normalizeExternalBaseUrl(this.data.externalUrl)
      : api.normalizeInternalBaseUrl(this.data.serverUrl)

    this.setData({ isSending: true })
    try {
      let operation
      if (isExternal) {
        const message = await new api.ExternalScanRelayApi(normalizedUrl).health()
        operation = {
          title: '外网连接正常',
          detail: `${message} · ${normalizedUrl}`,
          isError: false
        }
      } else {
        const materialApi = new api.MaterialPullApi(normalizedUrl)
        const message = await materialApi.healthReady()
        try {
          await materialApi.deviceLogin(
            this.data.deviceNo,
            this.data.deviceModel,
            this.data.employeeNo,
            'test_connection'
          )
        } catch (e) {
          // Android 原逻辑在测试内网连接时忽略 deviceLogin 的失败，只要 healthReady 成功即可。
        }
        operation = {
          title: '内网连接正常',
          detail: message,
          isError: false
        }
      }

      if (isExternal) {
        this.setData({ externalUrl: normalizedUrl })
      } else {
        this.setData({ serverUrl: normalizedUrl })
      }
      this.setData({ operation })
      this.toast(operation.title, 'success')
    } catch (e) {
      const operation = {
        title: '连接失败',
        detail: e && e.message ? e.message : '服务器无响应',
        isError: true
      }
      this.setData({ operation })
      this.toast(operation.title)
    } finally {
      this.setData({ isSending: false })
    }
  }
})
