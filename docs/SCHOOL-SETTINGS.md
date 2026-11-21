# How school settings work

Each school is configured on its own. One school's classes, plan, and staff do not change another school.

Four layers decide what a person sees. They are separate on purpose.

| Layer | Who decides | What it controls | Where it lives |
|---|---|---|---|
| 1. Plan | Platform subscription | Which modules this school is allowed to use | Settings → Plan modules (`/admin/subscription`) |
| 2. School setup | School administrator | Which of those modules this school actually uses, plus its academic structure | Settings, Academic Structure, Module settings |
| 3. Master data | School administrator | Dropdown values: classes, sections, and values typed as Other | Academic Structure, and the student record for a one-off Other value |
| 4. Staff access | School administrator | What this person can open | Staff access |

A module must pass all of the layers that apply. Transport is an example.

1. The plan includes transport.
2. The school is set up as a large school, or the menu is otherwise allowed to show it.
3. The signed-in person is allowed to open it.

If the plan does not include transport, the Transport field stays off the admission form even for the owner.

## What you can do today

Open **Settings** from the sidebar.

**Institution type** is the first choice:

- Small school: students, fees, attendance, and exams.
- Large school: those, plus transport, library, payroll, and campuses when the plan includes them.
- Coaching: exams are labeled Tests.
- College: programs use the same class and section records.

The configuration table shows each area as **Active**, **Not on this plan**, or **Plan not loaded**. An active row opens its screen. A row that is not on the plan opens Subscription.

Search on the same page finds the cards below: campuses, school profile, branding, classes, calendar, admission numbers, fees, payroll, staff access, documents, and workflows.

## Classes and sections

Admission does not keep its own class list. It reads the school's academic records for the current campus and academic session.

**More → Academic Structure** is where those records are kept.

- **Add missing standard classes** creates any of Nursery, LKG, UKG, and Class 1–12 that this campus and session do not already have.
- A class the school already named, such as Grade 7, is kept. Class 7 is not created beside it.
- **Deactivate** hides that class from Add Student. Students already saved in that class stay as they are.
- **Activate** shows it again.
- A new class name that means the same step is refused. Grade 7 and Class 7 are one class.
- A section name cannot be created twice for the same class.

On **Add Student**:

- The Class dropdown lists the active classes in academic order, then any standard step this school has not created yet, then Other.
- Choosing a standard step that is not saved yet creates it for this school when the student is saved.
- Other asks for a name. That name is what is stored.
- The Section dropdown lists only that class's sections, each name once.
- A class with no sections offers A–F. Choosing one saves it for that class only. Class 1 can have A, B, and C while Class 2 has only A and B.
- Other on Section works the same way as Other on Class.

## Standard fee structure

Open **Settings → Standard fee structure**. The fees and class amounts for this school are stored with the fee records that collection already uses. There is no second fee list.

- Add only the fees this school collects. Admission, tuition, exam, and the other common fees are in the list. A custom fee can be added by name.
- Each fee has one collection frequency: one-time, monthly, quarterly, half-yearly, or annually. The frequency applies to every class. The amount can differ by class.
- The classes are the ones already saved for this campus and session.
- Turn a fee off for a class when that class does not pay it.
- Save once. A new student in that class is given that structure. Receipts, dues, and discounts already recorded are not changed.

## Other dropdowns on Add Student

Nationality, religion, category, and mother tongue have a short list plus Other. Choosing Other stores the words you type. It does not store the word Other.

Gender stays Male, Female, or Other because those are the values the student record already accepts.

Names, Aadhaar, mobile, email, and address stay typed fields.

## What a settings change does

| Change | Result |
|---|---|
| Plan removes a module | The sidebar hides it. Opening its address is refused. Existing records stay. |
| Institution type is Small school | Transport, library, payroll, and campuses stay out of the daily menu unless the plan and the large-school choice both allow them. |
| A class is deactivated | It leaves the admission dropdown. Old students keep that class. |
| A class or section is added | The next Add Student page for that campus and session shows it. |
| A standard fee amount is changed | The next fee assignment for that class uses the new amount. Receipts already issued stay as they are. |
| A staff member loses a permission | That person loses the action. The owner still has it. |

## What is not a separate settings product

There is no second screen that copies the plan into a private on/off list. Module settings (`/admin/modules`) stores school-specific keys for modules the plan already includes. Turning a module off in the plan does not delete its records.

Religion, houses, and admission sources do not yet have their own master list that every form shares. A custom religion is saved on that student. Adding it to a shared school list is later work.

There is no setup wizard that asks for logo, board, subjects, and every module in one pass. The same choices are on Settings, Academic Structure, School profile, and Branding.
