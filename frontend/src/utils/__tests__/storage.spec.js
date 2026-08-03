import { describe, expect, it } from 'vitest'
import { formatStorage } from '../storage'

describe('formatStorage', () => {
  it('1024로 나누어떨어지면 TB로 적는다', () => {
    // 등록 화면에서 1TB를 골랐는데 상세 화면이 1024GB로 적던 문제입니다.
    expect(formatStorage(1024)).toBe('1TB')
    expect(formatStorage(2048)).toBe('2TB')
  })

  it('그 밖에는 GB 그대로 적는다', () => {
    expect(formatStorage(256)).toBe('256GB')
    expect(formatStorage(512)).toBe('512GB')
  })

  it('나누어떨어지지 않는 큰 값을 소수점 TB로 바꾸지 않는다', () => {
    // 용량은 직접 입력도 되어서, 3000을 2.9296875TB로 적으면 읽을 수 없습니다.
    expect(formatStorage(3000)).toBe('3000GB')
    expect(formatStorage(1536)).toBe('1536GB')
  })

  it('값이 없으면 빈 문자열을 준다', () => {
    expect(formatStorage(null)).toBe('')
    expect(formatStorage(undefined)).toBe('')
    expect(formatStorage('')).toBe('')
    expect(formatStorage(0)).toBe('')
  })
})
