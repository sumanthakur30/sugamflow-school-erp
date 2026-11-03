import { previewAdmissionNumber } from './admission-number.util';

describe('previewAdmissionNumber', () => {
  const today = new Date(2026, 10, 3);

  it('builds a school prefix with a two-digit year', () => {
    expect(
      previewAdmissionNumber(
        {
          admissionNoPrefix: 'HCP',
          admissionNoSeparator: '/',
          admissionNoYearDigits: 2,
          admissionNoIncludeBranch: false,
          admissionNoWidth: 4,
        },
        'MAIN',
        1,
        today,
      ),
    ).toBe('HCP/26/0001');
  });

  it('keeps the year and branch in the default format', () => {
    expect(
      previewAdmissionNumber(
        {
          admissionNoPrefix: 'ADM',
          admissionNoSeparator: '-',
          admissionNoYearDigits: 4,
          admissionNoIncludeBranch: true,
          admissionNoWidth: 4,
        },
        'main',
        12,
        today,
      ),
    ).toBe('ADM-2026-MAIN-0012');
  });
});
