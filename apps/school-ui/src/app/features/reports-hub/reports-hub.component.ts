import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EntitlementsService } from '../../core/entitlements.service';

interface ReportCard {
  title: string;
  description: string;
  path: string;
  feature?: string;
  category: 'academic' | 'student' | 'finance' | 'operations' | 'designer';
}

@Component({
  selector: 'sf-reports-hub',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './reports-hub.component.html',
  styleUrls: ['../../shared/admin-page.scss', './reports-hub.component.scss'],
})
export class ReportsHubComponent implements OnInit {
  private readonly entitlements = inject(EntitlementsService);

  readonly catalog: ReportCard[] = [
    {
      category: 'designer',
      title: 'Report Designer',
      description: 'Build certificates, offer letters, and custom PDF templates.',
      path: '/admin/reports',
      feature: 'FEATURE_REPORT_BUILDER',
    },
    {
      category: 'student',
      title: 'Admission funnel',
      description: 'Review applications by status and process the inbox.',
      path: '/admin/admission',
      feature: 'FEATURE_ADMISSION',
    },
    {
      category: 'student',
      title: 'Student Directory',
      description: 'Filter strength by class, status, and campus — export CSV.',
      path: '/admin/student-directory',
      feature: 'FEATURE_STUDENT_MASTER',
    },
    {
      category: 'academic',
      title: 'Attendance summary',
      description: 'Daily and class-wise attendance registers.',
      path: '/admin/attendance',
      feature: 'FEATURE_ATTENDANCE',
    },
    {
      category: 'academic',
      title: 'Exam / Gradebook',
      description: 'Marks entry, grade sheets, and exam results.',
      path: '/admin/exam',
      feature: 'FEATURE_EXAM',
    },
    {
      category: 'academic',
      title: 'Lifecycle / TC',
      description: 'Promotions, transfers, and leaving certificates.',
      path: '/admin/lifecycle',
      feature: 'FEATURE_ACADEMIC_LIFECYCLE',
    },
    {
      category: 'finance',
      title: 'Fee collection',
      description: 'Receipts, dues, and collection status by student.',
      path: '/admin/fee',
      feature: 'FEATURE_FEE',
    },
    {
      category: 'finance',
      title: 'Finance / Payments',
      description: 'Payment ledger and settlement overview.',
      path: '/admin/finance',
      feature: 'FEATURE_FEE',
    },
    {
      category: 'finance',
      title: 'Income & Expense',
      description: 'Executive P&L view with KPIs, trends, and multi-branch filters.',
      path: '/admin/income-expense',
      feature: 'FEATURE_FEE',
    },
    {
      category: 'finance',
      title: 'Payroll',
      description: 'Staff salary runs and payroll summaries.',
      path: '/admin/payroll',
      feature: 'FEATURE_PAYROLL',
    },
    {
      category: 'operations',
      title: 'Library circulation',
      description: 'Issues, returns, and overdue titles.',
      path: '/admin/library',
      feature: 'FEATURE_LIBRARY',
    },
    {
      category: 'operations',
      title: 'Hostel occupancy',
      description: 'Bed allocation and hostel strength.',
      path: '/admin/hostel',
      feature: 'FEATURE_HOSTEL',
    },
    {
      category: 'operations',
      title: 'Transport routes',
      description: 'Route assignments and passenger lists.',
      path: '/admin/transport',
      feature: 'FEATURE_TRANSPORT',
    },
  ];

  cards: ReportCard[] = [];

  readonly categories: Array<{ id: ReportCard['category']; label: string }> = [
    { id: 'designer', label: 'Templates' },
    { id: 'student', label: 'Students' },
    { id: 'academic', label: 'Academics' },
    { id: 'finance', label: 'Finance' },
    { id: 'operations', label: 'Operations' },
  ];

  ngOnInit(): void {
    this.entitlements.load().subscribe(() => this.refresh());
    this.refresh();
  }

  refresh(): void {
    this.cards = this.catalog.filter((c) => this.entitlements.isEnabled(c.feature));
  }

  cardsFor(category: ReportCard['category']): ReportCard[] {
    return this.cards.filter((c) => c.category === category);
  }
}
