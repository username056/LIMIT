import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DeviceCheckPage from '../DeviceCheckPage.vue'
import { getProductChecklist, getProductDraftProgress, updateProductDraftProgress } from '../../api/products'

const routerPushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1' } }),
  useRouter: () => ({ push: routerPushMock }),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('../../api/products', () => ({
  getProductChecklist: vi.fn(),
  getProductDraftProgress: vi.fn(),
  updateProductDraftProgress: vi.fn(),
}))

// 카메라·마이크·스피커·포인터는 이 페이지의 저장 로직과 무관하므로 실제 composable을 그대로 쓰고,
// 결과를 직접 통제해야 하는 키보드만 가짜로 바꾼다. missingCodes는 테스트마다 바꿔야 해서
// vi.hoisted로 만든 상자를 통해 바깥에서 주입한다.
const { nextMissingCodes } = vi.hoisted(() => ({ nextMissingCodes: { value: [] } }))

vi.mock('../../features/deviceCheck/useKeyboardCheck', () => ({
  useKeyboardCheck: () => {
    const status = ref('idle')
    return {
      start: () => {
        status.value = 'idle'
      },
      finish: () => {
        const missingCodes = nextMissingCodes.value
        status.value = missingCodes.length === 0 ? 'passed' : 'passedWithMissing'
        return { missingCodes, pressedCodes: [] }
      },
      stop: () => {},
      status,
      rows: [],
      pressed: new Set(),
      pressedCount: { value: 0 },
      total: 10,
    }
  },
}))

const checklistItem = {
  checklistItemId: 5,
  itemCode: 'LAP-KBD-005',
  evidenceType: 'SELLER_CONFIRMATION',
}

async function mountAndLoad(progress) {
  getProductChecklist.mockResolvedValue([checklistItem])
  getProductDraftProgress.mockResolvedValue(progress)
  updateProductDraftProgress.mockResolvedValue({})

  const wrapper = mount(DeviceCheckPage, {
    global: {
      stubs: {
        DefaultLayout: { template: '<div><slot /></div>' },
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

async function runKeyboardStepAndSave(wrapper, missingCodes) {
  nextMissingCodes.value = missingCodes

  await wrapper.find('button').trigger('click') // 점검 시작
  await flushPromises()
  await wrapper.find('button').trigger('click') // 완료 (finishKeyboard)
  await flushPromises()
  await wrapper.findAll('button').find((b) => b.text() === '다음').trigger('click')
  await flushPromises()
  await wrapper.find('button').trigger('click') // 저장하고 돌아가기
  await flushPromises()
}

describe('DeviceCheckPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('키보드에 응답 없는 키가 있으면 FAILED로 저장한다', async () => {
    const wrapper = await mountAndLoad({ step: 3, results: {} })

    await runKeyboardStepAndSave(wrapper, ['KeyA'])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', {
      step: 3,
      results: [{ checklistItemId: 5, result: 'FAILED' }],
    })
  })

  it('이전에 SUCCESS였던 항목이 이번 점검에서 실패하면 FAILED로 덮어써진다', async () => {
    const wrapper = await mountAndLoad({ step: 3, results: { 5: 'SUCCESS' } })

    await runKeyboardStepAndSave(wrapper, ['KeyB'])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', {
      step: 3,
      results: [{ checklistItemId: 5, result: 'FAILED' }],
    })
  })

  it('모든 키를 다 누르면 SUCCESS로 저장한다', async () => {
    const wrapper = await mountAndLoad({ step: 3, results: {} })

    await runKeyboardStepAndSave(wrapper, [])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', {
      step: 3,
      results: [{ checklistItemId: 5, result: 'SUCCESS' }],
    })
  })
})
