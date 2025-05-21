package com.example.money2;

import android.app.Dialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private FinanceViewModel viewModel;
    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter transactionAdapter;
    private View detailsView;
    private TextView userNameView;
    private TextView totalBalanceView;
    private TextView tvIncome, tvExpense;
    private TextView tvBudgetSpent, tvBudgetTotal, tvBudgetPercent;
    private TextView budgetAlertView;
    private MaterialCardView budgetCard;
    private SwipeRefreshLayout swipeRefresh;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fabAddTransaction;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Activity created");

        if (!isUserAuthenticated()) {
            redirectToLogin();
            return;
        }

        executorService = Executors.newSingleThreadExecutor();
        initializeViews();
        setupRecyclerView();
        setupViewModel();
        setupButtonListeners();
        setupSwipeRefresh();
        setupBottomNavigation();
        checkNetworkAndLoadData();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FinanceViewModel.UIState state = viewModel.getUiState().getValue();
                if (state == FinanceViewModel.UIState.DETAILS) {
                    viewModel.setUiState(FinanceViewModel.UIState.LIST);
                } else if (isEnabled()) {
                    setEnabled(false);
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private boolean isUserAuthenticated() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeViews() {
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);
        detailsView = findViewById(R.id.detailsView);
        userNameView = findViewById(R.id.userName);
        totalBalanceView = findViewById(R.id.totalBalance);
        tvIncome = findViewById(R.id.tvIncome);
        tvExpense = findViewById(R.id.tvExpense);
        budgetCard = findViewById(R.id.budgetCard);
        tvBudgetSpent = findViewById(R.id.tvBudgetSpent);
        tvBudgetTotal = findViewById(R.id.tvBudgetTotal);
        tvBudgetPercent = findViewById(R.id.tvBudgetPercent);
        budgetAlertView = findViewById(R.id.budgetAlert);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fabAddTransaction = findViewById(R.id.fabAddTransaction);
    }

    private void setupRecyclerView() {
        transactionAdapter = new TransactionAdapter(new ArrayList<>(), transaction -> {
            viewModel.setUiState(FinanceViewModel.UIState.DETAILS);
            TextView tvDetailTitle = detailsView.findViewById(R.id.tvDetailTitle);
            TextView tvDetailAmount = detailsView.findViewById(R.id.tvDetailAmount);
            TextView tvDetailDate = detailsView.findViewById(R.id.tvDetailDate);
            TextView tvDetailCategory = detailsView.findViewById(R.id.tvDetailCategory);
            TextView tvDetailSource = detailsView.findViewById(R.id.tvDetailSource);

            tvDetailTitle.setText(transaction.isIncome() ? "Доход" : "Расход");
            tvDetailAmount.setText(String.format(Locale.getDefault(), "%.2f ₽", transaction.amount));
            tvDetailDate.setText(transaction.date != null ? transaction.date : "Не указана");
            tvDetailCategory.setText(transaction.category != null ? transaction.category : "Не указана");
            tvDetailSource.setText(transaction.source != null ? transaction.source : "Не указан");

            Button btnEdit = detailsView.findViewById(R.id.btnEditTransaction);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, EditActivity.class);
                intent.putExtra("type", "transaction");
                intent.putExtra("userId", viewModel.getCurrentUserId());
                intent.putExtra("transaction", transaction);
                startActivity(intent);
            });
        });
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(transactionAdapter);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);

        viewModel.getUiState().observe(this, state -> {
            if (detailsView != null) {
                detailsView.setVisibility(state != null && state == FinanceViewModel.UIState.DETAILS ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getBalance().observe(this, balance -> {
            if (totalBalanceView != null) {
                Log.d(TAG, "Updating balance to: " + (balance != null ? balance : 0.0) + " ₽");
                totalBalanceView.setText(String.format(Locale.getDefault(), "%.2f ₽", balance != null ? balance : 0.0));
            }
        });

        viewModel.getTransactions().observe(this, transactions -> {
            if (transactions != null) {
                transactionAdapter.updateTransactions(transactions);
                updateBudgetDisplay(viewModel.getBudget().getValue());
                updateIncomeExpense(transactions);
                // Force balance recalculation
                viewModel.recalculateBalance();
            } else {
                transactionAdapter.updateTransactions(new ArrayList<>());
                updateBudgetDisplay(0.0);
                updateIncomeExpense(new ArrayList<>());
                viewModel.recalculateBalance();
            }
        });

        viewModel.getUserName().observe(this, name -> {
            if (userNameView != null) {
                userNameView.setText(name != null && !name.isEmpty() ? getString(R.string.welcome_user, name) : getString(R.string.guest));
            }
        });

        viewModel.getBudget().observe(this, this::updateBudgetDisplay);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getBudgetAlert().observe(this, alert -> {
            if (budgetAlertView != null) {
                LinearLayout alertActions = findViewById(R.id.alertActions);
                if (alert != null && !alert.isEmpty()) {
                    budgetAlertView.setVisibility(View.VISIBLE);
                    budgetAlertView.setText(alert);
                    if (alertActions != null) {
                        alertActions.setVisibility(View.VISIBLE);
                    }
                    Button btnIncreaseBudget = findViewById(R.id.btnIncreaseBudget);
                    Button btnAddIncome = findViewById(R.id.btnAddIncome);
                    if (btnIncreaseBudget != null && btnAddIncome != null) {
                        btnIncreaseBudget.setOnClickListener(v -> openBudgetSettings());
                        btnAddIncome.setOnClickListener(v -> showAddTransactionDialog());
                    }
                } else {
                    budgetAlertView.setVisibility(View.GONE);
                    if (alertActions != null) {
                        alertActions.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                viewModel.refreshData();
                swipeRefresh.setRefreshing(false);
            });
        }
    }

    private void setupBottomNavigation() {
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_stats) {
                    startActivity(new Intent(this, StatisticsActivity.class));
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    return true;
                }
                return false;
            });
        }
    }

    private void checkNetworkAndLoadData() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    loadUserData();
                    viewModel.refreshData();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, R.string.no_internet, Toast.LENGTH_LONG).show();
                    if (userNameView != null) {
                        userNameView.setText(R.string.guest_offline);
                    }
                    loadCachedData();
                });
            }
        });
    }

    private void loadCachedData() {
        Toast.makeText(this, R.string.loading_cached_data, Toast.LENGTH_SHORT).show();
    }

    private void loadUserData() {
        String userId = viewModel.getCurrentUserId();
        if (userId == null) {
            runOnUiThread(() -> {
                viewModel.setUserName(getString(R.string.guest));
                Toast.makeText(MainActivity.this, R.string.error_user_not_authenticated, Toast.LENGTH_LONG).show();
            });
            return;
        }

        executorService.execute(() -> {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String username = snapshot.child("username").getValue(String.class);
                    Double budget = snapshot.child("budget").getValue(Double.class);

                    runOnUiThread(() -> {
                        viewModel.setUserName(username != null ? username : getString(R.string.guest));
                        viewModel.setBudget(budget != null ? budget : 0.0);
                    });

                    DatabaseReference transactionsRef = FirebaseDatabase.getInstance().getReference("Transactions").child(userId);
                    transactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            List<FinanceViewModel.Transaction> transactions = new ArrayList<>();
                            for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                                FinanceViewModel.Transaction transaction = transactionSnapshot.getValue(FinanceViewModel.Transaction.class);
                                if (transaction != null) {
                                    transaction.id = transactionSnapshot.getKey();
                                    transactions.add(transaction);
                                    Log.d(TAG, "Loaded transaction: id=" + transaction.id + ", type=" + transaction.type + ", amount=" + transaction.amount + ", date=" + transaction.date);
                                }
                            }
                            runOnUiThread(() -> viewModel.setTransactions(transactions));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.error_loading_transactions, Toast.LENGTH_LONG).show());
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    runOnUiThread(() -> {
                        viewModel.setUserName(getString(R.string.guest));
                        viewModel.setBudget(0.0);
                        Toast.makeText(MainActivity.this, R.string.error_loading_data, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void setupButtonListeners() {
        if (budgetCard != null) {
            budgetCard.setOnClickListener(v -> openBudgetSettings());
        }
        if (fabAddTransaction != null) {
            fabAddTransaction.setOnClickListener(v -> showAddTransactionDialog());
        }
        setupFilterListeners();
    }

    private void showAddTransactionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_transaction);

        EditText etAmount = dialog.findViewById(R.id.etAmount);
        Spinner typeSpinner = dialog.findViewById(R.id.typeSpinner);
        Spinner categorySpinner = dialog.findViewById(R.id.categorySpinner);
        Spinner sourceSpinner = dialog.findViewById(R.id.sourceSpinner);
        Button btnSave = dialog.findViewById(R.id.btnSave);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        if (etAmount == null || typeSpinner == null || categorySpinner == null || sourceSpinner == null ||
                btnSave == null || btnCancel == null) {
            Toast.makeText(this, R.string.error_dialog_layout, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                Arrays.asList(getString(R.string.income_button), getString(R.string.expense_button)));
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        setupSpinners(typeSpinner, categorySpinner, sourceSpinner);

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Toast.makeText(this, R.string.error_empty_amount, Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
                return;
            }

            String typeDisplay = typeSpinner.getSelectedItem() != null ? typeSpinner.getSelectedItem().toString() : "";
            String category = categorySpinner.getSelectedItem() != null ? categorySpinner.getSelectedItem().toString() : "";
            String source = sourceSpinner.getSelectedItem() != null ? sourceSpinner.getSelectedItem().toString() : "";

            if (typeDisplay.isEmpty() || category.isEmpty() || source.isEmpty()) {
                Toast.makeText(this, R.string.error_select_category_source, Toast.LENGTH_SHORT).show();
                return;
            }

            String type = typeDisplay.equals(getString(R.string.income_button)) ? "income" : "expense";
            FinanceViewModel.Transaction transaction = new FinanceViewModel.Transaction(type, category, amount, source);
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            transaction.date = sdf.format(new Date());
            viewModel.addTransaction(transaction);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupSpinners(Spinner typeSpinner, Spinner categorySpinner, Spinner sourceSpinner) {
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String typeDisplay = parent.getItemAtPosition(position).toString();
                String type = typeDisplay.equals(getString(R.string.income_button)) ? "income" : "expense";
                viewModel.setCurrentTransactionType(type);

                List<String> categories = viewModel.getCategoriesForCurrentType();
                if (categories != null && !categories.isEmpty()) {
                    ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(MainActivity.this,
                            android.R.layout.simple_spinner_item, categories);
                    categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    categorySpinner.setAdapter(categoryAdapter);
                } else {
                    categorySpinner.setAdapter(null);
                    Toast.makeText(MainActivity.this, R.string.error_init_spinners, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        List<String> initialCategories = viewModel.getCategoriesForCurrentType();
        if (initialCategories != null && !initialCategories.isEmpty()) {
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, initialCategories);
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(categoryAdapter);
        } else {
            categorySpinner.setAdapter(null);
            Toast.makeText(this, R.string.error_init_spinners, Toast.LENGTH_SHORT).show();
        }

        List<String> sources = viewModel.getMoneySources();
        if (sources != null && !sources.isEmpty()) {
            ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, sources);
            sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceSpinner.setAdapter(sourceAdapter);
        } else {
            sourceSpinner.setAdapter(null);
            Toast.makeText(this, R.string.error_init_spinners, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateIncomeExpense(List<FinanceViewModel.Transaction> transactions) {
        double income = 0;
        double expense = 0;

        if (transactions != null) {
            for (FinanceViewModel.Transaction t : transactions) {
                if (t != null && t.amount != null) {
                    if (t.isIncome()) income += t.amount;
                    else expense += t.amount;
                }
            }
        }

        if (tvIncome != null) tvIncome.setText(String.format(Locale.getDefault(), "+%.2f ₽", income));
        if (tvExpense != null) tvExpense.setText(String.format(Locale.getDefault(), "-%.2f ₽", expense));
        Log.d(TAG, "Income: " + income + ", Expense: " + expense + ", Net: " + (income - expense));
    }

    private void updateBudgetDisplay(Double budget) {
        List<FinanceViewModel.Transaction> transactions = viewModel.getTransactions().getValue();
        String period = viewModel.getBudgetPeriod().getValue() != null ? viewModel.getBudgetPeriod().getValue() : "month";
        double spent = 0;

        if (transactions != null) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            Date currentDate = cal.getTime();

            for (FinanceViewModel.Transaction t : transactions) {
                if (t != null && !t.isIncome() && t.amount != null && t.date != null) {
                    try {
                        Date transactionDate = sdf.parse(t.date);
                        if (transactionDate == null) continue;
                        cal.setTime(currentDate);

                        boolean withinPeriod = false;
                        switch (period) {
                            case "week":
                                cal.add(Calendar.DAY_OF_YEAR, -7);
                                withinPeriod = !transactionDate.before(cal.getTime());
                                break;
                            case "month":
                                cal.set(Calendar.DAY_OF_MONTH, 1);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);
                                withinPeriod = !transactionDate.before(cal.getTime());
                                break;
                        }
                        if (withinPeriod) {
                            spent += t.amount;
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, "Date parse error for transaction date: " + t.date, e);
                    }
                }
            }
        }

        if (tvBudgetSpent != null) tvBudgetSpent.setText(String.format(Locale.getDefault(), "%.2f ₽ потрачено", spent));
        if (tvBudgetTotal != null) tvBudgetTotal.setText(String.format(Locale.getDefault(), "из %.2f ₽", budget != null ? budget : 0.0));

        int percent = budget != null && budget > 0 ? (int) ((spent / budget) * 100) : 0;
        if (tvBudgetPercent != null) tvBudgetPercent.setText(String.format("(%d%%)", percent));

        LinearProgressIndicator progress = findViewById(R.id.budgetProgress);
        if (progress != null) {
            progress.setProgress(Math.min(percent, 100));
            if (percent >= 90) {
                progress.setIndicatorColor(getResources().getColor(R.color.red, getTheme()));
                Toast.makeText(this, R.string.budget_almost_exhausted, Toast.LENGTH_LONG).show();
            } else {
                progress.setIndicatorColor(getResources().getColor(R.color.main_color, getTheme()));
            }
        }
        Log.d(TAG, "Budget spent: " + spent + ", Total: " + (budget != null ? budget : 0.0) + ", Percent: " + percent);
    }

    private void setupFilterListeners() {
        Chip chipDay = findViewById(R.id.chipDay);
        Chip chipWeek = findViewById(R.id.chipWeek);
        Chip chipMonth = findViewById(R.id.chipMonth);
        Chip chipAll = findViewById(R.id.chipAll);

        if (chipDay != null) {
            chipDay.setOnClickListener(v -> {
                Log.d(TAG, "Day filter clicked");
                filterTransactions("day");
                chipDay.setChecked(true);
                if (chipWeek != null) chipWeek.setChecked(false);
                if (chipMonth != null) chipMonth.setChecked(false);
                if (chipAll != null) chipAll.setChecked(false);
            });
        }
        if (chipWeek != null) {
            chipWeek.setOnClickListener(v -> {
                Log.d(TAG, "Week filter clicked");
                filterTransactions("week");
                chipWeek.setChecked(true);
                if (chipDay != null) chipDay.setChecked(false);
                if (chipMonth != null) chipMonth.setChecked(false);
                if (chipAll != null) chipAll.setChecked(false);
            });
        }
        if (chipMonth != null) {
            chipMonth.setOnClickListener(v -> {
                Log.d(TAG, "Month filter clicked");
                filterTransactions("month");
                chipMonth.setChecked(true);
                if (chipDay != null) chipDay.setChecked(false);
                if (chipWeek != null) chipWeek.setChecked(false);
                if (chipAll != null) chipAll.setChecked(false);
            });
        }
        if (chipAll != null) {
            chipAll.setOnClickListener(v -> {
                Log.d(TAG, "All filter clicked");
                viewModel.refreshData();
                chipAll.setChecked(true);
                if (chipDay != null) chipDay.setChecked(false);
                if (chipWeek != null) chipWeek.setChecked(false);
                if (chipMonth != null) chipMonth.setChecked(false);
            });
        }
    }

    private void filterTransactions(String period) {
        List<FinanceViewModel.Transaction> all = viewModel.getAllTransactions();
        if (all == null || all.isEmpty()) {
            Log.d(TAG, "No transactions available for filter: " + period);
            viewModel.setTransactions(new ArrayList<>());
            Toast.makeText(this, R.string.no_transactions_for_period, Toast.LENGTH_SHORT).show();
            return;
        }

        List<FinanceViewModel.Transaction> filtered = new ArrayList<>();
        Calendar currentCal = Calendar.getInstance();
        Calendar transactionCal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        Log.d(TAG, "Filtering transactions for period: " + period + ", total transactions: " + all.size());

        for (FinanceViewModel.Transaction t : all) {
            if (t == null || t.date == null) {
                Log.w(TAG, "Skipping transaction: null or missing date");
                continue;
            }
            try {
                Date transactionDate = sdf.parse(t.date);
                if (transactionDate == null) {
                    Log.w(TAG, "Failed to parse date: " + t.date);
                    continue;
                }

                transactionCal.setTime(transactionDate);
                currentCal.setTime(new Date());

                boolean include = false;
                switch (period) {
                    case "day":
                        include = transactionCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                                transactionCal.get(Calendar.DAY_OF_YEAR) == currentCal.get(Calendar.DAY_OF_YEAR);
                        break;
                    case "week":
                        Calendar weekStart = (Calendar) currentCal.clone();
                        weekStart.add(Calendar.DAY_OF_YEAR, -6); // Include 7 days (current day + 6 previous)
                        weekStart.set(Calendar.HOUR_OF_DAY, 0);
                        weekStart.set(Calendar.MINUTE, 0);
                        weekStart.set(Calendar.SECOND, 0);
                        weekStart.set(Calendar.MILLISECOND, 0);
                        include = !transactionDate.before(weekStart.getTime()) &&
                                !transactionDate.after(currentCal.getTime());
                        break;
                    case "month":
                        include = transactionCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                                transactionCal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH);
                        break;
                }
                if (include) {
                    filtered.add(t);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Date parse error for transaction: " + t.date, e);
                Toast.makeText(this, R.string.error_date_parse, Toast.LENGTH_SHORT).show();
            }
        }

        Log.d(TAG, "Filter '" + period + "': found " + filtered.size() + " transactions");
        viewModel.setTransactions(filtered);
        viewModel.setBudgetPeriod(period);
        if (filtered.isEmpty()) {
            Toast.makeText(this, R.string.no_transactions_for_period, Toast.LENGTH_SHORT).show();
        }
    }

    private void openBudgetSettings() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_budget_settings);

        EditText etBudget = dialog.findViewById(R.id.etBudget);
        Button btnSave = dialog.findViewById(R.id.btnSave);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        if (etBudget == null || btnSave == null || btnCancel == null) {
            Toast.makeText(this, R.string.error_dialog_layout, Toast.LENGTH_SHORT).show();
            return;
        }

        Double currentBudget = viewModel.getBudget().getValue();
        if (currentBudget != null) {
            etBudget.setText(String.format(Locale.getDefault(), "%.2f", currentBudget));
        }

        btnSave.setOnClickListener(v -> {
            String budgetStr = etBudget.getText().toString().trim();
            if (budgetStr.isEmpty()) {
                Toast.makeText(this, R.string.error_empty_budget, Toast.LENGTH_SHORT).show();
                return;
            }

            double budget;
            try {
                budget = Double.parseDouble(budgetStr);
                if (budget <= 0) {
                    Toast.makeText(this, R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_budget_format, Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.setBudget(budget);
            dialog.dismiss();
            updateBudgetDisplay(budget);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}