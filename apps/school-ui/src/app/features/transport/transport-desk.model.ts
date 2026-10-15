export type FleetStatus = 'Operational' | 'Maintenance Due' | 'Insurance Expired';
export type BusRun = 'On Route' | 'Idle' | 'Stopped';
export type PayStatus = 'Paid' | 'Overdue';

export interface FleetVehicle {
  id: string;
  registration: string;
  type: 'Bus' | 'Van' | 'Mini Bus';
  fuel: 'Diesel' | 'CNG' | 'Petrol';
  routeNo: string;
  driver: string;
  driverPhone: string;
  status: FleetStatus;
  run: BusRun;
  speedKmh: number;
  eta: string;
  insuranceExpiry: string;
  pollutionExpiry: string;
  licenseExpiry: string;
  lat: number;
  lng: number;
}

export interface RouteStop {
  name: string;
  fee: number;
  zoneKm: number;
}

export interface TransportRoutePlan {
  routeNo: string;
  name: string;
  vehicleId: string;
  stops: RouteStop[];
}

export interface TransportStudent {
  id: string;
  name: string;
  className: string;
  routeNo: string;
  pickup: string;
  drop: string;
  monthlyFee: number;
  payment: PayStatus;
  rfid: string;
}

export const TRANSPORT_SNAPSHOT = {
  transportStudents: 450,
  delayAlerts: 0,
  pendingFees: 28500,
};

export const FLEET: FleetVehicle[] = [
  v('BR01AB1201', 'Bus', 'Diesel', 'R-01', 'Ramesh Kumar', '9876501101', 'Operational', 'On Route', 32, '07:42 AM', 25.612, 85.124),
  v('BR01AB1202', 'Bus', 'Diesel', 'R-02', 'Suresh Yadav', '9876501102', 'Operational', 'On Route', 28, '07:48 AM', 25.598, 85.158),
  v('BR01AB1203', 'Bus', 'CNG', 'R-03', 'Amit Singh', '9876501103', 'Operational', 'Idle', 0, '07:55 AM', 25.631, 85.102),
  v('BR01AB1204', 'Mini Bus', 'Diesel', 'R-04', 'Prakash Das', '9876501104', 'Operational', 'On Route', 24, '08:02 AM', 25.584, 85.141),
  v('BR01AB1205', 'Bus', 'Diesel', 'R-05', 'Manoj Prasad', '9876501105', 'Operational', 'Stopped', 0, '08:10 AM', 25.621, 85.176),
  v('BR01AB1206', 'Van', 'Petrol', 'R-06', 'Neha Kumari', '9876501106', 'Operational', 'On Route', 36, '07:51 AM', 25.646, 85.133),
  v('BR01AB1207', 'Bus', 'CNG', 'R-07', 'Vikash Rai', '9876501107', 'Maintenance Due', 'Idle', 0, '—', 25.605, 85.09),
  v('BR01AB1208', 'Bus', 'Diesel', 'R-01', 'Santosh Gupta', '9876501108', 'Operational', 'On Route', 30, '07:46 AM', 25.618, 85.149),
  v('BR01AB1209', 'Mini Bus', 'CNG', 'R-02', 'Pooja Devi', '9876501109', 'Operational', 'On Route', 22, '07:58 AM', 25.592, 85.118),
  v('BR01AB1210', 'Bus', 'Diesel', 'R-03', 'Anil Thakur', '9876501110', 'Insurance Expired', 'Stopped', 0, '—', 25.638, 85.162),
  v('BR01AB1211', 'Van', 'Petrol', 'R-06', 'Kiran Jha', '9876501111', 'Operational', 'Idle', 0, '08:05 AM', 25.627, 85.111),
  v('BR01AB1212', 'Bus', 'Diesel', 'R-05', 'Deepak Sharma', '9876501112', 'Operational', 'On Route', 27, '07:50 AM', 25.601, 85.168),
];

export const ROUTE_PLANS: TransportRoutePlan[] = [
  {
    routeNo: 'R-01',
    name: 'Bailey Road',
    vehicleId: 'BR01AB1201',
    stops: [
      { name: 'School campus', fee: 0, zoneKm: 0 },
      { name: 'Boring Road', fee: 1800, zoneKm: 4 },
      { name: 'Patliputra', fee: 2200, zoneKm: 8 },
      { name: 'Danapur', fee: 2600, zoneKm: 14 },
    ],
  },
  {
    routeNo: 'R-02',
    name: 'Kankarbagh',
    vehicleId: 'BR01AB1202',
    stops: [
      { name: 'School campus', fee: 0, zoneKm: 0 },
      { name: 'Rajendra Nagar', fee: 1500, zoneKm: 3 },
      { name: 'Kankarbagh', fee: 1900, zoneKm: 6 },
      { name: 'Zero Mile', fee: 2400, zoneKm: 11 },
    ],
  },
  {
    routeNo: 'R-03',
    name: 'Gandhi Maidan',
    vehicleId: 'BR01AB1203',
    stops: [
      { name: 'School campus', fee: 0, zoneKm: 0 },
      { name: 'Fraser Road', fee: 1600, zoneKm: 5 },
      { name: 'Gandhi Maidan', fee: 2100, zoneKm: 9 },
    ],
  },
  {
    routeNo: 'R-05',
    name: 'Phulwari',
    vehicleId: 'BR01AB1205',
    stops: [
      { name: 'School campus', fee: 0, zoneKm: 0 },
      { name: 'AIIMS', fee: 2000, zoneKm: 7 },
      { name: 'Phulwari Sharif', fee: 2800, zoneKm: 16 },
    ],
  },
  {
    routeNo: 'R-06',
    name: 'Van shuttle',
    vehicleId: 'BR01AB1206',
    stops: [
      { name: 'School campus', fee: 0, zoneKm: 0 },
      { name: 'Exhibition Road', fee: 1400, zoneKm: 2 },
      { name: 'Kidwaipuri', fee: 1700, zoneKm: 5 },
    ],
  },
];

export const TRANSPORT_STUDENTS: TransportStudent[] = [
  row('Aarav Sharma', 'Class 10-A', 'R-01', 'Boring Road', 'Boring Road', 1800, 'Paid', 'RF-10021'),
  row('Ananya Singh', 'Class 8-A', 'R-01', 'Danapur', 'Patliputra', 2600, 'Overdue', 'RF-10044'),
  row('Vihaan Kumar', 'Class 5-A', 'R-02', 'Kankarbagh', 'Kankarbagh', 1900, 'Paid', 'RF-10058'),
  row('Myra Gupta', 'Class 12-A', 'R-02', 'Zero Mile', 'Rajendra Nagar', 2400, 'Overdue', 'RF-10073'),
  row('Kabir Yadav', 'Class 10-B', 'R-03', 'Fraser Road', 'Gandhi Maidan', 2100, 'Paid', 'RF-10081'),
  row('Sara Jha', 'Class 8-A', 'R-05', 'Phulwari Sharif', 'AIIMS', 2800, 'Overdue', 'RF-10096'),
  row('Ishaan Das', 'Class 5-A', 'R-06', 'Kidwaipuri', 'Exhibition Road', 1700, 'Paid', 'RF-10102'),
  row('Zoya Rahman', 'Class 10-A', 'R-03', 'Gandhi Maidan', 'Fraser Road', 2100, 'Paid', 'RF-10118'),
];

function v(
  registration: string,
  type: FleetVehicle['type'],
  fuel: FleetVehicle['fuel'],
  routeNo: string,
  driver: string,
  driverPhone: string,
  status: FleetStatus,
  run: BusRun,
  speedKmh: number,
  eta: string,
  lat: number,
  lng: number,
): FleetVehicle {
  return {
    id: registration,
    registration,
    type,
    fuel,
    routeNo,
    driver,
    driverPhone,
    status,
    run,
    speedKmh,
    eta,
    insuranceExpiry: status === 'Insurance Expired' ? '2026-08-12' : '2027-03-18',
    pollutionExpiry: status === 'Maintenance Due' ? '2026-10-20' : '2027-01-09',
    licenseExpiry: '2027-06-30',
    lat,
    lng,
  };
}

function row(
  name: string,
  className: string,
  routeNo: string,
  pickup: string,
  drop: string,
  monthlyFee: number,
  payment: PayStatus,
  rfid: string,
): TransportStudent {
  return { id: rfid, name, className, routeNo, pickup, drop, monthlyFee, payment, rfid };
}
