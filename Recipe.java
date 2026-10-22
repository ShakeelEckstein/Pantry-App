package com.example.pantryapp;

import java.util.ArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

    public class Recipe {
        private long id;
        private String name;
        private String steps;                 // one step per line ("\n" separated)
        private List<com.example.pantryapp.Ingredient> ingredients = new ArrayList<>();

        public Recipe() {}

        public Recipe(String name, String steps) {
            this.name = name;
            this.steps = steps;
        }

        public Recipe(long id, String name, String steps) {
            this.id = id;
            this.name = name;
            this.steps = steps;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSteps() { return steps; }
        public void setSteps(String steps) { this.steps = steps; }

        public List<com.example.pantryapp.Ingredient> getIngredients() { return ingredients; }
        public void setIngredients(List<com.example.pantryapp.Ingredient> ingredients) { this.ingredients = ingredients; }

        /** Steps split into an ordered list, handy for a RecyclerView. */
        public List<String> getStepList() {
            if (steps == null || steps.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(Arrays.asList(steps.split("\n")));
        }
    }
