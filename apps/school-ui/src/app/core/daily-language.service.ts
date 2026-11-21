import { Injectable, signal } from '@angular/core';

const COPY = {
  en: {
    english: 'English',
    hindi: 'हिन्दी',
    pay: 'Pay',
    receipt: 'Receipt',
    dueNow: 'Due now',
    pleaseWait: 'Please wait…',
    refresh: 'Refresh',
    refreshing: 'Refreshing…',
    feeNote: 'Pay an open due here. After the school records the payment, the receipt stays on this page.',
    attendanceNote: 'These are the marks the teacher submitted. A roll that is still a draft is not shown.',
    action: 'Action',
    student: 'Student',
    date: 'Date',
    attendance: 'Attendance',
    class: 'Class',
    reportCards: 'Report cards',
    reportCardsNote: 'Assemble a term report card per student from gradebook marks and download printable PDFs.',
    downloadClassCards: 'Download class cards',
    missingMarks: 'Missing marks',
    missingMarksNote: 'These are not printed as zero. A saved zero is included on the card.',
    todayCollection: "Today's collection",
    todayReceipts: "Today's receipts",
    todayHint: 'Approved receipts posted today',
    todayListHint: "Approved receipts posted today on this campus. The total matches Today's collection.",
    collectFee: 'Collect fee +',
    total: 'Total',
    noReceiptsToday: 'No approved receipt posted today on this campus.',
  },
  hi: {
    english: 'English',
    hindi: 'हिन्दी',
    pay: 'भुगतान',
    receipt: 'रसीद',
    dueNow: 'बकाया',
    pleaseWait: 'कृपया प्रतीक्षा करें…',
    refresh: 'रीफ़्रेश',
    refreshing: 'रीफ़्रेश हो रहा है…',
    feeNote: 'बकाया राशि यहाँ जमा करें। स्कूल भुगतान दर्ज करने के बाद रसीद इसी पृष्ठ पर रहती है।',
    attendanceNote: 'ये अंक शिक्षक ने जमा किए हैं। जो हाजिरी अभी ड्राफ़्ट है, वह यहाँ नहीं दिखती।',
    action: 'कार्रवाई',
    student: 'विद्यार्थी',
    date: 'तिथि',
    attendance: 'हाजिरी',
    class: 'कक्षा',
    reportCards: 'रिपोर्ट कार्ड',
    reportCardsNote: 'ग्रेडबुक के अंकों से प्रत्येक विद्यार्थी का रिपोर्ट कार्ड बनाएँ और पीडीएफ़ डाउनलोड करें।',
    downloadClassCards: 'कक्षा के कार्ड डाउनलोड करें',
    missingMarks: 'छूटे हुए अंक',
    missingMarksNote: 'इन्हें शून्य मानकर प्रिंट नहीं किया जाता। सहेजा गया शून्य कार्ड में रहता है।',
    todayCollection: 'आज का संग्रह',
    todayReceipts: 'आज की रसीदें',
    todayHint: 'आज दर्ज की गई स्वीकृत रसीदें',
    todayListHint: 'इस परिसर में आज दर्ज की गई स्वीकृत रसीदें। कुल राशि आज के संग्रह के बराबर है।',
    collectFee: 'शुल्क जमा करें +',
    total: 'कुल',
    noReceiptsToday: 'इस परिसर में आज कोई स्वीकृत रसीद दर्ज नहीं हुई।',
  },
} as const;

export type DailyLanguage = keyof typeof COPY;

@Injectable({ providedIn: 'root' })
export class DailyLanguageService {
  readonly code = signal<DailyLanguage>(this.read());

  t(key: keyof (typeof COPY)['en']): string {
    return COPY[this.code()][key];
  }

  set(code: DailyLanguage): void {
    this.code.set(code);
    try {
      localStorage.setItem('sf.dailyLanguage', code);
    } catch {
      // The screen still switches for this visit when storage is blocked.
    }
  }

  private read(): DailyLanguage {
    try {
      return localStorage.getItem('sf.dailyLanguage') === 'hi' ? 'hi' : 'en';
    } catch {
      return 'en';
    }
  }
}
