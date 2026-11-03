export type PostStatus =
  | 'GENERATED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'SCHEDULED'
  | 'PUBLISHING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'

export interface CurrentUser {
  id: string
  username: string
  displayName: string | null
  role: 'ADMIN' | 'REVIEWER' | 'VIEWER'
}

export interface UpcomingPost {
  id: string
  preview: string
  language: 'ta' | 'en' | 'mixed'
  category: string
  publishAt: string
}

export interface Overview {
  totalDrafts: number
  pendingApprovals: number
  approved: number
  scheduled: number
  published: number
  rejected: number
  failed: number
  byStatus: Record<PostStatus, number>
  upcoming: UpcomingPost[]
}

export interface ScheduleSettings {
  timezone: string
  morning: string
  afternoon: string
  evening: string
}

export interface ContentSettings {
  enabledLanguages: string[]
  languageMix: Record<string, number>
  variationsPerTopic: number
  duplicateLookbackDays: number
}

export interface SystemStatus {
  version: string
  timezone: string
  openAiConfigured: boolean
  openAiModel: string | null
  xApiConfigured: boolean
  xAccountConnected: boolean
  publishingEnabled: boolean
  phase: string
}

export interface AuditEntry {
  id: string
  actorType: string
  actorId: string | null
  action: string
  entityType: string
  entityId: string | null
  oldValue: string | null
  newValue: string | null
  details: string | null
  createdAt: string
}

export interface ApiErrorBody {
  status: number
  message: string
  fieldErrors?: { field: string; message: string }[]
}

export type Slot = 'MORNING' | 'AFTERNOON' | 'EVENING'
export type LanguageCode = 'ta' | 'en' | 'mixed'
export type VerificationStatus = 'VERIFIED' | 'PARTIALLY_VERIFIED' | 'UNVERIFIED' | 'CONFLICTING_INFORMATION'

export interface Draft {
  id: string
  content: string
  language: LanguageCode
  category: string
  slot: Slot | null
  status: PostStatus
  verificationStatus: VerificationStatus
  qualityEstimate: number | null
  proposedPublishAt: string | null
  createdAt: string
  topic: string | null
  sourceNotes: string | null
  sourceUrls: string[]
  verifiedClaims: string[]
  unverifiedClaims: string[]
  riskFlags: string[]
  duplicateWarning: string | null
  weightedLength: number
  maxLength: number
  aiModel: string | null
  promptVersion: string | null
  variationGroupId: string | null
}

export interface GenerateRequest {
  slot: Slot
  language?: LanguageCode
  topic?: string
  sourceNotes?: string
  sourceUrls?: string[]
}

export interface GenerationResult {
  created: Draft[]
  discarded: { slot: string; reason: string; preview: string | null }[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
