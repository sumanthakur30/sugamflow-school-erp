export type StudentLookupSource = 'STUDENT' | 'ADMISSION';

export interface StudentLookupRow {
  id: string;
  /** STUDENT = Student Master; ADMISSION = application not yet enrolled. */
  source?: StudentLookupSource;
  applicationId?: string;
  admissionNo?: string;
  fullName?: string;
  classSection?: string;
  rollNo?: string;
  parentName?: string;
  mobile?: string;
  email?: string;
  status?: string;
  gender?: string;
  house?: string;
  branchId?: string;
  academicSessionId?: string;
  transport?: boolean;
  hostel?: boolean;
  scholarship?: boolean;
  deleted?: boolean;
  photoUrl?: string;
}

export interface StudentLookupFilters {
  branchId?: string;
  academicSessionId?: string;
  classSection?: string;
  status?: string;
}

/** Statuses that should not be selectable for operational workflows by default. */
export const DEFAULT_BLOCKED_STUDENT_STATUSES = [
  'DELETED',
  'TRANSFERRED',
  'TC_ISSUED',
  'ALUMNI',
  'PASSED_OUT',
  'DROPOUT',
  'EXPELLED',
  'LEFT_SCHOOL',
] as const;

export function isStudentStatusBlocked(
  status: unknown,
  blocked: readonly string[] = DEFAULT_BLOCKED_STUDENT_STATUSES,
): boolean {
  const s = String(status || '')
    .trim()
    .toUpperCase();
  if (!s) return false;
  return blocked.some((b) => b.toUpperCase() === s);
}
