import { defineConfig } from 'wxt'

export default defineConfig({
  manifest: {
    name: 'ApplyMate',
    description: '智能求职信息填写助手',
    icons: {
      '16': 'icons/applymate-mark-16.png',
      '32': 'icons/applymate-mark-32.png',
      '48': 'icons/applymate-mark-48.png',
      '128': 'icons/applymate-mark-128.png',
    },
    permissions: ['activeTab', 'storage'],
    host_permissions: ['http://127.0.0.1/*'],
  },
})
