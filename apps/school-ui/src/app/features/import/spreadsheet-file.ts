export const STUDENT_IMPORT_FIELDS = [
  'fullName',
  'admissionNo',
  'age',
  'mobile',
  'email',
  'classApplied',
  'gender',
  'rollNo',
] as const;

const HEADER_ALIASES: Record<string, string> = {
  fullname: 'fullName',
  studentname: 'fullName',
  name: 'fullName',
  admissionno: 'admissionNo',
  admissionnumber: 'admissionNo',
  age: 'age',
  mobile: 'mobile',
  mobileno: 'mobile',
  phone: 'mobile',
  phoneno: 'mobile',
  contact: 'mobile',
  email: 'email',
  emailid: 'email',
  classapplied: 'classApplied',
  class: 'classApplied',
  classsection: 'classApplied',
  gender: 'gender',
  sex: 'gender',
  rollno: 'rollNo',
  rollnumber: 'rollNo',
  grade: 'grade',
  section: 'section',
};

export function templateRows(): string[][] {
  return [
    [...STUDENT_IMPORT_FIELDS],
    ['Asha Rao', 'ADM-001', '12', '9800000001', 'asha@example.com', 'Grade 8-A', 'Female', '1'],
  ];
}

export async function readSpreadsheet(file: File): Promise<string[][]> {
  const name = file.name.toLowerCase();
  if (name.endsWith('.xlsx')) {
    return rowsFromXlsx(new Uint8Array(await file.arrayBuffer()));
  }
  if (name.endsWith('.csv') || file.type === 'text/csv') {
    return rowsFromCsv(await file.text());
  }
  throw new Error('Choose a CSV or Excel (.xlsx) file');
}

export function rowsFromCsv(csv: string): string[][] {
  const text = csv.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  if (!text.trim()) return [];
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = false;
        }
      } else {
        cell += ch;
      }
      continue;
    }
    if (ch === '"') {
      quoted = true;
    } else if (ch === ',') {
      row.push(cell.trim());
      cell = '';
    } else if (ch === '\n') {
      row.push(cell.trim());
      if (row.some((value) => value)) rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += ch;
    }
  }
  row.push(cell.trim());
  if (row.some((value) => value)) rows.push(row);
  return rows;
}

export function toStudentCsv(rows: string[][]): string {
  return tableToCsv(toStudentTable(rows));
}

export function toStudentTable(rows: string[][]): string[][] {
  if (!rows.length) return [[...STUDENT_IMPORT_FIELDS]];
  const headers = rows[0].map(canonicalHeader);
  const out: string[][] = [[...STUDENT_IMPORT_FIELDS]];
  for (const source of rows.slice(1)) {
    const record: Record<string, string> = {};
    headers.forEach((header, index) => {
      const value = (source[index] || '').trim();
      if (header && value && !record[header]) record[header] = value;
    });
    if (!record['classApplied']) {
      record['classApplied'] = classFromGrade(record['grade'], record['section']);
    }
    const values = STUDENT_IMPORT_FIELDS.map((field) => record[field] || '');
    if (values.some((value) => value)) out.push(values);
  }
  return out;
}

export function tableToCsv(rows: string[][]): string {
  return rows.map((row) => row.map(csvEscape).join(',')).join('\n') + '\n';
}

export function workbookBytes(rows: string[][]): Uint8Array {
  const shared = new Map<string, number>();
  const strings: string[] = [];
  const remember = (value: string) => {
    const existing = shared.get(value);
    if (existing !== undefined) return existing;
    const index = strings.length;
    shared.set(value, index);
    strings.push(value);
    return index;
  };
  const sheetRows = rows.map((row, rowIndex) => {
    const cells = row.map((value, columnIndex) => {
      const ref = `${columnName(columnIndex)}${rowIndex + 1}`;
      return `<c r="${ref}" t="s"><v>${remember(value ?? '')}</v></c>`;
    });
    return `<row r="${rowIndex + 1}">${cells.join('')}</row>`;
  });
  const sharedXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.length}" uniqueCount="${strings.length}">` +
    strings.map((value) => `<si><t>${xml(value)}</t></si>`).join('') +
    `</sst>`;
  const sheetXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>` +
    sheetRows.join('') +
    `</sheetData></worksheet>`;
  const files: Record<string, string> = {
    '[Content_Types].xml':
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
      `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
      `<Default Extension="xml" ContentType="application/xml"/>` +
      `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>` +
      `<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>` +
      `<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>` +
      `</Types>`,
    '_rels/.rels':
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
      `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>` +
      `</Relationships>`,
    'xl/workbook.xml':
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
      `<sheets><sheet name="Students" sheetId="1" r:id="rId1"/></sheets></workbook>`,
    'xl/_rels/workbook.xml.rels':
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
      `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>` +
      `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>` +
      `</Relationships>`,
    'xl/worksheets/sheet1.xml': sheetXml,
    'xl/sharedStrings.xml': sharedXml,
  };
  return zipStore(files);
}

export async function rowsFromXlsx(bytes: Uint8Array): Promise<string[][]> {
  const files = await unzip(bytes);
  const workbook = textFile(files, 'xl/workbook.xml');
  const rels = textFile(files, 'xl/_rels/workbook.xml.rels');
  const sheetPath = studentSheetPath(workbook, rels);
  const sheet = textFile(files, sheetPath);
  const strings = sharedStrings(textFile(files, 'xl/sharedStrings.xml'));
  const rows = new Map<number, string[]>();
  for (const match of sheet.matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const attrs = match[1];
    const body = match[2] || '';
    const ref = attr(attrs, 'r');
    if (!ref) continue;
    const rowNo = Number(ref.replace(/[A-Z]+/i, ''));
    const type = attr(attrs, 't');
    let value = '';
    if (type === 'inlineStr') {
      value = decodeXml([...body.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((part) => part[1]).join(''));
    } else {
      const raw = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
      if (raw != null) {
        if (type === 's') value = strings[Number(raw)] || '';
        else value = plainNumber(decodeXml(raw));
      }
    }
    if (!rows.has(rowNo)) rows.set(rowNo, []);
    rows.get(rowNo)![columnIndex(ref)] = value.trim();
  }
  return [...rows.keys()]
    .sort((a, b) => a - b)
    .map((rowNo) => rows.get(rowNo) || [])
    .filter((row) => row.some((value) => value));
}

function canonicalHeader(header: string): string {
  const key = header.toLowerCase().replace(/[^a-z0-9]/g, '');
  return HEADER_ALIASES[key] || header.trim();
}

function classFromGrade(grade: string | undefined, section: string | undefined): string {
  const gradeText = (grade || '').trim();
  const sectionText = (section || '').trim();
  if (!gradeText) return '';
  const withGrade = /^grade\b/i.test(gradeText) || /[a-z]/i.test(gradeText) ? gradeText : `Grade ${gradeText}`;
  if (!sectionText || hasSection(withGrade, sectionText)) return withGrade;
  return `${withGrade}-${sectionText}`;
}

function hasSection(label: string, section: string): boolean {
  const tail = section.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?:^|[\\s-])${tail}$`, 'i').test(label);
}

function csvEscape(value: string): string {
  if (/[",\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

function columnName(index: number): string {
  let n = index + 1;
  let name = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    name = String.fromCharCode(65 + rem) + name;
    n = Math.floor((n - 1) / 26);
  }
  return name;
}

function columnIndex(ref: string): number {
  const letters = ref.replace(/[0-9]/g, '').toUpperCase();
  let n = 0;
  for (const ch of letters) n = n * 26 + (ch.charCodeAt(0) - 64);
  return n - 1;
}

function plainNumber(value: string): string {
  if (/^\d+\.0+$/.test(value)) return value.replace(/\.0+$/, '');
  return value;
}

function xml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function decodeXml(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function attr(source: string, name: string): string {
  return (source.match(new RegExp(`\\b${name}="([^"]*)"`)) || [])[1] || '';
}

function sharedStrings(xml: string): string[] {
  if (!xml) return [];
  return [...xml.matchAll(/<si\b[\s\S]*?<\/si>/g)].map((block) =>
    decodeXml([...block[0].matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((part) => part[1]).join('')),
  );
}

function studentSheetPath(workbook: string, rels: string): string {
  const sheets = [...workbook.matchAll(/<sheet\b([^>]*)\/>/g)].map((match) => ({
    name: attr(match[1], 'name'),
    id: attr(match[1], 'r:id') || attr(match[1], 'id'),
  }));
  const chosen = sheets.find((sheet) => sheet.name.toLowerCase() === 'students') || sheets[0];
  if (!chosen) throw new Error('The Excel file has no worksheet');
  const target = [...rels.matchAll(/<Relationship\b([^>]*)\/>/g)]
    .map((match) => ({ id: attr(match[1], 'Id'), target: attr(match[1], 'Target') }))
    .find((rel) => rel.id === chosen.id)?.target;
  if (!target) throw new Error('The Excel file has no worksheet');
  const clean = target.replace(/^\/?xl\//, '');
  return `xl/${clean}`;
}

function textFile(files: Map<string, Uint8Array>, name: string): string {
  const data = files.get(name) || files.get(name.replace(/^\/+/, ''));
  return data ? new TextDecoder().decode(data) : '';
}

async function unzip(bytes: Uint8Array): Promise<Map<string, Uint8Array>> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let eocd = -1;
  const start = Math.max(0, bytes.length - 22 - 65535);
  for (let i = bytes.length - 22; i >= start; i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error('The Excel file could not be read');
  const count = view.getUint16(eocd + 10, true);
  let cursor = view.getUint32(eocd + 16, true);
  const out = new Map<string, Uint8Array>();
  for (let n = 0; n < count; n++) {
    if (view.getUint32(cursor, true) !== 0x02014b50) throw new Error('The Excel file could not be read');
    const method = view.getUint16(cursor + 10, true);
    const compressedSize = view.getUint32(cursor + 20, true);
    const nameLength = view.getUint16(cursor + 28, true);
    const extraLength = view.getUint16(cursor + 30, true);
    const commentLength = view.getUint16(cursor + 32, true);
    const localOffset = view.getUint32(cursor + 42, true);
    const name = new TextDecoder().decode(bytes.subarray(cursor + 46, cursor + 46 + nameLength));
    const localNameLength = view.getUint16(localOffset + 26, true);
    const localExtraLength = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.subarray(dataStart, dataStart + compressedSize);
    out.set(name.replace(/\\/g, '/'), method === 0 ? compressed : await inflateRaw(compressed));
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return out;
}

async function inflateRaw(compressed: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function zipStore(files: Record<string, string>): Uint8Array {
  const encoder = new TextEncoder();
  const locals: Uint8Array[] = [];
  const centrals: Uint8Array[] = [];
  let offset = 0;
  for (const [name, contents] of Object.entries(files)) {
    const nameBytes = encoder.encode(name);
    const data = encoder.encode(contents);
    const crc = crc32(data);
    const local = new Uint8Array(30 + nameBytes.length + data.length);
    const localView = new DataView(local.buffer);
    localView.setUint32(0, 0x04034b50, true);
    localView.setUint16(4, 20, true);
    localView.setUint16(8, 0, true);
    localView.setUint32(14, crc, true);
    localView.setUint32(18, data.length, true);
    localView.setUint32(22, data.length, true);
    localView.setUint16(26, nameBytes.length, true);
    local.set(nameBytes, 30);
    local.set(data, 30 + nameBytes.length);
    locals.push(local);
    const central = new Uint8Array(46 + nameBytes.length);
    const centralView = new DataView(central.buffer);
    centralView.setUint32(0, 0x02014b50, true);
    centralView.setUint16(4, 20, true);
    centralView.setUint16(6, 20, true);
    centralView.setUint32(16, crc, true);
    centralView.setUint32(20, data.length, true);
    centralView.setUint32(24, data.length, true);
    centralView.setUint16(28, nameBytes.length, true);
    centralView.setUint32(42, offset, true);
    central.set(nameBytes, 46);
    centrals.push(central);
    offset += local.length;
  }
  const centralSize = centrals.reduce((sum, part) => sum + part.length, 0);
  const eocd = new Uint8Array(22);
  const eocdView = new DataView(eocd.buffer);
  eocdView.setUint32(0, 0x06054b50, true);
  eocdView.setUint16(8, centrals.length, true);
  eocdView.setUint16(10, centrals.length, true);
  eocdView.setUint32(12, centralSize, true);
  eocdView.setUint32(16, offset, true);
  const total = offset + centralSize + eocd.length;
  const out = new Uint8Array(total);
  let cursor = 0;
  for (const part of [...locals, ...centrals, eocd]) {
    out.set(part, cursor);
    cursor += part.length;
  }
  return out;
}

const CRC_TABLE = new Uint32Array(256);
for (let n = 0; n < 256; n++) {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  CRC_TABLE[n] = c >>> 0;
}

function crc32(data: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of data) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
