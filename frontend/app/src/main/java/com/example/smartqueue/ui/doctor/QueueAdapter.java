package com.example.smartqueue.ui.doctor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.QueueResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    private List<QueueResponse.QueueEntry> queueList = new ArrayList<>();
    private QueueActionListener listener;

    public interface QueueActionListener {
        void onPrescription(QueueResponse.QueueEntry entry);
        void onNoShow(QueueResponse.QueueEntry entry);
    }

    public void setQueueActionListener(QueueActionListener listener) {
        this.listener = listener;
    }

    public void setQueueList(List<QueueResponse.QueueEntry> list) {
        this.queueList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_patient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QueueResponse.QueueEntry entry = queueList.get(position);
        boolean immediateReview = entry.isImmediateReviewRequired()
                || "immediate_review".equals(entry.getRoutingLane());
        String status = entry.getStatus() != null ? entry.getStatus() : "waiting";

        holder.tvTokenNum.setText(immediateReview ? "ER" : "#" + entry.getTokenNumber());
        holder.tvPatientName.setText(textOrDefault(entry.getPatientName(), "Patient"));

        StringBuilder info = new StringBuilder();
        if (immediateReview) {
            info.append("Immediate review");
        } else {
            info.append("Position: ").append(entry.getPosition());
        }
        if (entry.getPatientAge() > 0) {
            info.append(" | Age ").append(entry.getPatientAge());
        }
        if (entry.getPatientPhone() != null && !entry.getPatientPhone().trim().isEmpty()) {
            info.append(" | ").append(entry.getPatientPhone().trim());
        }
        if (entry.getTriagePriorityClass() != null) {
            info.append(" | KTAS ").append(entry.getTriagePriorityClass());
        } else {
            info.append(" | Priority: ").append(textOrDefault(entry.getPriority(), "normal"));
        }
        if ("waiting".equals(status) && !entry.isNurseTriaged()) {
            info.append(" | Vitals pending");
        } else if ("waiting_doctor".equals(status) || entry.isNurseTriaged()) {
            info.append(" | Nurse triaged");
        }
        if (entry.isManualReviewRequired()) {
            info.append(" | Manual review");
        }
        holder.tvPatientInfo.setText(info.toString());
        if (immediateReview && "waiting_doctor".equals(status)) {
            holder.tvStatus.setText("IMMEDIATE");
        } else if ("waiting".equals(status) && !entry.isNurseTriaged()) {
            holder.tvStatus.setText("WAITING FOR VITALS");
        } else {
            holder.tvStatus.setText(formatStatus(status));
        }

        boolean canPrescribe = "called".equals(status) || "arrived".equals(status);
        holder.btnPrescription.setVisibility(canPrescribe ? View.VISIBLE : View.INVISIBLE);
        holder.btnPrescription.setEnabled(canPrescribe);
        holder.btnPrescription.setOnClickListener(v -> {
            if (canPrescribe && listener != null) listener.onPrescription(entry);
        });
        holder.btnNoShow.setOnClickListener(v -> {
            if (listener != null) listener.onNoShow(entry);
        });
    }

    @Override
    public int getItemCount() {
        return queueList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTokenNum, tvPatientName, tvPatientInfo, tvStatus;
        MaterialButton btnPrescription, btnNoShow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTokenNum = itemView.findViewById(R.id.tvTokenNum);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientInfo = itemView.findViewById(R.id.tvPatientInfo);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnPrescription = itemView.findViewById(R.id.btnQueuePrescription);
            btnNoShow = itemView.findViewById(R.id.btnQueueNoShow);
        }
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String formatStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "Waiting";
        }
        if ("waiting_doctor".equals(status)) {
            return "Waiting for doctor";
        }
        return status.substring(0, 1).toUpperCase() + status.substring(1);
    }
}
