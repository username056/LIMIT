import { apiClient } from './client'

export function extractOcrText(evidenceId) {
  return apiClient.post(`/inspections/evidence/${evidenceId}/ocr-results`, {})
}

export function parseDxdiag(evidenceId) {
  return apiClient.post(`/inspections/evidence/${evidenceId}/dxdiag-results`, {})
}

export function parseBatteryReport(evidenceId) {
  return apiClient.post(`/inspections/evidence/${evidenceId}/battery-report-results`, {})
}

export function getDiagnosis(checklistItemId) {
  return apiClient.get(`/inspections/listing-checklist-items/${checklistItemId}/diagnosis`)
}

export function confirmDiagnosisValue(checklistItemId, payload) {
  return apiClient.patch(`/inspections/listing-checklist-items/${checklistItemId}/diagnosis-values`, payload)
}

export function getProductDiagnosisSummary(productId) {
  return apiClient.get(`/inspections/products/${productId}/diagnosis-summary`)
}
