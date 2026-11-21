import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { EntitlementsService } from '../../core/entitlements.service';
import { matchFeeStructure } from '../settings-hub/fee-structure-match';

export interface StudentCreated {
  id: string;
  admissionNo: string;
  note?: string;
}

interface NamedRow {
  id: string;
  name: string;
  classId?: string;
  suggested?: boolean;
  sequenceNo?: number;
}

interface Choice {
  key: string;
  label: string;
}

interface PendingFile {
  type: string;
  fileName: string;
  contentType: string;
  contentBase64: string;
}

@Component({
  selector: 'sf-add-student-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-student-panel.component.html',
  styleUrls: ['../../shared/admin-page.scss', './add-student-panel.component.scss'],
})
export class AddStudentPanelComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly entitlements = inject(EntitlementsService);

  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly created = new EventEmitter<StudentCreated>();

  readonly genders = ['Male', 'Female', 'Other'];
  readonly bloodGroups = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
  readonly categories = ['General', 'OBC', 'SC', 'ST', 'EWS', 'Other'];
  readonly nationalities = ['Indian', 'Other'];
  readonly religions = ['Hindu', 'Muslim', 'Christian', 'Sikh', 'Other'];
  readonly motherTongues = ['Hindi', 'English', 'Urdu', 'Sanskrit', 'Other'];
  readonly admissionTypes = ['New', 'Transfer'];
  private readonly defaultSections = ['A', 'B', 'C', 'D', 'E', 'F'];
  readonly contacts = [
    { value: 'FATHER', label: 'Father' },
    { value: 'MOTHER', label: 'Mother' },
    { value: 'GUARDIAN', label: 'Guardian' },
  ];
  readonly documentTypes = [
    { type: 'BIRTH_CERTIFICATE', label: 'Birth certificate' },
    { type: 'TRANSFER_CERTIFICATE', label: 'Previous school transfer certificate' },
    { type: 'PREVIOUS_MARKSHEET', label: 'Previous school marksheet' },
    { type: 'OTHER', label: 'Other document' },
  ];

  firstName = '';
  middleName = '';
  lastName = '';
  dateOfBirth = '';
  gender = '';
  bloodGroup = '';
  nationality = 'Indian';
  otherNationality = '';
  religion = '';
  otherReligion = '';
  category = '';
  otherCategory = '';
  motherTongue = '';
  otherMotherTongue = '';
  otherClass = '';
  otherSection = '';
  aadhaar = '';
  photoName = '';
  photoPreview = '';
  private photoFile: PendingFile | null = null;

  admissionDate = '';
  sessionId = '';
  branchId = '';
  className = '';
  classQuery = '';
  section = '';
  sectionQuery = '';
  rollNo = '';
  previousSchool = '';
  previousClass = '';
  previousTcNumber = '';
  admissionType = 'New';
  status = 'ACTIVE';

  fatherName = '';
  fatherMobile = '';
  fatherWhatsapp = '';
  fatherEmail = '';
  fatherOccupation = '';
  fatherEducation = '';
  fatherAadhaar = '';
  motherName = '';
  motherMobile = '';
  motherWhatsapp = '';
  motherEmail = '';
  motherOccupation = '';
  motherEducation = '';
  motherAadhaar = '';
  guardianDifferent = false;
  guardianName = '';
  guardianRelation = '';
  guardianMobile = '';
  guardianEmail = '';
  guardianAddress = '';
  primaryContact = 'FATHER';

  permanentAddress = '';
  permanentAddressLine2 = '';
  permanentState = '';
  permanentDistrict = '';
  permanentCity = '';
  permanentPin = '';
  sameAddress = true;
  address = '';
  addressLine2 = '';
  state = '';
  district = '';
  city = '';
  pinCode = '';

  medicalInfo = '';
  allergies = '';
  emergencyContactName = '';
  emergencyContactNumber = '';
  previousRemarks = '';
  specialInstructions = '';
  documents: PendingFile[] = [];

  feeStructureKey = '';
  concessionKey = '';
  transportRequired = false;
  transportRoute = '';
  pickupPoint = '';
  dropPoint = '';

  classes: NamedRow[] = [];
  sections: NamedRow[] = [];
  branches: Choice[] = [];
  sessions: Choice[] = [];
  feeStructures: Choice[] = [];
  private structureRows: any[] = [];
  concessions: Choice[] = [];
  routes: Choice[] = [];

  saving = false;
  error = '';
  fieldErrors: Record<string, string> = {};
  readonly today = new Date().toISOString().slice(0, 10);

  ngOnInit(): void {
    this.admissionDate = this.today;
    this.sessionId = this.auth.getSessionId();
    this.branchId = this.auth.getBranchId();
    this.loadChoices();
  }

  get transportEnabled(): boolean {
    return this.entitlements.isEnabled('FEATURE_TRANSPORT');
  }

  get filteredClasses(): NamedRow[] {
    return this.filterRows(this.classes, this.classQuery);
  }

  get sectionChoices(): NamedRow[] {
    const selected = this.classes.find((row) => row.name === this.resolvedClass());
    const rows = selected?.id
      ? this.sections.filter((row) => !row.classId || row.classId === selected.id)
      : [];
    const unique = this.uniqueNames(this.filterRows(rows, this.sectionQuery));
    if (unique.length || !this.resolvedClass()) {
      return unique;
    }
    const query = this.sectionQuery.trim().toLowerCase();
    return this.defaultSections
      .filter((name) => !query || name.toLowerCase().includes(query))
      .map((name) => ({ id: '', name, suggested: true }));
  }

  onClassChange(): void {
    const names = new Set(this.sectionChoices.map((row) => row.name));
    if (this.section !== '__other__' && !names.has(this.section)) {
      this.section = '';
      this.otherSection = '';
    }
    this.applyStandardFee();
  }

  private applyStandardFee(): void {
    const className = this.resolvedClass();
    if (!className) {
      return;
    }
    this.feeStructureKey = matchFeeStructure(this.structureRows, className, this.sessionId);
  }

  onSameAddress(): void {
    if (!this.sameAddress) return;
    this.address = this.permanentAddress;
    this.addressLine2 = this.permanentAddressLine2;
    this.state = this.permanentState;
    this.district = this.permanentDistrict;
    this.city = this.permanentCity;
    this.pinCode = this.permanentPin;
  }

  async onPhoto(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.fieldErrors = { ...this.fieldErrors, photo: '' };
    if (!file.type.startsWith('image/')) {
      this.fieldErrors = { ...this.fieldErrors, photo: 'Use a JPG, PNG, or WEBP photo.' };
      return;
    }
    if (file.size > 512 * 1024) {
      this.fieldErrors = { ...this.fieldErrors, photo: 'Photo must be 512 KB or smaller.' };
      return;
    }
    const contentBase64 = await this.readFile(file);
    this.photoFile = {
      type: 'STUDENT_PHOTO',
      fileName: file.name,
      contentType: file.type,
      contentBase64,
    };
    this.photoName = file.name;
    this.photoPreview = contentBase64;
  }

  async onDocument(type: string, event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
      this.error = 'Documents must be PDF, JPG, PNG, or WEBP.';
      return;
    }
    if (file.size > 2048 * 1024) {
      this.error = 'Each document must be 2 MB or smaller.';
      return;
    }
    const contentBase64 = await this.readFile(file);
    this.documents = this.documents.filter((item) => item.type !== type);
    this.documents = [
      ...this.documents,
      { type, fileName: file.name, contentType: file.type, contentBase64 },
    ];
    this.error = '';
  }

  documentName(type: string): string {
    return this.documents.find((item) => item.type === type)?.fileName || '';
  }

  save(): void {
    this.error = '';
    this.fieldErrors = this.validate();
    if (Object.keys(this.fieldErrors).length) {
      this.error = 'Check the highlighted fields before saving.';
      return;
    }
    if (this.sameAddress) this.onSameAddress();
    this.saving = true;
    this.prepareAcademic().subscribe({
      next: () => this.postStudent(),
      error: () => {
        this.saving = false;
        this.error = 'The class or section could not be saved.';
      },
    });
  }

  private postStudent(): void {
    const mobile = this.contactMobile();
    this.api.post<any>('/api/student/students', this.payload(mobile)).subscribe({
      next: (row) => {
        const id = String(row?.id || '');
        const admissionNo = String(row?.admissionNo || '');
        this.uploadFiles(id).then(
          () => {
            this.saving = false;
            this.created.emit({ id, admissionNo });
          },
          () => {
            this.saving = false;
            this.created.emit({
              id,
              admissionNo,
              note: ' A document could not be uploaded. Open the student to add it.',
            });
          },
        );
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || 'The student was not saved.';
      },
    });
  }

  private loadChoices(): void {
    forkJoin({
      classes: this.api.get<any[]>('/api/academic/class-catalog').pipe(
        catchError(() => this.api.get<any[]>('/api/academic/classes').pipe(catchError(() => of([])))),
      ),
      sections: this.api.get<any[]>('/api/academic/sections').pipe(catchError(() => of([]))),
      branches: this.api.get<any[]>('/api/config/branches').pipe(catchError(() => of([]))),
      sessions: this.api.get<any[]>('/api/student/lifecycle/sessions').pipe(catchError(() => of([]))),
      structures: this.api.get<any[]>('/api/fee/finance/structures').pipe(catchError(() => of([]))),
      concessions: this.api.get<any[]>('/api/fee/finance/concessions').pipe(catchError(() => of([]))),
      routes: this.api.get<any[]>('/api/transport/ops/routes').pipe(catchError(() => of([]))),
    }).subscribe((rows) => {
      this.classes = this.named(rows.classes);
      this.sections = (rows.sections || [])
        .map((row) => ({
          id: String(row.id || ''),
          name: String(row.name || row.sectionName || '').trim(),
          classId: row.classId ? String(row.classId) : undefined,
        }))
        .filter((row) => row.name);
      this.branches = this.choices(rows.branches, ['branchKey', 'code', 'id'], ['name', 'branchKey']);
      if (!this.branches.some((row) => row.key === this.branchId) && this.branchId) {
        this.branches = [{ key: this.branchId, label: this.branchId }, ...this.branches];
      }
      this.sessions = this.choices(rows.sessions, ['definitionKey', 'key'], ['label', 'name', 'definitionKey']);
      if (!this.sessions.some((row) => row.key === this.sessionId) && this.sessionId) {
        this.sessions = [{ key: this.sessionId, label: this.sessionId }, ...this.sessions];
      }
      this.structureRows = rows.structures || [];
      this.feeStructures = this.choices(
        rows.structures,
        ['definitionKey'],
        ['label', 'name', 'definitionKey'],
      );
      this.applyStandardFee();
      this.concessions = this.choices(
        rows.concessions,
        ['definitionKey'],
        ['label', 'name', 'definitionKey'],
      );
      this.routes = this.choices(rows.routes, ['definitionKey', 'key'], ['label', 'name', 'definitionKey']);
    });
  }

  private validate(): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!this.firstName.trim()) errors['firstName'] = 'First name is required.';
    if (!this.lastName.trim()) errors['lastName'] = 'Last name is required.';
    if (!this.dateOfBirth) errors['dateOfBirth'] = 'Date of birth is required.';
    else if (this.dateOfBirth > this.today) errors['dateOfBirth'] = 'Date of birth cannot be a future date.';
    if (!this.gender) errors['gender'] = 'Gender is required.';
    if (!this.resolvedClass()) errors['className'] = 'Class is required.';
    if (!this.admissionDate) errors['admissionDate'] = 'Admission date is required.';
    if (!this.sessionId) errors['sessionId'] = 'Academic session is required.';
    if (!this.branchId) errors['branchId'] = 'Campus is required.';
    const contactName = this.fatherName.trim() || this.motherName.trim() || (this.guardianDifferent ? this.guardianName.trim() : '');
    if (!contactName) errors['fatherName'] = 'Enter a father, mother, or guardian name.';
    if (!this.contactMobile()) errors['mobile'] = 'Enter a mobile number for the student or a parent.';
    this.checkMobile('fatherMobile', this.fatherMobile, errors);
    this.checkMobile('motherMobile', this.motherMobile, errors);
    this.checkMobile('guardianMobile', this.guardianMobile, errors);
    this.checkMobile('emergencyContactNumber', this.emergencyContactNumber, errors);
    this.checkEmail('fatherEmail', this.fatherEmail, errors);
    this.checkEmail('motherEmail', this.motherEmail, errors);
    this.checkEmail('guardianEmail', this.guardianEmail, errors);
    this.checkAadhaar('aadhaar', this.aadhaar, errors);
    this.checkAadhaar('fatherAadhaar', this.fatherAadhaar, errors);
    this.checkAadhaar('motherAadhaar', this.motherAadhaar, errors);
    this.checkPin('permanentPin', this.permanentPin, errors);
    this.checkPin('pinCode', this.sameAddress ? this.permanentPin : this.pinCode, errors);
    return errors;
  }

  private payload(mobile: string): Record<string, unknown> {
    return {
      admissionForm: true,
      firstName: this.firstName.trim(),
      middleName: this.middleName.trim(),
      lastName: this.lastName.trim(),
      dateOfBirth: this.dateOfBirth,
      gender: this.gender,
      bloodGroup: this.bloodGroup,
      nationality: this.chosen(this.nationality, this.otherNationality),
      religion: this.chosen(this.religion, this.otherReligion),
      category: this.chosen(this.category, this.otherCategory),
      motherTongue: this.chosen(this.motherTongue, this.otherMotherTongue),
      aadhaar: this.aadhaar.replace(/\D/g, ''),
      admissionDate: this.admissionDate,
      academicSessionId: this.sessionId,
      branchId: this.branchId,
      className: this.resolvedClass(),
      section: this.resolvedSection(),
      rollNo: this.rollNo.trim(),
      previousSchool: this.previousSchool.trim(),
      previousClass: this.previousClass.trim(),
      previousTcNumber: this.previousTcNumber.trim(),
      admissionType: this.admissionType,
      status: this.status,
      fatherName: this.fatherName.trim(),
      fatherMobile: this.fatherMobile.trim(),
      fatherWhatsapp: this.fatherWhatsapp.trim(),
      fatherEmail: this.fatherEmail.trim(),
      fatherOccupation: this.fatherOccupation.trim(),
      fatherEducation: this.fatherEducation.trim(),
      fatherAadhaar: this.fatherAadhaar.replace(/\D/g, ''),
      motherName: this.motherName.trim(),
      motherMobile: this.motherMobile.trim(),
      motherWhatsapp: this.motherWhatsapp.trim(),
      motherEmail: this.motherEmail.trim(),
      motherOccupation: this.motherOccupation.trim(),
      motherEducation: this.motherEducation.trim(),
      motherAadhaar: this.motherAadhaar.replace(/\D/g, ''),
      guardianDifferent: this.guardianDifferent,
      guardianName: this.guardianName.trim(),
      guardianRelation: this.guardianRelation.trim(),
      guardianMobile: this.guardianMobile.trim(),
      guardianEmail: this.guardianEmail.trim(),
      guardianAddress: this.guardianAddress.trim(),
      primaryContact: this.primaryContact,
      permanentAddress: this.permanentAddress.trim(),
      permanentAddressLine2: this.permanentAddressLine2.trim(),
      permanentState: this.permanentState.trim(),
      permanentDistrict: this.permanentDistrict.trim(),
      permanentCity: this.permanentCity.trim(),
      permanentPin: this.permanentPin.trim(),
      sameAddress: this.sameAddress,
      address: this.address.trim(),
      addressLine2: this.addressLine2.trim(),
      state: this.state.trim(),
      district: this.district.trim(),
      city: this.city.trim(),
      pinCode: this.pinCode.trim(),
      medicalInfo: this.medicalInfo.trim(),
      allergies: this.allergies.trim(),
      emergencyContactName: this.emergencyContactName.trim(),
      emergencyContactNumber: this.emergencyContactNumber.trim(),
      previousRemarks: this.previousRemarks.trim(),
      specialInstructions: this.specialInstructions.trim(),
      feeStructureKey: this.feeStructureKey,
      concessionKey: this.concessionKey,
      transportRequired: this.transportRequired,
      transportRoute: this.transportRequired ? this.transportRoute : '',
      pickupPoint: this.transportRequired ? this.pickupPoint.trim() : '',
      dropPoint: this.transportRequired ? this.dropPoint.trim() : '',
      mobile,
      parentName: this.fatherName.trim() || this.motherName.trim() || this.guardianName.trim(),
    };
  }

  private contactMobile(): string {
    if (this.primaryContact === 'STUDENT') return this.fatherMobile.trim() || this.motherMobile.trim();
    if (this.primaryContact === 'FATHER' && this.fatherMobile.trim()) return this.fatherMobile.trim();
    if (this.primaryContact === 'MOTHER' && this.motherMobile.trim()) return this.motherMobile.trim();
    if (this.primaryContact === 'GUARDIAN' && this.guardianMobile.trim()) return this.guardianMobile.trim();
    return (
      this.fatherMobile.trim() ||
      this.motherMobile.trim() ||
      this.guardianMobile.trim()
    );
  }

  private async uploadFiles(studentId: string): Promise<void> {
    if (!studentId) return;
    const files = [...(this.photoFile ? [this.photoFile] : []), ...this.documents];
    for (const file of files) {
      await new Promise<void>((resolve, reject) => {
        this.api.post(`/api/student/students/${studentId}/attachments`, file).subscribe({
          next: () => resolve(),
          error: () => reject(new Error('upload')),
        });
      });
    }
  }

  private readFile(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ''));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(file);
    });
  }

  private chosen(value: string, other: string): string {
    if (value !== 'Other') {
      return value.trim();
    }
    return other.trim();
  }

  resolvedClass(): string {
    return this.className === '__other__' ? this.otherClass.trim() : this.className.trim();
  }

  private resolvedSection(): string {
    return this.section === '__other__' ? this.otherSection.trim() : this.section.trim();
  }

  private uniqueNames(rows: NamedRow[]): NamedRow[] {
    const seen = new Set<string>();
    const out: NamedRow[] = [];
    for (const row of rows) {
      const key = row.name.trim().toLowerCase();
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      out.push(row);
    }
    return out;
  }

  private prepareAcademic(): Observable<void> {
    return this.prepareClass().pipe(switchMap(() => this.prepareSection()));
  }

  private prepareClass(): Observable<void> {
    const name = this.resolvedClass();
    if (!name) {
      return of(undefined);
    }
    const row = this.classes.find((item) => item.name === name);
    if (row?.id && !row.suggested) {
      this.className = row.name;
      return of(undefined);
    }
    return this.api.post<any>('/api/academic/classes', { name, sequenceNo: row?.sequenceNo || 0 }).pipe(
      tap((created) => {
        const id = created?.id ? String(created.id) : '';
        const saved = String(created?.name || name);
        this.className = saved;
        const next = { id, name: saved, suggested: false, sequenceNo: row?.sequenceNo || 0 };
        this.classes = this.classes.some((item) => item.name === name || item.name === saved)
          ? this.classes.map((item) => (item.name === name || item.name === saved ? next : item))
          : [...this.classes, next];
      }),
      map(() => undefined),
      catchError(() => {
        this.className = name;
        return of(undefined);
      }),
    );
  }

  private prepareSection(): Observable<void> {
    const name = this.resolvedSection();
    if (!name) {
      return of(undefined);
    }
    const classRow = this.classes.find((item) => item.name === this.resolvedClass());
    const existing = this.sections.find(
      (item) =>
        item.name.trim().toLowerCase() === name.toLowerCase() &&
        (!classRow?.id || !item.classId || item.classId === classRow.id),
    );
    if (existing?.id) {
      this.section = existing.name;
      return of(undefined);
    }
    if (!classRow?.id) {
      this.section = name;
      return of(undefined);
    }
    return this.api.post<any>('/api/academic/sections', { name, classId: classRow.id }).pipe(
      tap((created) => {
        this.section = String(created?.name || name);
        this.sections = [
          ...this.sections,
          { id: created?.id ? String(created.id) : '', name: this.section, classId: classRow.id },
        ];
      }),
      map(() => undefined),
      catchError(() => {
        this.section = name;
        return of(undefined);
      }),
    );
  }

  private named(rows: any[]): NamedRow[] {
    return (rows || [])
      .map((row) => ({
        id: row?.id ? String(row.id) : '',
        name: String(row.name || row.className || '').trim(),
        suggested: row?.suggested === true || row?.status === 'SUGGESTED',
        sequenceNo: Number(row?.sequenceNo) || 0,
      }))
      .filter((row) => row.name);
  }

  private choices(rows: any[], keyFields: string[], labelFields: string[]): Choice[] {
    return (rows || [])
      .map((row) => {
        const payload = row?.payload && typeof row.payload === 'object' ? row.payload : {};
        const key = keyFields.map((field) => row[field] || payload[field]).find((value) => value);
        const label = labelFields.map((field) => payload[field] || row[field]).find((value) => value);
        return { key: String(key || '').trim(), label: String(label || key || '').trim() };
      })
      .filter((row) => row.key);
  }

  private filterRows(rows: NamedRow[], query: string): NamedRow[] {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((row) => row.name.toLowerCase().includes(q));
  }

  private checkMobile(key: string, value: string, errors: Record<string, string>): void {
    const text = value.trim();
    if (text && !/^[0-9+\-\s]{7,20}$/.test(text)) errors[key] = 'Enter a valid mobile number.';
  }

  private checkEmail(key: string, value: string, errors: Record<string, string>): void {
    const text = value.trim();
    if (text && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) errors[key] = 'Enter a valid email address.';
  }

  private checkAadhaar(key: string, value: string, errors: Record<string, string>): void {
    const digits = value.replace(/\D/g, '');
    if (value.trim() && digits.length !== 12) errors[key] = 'Aadhaar must be 12 digits.';
  }

  private checkPin(key: string, value: string, errors: Record<string, string>): void {
    const text = value.trim();
    if (text && !/^[0-9]{4,10}$/.test(text)) errors[key] = 'Enter a valid PIN code.';
  }
}
