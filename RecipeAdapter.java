package com.example.pantryapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RecipeAdapter extends ListAdapter<RecipeMatch, RecipeAdapter.VH> {

    public RecipeAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<RecipeMatch> DIFF =
            new DiffUtil.ItemCallback<RecipeMatch>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecipeMatch a, @NonNull RecipeMatch b) {
                    return a.getRecipe().getId() == b.getRecipe().getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecipeMatch a, @NonNull RecipeMatch b) {
                    return a.getAvailableCount() == b.getAvailableCount();
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe_match, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RecipeMatch match = getItem(position);

        holder.name.setText(match.getRecipe().getName());
        holder.summary.setText(match.getSummary() + " (" + match.getMatchPercent() + "% match)");

        List<String> missing = match.getMissingIngredientNames();
        if (missing.isEmpty()) {
            holder.missing.setVisibility(View.GONE);
        } else {
            holder.missing.setVisibility(View.VISIBLE);
            holder.missing.setText("Missing: " + String.join(", ", missing));
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, summary, missing;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.recipeName);
            summary = itemView.findViewById(R.id.recipeSummary);
            missing = itemView.findViewById(R.id.missingIngredients);
        }
    }
}
