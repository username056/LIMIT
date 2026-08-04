import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DeviceCheckPage from '../DeviceCheckPage.vue'
import { getProductChecklist, getProductDraftProgress, updateProductDraftProgress } from '../../api/products'

const { routeQuery, routerPushMock } = vi.hoisted(() => ({
  routeQuery: {},
  routerPushMock: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1' }, query: routeQuery }),
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
  OS_RESERVED_CODES: ['MetaLeft', 'AltLeft', 'AltRight'],
  AMBIGUOUS_CODES: ['ShiftRight'],
  useKeyboardCheck: () => {
    const status = ref('idle')
    return {
      start: () => {
        status.value = 'idle'
      },
      finish: () => {
        const missingCodes = nextMissingCodes.value
        status.value = missingCodes.length === 0 ? 'passed' : 'passedWithMissing'
        return { missingCodes, missingUnreliableCodes: [], pressedCodes: [], excludedCodes: [] }
      },
      stop: () => {},
      status,
      rows: [],
      pressed: new Set(),
      pressedCount: { value: 0 },
      total: { value: 10 },
      excludedCodes: new Set(),
      toggleExcluded: () => {},
    }
  },
}))

const checklistItem = {
  checklistItemId: 5,
  itemCode: 'LAP-KBD-005',
  evidenceType: 'SELLER_CONFIRMATION',
}

const ALL_TEST_TYPES = ['SPEAKER', 'DISPLAY', 'CHARGING', 'CAMERA', 'MICROPHONE', 'KEYBOARD', 'TOUCHPAD']
const ITEM_CODE_TEST_TYPE = {
  'LAP-FTR-SPK': 'SPEAKER',
  'LAP-DSP-003': 'DISPLAY',
  'LAP-CHG-007': 'CHARGING',
  'LAP-FTR-CAM': 'CAMERA',
  'LAP-FTR-MIC': 'MICROPHONE',
  'LAP-KBD-005': 'KEYBOARD',
  'LAP-PAD-006': 'TOUCHPAD',
}

function withOtherChecksCompleted(progress, activeTypes) {
  return {
    ...progress,
    deviceResults: {
      ...Object.fromEntries(ALL_TEST_TYPES
        .filter((testType) => !activeTypes.includes(testType))
        .map((testType) => [testType, 'SUCCESS'])),
      ...(progress.deviceResults || {}),
    },
  }
}

async function mountAndLoad(progress) {
  getProductChecklist.mockResolvedValue([checklistItem])
  getProductDraftProgress.mockResolvedValue(withOtherChecksCompleted(progress, ['KEYBOARD']))
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

async function mountWithChecklist(checklist, progress = { step: 2, results: {} }) {
  getProductChecklist.mockResolvedValue(checklist)
  const activeTypes = checklist.map((item) => ITEM_CODE_TEST_TYPE[item.itemCode]).filter(Boolean)
  getProductDraftProgress.mockResolvedValue(withOtherChecksCompleted(progress, activeTypes))
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

  const recheckButton = wrapper.findAll('button').find((button) => button.text() === '다시 점검')
  if (recheckButton) {
    await recheckButton.trigger('click')
    await flushPromises()
  }
  await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
  await flushPromises()
  await wrapper.findAll('button').find((button) => button.text() === '완료').trigger('click')
  await flushPromises()
  await wrapper.findAll('button').find((b) => b.text() === '다음').trigger('click')
  await flushPromises()
  for (let index = 0; index < 6; index += 1) {
    await wrapper.findAll('button').find((b) => b.text() === '다음').trigger('click')
    await flushPromises()
  }
  await wrapper.findAll('button').find((button) => button.text() === '저장하고 돌아가기').trigger('click')
  await flushPromises()
}

describe('DeviceCheckPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('체크리스트 점검 항목이 없어도 공통 7개 검사를 표시한다', async () => {
    const wrapper = await mountWithChecklist([])

    expect(wrapper.text()).toContain('1 / 7')
    expect(wrapper.text()).toContain('스피커')
    expect(updateProductDraftProgress).not.toHaveBeenCalled()
  })

  it('디스플레이와 충전을 웹에서 직접 점검해 결과를 저장한다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 21, itemCode: 'LAP-DSP-003', evidenceType: 'VIDEO', status: 'PENDING' },
      { checklistItemId: 22, itemCode: 'LAP-CHG-007', evidenceType: 'VIDEO', status: 'PENDING' },
    ])

    await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '정상이에요').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '다음').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '정상이에요').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '다음').trigger('click')
    for (let index = 0; index < 5; index += 1) {
      await wrapper.findAll('button').find((button) => button.text() === '다음').trigger('click')
    }
    await wrapper.findAll('button').find((button) => button.text() === '저장하고 돌아가기').trigger('click')
    await flushPromises()

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', expect.objectContaining({
      results: expect.arrayContaining([
        { checklistItemId: 21, result: 'SUCCESS' },
        { checklistItemId: 22, result: 'SUCCESS' },
      ]),
      deviceResults: expect.arrayContaining([
        { testType: 'DISPLAY', result: 'SUCCESS' },
        { testType: 'CHARGING', result: 'SUCCESS' },
      ]),
    }))
  })

  it('자동 완료된 항목은 웹 점검 없이 다음으로 넘어갈 수 있다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 31, itemCode: 'LAP-DSP-003', evidenceType: 'VIDEO', status: 'COMPLETED' },
    ], { step: 2, results: { 31: 'SUCCESS' } })

    expect(wrapper.text()).toContain('자동 입력 완료')
    expect(wrapper.text()).toContain('웹 점검을 생략할 수 있습니다.')
  })

  it('포인터 점검은 시작 전부터 시간 제한 안내를 보여준다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 41, itemCode: 'LAP-PAD-006', evidenceType: 'SELLER_CONFIRMATION', status: 'PENDING' },
    ])

    expect(wrapper.text()).toContain('10초 안에')
  })

  it('포인터 점검 시작을 누르면 입력 감지 상태와 완료 버튼을 표시한다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 41, itemCode: 'LAP-PAD-006', evidenceType: 'SELLER_CONFIRMATION', status: 'PENDING' },
    ])

    await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
    await flushPromises()

    const completeButton = wrapper.findAll('button').find((button) => button.text() === '완료')
    expect(completeButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('누르기 대기 · 이동/스크롤 대기')

    const pointerArea = wrapper.find('.border-dashed').element
    const pointerEvent = (type) => {
      const event = new Event(type)
      Object.defineProperty(event, 'pointerType', { value: 'mouse' })
      return event
    }
    pointerArea.dispatchEvent(pointerEvent('pointerdown'))
    for (let index = 0; index < 4; index += 1) {
      pointerArea.dispatchEvent(pointerEvent('pointermove'))
    }
    await flushPromises()

    expect(wrapper.text()).toContain('누르기 감지 · 이동/스크롤 감지')
    expect(wrapper.text()).toContain('클릭·드래그·스크롤')
  })

  it('입력 없이 포인터 점검을 완료하면 실패로 표시되고 다시 시도할 수 있다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 41, itemCode: 'LAP-PAD-006', evidenceType: 'SELLER_CONFIRMATION', status: 'PENDING' },
    ])

    await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '완료').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('button').find((button) => button.text() === '다시 시도')).toBeDefined()
    expect(wrapper.findAll('button').some((button) => button.text() === '완료')).toBe(false)
  })

  it('일정 시간 안에 입력이 없으면 자동으로 실패 처리되고 시간 제한 안내를 보여준다', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = await mountWithChecklist([
        { checklistItemId: 41, itemCode: 'LAP-PAD-006', evidenceType: 'SELLER_CONFIRMATION', status: 'PENDING' },
      ])

      await wrapper.findAll('button').find((button) => button.text() === '점검 시작').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('10초 안에')

      await vi.advanceTimersByTimeAsync(10000)
      await flushPromises()

      expect(wrapper.findAll('button').find((button) => button.text() === '다시 시도')).toBeDefined()
      expect(wrapper.findAll('button').some((button) => button.text() === '완료')).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('이전 웹 성공 결과는 자동 입력이 아니라 웹 점검 완료로 안내한다', async () => {
    const wrapper = await mountWithChecklist([], {
      step: 2,
      results: {},
      deviceResults: { SPEAKER: 'SUCCESS' },
    })

    expect(wrapper.text()).toContain('웹 점검 완료')
    expect(wrapper.text()).toContain('이전에 웹에서 정상 점검한 결과')
    expect(wrapper.text()).not.toContain('Limit 진단 프로그램에서 정상 결과')
  })

  it('카메라 자동 판정 시간과 동작 방법을 안내한다', async () => {
    const wrapper = await mountWithChecklist([
      { checklistItemId: 42, itemCode: 'LAP-FTR-CAM', evidenceType: 'SELLER_CONFIRMATION', status: 'PENDING' },
    ])

    expect(wrapper.text()).toContain('약 3초 동안 손을 흔들거나 기기를 조금 움직여 주세요.')
    expect(wrapper.text()).toContain('화면 변화를 감지하면 자동으로 점검이 완료됩니다.')
  })

  it('키보드 점검은 시작 전부터 좌우를 밝힌 감지 불확실 키 안내를 보여준다', async () => {
    const wrapper = await mountAndLoad({ step: 2, results: {} })

    expect(wrapper.text()).toContain('Win, 왼쪽 Alt, 오른쪽 Alt, 오른쪽 Shift')
  })

  it('키보드에 응답 없는 키가 있으면 FAILED로 저장한다', async () => {
    const wrapper = await mountAndLoad({ step: 2, results: {} })

    await runKeyboardStepAndSave(wrapper, ['KeyA'])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', expect.objectContaining({
      results: expect.arrayContaining([{ checklistItemId: 5, result: 'FAILED' }]),
      deviceResults: expect.arrayContaining([{ testType: 'KEYBOARD', result: 'FAILED' }]),
    }))
  })

  it('이전에 SUCCESS였던 항목이 이번 점검에서 실패하면 FAILED로 덮어써진다', async () => {
    const wrapper = await mountAndLoad({ step: 2, results: { 5: 'SUCCESS' } })

    await runKeyboardStepAndSave(wrapper, ['KeyB'])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', expect.objectContaining({
      deviceResults: expect.arrayContaining([{ testType: 'KEYBOARD', result: 'FAILED' }]),
    }))
  })

  it('모든 키를 다 누르면 SUCCESS로 저장한다', async () => {
    const wrapper = await mountAndLoad({ step: 2, results: {} })

    await runKeyboardStepAndSave(wrapper, [])

    expect(updateProductDraftProgress).toHaveBeenCalledWith('1', expect.objectContaining({
      deviceResults: expect.arrayContaining([{ testType: 'KEYBOARD', result: 'SUCCESS' }]),
    }))
  })

  it('저장하고 돌아가면 수정 화면이 아니라 상품 등록 2단계로 되돌린다', async () => {
    const wrapper = await mountAndLoad({ step: 2, results: {} })

    await runKeyboardStepAndSave(wrapper, [])

    // step을 안 실어 보내면 등록 화면이 startEdit에서 1단계를 열어, 하던 자리를 잃습니다.
    expect(routerPushMock).toHaveBeenCalledWith({
      name: 'seller-product-new',
      query: { step: '2', resumeProductId: '1' },
    })
  })
})
