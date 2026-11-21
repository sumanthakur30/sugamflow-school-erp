import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { InstitutionPresetService } from '../../core/institution-preset.service';

@Component({
  selector: 'sf-college-mode',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './college-mode.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class CollegeModeComponent implements OnInit {
  private readonly presets = inject(InstitutionPresetService);
  college = false;

  ngOnInit(): void {
    this.college = this.presets.current() === 'college';
    this.presets.preset$.subscribe((preset) => {
      this.college = preset === 'college';
    });
  }
}
