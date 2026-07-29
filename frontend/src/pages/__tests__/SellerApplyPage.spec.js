import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { registerSeller } from '../../api/seller'
import { restoreAuthSession } from '../../auth/session'
import SellerApplyPage from '../SellerApplyPage.vue'

const replace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace }),
}))
vi.mock('../../api/seller', () => ({
  registerSeller: vi.fn(),
}))
vi.mock('../../auth/session', () => ({
  restoreAuthSession: vi.fn(),
}))

describe('SellerApplyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    registerSeller.mockResolvedValue({ status: 'ACTIVE' })
    restoreAuthSession.mockResolvedValue(true)
  })

  it('판매자 등록 후 세션을 갱신하고 상품 관리로 이동한다', async () => {
    const wrapper = mount(SellerApplyPage, {
      global: {
        stubs: {
          MyPageLayout: { template: '<main><slot /></main>' },
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('국민은행')
    await inputs[1].setValue('판매자')
    await inputs[2].setValue('1234')
    await inputs[3].setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(registerSeller).toHaveBeenCalledWith({
      sellerType: 'INDIVIDUAL',
      countryCode: 'KR',
      businessName: null,
      settlementBankName: '국민은행',
      settlementAccountHolder: '판매자',
      settlementAccountLast4: '1234',
      sellerTermsAccepted: true,
    })
    expect(restoreAuthSession).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith({ name: 'seller-products' })
  })
})
