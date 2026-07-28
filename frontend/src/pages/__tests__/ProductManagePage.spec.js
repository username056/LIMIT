import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductManagePage from '../ProductManagePage.vue'
import {
  createProduct,
  getDeviceCategories,
  getDeviceModels,
  getMyProducts,
} from '../../api/products'

vi.mock('../../api/products', () => ({
  createProduct: vi.fn(),
  deleteProduct: vi.fn(),
  getDeviceCategories: vi.fn(),
  getDeviceModels: vi.fn(),
  getMyProduct: vi.fn(),
  getMyProducts: vi.fn(),
  transitionProductStatus: vi.fn(),
  updateProduct: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled', 'type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}
const tableStub = { template: '<table><tbody><slot /></tbody></table>' }

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('ProductManagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDeviceCategories.mockResolvedValue([{ categoryId: 10, name: '노트북' }])
    getDeviceModels.mockResolvedValue([{
      deviceModelId: 101,
      manufacturerName: 'Samsung',
      modelName: 'Galaxy Book',
    }])
    getMyProducts.mockResolvedValue({
      data: [],
      meta: { page: 0, totalPages: 0, hasNext: false },
    })
    createProduct.mockResolvedValue({ productId: 1001 })
  })

  it('기기 선택과 판매 정보 확인 후 상품 초안을 등록한다', async () => {
    const wrapper = mount(ProductManagePage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          BaseBadge: true,
          BaseTable: tableStub,
        },
      },
    })
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('10')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('101')
    await buttonByText(wrapper, '다음 단계').trigger('click')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('갤럭시 북 테스트 상품')
    await inputs[1].setValue('850000')
    await inputs[2].setValue('그라파이트')
    await inputs[3].setValue('512')
    await inputs[4].setValue('광주광역시 광산구')
    await wrapper.find('textarea').setValue('상태가 좋은 테스트 상품입니다.')
    await buttonByText(wrapper, '다음 단계').trigger('click')

    expect(wrapper.text()).toContain('입력한 내용을 확인해 주세요.')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith({
      categoryId: 10,
      deviceModelId: 101,
      name: '갤럭시 북 테스트 상품',
      description: '상태가 좋은 테스트 상품입니다.',
      price: 850000,
      color: '그라파이트',
      storageGb: 512,
      tradeRegion: '광주광역시 광산구',
    })
  })

  it('0원 상품은 최종 확인 단계로 진행하지 않는다', async () => {
    const wrapper = mount(ProductManagePage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          BaseBadge: true,
          BaseTable: tableStub,
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('select')[0].setValue('10')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('101')
    await buttonByText(wrapper, '다음 단계').trigger('click')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('가격 오류 상품')
    await inputs[1].setValue('0')
    await inputs[4].setValue('광주광역시 광산구')
    await buttonByText(wrapper, '다음 단계').trigger('click')

    expect(wrapper.text()).toContain('가격은 1원 이상 입력해 주세요.')
    expect(wrapper.text()).not.toContain('입력한 내용을 확인해 주세요.')
  })
})
