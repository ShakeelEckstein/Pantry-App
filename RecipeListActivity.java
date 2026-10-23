package com.example.pantryapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecipeListActivity extends AppCompatActivity {

    private RecipeDatabaseHelper db;
    private RecipeAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyText;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        db = RecipeDatabaseHelper.getInstance(this);
        recycler = findViewById(R.id.recipeRecycler);
        emptyText = findViewById(R.id.emptyRecipesText);

        adapter = new RecipeAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadRecipes();
    }

    private void loadRecipes() {
        io.execute(() -> {
            // ▼▼▼ HERE IS WHERE YOU PASTE IT ▼▼▼
            List<RecipeMatch> matches = db.getRecipeMatches();
            // ▲▲▲ END OF PASTE ▲▲▲

            runOnUiThread(() -> {
                adapter.submitList(matches);
                if (matches.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    emptyText.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}