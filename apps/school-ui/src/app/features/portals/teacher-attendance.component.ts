import { Component } from '@angular/core';
import { AttendanceRosterComponent } from '../../shared/attendance-roster';

@Component({
  selector: 'sf-teacher-attendance',
  standalone: true,
  imports: [AttendanceRosterComponent],
  template: `
    <sf-attendance-roster
      title="Class attendance"
      subtitle="Mark the section roster for a day (optional period). Submit when the roll is final."
    />
  `,
})
export class TeacherAttendanceComponent {}
