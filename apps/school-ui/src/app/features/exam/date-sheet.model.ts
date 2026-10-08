export type DateSheetStatus = 'Published' | 'Draft' | 'Closed';

export interface ExamDateSheet {
  id: string;
  title: string;
  className: string;
  year: string;
  from: string;
  to: string;
  students: number | null;
  subjects: number;
  status: DateSheetStatus;
  subjectNames: string[];
}

export const DATE_SHEET_CLASSES = [
  'PlayGroup-A',
  'Nursery-A',
  'Class 2-A',
  'Class 5-A',
];

export const DATE_SHEET_STATUSES: DateSheetStatus[] = ['Published', 'Draft', 'Closed'];

/** Sample schedules used until a date-sheet list API exists. */
export const DATE_SHEET_SEED: ExamDateSheet[] = [
  {
    id: 'ds-half-yearly',
    title: 'half yearly',
    className: 'PlayGroup-A',
    year: '2026',
    from: '2026-07-29',
    to: '2026-08-13',
    students: null,
    subjects: 2,
    status: 'Published',
    subjectNames: ['English', 'Mathematics'],
  },
  {
    id: 'ds-unit-1',
    title: 'First unit test',
    className: 'PlayGroup-A',
    year: '2026',
    from: '2026-06-23',
    to: '2026-06-30',
    students: null,
    subjects: 6,
    status: 'Published',
    subjectNames: ['English', 'Mathematics', 'EVS', 'Hindi', 'Art', 'Rhymes'],
  },
  {
    id: 'ds-neet',
    title: 'NEET',
    className: 'PlayGroup-A',
    year: '2026',
    from: '2026-04-29',
    to: '2026-04-29',
    students: 32,
    subjects: 4,
    status: 'Published',
    subjectNames: ['Physics', 'Chemistry', 'Biology', 'English'],
  },
  {
    id: 'ds-test-1',
    title: 'test 1',
    className: 'Class 5-A',
    year: '2026',
    from: '2026-03-02',
    to: '2026-03-08',
    students: 15,
    subjects: 6,
    status: 'Draft',
    subjectNames: ['English', 'Mathematics', 'Science', 'Social Studies', 'Hindi', 'Computer'],
  },
  {
    id: 'ds-class2',
    title: 'datesheet',
    className: 'Class 2-A',
    year: '2026',
    from: '2026-09-14',
    to: '2026-09-22',
    students: null,
    subjects: 10,
    status: 'Published',
    subjectNames: [
      'English',
      'Mathematics',
      'EVS',
      'Hindi',
      'Art',
      'Computer',
      'GK',
      'Moral Science',
      'Rhymes',
      'Handwriting',
    ],
  },
  {
    id: 'ds-nursery',
    title: 'datesheet',
    className: 'Nursery-A',
    year: '2026',
    from: '2026-10-05',
    to: '2026-10-12',
    students: null,
    subjects: 7,
    status: 'Closed',
    subjectNames: ['English', 'Mathematics', 'EVS', 'Rhymes', 'Art', 'Story', 'Motor skills'],
  },
];
