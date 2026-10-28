export type Role = 'ADMIN' | 'ACCOUNTANT' | 'VIEWER'

export type InvoiceStatus = 'PROCESSING' | 'NEEDS_REVIEW' | 'VALIDATED' | 'APPROVED' | 'DUPLICATE' | 'FAILED'

export interface UserInfo {
  id: number
  fullName: string
  email: string
  role: Role
  businessId: number
  businessName: string
  businessGstin: string | null
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  user: UserInfo
}

export interface ValidationIssue {
  code: string
  severity: 'ERROR' | 'WARNING'
  field: string
  message: string
}

export interface InvoiceSummary {
  id: number
  fileName: string
  status: InvoiceStatus
  supplierName: string | null
  supplierGstin: string | null
  invoiceNumber: string | null
  invoiceDate: string | null
  taxableValue: number | null
  totalTax: number | null
  totalAmount: number | null
  category: string | null
  errorCount: number
  warningCount: number
  createdAt: string
}

export interface LineItem {
  description: string | null
  hsnSac: string | null
  quantity: number | null
  unitPrice: number | null
  taxableValue: number | null
  gstRate: number | null
  cgst: number | null
  sgst: number | null
  igst: number | null
}

export interface InvoiceDetail {
  id: number
  fileName: string
  contentType: string
  status: InvoiceStatus
  supplierName: string | null
  supplierGstin: string | null
  buyerName: string | null
  buyerGstin: string | null
  invoiceNumber: string | null
  invoiceDate: string | null
  placeOfSupply: string | null
  supplyType: 'INTRA' | 'INTER' | null
  taxableValue: number | null
  cgst: number | null
  sgst: number | null
  igst: number | null
  cess: number | null
  totalAmount: number | null
  category: string | null
  itcEligible: boolean
  aiConfidence: number | null
  aiNotes: string | null
  duplicateOfId: number | null
  validationIssues: ValidationIssue[]
  errorMessage: string | null
  lineItems: LineItem[]
  uploadedBy: string
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface Totals {
  taxableValue: number
  cgst: number
  sgst: number
  igst: number
  cess: number
  totalTax: number
  totalAmount: number
}

export interface CategoryRow {
  category: string
  label: string
  itcEligible: boolean
  invoiceCount: number
  taxableValue: number
  totalTax: number
}

export interface GstSummary {
  businessName: string
  businessGstin: string | null
  period: string
  approvedOnly: boolean
  invoiceCount: number
  pendingReviewCount: number
  totals: Totals
  eligibleItc: number
  blockedItc: number
  byCategory: CategoryRow[]
  byRate: { gstRate: number; taxableValue: number; cgst: number; sgst: number; igst: number }[]
  byHsn: {
    hsnSac: string
    description: string | null
    quantity: number
    taxableValue: number
    gstRate: number
    cgst: number
    sgst: number
    igst: number
  }[]
  bySupplier: {
    supplierGstin: string
    supplierName: string | null
    invoiceCount: number
    taxableValue: number
    totalTax: number
    totalAmount: number
  }[]
  invoices: {
    id: number
    invoiceDate: string | null
    invoiceNumber: string | null
    supplierName: string | null
    supplierGstin: string | null
    supplyType: string | null
    category: string | null
    status: string
    taxableValue: number
    cgst: number
    sgst: number
    igst: number
    cess: number
    totalAmount: number
  }[]
}

export interface Dashboard {
  currentMonth: Totals
  eligibleItc: number
  blockedItc: number
  statusCounts: Record<InvoiceStatus, number>
  trend: { month: string; taxableValue: number; totalTax: number; invoiceCount: number }[]
  topCategories: CategoryRow[]
}

export interface TeamUser {
  id: number
  fullName: string
  email: string
  role: Role
  active: boolean
  createdAt: string
}

export const CATEGORIES: Record<string, string> = {
  RAW_MATERIALS: 'Raw materials',
  INVENTORY: 'Stock for resale',
  OFFICE_SUPPLIES: 'Office supplies & stationery',
  IT_AND_SOFTWARE: 'IT services & software',
  EQUIPMENT_AND_ASSETS: 'Equipment, furniture & electronics',
  FUEL_AND_TRAVEL: 'Fuel & travel',
  LOGISTICS: 'Freight & logistics',
  RENT_AND_UTILITIES: 'Rent & utilities',
  TELECOM_AND_INTERNET: 'Telecom & internet',
  PROFESSIONAL_SERVICES: 'Professional services',
  MARKETING: 'Marketing & advertising',
  REPAIRS_AND_MAINTENANCE: 'Repairs & maintenance',
  FOOD_AND_BEVERAGES: 'Food & beverages',
  MOTOR_VEHICLES: 'Motor vehicles',
  OTHER: 'Other',
}
