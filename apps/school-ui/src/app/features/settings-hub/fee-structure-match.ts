/** Same class and session rules as fee-service FeeStructureMatcher. */
export interface FeeStructureRow {
  definitionKey?: string;
  payload?: {
    definitionKey?: string;
    eligibility?: { classSection?: string; academicSessionId?: string };
  };
}

const SECTION = /(?:[\s-]+[a-z]|(?<=\d)[a-z])$/;

export function matchFeeStructure(rows: FeeStructureRow[], className: string, sessionId: string): string {
  const candidates = (rows || []).map((row) => {
    const payload = row?.payload || {};
    const eligibility = payload.eligibility || {};
    return {
      definitionKey: String(row?.definitionKey || payload.definitionKey || '').trim(),
      classSection: String(eligibility.classSection || ''),
      academicSessionId: String(eligibility.academicSessionId || ''),
    };
  });
  return pick(candidates, className, sessionId);
}

function pick(
  candidates: { definitionKey: string; classSection: string; academicSessionId: string }[],
  studentClass: string,
  sessionId: string,
): string {
  const student = norm(studentClass);
  if (!student) return '';
  const studentGrade = gradeKey(student);
  const session = norm(sessionId);
  const yearSpecific =
    !!session && candidates.some((candidate) => candidate && session === norm(candidate.academicSessionId));
  let best: { definitionKey: string } | null = null;
  let bestScore = -1;
  for (const candidate of candidates) {
    if (!candidate?.definitionKey) continue;
    const configured = norm(candidate.classSection);
    if (!configured) continue;
    const configuredSession = norm(candidate.academicSessionId);
    if (yearSpecific && configuredSession && configuredSession !== session) continue;
    const configuredGrade = gradeKey(configured);
    let score = 0;
    if (studentGrade && studentGrade === configuredGrade) {
      score = configured === configuredGrade ? 120 : 100;
    } else if (configured === student) {
      score = 80;
    } else if (student.startsWith(configured + '-') || student.startsWith(configured + ' ')) {
      score = 40;
    } else {
      continue;
    }
    if (configuredSession && configuredSession === session) score += 10;
    const better =
      score > bestScore ||
      (score === bestScore && !!best && candidate.definitionKey.localeCompare(best.definitionKey) < 0);
    if (better) {
      bestScore = score;
      best = candidate;
    }
  }
  return best ? best.definitionKey : '';
}

function gradeKey(value: string): string {
  return value.replace(SECTION, '');
}

function norm(value: string): string {
  return (value || '').trim().toLowerCase().replace(/\s+/g, ' ');
}
