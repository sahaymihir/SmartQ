package com.example.smartqueue.ui.patient;

import android.content.Context;
import android.content.Intent;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.response.ConsultationHistoryResponse;
import com.example.smartqueue.models.response.DoctorsResponse;

import java.util.List;

final class PatientQueueFlowHelper {

    static final String EXTRA_VISIT_TYPE = "smartq.extra.VISIT_TYPE";
    static final String EXTRA_FOLLOW_UP_TOKEN_ID = "smartq.extra.FOLLOW_UP_TOKEN_ID";
    static final String EXTRA_PREVIOUS_DOCTOR_ID = "smartq.extra.PREVIOUS_DOCTOR_ID";
    static final String EXTRA_PREVIOUS_DOCTOR_NAME = "smartq.extra.PREVIOUS_DOCTOR_NAME";
    static final String EXTRA_PREVIOUS_DOCTOR_SPECIALTY = "smartq.extra.PREVIOUS_DOCTOR_SPECIALTY";
    static final String EXTRA_SEEDED_SYMPTOMS = "smartq.extra.SEEDED_SYMPTOMS";
    static final String EXTRA_VISIT_DATE = "smartq.extra.VISIT_DATE";

    private PatientQueueFlowHelper() {}

    static Intent createNewVisitIntent(Context context) {
        Intent intent = new Intent(context, PatientQueueIntakeActivity.class);
        intent.putExtra(EXTRA_VISIT_TYPE, "new");
        return intent;
    }

    static Intent createFollowUpIntent(
            Context context,
            ConsultationHistoryResponse.Consultation consultation
    ) {
        Intent intent = new Intent(context, PatientQueueIntakeActivity.class);
        intent.putExtra(EXTRA_VISIT_TYPE, "follow_up");
        intent.putExtra(EXTRA_FOLLOW_UP_TOKEN_ID, consultation.getTokenId());
        intent.putExtra(EXTRA_PREVIOUS_DOCTOR_ID, consultation.getDoctorId());
        intent.putExtra(EXTRA_PREVIOUS_DOCTOR_NAME, consultation.getDoctorName());
        intent.putExtra(EXTRA_PREVIOUS_DOCTOR_SPECIALTY, consultation.getDoctorSpecialty());
        intent.putExtra(
                EXTRA_SEEDED_SYMPTOMS,
                !isBlank(consultation.getSymptomsSummary())
                        ? consultation.getSymptomsSummary()
                        : textOrDefault(consultation.getSymptoms(), "")
        );
        intent.putExtra(EXTRA_VISIT_DATE, consultation.getDate());
        return intent;
    }

    static QueueLaunchArgs readArgs(Intent intent) {
        return new QueueLaunchArgs(
                intent != null ? intent.getStringExtra(EXTRA_VISIT_TYPE) : null,
                intent != null ? intent.getStringExtra(EXTRA_FOLLOW_UP_TOKEN_ID) : null,
                intent != null ? intent.getStringExtra(EXTRA_PREVIOUS_DOCTOR_ID) : null,
                intent != null ? intent.getStringExtra(EXTRA_PREVIOUS_DOCTOR_NAME) : null,
                intent != null ? intent.getStringExtra(EXTRA_PREVIOUS_DOCTOR_SPECIALTY) : null,
                intent != null ? intent.getStringExtra(EXTRA_SEEDED_SYMPTOMS) : null,
                intent != null ? intent.getStringExtra(EXTRA_VISIT_DATE) : null
        );
    }

    static JoinQueueRequest buildJoinQueueRequest(String symptoms, String visitType, String followUpTokenId) {
        return new JoinQueueRequest(symptoms, visitType, followUpTokenId, "en");
    }

    static String buildDoctorDisplayLabel(DoctorsResponse.Doctor doctor) {
        String name = textOrDefault(doctor.getName(), "Doctor");
        String specialty = textOrDefault(doctor.getSpecialty(), "General OPD");
        if (!doctor.isAvailable()) {
            return name + " - " + specialty + " (Unavailable)";
        }
        return name + " - " + specialty;
    }

    static DoctorsResponse.Doctor resolveFollowUpDoctor(
            List<DoctorsResponse.Doctor> allDoctors,
            String previousDoctorId,
            String previousDoctorSpecialty
    ) {
        if (allDoctors == null || allDoctors.isEmpty()) {
            return null;
        }

        DoctorsResponse.Doctor sameSpecialtyDoctor = null;
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (!doctor.isAvailable()) {
                continue;
            }
            if (!isBlank(previousDoctorId) && previousDoctorId.equals(doctor.getId())) {
                return doctor;
            }
            if (sameSpecialtyDoctor == null
                    && !isBlank(previousDoctorSpecialty)
                    && previousDoctorSpecialty.equalsIgnoreCase(doctor.getSpecialty())) {
                sameSpecialtyDoctor = doctor;
            }
        }
        return sameSpecialtyDoctor;
    }

    static String buildFollowUpResolutionText(
            Context context,
            QueueLaunchArgs args,
            DoctorsResponse.Doctor resolvedDoctor
    ) {
        if (resolvedDoctor != null
                && !isBlank(args.previousDoctorId)
                && args.previousDoctorId.equals(resolvedDoctor.getId())) {
            return context.getString(R.string.follow_up_same_doctor_hint);
        }
        if (resolvedDoctor != null) {
            return context.getString(R.string.follow_up_same_specialty_hint);
        }
        return context.getString(R.string.follow_up_choose_doctor);
    }

    static String formatVisitTypeLabel(Context context, String visitType, String specialty) {
        String label = "follow_up".equals(visitType)
                ? context.getString(R.string.visit_type_follow_up)
                : context.getString(R.string.visit_type_new);
        if (!isBlank(specialty)) {
            label += " | " + specialty;
        }
        return label;
    }

    static String formatHistoryDate(String rawDate) {
        if (isBlank(rawDate)) {
            return "Visit";
        }
        int separator = rawDate.indexOf('T');
        return separator > 0 ? rawDate.substring(0, separator) : rawDate;
    }

    static String buildHistorySummary(ConsultationHistoryResponse.Consultation consultation) {
        if (!isBlank(consultation.getSymptomsSummary())) {
            return consultation.getSymptomsSummary();
        }
        if (!isBlank(consultation.getSymptoms())) {
            return consultation.getSymptoms();
        }
        return "No symptom summary recorded.";
    }

    static String buildOutcomeSummary(ConsultationHistoryResponse.Consultation consultation) {
        if (!isBlank(consultation.getConclusionPreview())) {
            return consultation.getConclusionPreview();
        }
        if (!isBlank(consultation.getDiagnosis())) {
            return consultation.getDiagnosis();
        }
        return "Open for full prescription details.";
    }

    static String textOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class QueueLaunchArgs {
        final String visitType;
        final String followUpTokenId;
        final String previousDoctorId;
        final String previousDoctorName;
        final String previousDoctorSpecialty;
        final String seededSymptoms;
        final String previousVisitDate;

        QueueLaunchArgs(
                String visitType,
                String followUpTokenId,
                String previousDoctorId,
                String previousDoctorName,
                String previousDoctorSpecialty,
                String seededSymptoms,
                String previousVisitDate
        ) {
            this.visitType = isBlank(visitType) ? "new" : visitType;
            this.followUpTokenId = followUpTokenId;
            this.previousDoctorId = previousDoctorId;
            this.previousDoctorName = previousDoctorName;
            this.previousDoctorSpecialty = previousDoctorSpecialty;
            this.seededSymptoms = seededSymptoms;
            this.previousVisitDate = previousVisitDate;
        }

        boolean isFollowUp() {
            return "follow_up".equals(visitType);
        }
    }
}
