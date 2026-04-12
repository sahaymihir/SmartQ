# SmartQ Demo Credentials

Use `cd backend && npm run seed:demo` to clear the database and recreate the full classroom demo dataset, or log in as the superadmin and use the `Reset & Seed Demo Data` button from the admin dashboard.

## Shared Passwords

| Role | Password |
| --- | --- |
| Superadmin | `Super@123` |
| Admin | `Admin@123` |
| Doctor | `Doctor@123` |
| Nurse | `Nurse@123` |
| Patient | `Patient@123` |

## Recommended Demo Logins

| Role | Name | Email | Why this account is useful |
| --- | --- | --- | --- |
| Superadmin | SmartQ Superadmin | `superadmin@smartq.in` | Can reset seed data, open user management, and access all admin screens |
| Admin | Aisha Thomas | `aisha.admin@smartq.in` | Good for admin-only walkthroughs after the seed is complete |
| Doctor | Dr. Vikram Nair | `dr.vikram@smartq.in` | Has a live called patient, one immediate-review patient, and a draft prescription |
| Doctor | Dr. Rajesh Patel | `dr.rajesh@smartq.in` | Has an orthopaedics follow-up currently in queue |
| Doctor | Dr. Ananya Krishnamurthy | `dr.ananya@smartq.in` | Has cardiology follow-up patients and a live queue |
| Doctor | Dr. Neha Bansal | `dr.neha@smartq.in` | Good for showing endocrine department coverage with a diabetic follow-up |
| Doctor | Dr. Rhea D'Souza | `dr.rhea@smartq.in` | Good for ENT coverage with a live follow-up patient |
| Nurse | Nurse Radha Pillai | `radha.nurse@smartq.in` | Good for nurse-triage / emergency desk demos |
| Patient | Aarav Mehta | `aarav.patient@smartq.in` | Currently called in Dr. Vikram Nair’s queue |
| Patient | Rahul Mehta | `rahul.patient@smartq.in` | In the immediate-review lane with a safety escalation |
| Patient | Harsha Gowda | `harsha.patient@smartq.in` | Has completed history and an active cardiology follow-up |
| Patient | Priya Sharma | `priya.patient@smartq.in` | Has completed dermatology prescription history |

## Department Coverage

The demo seed now includes at least one doctor for every department currently represented in SmartQ's routing and specialty layers, with extra staffing in a few high-traffic areas:

| Department | Seeded doctors |
| --- | --- |
| General OPD | Dr. Vikram Nair, Dr. Farah Siddiqui |
| Cardiology | Dr. Ananya Krishnamurthy, Dr. Arvind Narang |
| Orthopaedics | Dr. Rajesh Patel, Dr. Neeraj Malhotra |
| Neurology | Dr. Sunita Sharma, Dr. Nandita Bose |
| Dermatology | Dr. Priya Menon |
| Gastroenterology | Dr. Anil Gupta |
| Paediatrics | Dr. Kavita Reddy |
| Pulmonology | Dr. Mohan Iyer |
| Otolaryngology (ENT) | Dr. Rhea D'Souza |
| Endocrinology | Dr. Neha Bansal |
| Nephrology / Urology | Dr. Vivek Kulkarni |
| Emergency Medicine | Dr. Tara Fernandes |
| Hematology | Dr. Charu Singh |
| Infectious Disease | Dr. Sonal Abraham |

## All Staff Accounts

### Superadmin

| Name | Email |
| --- | --- |
| SmartQ Superadmin | `superadmin@smartq.in` |

### Admins

| Name | Email |
| --- | --- |
| Aisha Thomas | `aisha.admin@smartq.in` |
| Nikhil Rao | `nikhil.admin@smartq.in` |
| Meera Kulkarni | `meera.admin@smartq.in` |
| Harish Menon | `harish.admin@smartq.in` |

### Doctors

| Name | Specialty | Email |
| --- | --- | --- |
| Dr. Ananya Krishnamurthy | Cardiology | `dr.ananya@smartq.in` |
| Dr. Rajesh Patel | Orthopaedics | `dr.rajesh@smartq.in` |
| Dr. Sunita Sharma | Neurology | `dr.sunita@smartq.in` |
| Dr. Vikram Nair | General OPD | `dr.vikram@smartq.in` |
| Dr. Priya Menon | Dermatology | `dr.priya@smartq.in` |
| Dr. Anil Gupta | Gastroenterology | `dr.anil@smartq.in` |
| Dr. Kavita Reddy | Paediatrics | `dr.kavita@smartq.in` |
| Dr. Mohan Iyer | Pulmonology | `dr.mohan@smartq.in` |
| Dr. Farah Siddiqui | General OPD | `dr.farah@smartq.in` |
| Dr. Arvind Narang | Cardiology | `dr.arvind@smartq.in` |
| Dr. Neeraj Malhotra | Orthopaedics | `dr.neeraj@smartq.in` |
| Dr. Nandita Bose | Neurology | `dr.nandita@smartq.in` |
| Dr. Rhea D'Souza | Otolaryngology (ENT) | `dr.rhea@smartq.in` |
| Dr. Neha Bansal | Endocrinology | `dr.neha@smartq.in` |
| Dr. Vivek Kulkarni | Nephrology / Urology | `dr.vivek.k@smartq.in` |
| Dr. Tara Fernandes | Emergency Medicine | `dr.tara@smartq.in` |
| Dr. Charu Singh | Hematology | `dr.charu@smartq.in` |
| Dr. Sonal Abraham | Infectious Disease | `dr.sonal@smartq.in` |

### Nurses

| Name | Email |
| --- | --- |
| Nurse Radha Pillai | `radha.nurse@smartq.in` |
| Nurse Anita Desai | `anita.nurse@smartq.in` |
| Nurse Suresh Babu | `suresh.nurse@smartq.in` |
| Nurse Lakshmi Venkat | `lakshmi.nurse@smartq.in` |
| Nurse Preethi Nambiar | `preethi.nurse@smartq.in` |
| Nurse Josephine D'Souza | `josephine.nurse@smartq.in` |

## Patient Accounts

| Name | Email | Notes |
| --- | --- | --- |
| Aarav Mehta | `aarav.patient@smartq.in` | Live called token in General OPD |
| Priya Sharma | `priya.patient@smartq.in` | Completed dermatology history |
| Ravi Kumar | `ravi.patient@smartq.in` | Neurology queue |
| Sunita Devi | `sunita.patient@smartq.in` | Emergency Medicine follow-up currently called |
| Rahul Mehta | `rahul.patient@smartq.in` | Immediate-review lane |
| Deepa Nair | `deepa.patient@smartq.in` | Pulmonology follow-up in queue |
| Sanjay Patel | `sanjay.patient@smartq.in` | Orthopaedics follow-up currently called |
| Kavitha Reddy | `kavitha.patient@smartq.in` | Idle patient account |
| Anil Verma | `anil.patient@smartq.in` | Cardiology follow-up currently called |
| Meena Rao | `meena.patient@smartq.in` | Hematology follow-up in queue with prior prescription history |
| Vikram Joshi | `vikram.patient@smartq.in` | Endocrinology follow-up in queue |
| Pooja Iyer | `pooja.patient@smartq.in` | Dermatology follow-up in queue |
| Naresh Bhat | `naresh.patient@smartq.in` | Live General OPD queue |
| Geeta Mishra | `geeta.patient@smartq.in` | Cardiology queue |
| Imran Khan | `imran.patient@smartq.in` | Infectious Disease follow-up in queue |
| Sneha Thomas | `sneha.patient@smartq.in` | Live General OPD queue |
| Kiran Shetty | `kiran.patient@smartq.in` | Neurology follow-up in queue |
| Fatima Ali | `fatima.patient@smartq.in` | Gastroenterology follow-up in queue |
| Manoj Das | `manoj.patient@smartq.in` | Nephrology / Urology follow-up in queue |
| Ritu Kapoor | `ritu.patient@smartq.in` | ENT follow-up in queue |
| Aarohi Nair | `aarohi.patient@smartq.in` | Paediatrics follow-up currently called |
| Ishan Kapoor | `ishan.patient@smartq.in` | Idle patient account |
| Harsha Gowda | `harsha.patient@smartq.in` | Cardiology follow-up in queue with prescription history |
| Leela Krishnan | `leela.patient@smartq.in` | Orthopaedics queue |
| Aditi Sen | `aditi.patient@smartq.in` | Second orthopaedics queue |
| Naveen Prasad | `naveen.patient@smartq.in` | General OPD follow-up in queue |
