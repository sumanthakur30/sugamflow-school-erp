import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-ai-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ai-config.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class AiConfigComponent implements OnInit {
  private readonly api = inject(ApiService);
  flags: Record<string, boolean> = {};
  status = '';

  ngOnInit(): void {
    this.api.get<Record<string, boolean>>('/api/config/ui/ai').subscribe((f) => (this.flags = f));
  }

  entries(): [string, boolean][] {
    return Object.entries(this.flags) as [string, boolean][];
  }

  save(): void {
    this.api.put('/api/config/ui/ai', this.flags).subscribe((f) => {
      this.flags = f as Record<string, boolean>;
      this.status = 'AI configuration saved';
    });
  }
}
