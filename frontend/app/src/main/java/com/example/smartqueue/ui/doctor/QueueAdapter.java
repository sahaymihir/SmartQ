package com.example.smartqueue.ui.doctor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.QueueResponse;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    private List<QueueResponse.QueueEntry> queueList = new ArrayList<>();

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

        holder.tvTokenNum.setText(immediateReview ? "ER" : "#" + entry.getTokenNumber());
        holder.tvPatientName.setText(entry.getPatientName());

        StringBuilder info = new StringBuilder();
        if (immediateReview) {
            info.append("Immediate review");
        } else {
            info.append("Position: ").append(entry.getPosition());
        }
        if (entry.getTriagePriorityClass() != null) {
            info.append(" | KTAS ").append(entry.getTriagePriorityClass());
        } else {
            info.append(" | Priority: ").append(entry.getPriority());
        }
        if (entry.isManualReviewRequired()) {
            info.append(" | Manual review");
        }
        holder.tvPatientInfo.setText(info.toString());
        holder.tvStatus.setText(immediateReview && "waiting".equals(entry.getStatus())
                ? "IMMEDIATE"
                : entry.getStatus());
    }

    @Override
    public int getItemCount() {
        return queueList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTokenNum, tvPatientName, tvPatientInfo, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTokenNum = itemView.findViewById(R.id.tvTokenNum);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientInfo = itemView.findViewById(R.id.tvPatientInfo);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
