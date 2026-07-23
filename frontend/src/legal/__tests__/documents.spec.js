import { describe, expect, it } from 'vitest'
import { privacySections, serviceTermsSections } from '../documents'

describe('legal documents', () => {
  it('중개 서비스의 핵심 권리와 금지행위를 고지한다', () => {
    const text = JSON.stringify(serviceTermsSections)

    expect(text).toContain('통신판매중개자')
    expect(text).toContain('금지행위')
    expect(text).toContain('분쟁')
  })

  it('개인정보 필수 공개 항목과 법정 거래기록 보존 기간을 포함한다', () => {
    const text = JSON.stringify(privacySections)

    expect(text).toContain('처리 목적')
    expect(text).toContain('제3자 제공')
    expect(text).toContain('계약 또는 청약철회')
    expect(text).toContain('5년')
    expect(text).toContain('이용자의 권리')
  })
})
