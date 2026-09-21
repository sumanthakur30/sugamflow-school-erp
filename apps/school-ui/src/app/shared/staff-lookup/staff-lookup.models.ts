export interface StaffLookupRow {
  id: string;
  fullName?: string;
  name?: string;
  employeeNo?: string;
  department?: string;
  designation?: string;
  email?: string;
  mobile?: string;
  status?: string;
  branchId?: string;
}

export interface StaffLookupFilters {
  status: string;
  department: string;
}
