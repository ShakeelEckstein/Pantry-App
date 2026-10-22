package com.example.pantryapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schema (v2):

 *   recipes             (id, name, steps)
 *   recipe_ingredients  (id, recipe_id, ingredient_name, canonical_name, quantity, unit)
 *   pantry_items        (id, name, canonical_name, quantity, unit, category, expiry_date)
 *   ingredient_aliases  (alias, canonical)

 * Matching: recipe_ingredients.canonical_name is compared with
 * pantry_items.canonical_name. Canonical names are produced by
 * resolveCanonical(), which lowercases, strips descriptors after a comma,
 * singularise and then applies the alias table.

 * Usage:
 *   RecipeDatabaseHelper db = RecipeDatabaseHelper.getInstance(context);
 *   List<Recipe> all = db.getAllRecipes();
 *   List<RecipeMatch> matches = db.getRecipeMatches();   // ranked by pantry coverage
 *   List<RecipeMatch> cookable = db.getCookableRecipes(); // you have everything
 */
public class RecipeDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recipes.db";
    private static final int DATABASE_VERSION = 2;

    // ---------------------------------------------------------------- recipes
    public static final String TABLE_RECIPES = "recipes";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_STEPS = "steps";

    // ------------------------------------------------------- recipe_ingredients
    public static final String TABLE_INGREDIENTS = "recipe_ingredients";
    public static final String COL_ING_ID = "id";
    public static final String COL_ING_RECIPE_ID = "recipe_id";
    public static final String COL_ING_NAME = "ingredient_name";
    public static final String COL_ING_CANONICAL = "canonical_name";
    public static final String COL_ING_QUANTITY = "quantity";
    public static final String COL_ING_UNIT = "unit";

    // ------------------------------------------------------------ pantry_items
    public static final String TABLE_PANTRY = "pantry_items";
    public static final String COL_PANTRY_ID = "id";
    public static final String COL_PANTRY_NAME = "name";
    public static final String COL_PANTRY_CANONICAL = "canonical_name";
    public static final String COL_PANTRY_QUANTITY = "quantity";
    public static final String COL_PANTRY_UNIT = "unit";
    public static final String COL_PANTRY_CATEGORY = "category";
    public static final String COL_PANTRY_EXPIRY = "expiry_date";   // epoch millis, 0 = none

    // ------------------------------------------------------ ingredient_aliases
    public static final String TABLE_ALIASES = "ingredient_aliases";
    public static final String COL_ALIAS_ALIAS = "alias";           // already normalised
    public static final String COL_ALIAS_CANONICAL = "canonical";

    /** Assumed to be in every kitchen; never counted as a missing ingredient. */
    public static final Set<String> STAPLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("salt", "water", "pepper", "black pepper")));

    // ------------------------------------------------------------- DDL
    private static final String CREATE_RECIPES =
            "CREATE TABLE " + TABLE_RECIPES + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NAME + " TEXT NOT NULL, " +
                    COL_STEPS + " TEXT" +
                    ");";

    private static final String CREATE_INGREDIENTS =
            "CREATE TABLE " + TABLE_INGREDIENTS + " (" +
                    COL_ING_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_ING_RECIPE_ID + " INTEGER NOT NULL, " +
                    COL_ING_NAME + " TEXT NOT NULL, " +
                    COL_ING_CANONICAL + " TEXT NOT NULL, " +
                    COL_ING_QUANTITY + " REAL, " +
                    COL_ING_UNIT + " TEXT, " +
                    "FOREIGN KEY(" + COL_ING_RECIPE_ID + ") REFERENCES " +
                    TABLE_RECIPES + "(" + COL_ID + ") ON DELETE CASCADE" +
                    ");";

    private static final String CREATE_PANTRY =
            "CREATE TABLE " + TABLE_PANTRY + " (" +
                    COL_PANTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PANTRY_NAME + " TEXT NOT NULL, " +
                    COL_PANTRY_CANONICAL + " TEXT NOT NULL, " +
                    COL_PANTRY_QUANTITY + " REAL, " +
                    COL_PANTRY_UNIT + " TEXT, " +
                    COL_PANTRY_CATEGORY + " TEXT, " +
                    COL_PANTRY_EXPIRY + " INTEGER" +
                    ");";

    private static final String CREATE_ALIASES =
            "CREATE TABLE " + TABLE_ALIASES + " (" +
                    COL_ALIAS_ALIAS + " TEXT PRIMARY KEY, " +
                    COL_ALIAS_CANONICAL + " TEXT NOT NULL" +
                    ");";

    private static final String CREATE_INGREDIENTS_INDEX =
            "CREATE INDEX idx_ingredients_recipe ON " + TABLE_INGREDIENTS +
                    "(" + COL_ING_RECIPE_ID + ");";

    private static final String CREATE_INGREDIENTS_CANONICAL_INDEX =
            "CREATE INDEX idx_ingredients_canonical ON " + TABLE_INGREDIENTS +
                    "(" + COL_ING_CANONICAL + ");";

    private static final String CREATE_PANTRY_INDEX =
            "CREATE INDEX idx_pantry_canonical ON " + TABLE_PANTRY +
                    "(" + COL_PANTRY_CANONICAL + ");";

    // ------------------------------------------------------------- singleton
    private static RecipeDatabaseHelper instance;

    public static synchronized RecipeDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new RecipeDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private RecipeDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);   // makes ON DELETE CASCADE work
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_RECIPES);
        db.execSQL(CREATE_INGREDIENTS);
        db.execSQL(CREATE_PANTRY);
        db.execSQL(CREATE_ALIASES);
        db.execSQL(CREATE_INGREDIENTS_INDEX);
        db.execSQL(CREATE_INGREDIENTS_CANONICAL_INDEX);
        db.execSQL(CREATE_PANTRY_INDEX);
        seedAliases(db);
        seedRecipes(db);   // onCreate already runs inside a transaction
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // New lookup table first, so the backfill below can use it.
            db.execSQL(CREATE_ALIASES);
            seedAliases(db);

            // Add + populate canonical_name on existing recipe ingredients.
            db.execSQL("ALTER TABLE " + TABLE_INGREDIENTS + " ADD COLUMN " +
                    COL_ING_CANONICAL + " TEXT NOT NULL DEFAULT ''");
            backfillCanonicalNames(db);
            db.execSQL(CREATE_INGREDIENTS_CANONICAL_INDEX);

            // Pantry is brand new, so there is nothing to migrate.
            db.execSQL(CREATE_PANTRY);
            db.execSQL(CREATE_PANTRY_INDEX);
        }
    }

    private void backfillCanonicalNames(SQLiteDatabase db) {
        // Read everything first; mutating a table while a cursor is open on it is fragile.
        List<long[]> ids = new ArrayList<>();
        List<String> canonicals = new ArrayList<>();
        try (Cursor c = db.query(TABLE_INGREDIENTS,
                new String[]{COL_ING_ID, COL_ING_NAME}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                ids.add(new long[]{c.getLong(0)});
                canonicals.add(resolveCanonical(db, c.getString(1)));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            ContentValues cv = new ContentValues();
            cv.put(COL_ING_CANONICAL, canonicals.get(i));
            db.update(TABLE_INGREDIENTS, cv, COL_ING_ID + "=?",
                    new String[]{String.valueOf(ids.get(i)[0])});
        }
    }

    // ------------------------------------------------------------------
    // Canonicalisation (the matching key)
    // ------------------------------------------------------------------

    /**
     * Turns free text into a matching key:
     *   "Onion, diced"        -> "onion"
     *   "Tinned chopped tomatoes" -> "tinned chopped tomato"  (alias then -> "tomato")
     *   "Bay leaves"          -> "bay leaf"
     *   "Chocolate chips"     -> "chocolate chip"
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).trim();

        int comma = s.indexOf(',');
        if (comma >= 0) s = s.substring(0, comma);

        s = s.replaceAll("\\(.*?\\)", " ");          // drop "(optional)" etc.
        s = s.replaceAll("[^a-z0-9 ]", " ");         // drop punctuation
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return s;

        // Crude singularisation of the last word.
        if (s.endsWith("aves")) {                    // leaves -> leaf, loaves -> loaf
            s = s.substring(0, s.length() - 4) + "af";
        } else if (s.endsWith("oes")) {              // tomatoes -> tomato
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("ies") && s.length() > 4) {   // berries -> berry
            s = s.substring(0, s.length() - 3) + "y";
        } else if (s.endsWith("s") && s.length() > 3
                && !s.endsWith("ss") && !s.endsWith("us") && !s.endsWith("is")) {
            s = s.substring(0, s.length() - 1);      // onions -> onion, eggs -> egg
        }
        return s;
    }

    /** Normalises then applies the alias table. Safe to call from any thread. */
    public String resolveCanonical(String raw) {
        return resolveCanonical(getReadableDatabase(), raw);
    }

    private String resolveCanonical(SQLiteDatabase db, String raw) {
        String norm = normalize(raw);
        if (norm.isEmpty()) return norm;
        try (Cursor c = db.query(TABLE_ALIASES, new String[]{COL_ALIAS_CANONICAL},
                COL_ALIAS_ALIAS + "=?", new String[]{norm}, null, null, null)) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return norm;
    }

    // ------------------------------------------------------------------
    // Recipe CRUD
    // ------------------------------------------------------------------

    /** Inserts the recipe and its ingredients atomically. Returns the new recipe id, or -1. */
    public long insertRecipe(Recipe recipe) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long recipeId = insertRecipeRow(db, recipe.getName(), recipe.getSteps());
            if (recipeId == -1) return -1;
            for (Ingredient ing : recipe.getIngredients()) {
                insertIngredientRow(db, recipeId, ing);
            }
            db.setTransactionSuccessful();
            recipe.setId(recipeId);
            return recipeId;
        } finally {
            db.endTransaction();
        }
    }

    /** Updates name/steps and replaces all ingredients with those on the recipe object. */
    public boolean updateRecipe(Recipe recipe) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_NAME, recipe.getName());
            cv.put(COL_STEPS, recipe.getSteps());
            int rows = db.update(TABLE_RECIPES, cv, COL_ID + "=?",
                    new String[]{String.valueOf(recipe.getId())});
            if (rows == 0) return false;

            db.delete(TABLE_INGREDIENTS, COL_ING_RECIPE_ID + "=?",
                    new String[]{String.valueOf(recipe.getId())});
            for (Ingredient ing : recipe.getIngredients()) {
                insertIngredientRow(db, recipe.getId(), ing);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** Deletes the recipe; its ingredients are removed by the foreign key cascade. */
    public int deleteRecipe(long id) {
        return getWritableDatabase().delete(TABLE_RECIPES, COL_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    /** Returns one recipe including its ingredients, or null if not found. */
    public Recipe getRecipe(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_RECIPES, null, COL_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            Recipe recipe = recipeFromCursor(c);
            recipe.setIngredients(getIngredientsForRecipe(id));
            return recipe;
        }
    }

    /** All recipes (name + steps only; ingredients are loaded lazily). */
    public List<Recipe> getAllRecipes() {
        List<Recipe> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_RECIPES, null, null, null,
                null, null, COL_NAME + " COLLATE NOCASE ASC")) {
            while (c.moveToNext()) list.add(recipeFromCursor(c));
        }
        return list;
    }

    /** Searches recipe names and ingredient names. */
    public List<Recipe> searchRecipes(String query) {
        List<Recipe> list = new ArrayList<>();
        String like = "%" + query.trim() + "%";
        String sql = "SELECT DISTINCT r." + COL_ID + ", r." + COL_NAME + ", r." + COL_STEPS +
                " FROM " + TABLE_RECIPES + " r" +
                " LEFT JOIN " + TABLE_INGREDIENTS + " i ON i." + COL_ING_RECIPE_ID + " = r." + COL_ID +
                " WHERE r." + COL_NAME + " LIKE ? OR i." + COL_ING_NAME + " LIKE ?" +
                " ORDER BY r." + COL_NAME + " COLLATE NOCASE ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{like, like})) {
            while (c.moveToNext()) list.add(recipeFromCursor(c));
        }
        return list;
    }

    public int getRecipeCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECIPES, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ------------------------------------------------------------------
    // Ingredient queries
    // ------------------------------------------------------------------

    public List<Ingredient> getIngredientsForRecipe(long recipeId) {
        List<Ingredient> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_INGREDIENTS, null,
                COL_ING_RECIPE_ID + "=?", new String[]{String.valueOf(recipeId)},
                null, null, COL_ING_ID + " ASC")) {
            while (c.moveToNext()) list.add(ingredientFromCursor(c));
        }
        return list;
    }

    private Map<Long, List<Ingredient>> getAllIngredientsGroupedByRecipe() {
        Map<Long, List<Ingredient>> map = new HashMap<>();
        try (Cursor c = getReadableDatabase().query(TABLE_INGREDIENTS, null,
                null, null, null, null, COL_ING_ID + " ASC")) {
            while (c.moveToNext()) {
                Ingredient ing = ingredientFromCursor(c);
                List<Ingredient> bucket = map.get(ing.getRecipeId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    map.put(ing.getRecipeId(), bucket);
                }
                bucket.add(ing);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // Pantry CRUD
    // ------------------------------------------------------------------

    /** Adds a pantry item. canonical_name is derived from the name automatically. */
    public long insertPantryItem(PantryItem item) {
        SQLiteDatabase db = getWritableDatabase();
        String canonical = item.getCanonicalName();
        if (canonical == null || canonical.isEmpty()) {
            canonical = resolveCanonical(db, item.getName());
        }
        ContentValues cv = new ContentValues();
        cv.put(COL_PANTRY_NAME, item.getName());
        cv.put(COL_PANTRY_CANONICAL, canonical);
        cv.put(COL_PANTRY_QUANTITY, item.getQuantity());
        cv.put(COL_PANTRY_UNIT, item.getUnit());
        cv.put(COL_PANTRY_CATEGORY, item.getCategory());
        cv.put(COL_PANTRY_EXPIRY, item.getExpiryDate());
        long id = db.insert(TABLE_PANTRY, null, cv);
        item.setId(id);
        item.setCanonicalName(canonical);
        return id;
    }

    public boolean updatePantryItem(PantryItem item) {
        SQLiteDatabase db = getWritableDatabase();
        String canonical = item.getCanonicalName();
        if (canonical == null || canonical.isEmpty()) {
            canonical = resolveCanonical(db, item.getName());
        }
        ContentValues cv = new ContentValues();
        cv.put(COL_PANTRY_NAME, item.getName());
        cv.put(COL_PANTRY_CANONICAL, canonical);
        cv.put(COL_PANTRY_QUANTITY, item.getQuantity());
        cv.put(COL_PANTRY_UNIT, item.getUnit());
        cv.put(COL_PANTRY_CATEGORY, item.getCategory());
        cv.put(COL_PANTRY_EXPIRY, item.getExpiryDate());
        int rows = db.update(TABLE_PANTRY, cv, COL_PANTRY_ID + "=?",
                new String[]{String.valueOf(item.getId())});
        item.setCanonicalName(canonical);
        return rows > 0;
    }

    public int deletePantryItem(long id) {
        return getWritableDatabase().delete(TABLE_PANTRY, COL_PANTRY_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public void clearPantry() {
        getWritableDatabase().delete(TABLE_PANTRY, null, null);
    }

    public List<PantryItem> getAllPantryItems() {
        List<PantryItem> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_PANTRY, null, null, null,
                null, null, COL_PANTRY_CANONICAL + " ASC")) {
            while (c.moveToNext()) list.add(pantryItemFromCursor(c));
        }
        return list;
    }

    /** Pantry items with an expiry date at or before the given timestamp. */
    public List<PantryItem> getPantryItemsExpiringBefore(long epochMillis) {
        List<PantryItem> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_PANTRY, null,
                COL_PANTRY_EXPIRY + " > 0 AND " + COL_PANTRY_EXPIRY + " <= ?",
                new String[]{String.valueOf(epochMillis)},
                null, null, COL_PANTRY_EXPIRY + " ASC")) {
            while (c.moveToNext()) list.add(pantryItemFromCursor(c));
        }
        return list;
    }

    public int getPantryCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_PANTRY, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Canonical names currently in the pantry — used by the matching engine. */
    public Set<String> getPantryCanonicalNames() {
        Set<String> set = new HashSet<>();
        try (Cursor c = getReadableDatabase().query(TABLE_PANTRY,
                new String[]{COL_PANTRY_CANONICAL}, null, null, null, null, null)) {
            while (c.moveToNext()) set.add(c.getString(0));
        }
        return set;
    }

    // ------------------------------------------------------------------
    // Matching engine  ← the actual "what can I cook?" feature
    // ------------------------------------------------------------------

    /**
     * Every recipe that shares at least one non-staple ingredient with the
     * pantry, best match first. Staples (salt, water, pepper) are ignored.
     */
    public List<RecipeMatch> getRecipeMatches() {
        return getRecipeMatches(false);
    }

    /**
     * @param onlyCookable when true, returns only recipes where every
     *                     non-staple ingredient is in the pantry.
     */
    public List<RecipeMatch> getRecipeMatches(boolean onlyCookable) {
        Set<String> pantry = getPantryCanonicalNames();
        Map<Long, List<Ingredient>> byRecipe = getAllIngredientsGroupedByRecipe();
        List<RecipeMatch> matches = new ArrayList<>();

        for (Recipe recipe : getAllRecipes()) {
            List<Ingredient> ingredients = byRecipe.get(recipe.getId());
            if (ingredients == null) ingredients = Collections.emptyList();

            int required = 0;
            List<String> missing = new ArrayList<>();
            for (Ingredient ing : ingredients) {
                String canonical = ing.getCanonicalName();
                if (canonical == null || canonical.isEmpty()) continue;
                if (STAPLES.contains(canonical)) continue;   // assumed to be in the kitchen
                required++;
                if (!pantry.contains(canonical)) missing.add(ing.getName());
            }

            int available = required - missing.size();
            if (required > 0 && available == 0) continue;    // nothing in common, don't suggest
            RecipeMatch match = new RecipeMatch(recipe, required, available, missing);
            if (onlyCookable && !match.isCookable()) continue;
            matches.add(match);
        }

        matches.sort((a, b) -> {
            int cmp = Float.compare(b.getMatchRatio(), a.getMatchRatio());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getAvailableCount(), a.getAvailableCount());
            if (cmp != 0) return cmp;
            return a.getRecipe().getName().compareToIgnoreCase(b.getRecipe().getName());
        });
        return matches;
    }

    /** Recipes you can cook right now with no extra shopping. */
    public List<RecipeMatch> getCookableRecipes() {
        return getRecipeMatches(true);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private Recipe recipeFromCursor(Cursor c) {
        return new Recipe(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_STEPS)));
    }

    private Ingredient ingredientFromCursor(Cursor c) {
        return new Ingredient(
                c.getLong(c.getColumnIndexOrThrow(COL_ING_ID)),
                c.getLong(c.getColumnIndexOrThrow(COL_ING_RECIPE_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_ING_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_ING_CANONICAL)),
                c.getDouble(c.getColumnIndexOrThrow(COL_ING_QUANTITY)),
                c.getString(c.getColumnIndexOrThrow(COL_ING_UNIT)));
    }

    private PantryItem pantryItemFromCursor(Cursor c) {
        return new PantryItem(
                c.getLong(c.getColumnIndexOrThrow(COL_PANTRY_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_PANTRY_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_PANTRY_CANONICAL)),
                c.getDouble(c.getColumnIndexOrThrow(COL_PANTRY_QUANTITY)),
                c.getString(c.getColumnIndexOrThrow(COL_PANTRY_UNIT)),
                c.getString(c.getColumnIndexOrThrow(COL_PANTRY_CATEGORY)),
                c.getLong(c.getColumnIndexOrThrow(COL_PANTRY_EXPIRY)));
    }

    private long insertRecipeRow(SQLiteDatabase db, String name, String steps) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, name);
        cv.put(COL_STEPS, steps);
        return db.insert(TABLE_RECIPES, null, cv);
    }

    private void insertIngredientRow(SQLiteDatabase db, long recipeId, Ingredient ing) {
        String canonical = ing.getCanonicalName();
        if (canonical == null || canonical.isEmpty()) {
            canonical = resolveCanonical(db, ing.getName());   // user-added recipes
        }
        ContentValues cv = new ContentValues();
        cv.put(COL_ING_RECIPE_ID, recipeId);
        cv.put(COL_ING_NAME, ing.getName());
        cv.put(COL_ING_CANONICAL, canonical);
        cv.put(COL_ING_QUANTITY, ing.getQuantity());
        cv.put(COL_ING_UNIT, ing.getUnit());
        db.insert(TABLE_INGREDIENTS, null, cv);
    }

    // ------------------------------------------------------------------
    // Seed data
    // ------------------------------------------------------------------

    private static Ingredient ing(String name, String canonical, double qty, String unit) {
        return new Ingredient(name, canonical, qty, unit);
    }

    private static String steps(String... s) {
        return String.join("\n", s);
    }

    private void seed(SQLiteDatabase db, String name, String steps, Ingredient... ingredients) {
        long recipeId = insertRecipeRow(db, name, steps);
        for (Ingredient i : ingredients) insertIngredientRow(db, recipeId, i);
    }

    private void alias(SQLiteDatabase db, String alias, String canonical) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ALIAS_ALIAS, alias);       // keys are stored already normalised
        cv.put(COL_ALIAS_CANONICAL, canonical);
        db.insertWithOnConflict(TABLE_ALIASES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void seedAliases(SQLiteDatabase db) {
        // tomato products
        alias(db, "tinned tomato", "tomato");
        alias(db, "canned tomato", "tomato");
        alias(db, "chopped tomato", "tomato");
        alias(db, "plum tomato", "tomato");
        alias(db, "tomato passata", "tomato");
        alias(db, "passata", "tomato");
        // beef
        alias(db, "mince", "beef mince");
        alias(db, "ground beef", "beef mince");
        alias(db, "stewing beef", "beef");
        // chicken
        alias(db, "chicken breast", "chicken");
        alias(db, "chicken thigh", "chicken");
        alias(db, "chicken thigh fillet", "chicken");
        alias(db, "chicken fillet", "chicken");
        // peppers
        alias(db, "red pepper", "bell pepper");
        alias(db, "green pepper", "bell pepper");
        alias(db, "yellow pepper", "bell pepper");
        alias(db, "sweet pepper", "bell pepper");
        // lettuce
        alias(db, "cos lettuce", "lettuce");
        alias(db, "romaine lettuce", "lettuce");
        // flour
        alias(db, "plain flour", "flour");
        alias(db, "all purpose flour", "flour");
        alias(db, "cake flour", "flour");
        alias(db, "bread flour", "flour");
        alias(db, "self raising flour", "flour");
        // sugar
        alias(db, "caster sugar", "sugar");
        alias(db, "white sugar", "sugar");
        alias(db, "granulated sugar", "sugar");
        // dairy
        alias(db, "double cream", "cream");
        alias(db, "heavy cream", "cream");
        // onion family / garlic
        alias(db, "spring onion", "onion");
        alias(db, "shallot", "onion");
        alias(db, "garlic clove", "garlic");
        // olives, lentils, bread
        alias(db, "kalamata olive", "olive");
        alias(db, "black olive", "olive");
        alias(db, "red lentil", "lentil");
        alias(db, "green lentil", "lentil");
        alias(db, "sourdough", "bread");
        alias(db, "sourdough bread", "bread");
        alias(db, "white bread", "bread");
        alias(db, "brown bread", "bread");
        // stock
        alias(db, "chicken stock", "stock");
        alias(db, "beef stock", "stock");
        alias(db, "vegetable stock", "stock");
        alias(db, "stock cube", "stock");
        alias(db, "bouillon", "stock");
        // herbs
        alias(db, "coriander leaf", "coriander");
        alias(db, "fresh coriander", "coriander");
    }

    private void seedRecipes(SQLiteDatabase db) {

        seed(db, "Spaghetti Bolognese",
                steps("Heat oil in a large pan and soften the onion, carrot and garlic for 5 minutes.",
                        "Add the mince and brown well, breaking up any lumps.",
                        "Stir in the tomato paste, chopped tomatoes and oregano. Season.",
                        "Simmer on low for 30-40 minutes, stirring occasionally.",
                        "Cook the spaghetti according to the packet, drain and serve topped with the sauce."),
                ing("Spaghetti", "spaghetti", 400, "g"),
                ing("Beef mince", "beef mince", 500, "g"),
                ing("Onion, diced", "onion", 1, "pcs"),
                ing("Garlic cloves, minced", "garlic", 2, "pcs"),
                ing("Carrot, grated", "carrot", 1, "pcs"),
                ing("Tinned chopped tomatoes", "tomato", 400, "g"),
                ing("Tomato paste", "tomato paste", 2, "tbsp"),
                ing("Dried oregano", "oregano", 1, "tsp"),
                ing("Olive oil", "olive oil", 2, "tbsp"),
                ing("Salt", "salt", 1, "tsp"));

        seed(db, "Chicken Curry",
                steps("Fry the onion in oil until golden, then add the garlic and ginger for 1 minute.",
                        "Stir in the curry powder and turmeric and cook for 30 seconds.",
                        "Add the chicken and brown on all sides.",
                        "Pour in the tomatoes and coconut milk. Simmer for 25 minutes until tender.",
                        "Season, scatter with coriander and serve with rice."),
                ing("Chicken thigh fillets, cubed", "chicken", 600, "g"),
                ing("Onion, chopped", "onion", 1, "pcs"),
                ing("Garlic cloves, minced", "garlic", 3, "pcs"),
                ing("Fresh ginger, grated", "ginger", 1, "tbsp"),
                ing("Curry powder", "curry powder", 2, "tbsp"),
                ing("Turmeric", "turmeric", 1, "tsp"),
                ing("Tinned chopped tomatoes", "tomato", 400, "g"),
                ing("Coconut milk", "coconut milk", 200, "ml"),
                ing("Vegetable oil", "vegetable oil", 2, "tbsp"),
                ing("Fresh coriander", "coriander", 1, "handful"));

        seed(db, "Fluffy Pancakes",
                steps("Whisk the flour, sugar, baking powder and salt in a bowl.",
                        "In another bowl, mix the milk, egg and melted butter.",
                        "Pour the wet mixture into the dry and stir until just combined (lumps are fine).",
                        "Cook 1/4 cup scoops in a buttered pan over medium heat until bubbles form, then flip.",
                        "Serve with syrup, fruit or bacon."),
                ing("Cake flour", "flour", 200, "g"),
                ing("Sugar", "sugar", 2, "tbsp"),
                ing("Baking powder", "baking powder", 2, "tsp"),
                ing("Salt", "salt", 0.5, "tsp"),
                ing("Milk", "milk", 250, "ml"),
                ing("Egg", "egg", 1, "pcs"),
                ing("Butter, melted", "butter", 3, "tbsp"));

        seed(db, "Greek Salad",
                steps("Combine the tomatoes, cucumber, onion and olives in a bowl.",
                        "Whisk the olive oil, vinegar and oregano, then pour over the salad.",
                        "Top with feta and serve immediately."),
                ing("Ripe tomatoes, chunked", "tomato", 3, "pcs"),
                ing("Cucumber, chunked", "cucumber", 1, "pcs"),
                ing("Red onion, sliced", "onion", 0.5, "pcs"),
                ing("Feta cheese", "feta", 100, "g"),
                ing("Kalamata olives", "olive", 0.5, "cup"),
                ing("Olive oil", "olive oil", 3, "tbsp"),
                ing("Red wine vinegar", "vinegar", 1, "tbsp"),
                ing("Dried oregano", "oregano", 1, "tsp"));

        seed(db, "Beef Tacos",
                steps("Brown the mince in a pan over medium-high heat.",
                        "Add the paprika, cumin, garlic powder, salt and 3 tbsp water. Simmer for 5 minutes.",
                        "Warm the taco shells in the oven for 3 minutes.",
                        "Fill with the beef and top with lettuce, tomato, cheese, sour cream and salsa."),
                ing("Beef mince", "beef mince", 500, "g"),
                ing("Paprika", "paprika", 1, "tbsp"),
                ing("Ground cumin", "cumin", 1, "tsp"),
                ing("Garlic powder", "garlic powder", 1, "tsp"),
                ing("Taco shells", "taco shell", 8, "pcs"),
                ing("Shredded lettuce", "lettuce", 1, "cup"),
                ing("Tomatoes, diced", "tomato", 2, "pcs"),
                ing("Grated cheddar", "cheddar", 1, "cup"),
                ing("Sour cream", "sour cream", 0.5, "cup"),
                ing("Salsa", "salsa", 0.5, "cup"));

        seed(db, "Vegetable Stir-Fry",
                steps("Mix the soy sauce, honey and sesame oil in a small bowl.",
                        "Heat the vegetable oil in a wok until very hot.",
                        "Stir-fry the carrot and broccoli for 3 minutes, then add the pepper, mange tout and garlic for 2 more.",
                        "Add the sauce, toss for 1 minute and serve with rice or noodles."),
                ing("Broccoli head, in florets", "broccoli", 1, "pcs"),
                ing("Red pepper, sliced", "bell pepper", 1, "pcs"),
                ing("Carrot, julienned", "carrot", 1, "pcs"),
                ing("Mange tout", "mange tout", 100, "g"),
                ing("Garlic cloves, minced", "garlic", 2, "pcs"),
                ing("Soy sauce", "soy sauce", 3, "tbsp"),
                ing("Honey", "honey", 1, "tbsp"),
                ing("Sesame oil", "sesame oil", 1, "tsp"),
                ing("Vegetable oil", "vegetable oil", 2, "tbsp"));

        seed(db, "Margherita Pizza",
                steps("Mix the flour, yeast and salt. Add the water and oil and knead for 8 minutes.",
                        "Cover and let rise for 1 hour until doubled.",
                        "Preheat the oven to its highest setting (about 250°C) with a baking tray inside.",
                        "Stretch the dough, spread with passata and top with mozzarella.",
                        "Bake for 10-12 minutes until bubbling. Add basil and serve."),
                ing("Bread flour", "flour", 300, "g"),
                ing("Instant yeast", "yeast", 1, "tsp"),
                ing("Salt", "salt", 1, "tsp"),
                ing("Warm water", "water", 200, "ml"),
                ing("Olive oil", "olive oil", 1, "tbsp"),
                ing("Tomato passata", "tomato", 120, "ml"),
                ing("Mozzarella, torn", "mozzarella", 200, "g"),
                ing("Fresh basil leaves", "basil", 1, "handful"));

        seed(db, "Chocolate Chip Cookies",
                steps("Preheat the oven to 190°C.",
                        "Cream the butter and sugars until fluffy, then beat in the eggs and vanilla.",
                        "Mix in the flour, baking soda and salt, then fold in the chocolate chips.",
                        "Drop tablespoons of dough onto lined baking trays.",
                        "Bake for 9-12 minutes until golden. Cool on the tray for 5 minutes."),
                ing("Butter, softened", "butter", 225, "g"),
                ing("White sugar", "sugar", 0.75, "cup"),
                ing("Brown sugar", "brown sugar", 0.75, "cup"),
                ing("Eggs", "egg", 2, "pcs"),
                ing("Vanilla extract", "vanilla", 2, "tsp"),
                ing("Flour", "flour", 2.25, "cup"),
                ing("Baking soda", "baking soda", 1, "tsp"),
                ing("Salt", "salt", 1, "tsp"),
                ing("Chocolate chips", "chocolate chip", 2, "cup"));

        seed(db, "Creamy Tomato Soup",
                steps("Melt the butter and soften the onion and garlic for 5 minutes.",
                        "Add the tomatoes, stock and sugar. Simmer for 20 minutes.",
                        "Blend until smooth with a stick blender.",
                        "Stir in the cream, season and heat gently before serving."),
                ing("Butter", "butter", 2, "tbsp"),
                ing("Onion, chopped", "onion", 1, "pcs"),
                ing("Garlic cloves", "garlic", 2, "pcs"),
                ing("Tinned tomatoes", "tomato", 800, "g"),
                ing("Vegetable stock", "stock", 500, "ml"),
                ing("Cream", "cream", 0.5, "cup"),
                ing("Sugar", "sugar", 1, "tsp"),
                ing("Salt", "salt", 1, "tsp"));

        seed(db, "Ultimate Grilled Cheese",
                steps("Butter the outer sides of both bread slices.",
                        "Fill with the cheeses between the unbuttered sides.",
                        "Toast in a pan over medium-low heat for 3-4 minutes per side until golden."),
                ing("White bread slices", "bread", 2, "pcs"),
                ing("Butter, softened", "butter", 2, "tbsp"),
                ing("Grated cheddar", "cheddar", 0.5, "cup"),
                ing("Grated mozzarella", "mozzarella", 0.25, "cup"));

        seed(db, "Chicken Caesar Salad",
                steps("Season the chicken and cook in oil for 5-6 minutes per side. Rest, then slice.",
                        "Toss the lettuce with the dressing.",
                        "Top with the chicken, croutons and parmesan."),
                ing("Chicken breasts", "chicken", 2, "pcs"),
                ing("Cos lettuce, chopped", "lettuce", 1, "pcs"),
                ing("Parmesan shavings", "parmesan", 0.5, "cup"),
                ing("Croutons", "crouton", 1, "cup"),
                ing("Caesar dressing", "caesar dressing", 80, "ml"),
                ing("Olive oil", "olive oil", 1, "tbsp"));

        seed(db, "Banana Bread",
                steps("Preheat the oven to 175°C and grease a loaf tin.",
                        "Mash the bananas and stir in the butter, sugar, egg and vanilla.",
                        "Add the baking soda, salt and flour and mix until just combined.",
                        "Pour into the tin and bake for 55-60 minutes until a skewer comes out clean."),
                ing("Very ripe bananas", "banana", 3, "pcs"),
                ing("Butter, melted", "butter", 80, "ml"),
                ing("Sugar", "sugar", 0.75, "cup"),
                ing("Egg", "egg", 1, "pcs"),
                ing("Vanilla extract", "vanilla", 1, "tsp"),
                ing("Baking soda", "baking soda", 1, "tsp"),
                ing("Salt", "salt", 1, "pinch"),
                ing("Flour", "flour", 1.5, "cup"));

        seed(db, "Bobotie",
                steps("Preheat the oven to 180°C.",
                        "Fry the onions, add the mince and curry powder and cook until browned.",
                        "Squeeze the bread (keep the milk), then mash it into the mince with jam, chutney, raisins and vinegar.",
                        "Spoon into a baking dish and press down. Top with bay leaves.",
                        "Whisk the eggs with the reserved milk, pour over and bake 30-35 minutes until set."),
                ing("Beef mince", "beef mince", 750, "g"),
                ing("Onions, chopped", "onion", 2, "pcs"),
                ing("Bread slices", "bread", 2, "pcs"),
                ing("Milk", "milk", 250, "ml"),
                ing("Curry powder", "curry powder", 2, "tbsp"),
                ing("Apricot jam", "jam", 1, "tbsp"),
                ing("Chutney", "chutney", 2, "tbsp"),
                ing("Raisins", "raisin", 2, "tbsp"),
                ing("Vinegar", "vinegar", 1, "tbsp"),
                ing("Eggs", "egg", 2, "pcs"),
                ing("Bay leaves", "bay leaf", 4, "pcs"));

        seed(db, "Pap and Chakalaka",
                steps("Bring the salted water to a boil and slowly stir in the maize meal. Cover and cook on low for 30 minutes, stirring now and then.",
                        "Meanwhile, fry the onion, pepper and carrot in oil until soft.",
                        "Add the curry powder, chutney and baked beans. Simmer for 10 minutes.",
                        "Serve the chakalaka alongside the pap."),
                ing("Maize meal", "maize meal", 2, "cup"),
                ing("Water", "water", 1, "l"),
                ing("Salt", "salt", 1, "tsp"),
                ing("Onion, chopped", "onion", 1, "pcs"),
                ing("Green pepper, diced", "bell pepper", 1, "pcs"),
                ing("Carrots, grated", "carrot", 2, "pcs"),
                ing("Baked beans", "baked bean", 410, "g"),
                ing("Curry powder", "curry powder", 2, "tbsp"),
                ing("Chutney", "chutney", 2, "tbsp"),
                ing("Vegetable oil", "vegetable oil", 2, "tbsp"));

        seed(db, "Beef Stew",
                steps("Toss the beef in seasoned flour and brown in batches in hot oil.",
                        "Soften the onions, add the tomato paste and cook for 1 minute.",
                        "Return the beef, add the stock, wine and bay leaves. Simmer covered for 1 hour 30 minutes.",
                        "Add the carrots and potatoes and cook for another 30 minutes until tender."),
                ing("Stewing beef, cubed", "beef", 1, "kg"),
                ing("Flour", "flour", 2, "tbsp"),
                ing("Onions, chopped", "onion", 2, "pcs"),
                ing("Carrots, sliced", "carrot", 3, "pcs"),
                ing("Potatoes, cubed", "potato", 3, "pcs"),
                ing("Tomato paste", "tomato paste", 2, "tbsp"),
                ing("Beef stock", "stock", 500, "ml"),
                ing("Red wine (optional)", "wine", 1, "cup"),
                ing("Bay leaves", "bay leaf", 2, "pcs"),
                ing("Vegetable oil", "vegetable oil", 2, "tbsp"));

        seed(db, "Avocado Toast with Egg",
                steps("Toast the sourdough.",
                        "Mash the avocado with lemon juice, salt and pepper and spread on the toast.",
                        "Fry or poach the eggs and place on top.",
                        "Finish with chilli flakes."),
                ing("Sourdough slices", "bread", 2, "pcs"),
                ing("Ripe avocado", "avocado", 1, "pcs"),
                ing("Eggs", "egg", 2, "pcs"),
                ing("Lemon juice", "lemon", 1, "tsp"),
                ing("Chilli flakes", "chilli flake", 1, "pinch"),
                ing("Salt", "salt", 1, "pinch"));

        seed(db, "Lentil Dhal",
                steps("Fry the onion, garlic and ginger in oil until soft.",
                        "Stir in the spices for 30 seconds.",
                        "Add the lentils, coconut milk and water. Simmer for 25 minutes, stirring often, until creamy.",
                        "Season with salt and serve with rice or naan."),
                ing("Red lentils, rinsed", "lentil", 1.5, "cup"),
                ing("Onion, chopped", "onion", 1, "pcs"),
                ing("Garlic cloves", "garlic", 3, "pcs"),
                ing("Fresh ginger, grated", "ginger", 1, "tbsp"),
                ing("Turmeric", "turmeric", 1, "tsp"),
                ing("Ground cumin", "cumin", 2, "tsp"),
                ing("Garam masala", "garam masala", 1, "tsp"),
                ing("Coconut milk", "coconut milk", 400, "ml"),
                ing("Water", "water", 500, "ml"),
                ing("Vegetable oil", "vegetable oil", 2, "tbsp"));

        seed(db, "Classic Milk Tart (Melktert)",
                steps("Preheat the oven to 180°C and blind-bake the tart shell for 10 minutes.",
                        "Heat the milk and butter until just simmering.",
                        "Whisk the egg yolks, sugar and cornflour, then slowly whisk in the hot milk. Return to the pot and stir until thick.",
                        "Beat the egg whites to soft peaks and fold into the custard with the vanilla.",
                        "Pour into the shell, bake for 20-25 minutes until set, and dust with cinnamon."),
                ing("Sweet pastry tart shell", "pastry", 1, "pcs"),
                ing("Milk", "milk", 500, "ml"),
                ing("Butter", "butter", 1, "tbsp"),
                ing("Sugar", "sugar", 0.5, "cup"),
                ing("Cornflour", "cornflour", 3, "tbsp"),
                ing("Eggs, separated", "egg", 2, "pcs"),
                ing("Vanilla extract", "vanilla", 1, "tsp"),
                ing("Ground cinnamon", "cinnamon", 1, "tsp"));
    }
}