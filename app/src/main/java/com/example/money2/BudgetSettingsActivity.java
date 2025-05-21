package com.example.money2;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.FirebaseDatabase;

public class BudgetSettingsActivity extends AppCompatActivity {
    private FinanceViewModel viewModel;
    private TextInputEditText etBudget;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_settings);

        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        initializeViews();
        setupToolbar();
        setupButtonListeners();

        // Загрузка текущего бюджета
        Double currentBudget = getIntent().getDoubleExtra("current_budget", 0.0);
        if (currentBudget > 0) {
            etBudget.setText(String.format("%.2f", currentBudget));
        }
    }

    private void initializeViews() {
        etBudget = findViewById(R.id.etBudget);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupButtonListeners() {
        btnSave.setOnClickListener(v -> saveBudget());
    }

    private void saveBudget() {
        String budgetText = etBudget.getText().toString().trim();
        if (budgetText.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_budget, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double budgetValue = Double.parseDouble(budgetText);
            if (budgetValue <= 0) {
                Toast.makeText(this, R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
                return;
            }

            // Сохранение бюджета в Firebase
            String userId = viewModel.getCurrentUserId();
            if (userId != null) {
                FirebaseDatabase.getInstance().getReference("Users")
                        .child(userId)
                        .child("budget")
                        .setValue(budgetValue)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                viewModel.setBudget(budgetValue);
                                Toast.makeText(this, R.string.budget_saved, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, R.string.error_saving_budget, Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(this, R.string.error_user_not_authenticated, Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
        }
    }
}