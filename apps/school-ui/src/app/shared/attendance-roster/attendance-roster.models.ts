export const ATTENDANCE_MARKS = ['PRESENT', 'ABSENT', 'LATE', 'LEAVE'] as const;

export type AttendanceMark = (typeof ATTENDANCE_MARKS)[number];
export type AttendanceFilter = 'ALL' | AttendanceMark | 'NOT_MARKED';
export type AttendanceSessionStatus = 'DRAFT' | 'SUBMITTED' | 'LOCKED';

export interface AttendanceSection {
  id: string;
  name?: string | null;
  studentLabel?: string | null;
}

export interface AttendancePeriod {
  id: string;
  label: string;
  periodNo?: number | null;
  breakPeriod?: boolean | null;
}

export interface AttendanceSession {
  id: string;
  status: AttendanceSessionStatus | string;
  updatedAt?: string | null;
}

export interface AttendanceStudent {
  studentId?: string | null;
  admissionNo?: string | null;
  studentName?: string | null;
  classSection?: string | null;
  photoUrl?: string | null;
  markStatus?: AttendanceMark | string | null;
  remark?: string | null;
}

export interface AttendanceRoster {
  sectionId: string;
  sectionLabel: string;
  date: string;
  periodId?: string | null;
  session?: AttendanceSession | null;
  students: AttendanceStudent[];
}

export interface AttendanceStudentState extends AttendanceStudent {
  key: string;
  rosterIndex: number;
  status: AttendanceMark | null;
  remarkText: string;
}

export interface AttendanceSummary {
  total: number;
  present: number;
  absent: number;
  late: number;
  leave: number;
  notMarked: number;
}

export interface AttendanceMarkRequest {
  studentId?: string | null;
  admissionNo?: string | null;
  studentName?: string | null;
  status: AttendanceMark;
  remark?: string;
}

export interface ParentAlertDelivery {
  channel?: string;
  status?: string;
  error?: string;
}

export interface ParentAlert {
  studentName?: string;
  admissionNo?: string;
  status?: string;
  delivery?: ParentAlertDelivery[];
}

export interface ParentAlertSummary {
  notified?: number;
  skipped?: number;
  disabled?: boolean;
  error?: string;
  alerts?: ParentAlert[];
}

export interface AttendanceBulkResponse {
  session?: AttendanceSession;
  markCount?: number;
  parentAlerts?: ParentAlertSummary;
}
