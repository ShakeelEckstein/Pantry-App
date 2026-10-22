package com.example.pantryapp;

import java.util.Collections;
import java.util.List;

/** A recipe plus how well the current pantry covers it. */
public class RecipeMatch {

    private final Recipe recipe;
    private final int requiredCount;                    // non-staple ingredients needed
    private final int availableCount;                   // of those, how many are in the pantry
    private final List<String> missingIngredientNames;  // display names you still need

    public RecipeMatch(Recipe recipe, int requiredCount, int availableCount,
                       List<String> missingIngredientNames) {
        this.recipe = recipe;
        this.requiredCount = requiredCount;
        this.availableCount = availableCount;
        this.missingIngredientNames = missingIngredientNames == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(missingIngredientNames);
    }

    public Recipe getRecipe() { return recipe; }
    public int getRequiredCount() { return requiredCount; }
    public int getAvailableCount() { return availableCount; }
    public List<String> getMissingIngredientNames() { return missingIngredientNames; }

    /** 0.0 – 1.0 */
    public float getMatchRatio() {
        if (requiredCount == 0) return 1f;
        return (float) availableCount / requiredCount;
    }

    /** 0 – 100, for a progress bar or a percentage label. */
    public int getMatchPercent() {
        return Math.round(getMatchRatio() * 100f);
    }

    public boolean isCookable() {
        return missingIngredientNames.isEmpty();
    }

    /** "You have everything" or "7 of 9 ingredients" — for list rows. */
    public String getSummary() {
        if (isCookable()) return "You have everything";
        return availableCount + " of " + requiredCount + " ingredients";
    }
}