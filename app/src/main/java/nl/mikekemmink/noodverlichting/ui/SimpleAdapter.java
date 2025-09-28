package nl.mikekemmink.noodverlichting.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VH> {
    public interface OnClick { void onItemClick(String value); }
    private final List<String> data; private final OnClick cb;
    public SimpleAdapter(List<String> data, OnClick cb) { this.data = data; this.cb = cb; }
    static class VH extends RecyclerView.ViewHolder { TextView t; VH(View v){super(v); t=v.findViewById(android.R.id.text1);} }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vType){
        View v = LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_1, p, false); return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos){ String s=data.get(pos); h.t.setText(s); h.itemView.setOnClickListener(v->cb.onItemClick(s)); }
    @Override public int getItemCount(){ return data.size(); }
}