import { StudentLookupRow } from './student-lookup.models';

/**
 * Form-builder keys that identify a student. These must be filled via
 * {@link applyStudentLookupToAnswers} (search → select), not free typing.
 */
export const STUDENT_IDENTITY_FIELD_KEYS = new Set([
  'studentName',
  'fullName',
  'admissionNo',
  'admissionNumber',
  'studentId',
  'email',
  'studentEmail',
  'mobile',
  'phone',
  'studentMobile',
  'parentName',
  'fatherName',
  'rollNo',
]);

/** Keys that are useful to autofill but may stay editable (e.g. class override). */
export const STUDENT_CONTEXT_FIELD_KEYS = new Set([
  'classSection',
  'class',
  'classApplied',
]);

export function isStudentIdentityField(key: string): boolean {
  return STUDENT_IDENTITY_FIELD_KEYS.has(key);
}

export function filterNonIdentityFields<T extends { key: string }>(fields: T[]): T[] {
  return fields.filter((f) => !STUDENT_IDENTITY_FIELD_KEYS.has(f.key));
}

/**
 * Maps a selected directory row onto a form-builder answers map.
 * Covers common key aliases used across Attendance, Exam, Fee, Library, etc.
 */
export function applyStudentLookupToAnswers(
  answers: Record<string, unknown>,
  row: StudentLookupRow,
): void {
  const set = (key: string, value: unknown) => {
    if (value == null) return;
    const s = String(value).trim();
    if (!s) return;
    answers[key] = s;
  };

  set('studentName', row.fullName);
  set('fullName', row.fullName);
  set('admissionNo', row.admissionNo);
  set('admissionNumber', row.admissionNo);
  set('studentId', row.id);
  set('email', row.email);
  set('studentEmail', row.email);
  set('mobile', row.mobile);
  set('phone', row.mobile);
  set('studentMobile', row.mobile);
  set('classSection', row.classSection);
  set('class', row.classSection);
  set('classApplied', row.classSection);
  set('rollNo', row.rollNo);
  set('parentName', row.parentName);
  set('fatherName', row.parentName);
}

export function clearStudentLookupAnswers(answers: Record<string, unknown>): void {
  for (const key of STUDENT_IDENTITY_FIELD_KEYS) {
    if (key in answers) {
      answers[key] = '';
    }
  }
  for (const key of STUDENT_CONTEXT_FIELD_KEYS) {
    if (key in answers) {
      answers[key] = '';
    }
  }
}
