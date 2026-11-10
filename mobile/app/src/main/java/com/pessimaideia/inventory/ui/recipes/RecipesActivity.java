package com.pessimaideia.inventory.ui.recipes;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.model.RecipeResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecipesActivity extends AppCompatActivity {

    private EditText hint;
    private Button suggest;
    private LinearProgressIndicator progress;
    private LinearLayout results;
    private boolean shown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipes);
        setTitle(R.string.title_recipes);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        hint = findViewById(R.id.hint);
        suggest = findViewById(R.id.btn_suggest);
        progress = findViewById(R.id.progress);
        results = findViewById(R.id.results);
        suggest.setOnClickListener(v -> suggest());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void suggest() {
        String lang = "pt".equals(Locale.getDefault().getLanguage()) ? "pt" : "en";
        setBusy(true);
        results.removeAllViews();
        results.addView(text(getString(R.string.recipes_thinking), R.color.fd_text_muted, 15, false));
        App.from(this).api().suggestRecipes(hint.getText().toString().trim(), lang, shown,
                new ApiCallback<RecipeResult>() {
                    @Override
                    public void onSuccess(RecipeResult result) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        shown = true;
                        suggest.setText(R.string.recipes_again);
                        results.removeAllViews();
                        for (RecipeResult.Recipe recipe : result.recipes) results.addView(card(recipe));
                    }

                    @Override
                    public void onError(ApiException error) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        results.removeAllViews();
                        String message = error.statusCode == 503 ? getString(R.string.recipes_off)
                                : error.statusCode == 409 ? getString(R.string.recipes_no_stock)
                                : getString(R.string.recipes_failed, error.getMessage());
                        results.addView(text(message, R.color.movement_out, 15, false));
                    }
                });
    }

    private void setBusy(boolean busy) {
        suggest.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
    }

    private View card(RecipeResult.Recipe recipe) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);

        card.addView(text(recipe.title, R.color.fd_text, 18, true));
        if (!TextUtils.isEmpty(recipe.summary)) card.addView(text(recipe.summary, R.color.fd_text_muted, 14, false));
        List<String> meta = new ArrayList<>();
        if (recipe.minutes > 0) meta.add(getString(R.string.recipes_minutes, recipe.minutes));
        if (recipe.servings > 0) meta.add(getString(R.string.recipes_servings, recipe.servings));
        if (!meta.isEmpty()) card.addView(text(TextUtils.join(" · ", meta), R.color.fd_text_faint, 13, false));

        card.addView(heading(R.string.recipes_ingredients));
        StringBuilder ingredients = new StringBuilder();
        for (RecipeResult.Ingredient i : recipe.ingredients) {
            if (ingredients.length() > 0) ingredients.append('\n');
            ingredients.append("• ");
            if (!TextUtils.isEmpty(i.amount)) ingredients.append(i.amount).append(' ');
            ingredients.append(i.name);
            if (i.fromStock) ingredients.append("  ✓");
        }
        card.addView(text(ingredients.toString(), R.color.fd_text_body, 15, false));
        if (recipe.missing != null && !recipe.missing.isEmpty()) {
            card.addView(text(getString(R.string.recipes_missing, TextUtils.join(", ", recipe.missing)),
                    R.color.movement_out, 14, false));
        }

        card.addView(heading(R.string.recipes_steps));
        StringBuilder steps = new StringBuilder();
        for (int n = 0; n < recipe.steps.size(); n++) {
            if (n > 0) steps.append('\n');
            steps.append(n + 1).append(". ").append(recipe.steps.get(n));
        }
        card.addView(text(steps.toString(), R.color.fd_text_body, 15, false));
        return card;
    }

    private TextView heading(int res) {
        TextView view = text(getString(res), R.color.fd_text, 15, true);
        view.setPadding(0, dp(10), 0, dp(2));
        return view;
    }

    private TextView text(String value, int color, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(ContextCompat.getColor(this, color));
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
