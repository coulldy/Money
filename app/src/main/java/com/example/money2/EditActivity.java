package com.example.money2;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.List;
import java.util.Locale;

// Активность для редактирования бюджета, профиля или транзакции
public class EditActivity extends AppCompatActivity {
    private FinanceViewModel viewModel;
    private Spinner categorySpinner, sourceSpinner;
    private EditText etAmount, etBudget, etUsername, etEmail;
    private TextView titleView;
    private FinanceViewModel.Transaction transaction;
    private String type;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        // Инициализация ViewModel
        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        initializeViews();
        setupToolbar();

        // Получение данных из Intent
        type = getIntent().getStringExtra("type");
        userId = getIntent().getStringExtra("userId");

        // Проверка, что type и userId переданы
        if (type == null || userId == null) {
            showErrorAndFinish(getString(R.string.error_missing_type));
            return;
        }

        // Проверка авторизации пользователя
        if (viewModel.getCurrentUserId() == null) {
            showErrorAndFinish(getString(R.string.error_user_not_authenticated));
            return;
        }

        // Настройка формы в зависимости от типа
        switch (type) {
            case "budget":
                setupBudgetForm();
                break;
            case "profile":
                setupProfileForm();
                break;
            default:
                // Попытка получить объект транзакции
                try {
                    transaction = (FinanceViewModel.Transaction) getIntent().getSerializableExtra("transaction");
                    if (transaction == null || transaction.id == null) {
                        showErrorAndFinish(getString(R.string.error_transaction_not_found));
                        return;
                    }
                    setupTransactionForm();
                } catch (Exception e) {
                    showErrorAndFinish(getString(R.string.error_transaction_format));
                }
                break;
        }
    }

    // Инициализация всех View-элементов
    private void initializeViews() {
        titleView = findViewById(R.id.titleView);
        categorySpinner = findViewById(R.id.categorySpinner);
        sourceSpinner = findViewById(R.id.sourceSpinner);
        etAmount = findViewById(R.id.etAmount);
        etBudget = findViewById(R.id.etBudget);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);

        // Назначение обработчиков кнопок
        findViewById(R.id.btnSave).setOnClickListener(v -> saveChanges());
        findViewById(R.id.btnDelete).setOnClickListener(v -> deleteTransaction());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // Настройка тулбара
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // Настройка формы для редактирования бюджета
    private void setupBudgetForm() {
        titleView.setText(R.string.set_budget_title);
        etBudget.setVisibility(View.VISIBLE);
        categorySpinner.setVisibility(View.GONE);
        sourceSpinner.setVisibility(View.GONE);
        etAmount.setVisibility(View.GONE);
        etUsername.setVisibility(View.GONE);
        etEmail.setVisibility(View.GONE);
        findViewById(R.id.btnDelete).setVisibility(View.GONE);

        // Установка текущего бюджета, если он есть
        Double currentBudget = viewModel.getBudget().getValue();
        if (currentBudget != null && currentBudget > 0) {
            etBudget.setText(String.format(Locale.getDefault(), "%.2f", currentBudget));
        }
    }

    // Настройка формы для редактирования профиля
    private void setupProfileForm() {
        titleView.setText(R.string.edit_profile_title);
        etBudget.setVisibility(View.GONE);
        categorySpinner.setVisibility(View.GONE);
        sourceSpinner.setVisibility(View.GONE);
        etAmount.setVisibility(View.GONE);
        etUsername.setVisibility(View.VISIBLE);
        etEmail.setVisibility(View.VISIBLE);
        findViewById(R.id.btnDelete).setVisibility(View.GONE);

        // Загрузка данных пользователя из Firebase
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    String username = snapshot.child("username").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    etUsername.setText(username != null ? username : "");
                    etEmail.setText(email != null ? email : "");
                } catch (Exception e) {
                    showError(getString(R.string.error_loading_profile));
                    etUsername.setText("");
                    etEmail.setText("");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                showError(getString(R.string.error_loading_profile));
                etUsername.setText("");
                etEmail.setText("");
            }
        });
    }

    // Настройка формы для редактирования транзакции
    private void setupTransactionForm() {
        titleView.setText(R.string.edit_transaction_title);
        etBudget.setVisibility(View.GONE);
        findViewById(R.id.btnDelete).setVisibility(View.VISIBLE);
        etUsername.setVisibility(View.GONE);
        etEmail.setVisibility(View.GONE);

        // Установка типа транзакции
        viewModel.setCurrentTransactionType(transaction.type != null ? transaction.type : "income");
        setupSpinners();

        // Установка значений в Spinner
        List<String> categories = viewModel.getCategoriesForCurrentType();
        List<String> sources = viewModel.getMoneySources();

        if (categories != null && transaction.category != null) {
            int categoryPosition = categories.indexOf(transaction.category);
            if (categoryPosition >= 0) {
                categorySpinner.setSelection(categoryPosition);
            }
        }

        if (sources != null && transaction.source != null) {
            int sourcePosition = sources.indexOf(transaction.source);
            if (sourcePosition >= 0) {
                sourceSpinner.setSelection(sourcePosition);
            }
        }

        // Установка суммы
        etAmount.setText(String.format(Locale.getDefault(), "%.2f", transaction.amount));
    }

    // Настройка Spinner'ов для категорий и источников
    private void setupSpinners() {
        List<String> categories = viewModel.getCategoriesForCurrentType();
        List<String> sources = viewModel.getMoneySources();

        // Настройка Spinner для категорий
        if (categories != null && !categories.isEmpty()) {
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, categories);
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(categoryAdapter);
        } else {
            showError(getString(R.string.error_init_spinners));
        }

        // Настройка Spinner для источников
        if (sources != null && !sources.isEmpty()) {
            ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, sources);
            sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceSpinner.setAdapter(sourceAdapter);
        } else {
            showError(getString(R.string.error_init_spinners));
        }
    }

    // Обработка сохранения изменений
    private void saveChanges() {
        switch (type) {
            case "budget":
                handleBudgetSave();
                break;
            case "profile":
                handleProfileSave();
                break;
            default:
                handleTransactionSave();
                break;
        }
    }

    // Сохранение бюджета
    private void handleBudgetSave() {
        String budgetText = etBudget.getText().toString().trim();
        if (budgetText.isEmpty()) {
            showError(getString(R.string.error_empty_budget));
            return;
        }

        try {
            double budgetValue = Double.parseDouble(budgetText);
            if (budgetValue <= 0) {
                showError(getString(R.string.error_invalid_budget));
                return;
            }

            String userId = viewModel.getCurrentUserId();
            if (userId != null) {
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users")
                        .child(userId)
                        .child("budget");
                ref.setValue(budgetValue)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                viewModel.setBudget(budgetValue);
                                showSuccess(getString(R.string.budget_saved));
                                finish();
                            } else {
                                showError(getString(R.string.error_saving_budget));
                            }
                        });
            } else {
                showError(getString(R.string.error_user_not_authenticated));
            }
        } catch (NumberFormatException e) {
            showError(getString(R.string.error_invalid_budget));
        }
    }

    // Сохранение профиля
    private void handleProfileSave() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty()) {
            showError(getString(R.string.error_empty_profile));
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        userRef.child("username").setValue(username);
        userRef.child("email").setValue(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showSuccess(getString(R.string.profile_saved));
                        finish();
                    } else {
                        showError(getString(R.string.error_saving_profile));
                    }
                });
    }

    // Сохранение транзакции
    private void handleTransactionSave() {
        String amountText = etAmount.getText().toString().trim();
        if (amountText.isEmpty()) {
            showError(getString(R.string.error_empty_amount));
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                showError(getString(R.string.error_invalid_amount));
                return;
            }

            Object selectedCategory = categorySpinner.getSelectedItem();
            Object selectedSource = sourceSpinner.getSelectedItem();

            if (selectedCategory == null || selectedSource == null) {
                showError(getString(R.string.error_select_category_source));
                return;
            }

            // Обновление данных транзакции
            if (transaction == null) {
                transaction = new FinanceViewModel.Transaction(
                        viewModel.getCurrentTransactionType(),
                        selectedCategory.toString(),
                        amount,
                        selectedSource.toString()
                );
            } else {
                transaction.category = selectedCategory.toString();
                transaction.amount = amount;
                transaction.source = selectedSource.toString();
                transaction.type = viewModel.getCurrentTransactionType() != null ?
                        viewModel.getCurrentTransactionType() : "income";
            }

            viewModel.updateTransaction(transaction);
            showSuccess(getString(R.string.transaction_updated));
            finish();
        } catch (NumberFormatException e) {
            showError(getString(R.string.error_invalid_amount));
        } catch (IllegalStateException e) {
            showError("Недостаточно средств для изменения транзакции!");
        }
    }

    // Удаление транзакции
    private void deleteTransaction() {
        if (transaction != null && transaction.id != null) {
            viewModel.deleteTransaction(transaction.id);
            showSuccess(getString(R.string.transaction_deleted));
            finish();
        } else {
            showError(getString(R.string.error_delete_transaction));
        }
    }

    // Показ сообщения об ошибке
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Показ сообщения об успехе
    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Показ ошибки и завершение активности
    private void showErrorAndFinish(String message) {
        showError(message);
        finish();
    }
}