import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-localization',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './localization.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class LocalizationComponent implements OnInit {
  private readonly api = inject(ApiService);
  locale: any = null;
  status = '';

  ngOnInit(): void {
    this.api.get('/api/config/localization').subscribe((l) => (this.locale = l));
  }

  save(): void {
    this.api.put('/api/config/localization', this.locale).subscribe((l) => {
      this.locale = l;
      this.status = 'Localization saved';
    });
  }
}
