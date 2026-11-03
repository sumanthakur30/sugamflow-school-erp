export interface AdmissionNumberSettings {
  admissionNoPrefix: string;
  admissionNoSeparator: string;
  admissionNoYearDigits: number;
  admissionNoIncludeBranch: boolean;
  admissionNoWidth: number;
}

export function previewAdmissionNumber(
  settings: AdmissionNumberSettings,
  branchId: string,
  sequence: number,
  today = new Date(),
): string {
  const prefix = (settings.admissionNoPrefix || 'ADM').replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 12) || 'ADM';
  const separator = (settings.admissionNoSeparator || '-').trim().slice(0, 3) || '-';
  const digits = Number(settings.admissionNoYearDigits);
  const yearFull = String(today.getFullYear());
  const year = digits === 0 ? '' : digits === 2 ? yearFull.slice(2) : yearFull;
  const width = Math.min(8, Math.max(1, Number(settings.admissionNoWidth) || 4));
  let stem = prefix;
  if (year) stem += separator + year;
  if (settings.admissionNoIncludeBranch !== false) {
    const branch = (branchId || 'MAIN').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6) || 'MAIN';
    stem += separator + branch;
  }
  const value = Math.max(1, sequence);
  return stem + separator + String(value).padStart(width, '0');
}
