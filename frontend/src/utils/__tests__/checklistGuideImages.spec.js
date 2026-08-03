import { describe, expect, it } from 'vitest'
import { guideContentFor, guideImageFor } from '../checklistGuideImages'

describe('guideContentFor', () => {
  // 실제 체크리스트 생성 응답(Galaxy Book4)의 items 중 등록해 둔 항목들을 확인합니다.
  it.each([
    ['EXT-001', '외관'],
    ['DSP-002', '터치'],
    ['SYS-003', '모델명'],
    ['BAT-005', '완전 충전 용량'],
    ['DXD-006', 'GPU'],
  ])('Windows 노트북의 %s 항목이 등록돼 있다', (itemCode, purposeKeyword) => {
    const content = guideContentFor({ itemCode, isRequired: true }, 'Windows 노트북')
    expect(content).not.toBeNull()
    expect(content.purpose).toContain(purposeKeyword)
    expect(content.image).toBeTruthy()
  })

  it('필수 항목이어도 등록되지 않은 조합이면 null을 반환한다', () => {
    // itemCode 자체가 등록돼 있지 않음
    expect(guideContentFor({ itemCode: 'BAT-001', isRequired: true }, 'Windows 노트북')).toBeNull()
    // itemCode는 Windows 노트북에 등록돼 있지만 다른 카테고리는 아직 비어 있음
    expect(guideContentFor({ itemCode: 'EXT-001', isRequired: true }, '일반형 스마트폰')).toBeNull()
    // 존재하지 않는 카테고리
    expect(guideContentFor({ itemCode: 'EXT-001', isRequired: true }, '알 수 없는 카테고리')).toBeNull()
  })

  // 비필수 항목은 등록돼 있어도 무시하고 항상 서버 기본값을 쓰도록 되어 있습니다.
  it('비필수 항목은 등록돼 있어도 null을 반환한다', () => {
    expect(guideContentFor({ itemCode: 'EXT-001', isRequired: false }, 'Windows 노트북')).toBeNull()
  })

  // Lombok boolean getter + Jackson 직렬화로 실제 응답 필드가 isRequired가 아니라
  // required로 내려오는 경우가 있습니다(getProductChecklist 실응답에서 확인됨).
  it('필드명이 isRequired 대신 required로 내려와도 필수로 인식한다', () => {
    const content = guideContentFor({ itemCode: 'EXT-001', required: true }, 'Windows 노트북')
    expect(content).not.toBeNull()
  })

  it('required가 false면 isRequired가 true여도 비필수로 취급하지 않는다 - required를 우선한다', () => {
    expect(guideContentFor({ itemCode: 'EXT-001', required: false, isRequired: true }, 'Windows 노트북')).toBeNull()
  })
})

describe('guideImageFor', () => {
  it('매칭되는 안내가 없어도 evidenceType에 맞는 범용 이미지를 반환한다', () => {
    expect(guideImageFor({ itemCode: 'BAT-001', isRequired: true, evidenceType: 'DIAGNOSTIC_FILE' }, 'Windows 노트북')).toBeTruthy()
  })

  it('evidenceType도 알 수 없으면 null을 반환한다', () => {
    expect(guideImageFor({ itemCode: 'X-1', isRequired: true, evidenceType: 'SELLER_CONFIRMATION' }, 'Windows 노트북')).toBeNull()
  })
})
