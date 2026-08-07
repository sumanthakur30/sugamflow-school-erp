import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../../core/api.service';

export interface ComplianceCampaignSummary {
  id: number;
  title: string;
  boardCode: string;
  status: string;
  dueAt?: string;
  complianceScore?: number;
  blockerCount: number;
  warnCount: number;
}

export interface ComplianceDashboard {
  complianceScore: number;
  pendingCampaigns: number;
  submittedCampaigns: number;
  overdueCampaigns: number;
  profileCompletenessPercent: number;
  missingProfileFields: number;
  principalActionItems: string[];
  recentCampaigns: ComplianceCampaignSummary[];
}

export interface ComplianceProfile {
  id?: number;
  organizationId?: string;
  boardCode: string;
  activePackKey?: string;
  schoolName?: string;
  affiliationNumber?: string;
  schoolCode?: string;
  udisePlus?: string;
  diseCode?: string;
  addressLine?: string;
  city?: string;
  stateCode?: string;
  pincode?: string;
  principalName?: string;
  principalMobile?: string;
  principalEmail?: string;
  schoolPhone?: string;
  schoolEmail?: string;
  bankAccountName?: string;
  bankAccountNumber?: string;
  bankIfsc?: string;
  trustSocietyName?: string;
  recognitionDetails?: string;
  infrastructureNotes?: string;
  profileCompletenessPercent?: number;
}

export interface ReadinessGap {
  entityType: string;
  fieldKey: string;
  severity: string;
  count: number;
  label: string;
}

export interface ComplianceReadiness {
  openStudentFindings: number;
  openStaffFindings: number;
  openBlockers: number;
  openWarnings: number;
  readinessPercent: number;
  gaps: ReadinessGap[];
  latestCampaignId?: number;
  latestCampaignStatus?: string;
}

export interface ValidationFinding {
  id: number;
  campaignId?: number;
  entityType: string;
  entityId?: string;
  entityLabel?: string;
  fieldKey?: string;
  ruleCode: string;
  severity: string;
  message: string;
  suggestion?: string;
  status: string;
  source?: string;
  confidence?: number;
  createdAt?: string;
}

export interface ValidateRunResult {
  campaignId: number;
  campaignTitle: string;
  status: string;
  complianceScore: number;
  recordsChecked: number;
  blockerCount: number;
  warnCount: number;
  validatedAt: string;
}

export interface FindingsPage {
  items: ValidationFinding[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface InfrastructureAsset {
  id?: number;
  organizationId?: string;
  branchId?: string;
  category: string;
  name: string;
  quantity: number;
  capacity?: number;
  unitLabel?: string;
  conditionCode: string;
  locationNote?: string;
  notes?: string;
  active?: boolean;
  updatedAt?: string;
}

export interface InfrastructureSummary {
  totalAssets: number;
  totalQuantity: number;
  byCategory: { category: string; assets: number; quantity: number }[];
  missingRecommendedCategories: string[];
}

export interface ComplianceDocument {
  id?: number;
  organizationId?: string;
  branchId?: string;
  docType: string;
  title: string;
  referenceNo?: string;
  issuer?: string;
  issuedOn?: string;
  expiresOn?: string;
  status?: string;
  externalUrl?: string;
  fileName?: string;
  contentType?: string;
  fileSize?: number;
  hasFile?: boolean;
  versionLabel?: string;
  notes?: string;
  active?: boolean;
  updatedAt?: string;
}

export interface DocumentVaultSummary {
  totalDocuments: number;
  validCount: number;
  expiringCount: number;
  expiredCount: number;
  missingRecommendedTypes: string[];
  expiringSoon: ComplianceDocument[];
}

export interface CampaignApproval {
  id: number;
  stepCode: string;
  decision: string;
  actorUserId?: string;
  commentText?: string;
  decidedAt?: string;
  createdAt?: string;
}

export interface CampaignArtifact {
  id: number;
  artifactKey: string;
  formatCode: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  checksumSha256?: string;
  createdAt?: string;
}

export interface ComplianceCampaign {
  id: number;
  organizationId?: string;
  academicSessionId?: string;
  boardCode: string;
  packKey?: string;
  title: string;
  status: string;
  dueAt?: string;
  complianceScore?: number;
  blockerCount: number;
  warnCount: number;
  requireManagementApproval: boolean;
  lockedAt?: string;
  lockedBy?: string;
  submittedAt?: string;
  submittedBy?: string;
  archivedAt?: string;
  archivedBy?: string;
  adapterChannel?: string;
  createdAt?: string;
  updatedAt?: string;
  approvals?: CampaignApproval[];
  artifacts?: CampaignArtifact[];
}

export interface DisclosurePackage {
  organizationId: string;
  slug: string;
  title: string;
  summary: string;
  bodyHtml: string;
  snapshot: Record<string, unknown>;
  warnings: string[];
  generatedAt?: string;
  lastPublishedAt?: string;
  lastPublishStatus?: string;
  cmsPageId?: string;
  publicUrlHint?: string;
}

export interface AiScanJob {
  id: number;
  campaignId?: number;
  status: string;
  provider: string;
  warnCount: number;
  recordsScanned: number;
  resultMessage?: string;
  fallbackUsed: boolean;
  createdBy?: string;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface AdapterJob {
  id: number;
  campaignId?: number;
  boardCode: string;
  channel: string;
  status: string;
  externalRef?: string;
  requestNote?: string;
  resultMessage?: string;
  artifactCount: number;
  createdBy?: string;
  createdAt?: string;
  completedAt?: string;
}

export interface BoardDefinition {
  code: string;
  name: string;
  active: boolean;
}

export interface ComplianceTemplate {
  id: number;
  packKey: string;
  boardCode: string;
  versionLabel: string;
  title: string;
  description?: string;
  status: string;
  active: boolean;
  publishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FieldMapRow {
  id: number;
  boardCode: string;
  entityType: string;
  fieldKey: string;
  sourcePath: string;
  label: string;
  required: boolean;
  severity: string;
  formatRegex?: string;
  sortOrder: number;
  active: boolean;
}

export interface ValidationRuleRow {
  id: number;
  boardCode: string;
  ruleCode: string;
  entityType: string;
  ruleType: string;
  configJson?: Record<string, unknown>;
  severity: string;
  messageTemplate?: string;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class ComplianceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/compliance`;

  dashboard(): Observable<ComplianceDashboard> {
    return this.http
      .get<ApiResponse<ComplianceDashboard>>(`${this.base}/dashboard`)
      .pipe(map((r) => r.data));
  }

  getProfile(): Observable<ComplianceProfile> {
    return this.http
      .get<ApiResponse<ComplianceProfile>>(`${this.base}/profile`)
      .pipe(map((r) => r.data));
  }

  saveProfile(body: ComplianceProfile): Observable<ComplianceProfile> {
    return this.http
      .put<ApiResponse<ComplianceProfile>>(`${this.base}/profile`, body)
      .pipe(map((r) => r.data));
  }

  readiness(): Observable<ComplianceReadiness> {
    return this.http
      .get<ApiResponse<ComplianceReadiness>>(`${this.base}/readiness`)
      .pipe(map((r) => r.data));
  }

  findings(params?: {
    entityType?: string;
    severity?: string;
    campaignId?: number;
    page?: number;
    size?: number;
  }): Observable<FindingsPage> {
    const q: Record<string, string | number> = {};
    if (params?.entityType) q['entityType'] = params.entityType;
    if (params?.severity) q['severity'] = params.severity;
    if (params?.campaignId != null) q['campaignId'] = params.campaignId;
    if (params?.page != null) q['page'] = params.page;
    if (params?.size != null) q['size'] = params.size;
    return this.http
      .get<ApiResponse<FindingsPage>>(`${this.base}/findings`, { params: q })
      .pipe(map((r) => r.data));
  }

  runValidation(body?: {
    title?: string;
    boardCode?: string;
    academicSessionId?: string;
  }): Observable<ValidateRunResult> {
    return this.http
      .post<ApiResponse<ValidateRunResult>>(`${this.base}/campaigns/validate`, body ?? {})
      .pipe(map((r) => r.data));
  }

  infrastructureSummary(): Observable<InfrastructureSummary> {
    return this.http
      .get<ApiResponse<InfrastructureSummary>>(`${this.base}/infrastructure/summary`)
      .pipe(map((r) => r.data));
  }

  listInfrastructure(category?: string): Observable<InfrastructureAsset[]> {
    const params: Record<string, string> = {};
    if (category) params['category'] = category;
    return this.http
      .get<ApiResponse<InfrastructureAsset[]>>(`${this.base}/infrastructure`, { params })
      .pipe(map((r) => r.data));
  }

  saveInfrastructure(body: InfrastructureAsset, id?: number): Observable<InfrastructureAsset> {
    if (id) {
      return this.http
        .put<ApiResponse<InfrastructureAsset>>(`${this.base}/infrastructure/${id}`, body)
        .pipe(map((r) => r.data));
    }
    return this.http
      .post<ApiResponse<InfrastructureAsset>>(`${this.base}/infrastructure`, body)
      .pipe(map((r) => r.data));
  }

  deleteInfrastructure(id: number): Observable<unknown> {
    return this.http.delete(`${this.base}/infrastructure/${id}`);
  }

  documentsSummary(): Observable<DocumentVaultSummary> {
    return this.http
      .get<ApiResponse<DocumentVaultSummary>>(`${this.base}/documents/summary`)
      .pipe(map((r) => r.data));
  }

  listDocuments(params?: { docType?: string; status?: string }): Observable<ComplianceDocument[]> {
    const q: Record<string, string> = {};
    if (params?.docType) q['docType'] = params.docType;
    if (params?.status) q['status'] = params.status;
    return this.http
      .get<ApiResponse<ComplianceDocument[]>>(`${this.base}/documents`, { params: q })
      .pipe(map((r) => r.data));
  }

  saveDocument(body: ComplianceDocument, id?: number): Observable<ComplianceDocument> {
    if (id) {
      return this.http
        .put<ApiResponse<ComplianceDocument>>(`${this.base}/documents/${id}`, body)
        .pipe(map((r) => r.data));
    }
    return this.http
      .post<ApiResponse<ComplianceDocument>>(`${this.base}/documents`, body)
      .pipe(map((r) => r.data));
  }

  deleteDocument(id: number): Observable<unknown> {
    return this.http.delete(`${this.base}/documents/${id}`);
  }

  uploadDocumentFile(id: number, file: File): Observable<ComplianceDocument> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http
      .post<ApiResponse<ComplianceDocument>>(`${this.base}/documents/${id}/file`, form)
      .pipe(map((r) => r.data));
  }

  downloadDocumentFile(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/documents/${id}/file`, { responseType: 'blob' });
  }

  listCampaigns(): Observable<ComplianceCampaign[]> {
    return this.http
      .get<ApiResponse<ComplianceCampaign[]>>(`${this.base}/campaigns`)
      .pipe(map((r) => r.data));
  }

  getCampaign(id: number): Observable<ComplianceCampaign> {
    return this.http
      .get<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}`)
      .pipe(map((r) => r.data));
  }

  createCampaign(body: {
    title: string;
    boardCode?: string;
    packKey?: string;
    academicSessionId?: string;
    requireManagementApproval?: boolean;
  }): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns`, body)
      .pipe(map((r) => r.data));
  }

  validateCampaign(id: number): Observable<ValidateRunResult> {
    return this.http
      .post<ApiResponse<ValidateRunResult>>(`${this.base}/campaigns/${id}/validate`, {})
      .pipe(map((r) => r.data));
  }

  submitCampaignForReview(id: number): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/submit-for-review`, {})
      .pipe(map((r) => r.data));
  }

  approveCampaign(
    id: number,
    body: { stepCode: string; decision: string; comment?: string },
  ): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/approve`, body)
      .pipe(map((r) => r.data));
  }

  lockCampaign(id: number): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/lock`, {})
      .pipe(map((r) => r.data));
  }

  exportCampaign(id: number, formats: string[] = ['CSV', 'JSON']): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/export`, { formats })
      .pipe(map((r) => r.data));
  }

  submitCampaign(id: number, channel = 'FILE', note?: string): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/submit`, {
        channel,
        note,
      })
      .pipe(map((r) => r.data));
  }

  archiveCampaign(id: number): Observable<ComplianceCampaign> {
    return this.http
      .post<ApiResponse<ComplianceCampaign>>(`${this.base}/campaigns/${id}/archive`, {})
      .pipe(map((r) => r.data));
  }

  downloadArtifact(campaignId: number, artifactId: number): Observable<Blob> {
    return this.http.get(`${this.base}/campaigns/${campaignId}/artifacts/${artifactId}/download`, {
      responseType: 'blob',
    });
  }

  disclosurePreview(): Observable<DisclosurePackage> {
    return this.http
      .get<ApiResponse<DisclosurePackage>>(`${this.base}/disclosure/preview`)
      .pipe(map((r) => r.data));
  }

  publishDisclosure(body?: {
    slug?: string;
    title?: string;
    publishNow?: boolean;
  }): Observable<DisclosurePackage> {
    return this.http
      .post<ApiResponse<DisclosurePackage>>(`${this.base}/disclosure/publish`, body ?? {})
      .pipe(map((r) => r.data));
  }

  startAiScan(campaignId: number): Observable<AiScanJob> {
    return this.http
      .post<ApiResponse<AiScanJob>>(`${this.base}/campaigns/${campaignId}/ai-scan`, {})
      .pipe(map((r) => r.data));
  }

  getAiScan(jobId: number): Observable<AiScanJob> {
    return this.http
      .get<ApiResponse<AiScanJob>>(`${this.base}/ai-scans/${jobId}`)
      .pipe(map((r) => r.data));
  }

  listAdapterJobs(campaignId?: number): Observable<AdapterJob[]> {
    const params: Record<string, number> = {};
    if (campaignId != null) params['campaignId'] = campaignId;
    return this.http
      .get<ApiResponse<AdapterJob[]>>(`${this.base}/adapter-jobs`, { params })
      .pipe(map((r) => r.data));
  }

  listBoards(): Observable<BoardDefinition[]> {
    return this.http
      .get<ApiResponse<BoardDefinition[]>>(`${this.base}/boards`)
      .pipe(map((r) => r.data));
  }

  listPublishedPacks(): Observable<ComplianceTemplate[]> {
    return this.http
      .get<ApiResponse<ComplianceTemplate[]>>(`${this.base}/packs`)
      .pipe(map((r) => r.data));
  }

  listPlatformTemplates(): Observable<ComplianceTemplate[]> {
    return this.http
      .get<ApiResponse<ComplianceTemplate[]>>(`${this.base}/platform/templates`)
      .pipe(map((r) => r.data));
  }

  createPlatformTemplate(body: {
    packKey: string;
    boardCode: string;
    versionLabel: string;
    title: string;
    description?: string;
    status?: string;
    active?: boolean;
  }): Observable<ComplianceTemplate> {
    return this.http
      .post<ApiResponse<ComplianceTemplate>>(`${this.base}/platform/templates`, body)
      .pipe(map((r) => r.data));
  }

  updatePlatformTemplate(
    id: number,
    body: { title?: string; description?: string; status?: string; active?: boolean },
  ): Observable<ComplianceTemplate> {
    return this.http
      .put<ApiResponse<ComplianceTemplate>>(`${this.base}/platform/templates/${id}`, body)
      .pipe(map((r) => r.data));
  }

  listFieldMaps(boardCode: string): Observable<FieldMapRow[]> {
    return this.http
      .get<ApiResponse<FieldMapRow[]>>(`${this.base}/platform/field-maps`, {
        params: { boardCode },
      })
      .pipe(map((r) => r.data));
  }

  updateFieldMap(
    id: number,
    body: {
      label?: string;
      required?: boolean;
      severity?: string;
      formatRegex?: string;
      sortOrder?: number;
      active?: boolean;
    },
  ): Observable<FieldMapRow> {
    return this.http
      .put<ApiResponse<FieldMapRow>>(`${this.base}/platform/field-maps/${id}`, body)
      .pipe(map((r) => r.data));
  }

  listRules(boardCode: string): Observable<ValidationRuleRow[]> {
    return this.http
      .get<ApiResponse<ValidationRuleRow[]>>(`${this.base}/platform/rules`, {
        params: { boardCode },
      })
      .pipe(map((r) => r.data));
  }

  updateRule(
    id: number,
    body: {
      severity?: string;
      messageTemplate?: string;
      configJson?: Record<string, unknown>;
      active?: boolean;
    },
  ): Observable<ValidationRuleRow> {
    return this.http
      .put<ApiResponse<ValidationRuleRow>>(`${this.base}/platform/rules/${id}`, body)
      .pipe(map((r) => r.data));
  }
}
