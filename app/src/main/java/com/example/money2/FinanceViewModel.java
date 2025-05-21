package com.example.money2;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import android.util.Log;

public class FinanceViewModel extends ViewModel {
    public enum UIState { LIST, ADD_FORM, DETAILS }

    private final MutableLiveData<UIState> uiState = new MutableLiveData<>(UIState.LIST);
    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> budget = new MutableLiveData<>(0.0);
    private final MutableLiveData<String> userName = new MutableLiveData<>("");
    private final MutableLiveData<List<Transaction>> transactions = new MutableLiveData<>(new ArrayList<>());
    private List<Transaction> allTransactions = new ArrayList<>();
    private String currentTransactionType = "income";
    private final MutableLiveData<String> budgetPeriod = new MutableLiveData<>("month");
    private final MutableLiveData<Date> customBudgetStartDate = new MutableLiveData<>();
    private final MutableLiveData<Date> customBudgetEndDate = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> budgetAlert = new MutableLiveData<>();

    public FinanceViewModel() {
        refreshData();
    }

    public static class Transaction implements java.io.Serializable {
        public String id;
        public String type;
        public String category;
        public Double amount;
        public String source;
        public String date;

        public Transaction() {
        }

        public Transaction(String type, String category, double amount, String source) {
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.source = source;
            this.date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
            this.id = String.valueOf(System.currentTimeMillis());
        }

        public boolean isIncome() {
            return "income".equals(type);
        }

        public HashMap<String, Object> toMap() {
            HashMap<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("category", category);
            map.put("amount", amount);
            map.put("source", source);
            map.put("date", date);
            return map;
        }
    }

    public LiveData<UIState> getUiState() { return uiState; }
    public void setUiState(UIState state) { uiState.setValue(state); }
    public LiveData<Double> getBalance() { return balance; }
    public void setBalance(double value) { balance.setValue(value); }
    public LiveData<Double> getBudget() { return budget; }
    public void setBudget(double value) {
        budget.setValue(value);
        saveBudgetToFirebase(value);
        checkBudgetStatus();
    }
    public LiveData<String> getUserName() { return userName; }
    public void setUserName(String name) { userName.setValue(name != null ? name : ""); }
    public LiveData<List<Transaction>> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> list) {
        transactions.setValue(list != null ? list : new ArrayList<>());
        checkBudgetStatus();
    }
    public List<Transaction> getAllTransactions() { return new ArrayList<>(allTransactions); }
    public String getCurrentTransactionType() { return currentTransactionType; }
    public void setCurrentTransactionType(String type) {
        currentTransactionType = type != null ? type : "income";
    }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getBudgetPeriod() { return budgetPeriod; }
    public void setBudgetPeriod(String period) { budgetPeriod.setValue(period); }
    public LiveData<Date> getCustomBudgetStartDate() { return customBudgetStartDate; }
    public void setCustomBudgetStartDate(Date date) { customBudgetStartDate.setValue(date); }
    public LiveData<Date> getCustomBudgetEndDate() { return customBudgetEndDate; }
    public void setCustomBudgetEndDate(Date date) { customBudgetEndDate.setValue(date); }
    public LiveData<String> getBudgetAlert() { return budgetAlert; }

    public String getCurrentUserId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    public List<String> getCategoriesForCurrentType() {
        if ("income".equals(currentTransactionType)) {
            return Arrays.asList("Зарплата", "Фриланс", "Инвестиции", "Другое");
        } else {
            return Arrays.asList("Еда", "Транспорт", "Жилье", "Развлечения", "Другое");
        }
    }

    public List<String> getMoneySources() {
        return Arrays.asList("Наличные", "Карта", "Банковский счет", "Кредитная карта");
    }

    public void addTransaction(Transaction transaction) {
        String userId = getCurrentUserId();
        if (userId == null || transaction == null || transaction.id == null) {
            errorMessage.setValue("Ошибка: пользователь не аутентифицирован или транзакция недействительна");
            return;
        }

        double currentBalance = balance.getValue() != null ? balance.getValue() : 0.0;
        double newBalance = currentBalance + (transaction.isIncome() ? transaction.amount : -transaction.amount);

        if (!transaction.isIncome() && newBalance < 0) {
            errorMessage.setValue("Недостаточно средств для расхода!");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("transactions")
                .child(transaction.id)
                .setValue(transaction.toMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Transaction> current = transactions.getValue();
                        allTransactions.add(transaction);
                        if (current != null) {
                            current.add(transaction);
                            transactions.setValue(current);
                            balance.setValue(newBalance);
                            checkBudgetStatus();
                        }
                    } else {
                        errorMessage.setValue("Ошибка сохранения транзакции");
                    }
                });
    }

    public void updateTransaction(Transaction transaction) {
        String userId = getCurrentUserId();
        if (userId == null || transaction == null || transaction.id == null) {
            errorMessage.setValue("Ошибка: пользователь не аутентифицирован или транзакция недействительна");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("transactions")
                .child(transaction.id)
                .setValue(transaction.toMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Transaction> current = transactions.getValue();
                        if (current != null) {
                            for (int i = 0; i < current.size(); i++) {
                                if (current.get(i).id.equals(transaction.id)) {
                                    current.set(i, transaction);
                                    break;
                                }
                            }
                            for (int i = 0; i < allTransactions.size(); i++) {
                                if (allTransactions.get(i).id.equals(transaction.id)) {
                                    allTransactions.set(i, transaction);
                                    break;
                                }
                            }
                            transactions.setValue(current);
                            recalculateBalance();
                            checkBudgetStatus();
                        }
                    } else {
                        errorMessage.setValue("Ошибка обновления транзакции");
                    }
                });
    }

    public void deleteTransaction(String id) {
        String userId = getCurrentUserId();
        if (userId == null || id == null) {
            errorMessage.setValue("Ошибка: пользователь не аутентифицирован или ID транзакции недействителен");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("transactions")
                .child(id)
                .removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Transaction> current = transactions.getValue();
                        if (current != null) {
                            Transaction toRemove = null;
                            for (Transaction t : current) {
                                if (t.id.equals(id)) {
                                    toRemove = t;
                                    break;
                                }
                            }
                            if (toRemove != null) {
                                current.remove(toRemove);
                                allTransactions.remove(toRemove);
                                transactions.setValue(current);
                                recalculateBalance();
                                checkBudgetStatus();
                            }
                        }
                    } else {
                        errorMessage.setValue("Ошибка удаления транзакции");
                    }
                });
    }

    void recalculateBalance() {
        double currentBalance = 0.0;
        List<Transaction> current = transactions.getValue();
        if (current != null) {
            for (Transaction t : current) {
                if (t != null && t.amount != null) {
                    currentBalance += t.isIncome() ? t.amount : -t.amount;
                }
            }
        }
        balance.setValue(currentBalance);
    }

    public void refreshData() {
        String userId = getCurrentUserId();
        if (userId == null) {
            errorMessage.setValue("Пользователь не аутентифицирован");
            Log.e("FinanceViewModel", "Пользователь не аутентифицирован");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("transactions")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Transaction> loadedTransactions = new ArrayList<>();
                        Log.d("FinanceViewModel", "Получено транзакций из Firebase: " + snapshot.getChildrenCount());
                        for (DataSnapshot data : snapshot.getChildren()) {
                            try {
                                Transaction t = data.getValue(Transaction.class);
                                if (t != null && t.type != null && t.category != null &&
                                        t.amount != null && t.source != null && t.date != null) {
                                    t.id = data.getKey();
                                    loadedTransactions.add(t);
                                    Log.d("FinanceViewModel", "Добавлена транзакция: " + t.id + ", тип: " + t.type + ", категория: " + t.category);
                                } else {
                                    Log.w("FinanceViewModel", "Некорректная транзакция: " + data.getKey());
                                }
                            } catch (Exception e) {
                                errorMessage.setValue("Ошибка десериализации транзакции: " + e.getMessage());
                                Log.e("FinanceViewModel", "Ошибка десериализации: " + data.getKey(), e);
                            }
                        }
                        Log.d("FinanceViewModel", "Всего загружено " + loadedTransactions.size() + " транзакций");
                        allTransactions = loadedTransactions;
                        transactions.setValue(loadedTransactions);
                        recalculateBalance();
                        checkBudgetStatus();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        errorMessage.setValue("Ошибка загрузки транзакций: " + error.getMessage());
                        Log.e("FinanceViewModel", "Ошибка Firebase: " + error.getMessage());
                    }
                });
    }

    private void saveBudgetToFirebase(double budget) {
        String userId = getCurrentUserId();
        if (userId == null) {
            errorMessage.setValue("Пользователь не аутентифицирован");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("budget")
                .setValue(budget)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        errorMessage.setValue("Ошибка сохранения бюджета");
                    }
                });
    }

    private void checkBudgetStatus() {
        if (!"month".equals(budgetPeriod.getValue())) {
            budgetAlert.setValue(null);
            return;
        }

        double monthlyBudget = budget.getValue() != null ? budget.getValue() : 0.0;
        double monthlyExpenses = calculateMonthlyExpenses();

        if (monthlyExpenses >= monthlyBudget && monthlyBudget > 0) {
            budgetAlert.setValue(String.format("Ваш месячный бюджет израсходован! Потрачено: %.2f ₽. Увеличьте бюджет или добавьте доход.", monthlyExpenses));
        } else if (monthlyExpenses >= monthlyBudget * 0.9 && monthlyBudget > 0) {
            budgetAlert.setValue(String.format("Бюджет почти израсходован: %.2f ₽ из %.2f ₽. Рассмотрите увеличение бюджета.", monthlyExpenses, monthlyBudget));
        } else {
            budgetAlert.setValue(null);
        }
    }

    private double calculateMonthlyExpenses() {
        List<Transaction> currentTransactions = transactions.getValue();
        if (currentTransactions == null) return 0.0;

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        int currentYear = calendar.get(Calendar.YEAR);

        double totalExpenses = 0.0;
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        for (Transaction t : currentTransactions) {
            if ("expense".equals(t.type) && t.date != null) {
                try {
                    Date transactionDate = dateFormat.parse(t.date);
                    calendar.setTime(transactionDate);
                    int transactionMonth = calendar.get(Calendar.MONTH);
                    int transactionYear = calendar.get(Calendar.YEAR);

                    if (transactionMonth == currentMonth && transactionYear == currentYear) {
                        totalExpenses += t.amount != null ? t.amount : 0.0;
                    }
                } catch (Exception e) {
                    Log.e("FinanceViewModel", "Ошибка парсинга даты: " + t.date, e);
                }
            }
        }

        return totalExpenses;
    }
}