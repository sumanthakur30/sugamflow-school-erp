import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  FLEET,
  FleetVehicle,
  ROUTE_PLANS,
  TRANSPORT_SNAPSHOT,
  TRANSPORT_STUDENTS,
  TransportRoutePlan,
  TransportStudent,
} from './transport-desk.model';

type DeskTab = 'live' | 'routes' | 'fleet' | 'students';

@Component({
  selector: 'sf-transport-desk',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transport-desk.component.html',
  styleUrl: './transport-desk.component.scss',
})
export class TransportDeskComponent {
  @Input() savedRouteCount = 0;
  @Output() openSavedRoutes = new EventEmitter<void>();
  @Output() assignStudents = new EventEmitter<void>();

  tab: DeskTab = 'live';
  vehicles: FleetVehicle[] = FLEET.map((row) => ({ ...row }));
  routes = ROUTE_PLANS;
  students: TransportStudent[] = TRANSPORT_STUDENTS.map((row) => ({ ...row }));
  selectedId = this.vehicles[0]?.id ?? '';
  notice = '';

  routeFilter = '';
  typeFilter = '';
  payFilter = '';
  classFilter = '';

  vehicleForm = false;
  vehicleDraft = { registration: '', type: 'Bus' as FleetVehicle['type'], routeNo: 'R-01', driver: '', driverPhone: '' };
  maintainFor: FleetVehicle | null = null;
  maintainNote = '';

  readonly routeOptions = [...new Set(this.routes.map((route) => route.routeNo))];
  readonly classOptions = [...new Set(this.students.map((row) => row.className))];
  readonly typeOptions: FleetVehicle['type'][] = ['Bus', 'Van', 'Mini Bus'];

  get activeFleet(): number {
    return this.vehicles.filter((row) => row.status === 'Operational').length;
  }

  readonly pendingFees = TRANSPORT_SNAPSHOT.pendingFees;

  get visibleStudents(): TransportStudent[] {
    return this.students.filter((row) => {
      if (this.routeFilter && row.routeNo !== this.routeFilter) return false;
      if (this.payFilter && row.payment !== this.payFilter) return false;
      if (this.classFilter && row.className !== this.classFilter) return false;
      return true;
    });
  }

  get visibleVehicles(): FleetVehicle[] {
    return this.vehicles.filter((row) => {
      if (this.routeFilter && row.routeNo !== this.routeFilter) return false;
      if (this.typeFilter && row.type !== this.typeFilter) return false;
      return true;
    });
  }

  get selected(): FleetVehicle | undefined {
    return this.vehicles.find((row) => row.id === this.selectedId) ?? this.visibleVehicles[0];
  }

  marker(vehicle: FleetVehicle): { x: number; y: number } {
    const lats = this.vehicles.map((row) => row.lat);
    const lngs = this.vehicles.map((row) => row.lng);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);
    const x = 12 + ((vehicle.lng - minLng) / (maxLng - minLng || 1)) * 76;
    const y = 12 + ((maxLat - vehicle.lat) / (maxLat - minLat || 1)) * 76;
    return { x, y };
  }

  routeLine(): string {
    return this.visibleVehicles.map((row) => {
      const point = this.marker(row);
      return `${point.x},${point.y}`;
    }).join(' ');
  }

  selectVehicle(id: string): void {
    this.selectedId = id;
    this.tab = 'live';
  }

  editRoute(route: TransportRoutePlan): void {
    this.notice = `${route.routeNo} ${route.name} is open. Save it from the Routes tab if this campus should keep it.`;
    this.openSavedRoutes.emit();
  }

  manageStudents(routeNo: string): void {
    this.routeFilter = routeNo;
    this.tab = 'students';
  }

  openMaintain(vehicle: FleetVehicle): void {
    this.maintainFor = vehicle;
    this.maintainNote = '';
  }

  saveMaintain(): void {
    if (!this.maintainFor) return;
    const id = this.maintainFor.id;
    this.vehicles = this.vehicles.map((row) =>
      row.id === id ? { ...row, status: 'Maintenance Due', run: 'Idle', speedKmh: 0, eta: '—' } : row,
    );
    this.notice = `Maintenance logged for ${id}.`;
    this.maintainFor = null;
  }

  addVehicle(): void {
    const registration = this.vehicleDraft.registration.trim().toUpperCase();
    if (!registration) return;
    this.vehicles = [
      {
        id: registration,
        registration,
        type: this.vehicleDraft.type,
        fuel: 'Diesel',
        routeNo: this.vehicleDraft.routeNo,
        driver: this.vehicleDraft.driver.trim() || 'Unassigned',
        driverPhone: this.vehicleDraft.driverPhone.trim() || '—',
        status: 'Operational',
        run: 'Idle',
        speedKmh: 0,
        eta: '—',
        insuranceExpiry: '2027-03-18',
        pollutionExpiry: '2027-01-09',
        licenseExpiry: '2027-06-30',
        lat: 25.61,
        lng: 85.13,
      },
      ...this.vehicles,
    ];
    this.vehicleForm = false;
    this.notice = `${registration} added to the fleet.`;
  }

  downloadRoute(): void {
    const lines = ['SugamFlow route sheet', ''];
    for (const route of this.routes) {
      lines.push(`${route.routeNo} ${route.name}`);
      route.stops.forEach((stop, index) => {
        lines.push(`  ${index + 1}. ${stop.name}  ${stop.zoneKm} km  fee ${stop.fee || '—'}`);
      });
      lines.push('');
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'route-sheet.txt';
    link.click();
    URL.revokeObjectURL(url);
    this.notice = 'Route sheet downloaded. Print that file if you need a PDF.';
  }

  inr(value: number): string {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(value);
  }

  readonly studentTotal = TRANSPORT_SNAPSHOT.transportStudents;
  readonly delayAlerts = TRANSPORT_SNAPSHOT.delayAlerts;
}

