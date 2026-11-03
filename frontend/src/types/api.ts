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
  version: number
  editedByHuman: boolean
  savedForLater: boolean
  rejectionReason: string | null
  parentPostId: string | null
  publishing: {
    queueStatus: string | null
    scheduledAt: string | null
    attempts: number
    lastError: string | null
    xPostId: string | null
    xUrl: string | null
    publishedAt: string | null
    approvedBy: string | null
    approvedAt: string | null
  } | null
}

export interface HistoryEntry {
  at: string
  action: string
  actor: string
  details: string | null
  oldValue: string | null
  newValue: string | null
}

export interface XAccountStatus {
  appConfigured: boolean
  encryptionKeyConfigured: boolean
  connected: boolean
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'NOT_CONNECTED'
  username: string | null
  connectedSince: string | null
  missing: string | null
}

export interface NotificationItem {
  id: string
  type: string
  title: string
  message: string | null
  entityType: string | null
  entityId: string | null
  read: boolean
  createdAt: string
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

export type SourceType = 'TAMIL_NEWS' | 'NATIONAL_NEWS' | 'GOVERNMENT' | 'PRESS_RELEASE' | 'ELECTION_COMMISSION' | 'PARTY_OFFICIAL' | 'LEGISLATIVE'
export type AnalysisStatus = 'NEW' | 'ANALYSED' | 'FAILED' | 'IRRELEVANT'

export interface NewsSource {
  id: string
  name: string
  baseUrl: string
  feedUrl: string | null
  sourceType: SourceType
  language: LanguageCode
  primarySource: boolean
  reliabilityTier: number
  reliabilityNotes: string | null
  enabled: boolean
  fetchIntervalMinutes: number
  requiresKeywordMatch: boolean
  lastFetchedAt: string | null
  lastFetchStatus: string | null
  lastFetchMessage: string | null
  consecutiveFailures: number
}

export type NewsSourceInput = Omit<NewsSource, 'id' | 'lastFetchedAt' | 'lastFetchStatus' | 'lastFetchMessage' | 'consecutiveFailures'>

export interface Claim {
  id: string
  text: string
  type: 'FACT' | 'STATISTIC' | 'ATTRIBUTED_STATEMENT' | 'OPINION'
  attributedTo: string | null
  verificationStatus: VerificationStatus
  verificationMethod: string | null
  independentSourceCount: number
  supportingUrls: string[]
  conflictingUrls: string[]
  verificationNotes: string | null
  verifiedAt: string | null
}

export interface Article {
  id: string
  title: string
  url: string
  sourceId: string
  sourceName: string
  sourceType: SourceType | null
  primarySource: boolean
  reliabilityTier: number
  publishedAt: string | null
  retrievedAt: string
  summary: string | null
  category: string | null
  language: LanguageCode | null
  analysisStatus: AnalysisStatus
  relevant: boolean
  matchedKeywords: string[]
  analysisError: string | null
  analysedAt: string | null
  verifiedAt: string | null
  claims: Claim[]
  usedInDraft: boolean
  focusMatch: boolean
}

export interface FetchRun {
  sourcesChecked: number
  newArticles: number
  relevantArticles: number
  sources: { source: string; status: string; message: string | null; newArticles: number }[]
}

export interface ContentFocus {
  voice: string
  themeMorning: string
  themeAfternoon: string
  themeEvening: string
  hashtags: string[]
  maxHashtags: number
  focusKeywords: string[]
  focusOnly: boolean
}
