export type MeetingPlatform = 'Google Meet' | 'Zoom' | 'MS Teams' | 'YouTube Live';
export type ClassStatus = 'Scheduled' | 'In Progress' | 'Completed' | 'Cancelled';
export type SessionWindow = 'Upcoming' | 'Live Now' | 'Past Sessions';

export interface OnlineClass {
  id: string;
  title: string;
  subject: string;
  platform: MeetingPlatform;
  startsAt: string;
  durationMinutes: number;
  sections: string[];
  teacher: string;
  meetingUrl: string;
  passcode: string;
  description: string;
  notify: boolean;
  status: ClassStatus;
  recordingUrl: string;
}

export const PLATFORMS: MeetingPlatform[] = ['Google Meet', 'Zoom', 'MS Teams', 'YouTube Live'];
export const CLASS_STATUSES: ClassStatus[] = ['Scheduled', 'In Progress', 'Completed', 'Cancelled'];
export const SESSION_WINDOWS: SessionWindow[] = ['Upcoming', 'Live Now', 'Past Sessions'];
export const SECTION_OPTIONS = ['Class 5-A', 'Class 8-A', 'Class 10-A', 'Class 10-B', 'Class 12-A'];
export const SUBJECT_OPTIONS = ['Physics', 'Science', 'Chemistry', 'Mathematics', 'English'];
export const TEACHER_OPTIONS = [
  'Dakshita',
  'Kamlesh Pawar',
  'Vinod Kumar',
  'Neetu Verma',
  'Atul Manager',
  'Kanchan Das',
  'Ramesh Mishra',
];

function at(minutesFromNow: number): string {
  return new Date(Date.now() + minutesFromNow * 60_000).toISOString();
}

export const ONLINE_CLASS_SEED: OnlineClass[] = [
  {
    id: 'oc-physics',
    title: 'Fundamentals of Electro Magnetics',
    subject: 'Physics',
    platform: 'Google Meet',
    startsAt: at(180),
    durationMinutes: 45,
    sections: ['Class 10-A', 'Class 10-B'],
    teacher: 'Dakshita',
    meetingUrl: 'https://meet.google.com/sugam-physics',
    passcode: 'PHY1045',
    description: 'Magnetism revision before the unit test.',
    notify: true,
    status: 'Scheduled',
    recordingUrl: '',
  },
  {
    id: 'oc-science',
    title: 'Science live lab',
    subject: 'Science',
    platform: 'Zoom',
    startsAt: at(-8),
    durationMinutes: 40,
    sections: ['Class 8-A'],
    teacher: 'Kamlesh Pawar',
    meetingUrl: 'https://zoom.us/j/8582104451',
    passcode: 'SCI808',
    description: 'Heat and temperature demonstration.',
    notify: true,
    status: 'In Progress',
    recordingUrl: '',
  },
  {
    id: 'oc-soon',
    title: 'Online class test',
    subject: 'Mathematics',
    platform: 'Google Meet',
    startsAt: at(8),
    durationMinutes: 30,
    sections: ['Class 5-A'],
    teacher: 'Neetu Verma',
    meetingUrl: 'https://meet.google.com/sugam-maths',
    passcode: 'MTH530',
    description: 'Fractions practice. Join opens ten minutes before start.',
    notify: false,
    status: 'Scheduled',
    recordingUrl: '',
  },
  {
    id: 'oc-chem',
    title: 'Chemistry - INORGANIC',
    subject: 'Chemistry',
    platform: 'Google Meet',
    startsAt: at(60 * 26),
    durationMinutes: 50,
    sections: ['Class 12-A'],
    teacher: 'Vinod Kumar',
    meetingUrl: 'https://meet.google.com/sugam-chem',
    passcode: 'CHM1250',
    description: 'Periodic trends and bonding.',
    notify: true,
    status: 'Scheduled',
    recordingUrl: '',
  },
  {
    id: 'oc-done',
    title: 'Test ONLINE class',
    subject: 'English',
    platform: 'MS Teams',
    startsAt: at(-60 * 26),
    durationMinutes: 40,
    sections: ['Class 10-A'],
    teacher: 'Atul Manager',
    meetingUrl: 'https://teams.microsoft.com/l/meetup-join/sugam-english',
    passcode: 'ENG1040',
    description: 'Reading comprehension.',
    notify: false,
    status: 'Completed',
    recordingUrl: 'https://youtu.be/sugam-english-rec',
  },
  {
    id: 'oc-past',
    title: 'demoko',
    subject: 'Science',
    platform: 'YouTube Live',
    startsAt: at(-60 * 50),
    durationMinutes: 35,
    sections: ['Class 8-A', 'Class 5-A'],
    teacher: 'Kanchan Das',
    meetingUrl: 'https://youtube.com/live/sugam-demo',
    passcode: '',
    description: 'Recorded after class. Recording link is still missing.',
    notify: false,
    status: 'Completed',
    recordingUrl: '',
  },
  {
    id: 'oc-cancel',
    title: 'fvbggf',
    subject: 'Physics',
    platform: 'Zoom',
    startsAt: at(60 * 8),
    durationMinutes: 30,
    sections: ['Class 10-B'],
    teacher: 'Ramesh Mishra',
    meetingUrl: 'https://zoom.us/j/100200300',
    passcode: 'CAN100',
    description: 'Cancelled because the lab was closed.',
    notify: false,
    status: 'Cancelled',
    recordingUrl: '',
  },
];
