package com.example.femora;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NearbyHelpAdapter extends RecyclerView.Adapter<NearbyHelpAdapter.ViewHolder> {

    private Context context;
    private List<NearbyHelp> helpList;

    public NearbyHelpAdapter(Context context, List<NearbyHelp> helpList) {
        this.context = context;
        this.helpList = helpList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_nearby_help, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NearbyHelp item = helpList.get(position);
        holder.tvPlaceName.setText(item.getName());
        holder.tvAddress.setText(item.getAddress());
        holder.tvDistance.setText(item.getDistance());
        holder.tvPhone.setText(item.getPhone());
        holder.ivIcon.setImageResource(item.getIconRes());

        holder.btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + item.getPhone()));
            context.startActivity(intent);
        });

        holder.btnNavigate.setOnClickListener(v -> {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + item.getName() + " " + item.getAddress());
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            context.startActivity(mapIntent);
        });
    }

    @Override
    public int getItemCount() {
        return helpList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlaceName, tvAddress, tvDistance, tvPhone;
        ImageView ivIcon;
        Button btnCall, btnNavigate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlaceName = itemView.findViewById(R.id.tvPlaceName);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            btnCall = itemView.findViewById(R.id.btnCall);
            btnNavigate = itemView.findViewById(R.id.btnNavigate);
        }
    }
}