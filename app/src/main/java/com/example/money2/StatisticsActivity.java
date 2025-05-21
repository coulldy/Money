package com.example.money2;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {
    private FinanceViewModel viewModel;
    private LinearLayout statsContainer;
    private RecyclerView rvIncomeTransactions, rvExpenseTransactions;
    private TransactionAdapter incomeAdapter, expenseAdapter;
    private Spinner categoryFilterSpinner;
    private TextView incomeSummary, expenseSummary;
    private PieChart incomePieChart, expensePieChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        statsContainer = findViewById(R.id.statsContainer);
        rvIncomeTransactions = findViewById(R.id.rvIncomeTransactions);
        rvExpenseTransactions = findViewById(R.id.rvExpenseTransactions);
        categoryFilterSpinner = findViewById(R.id.categoryFilterSpinner);
        incomeSummary = findViewById(R.id.incomeSummary);
        expenseSummary = findViewById(R.id.expenseSummary);
        incomePieChart = findViewById(R.id.incomePieChart);
        expensePieChart = findViewById(R.id.expensePieChart);

        // Настройка RecyclerView
        rvIncomeTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvExpenseTransactions.setLayoutManager(new LinearLayoutManager(this));
        incomeAdapter = new TransactionAdapter(new ArrayList<>(), transaction -> {});
        expenseAdapter = new TransactionAdapter(new ArrayList<>(), transaction -> {});
        rvIncomeTransactions.setAdapter(incomeAdapter);
        rvExpenseTransactions.setAdapter(expenseAdapter);

        // Настройка PieCharts
        setupPieChart(incomePieChart, "Доходы по категориям");
        setupPieChart(expensePieChart, "Расходы по категориям");

        // Настройка кнопки "Назад"
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Инициализация ViewModel
        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);

        // Настройка Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getCategoriesForSpinner());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categoryFilterSpinner.setAdapter(spinnerAdapter);

        // Обработка выбора категории
        categoryFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = (String) parent.getItemAtPosition(position);
                Log.d("StatisticsActivity", "Выбрана категория: " + selectedCategory);
                displayStatistics(viewModel.getTransactions().getValue(), selectedCategory);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d("StatisticsActivity", "Категория не выбрана");
                displayStatistics(viewModel.getTransactions().getValue(), null);
            }
        });

        // Проверка авторизации
        if (viewModel.getCurrentUserId() == null) {
            TextView errorView = new TextView(this);
            errorView.setText("Пожалуйста, авторизуйтесь для просмотра статистики");
            errorView.setTextSize(16);
            errorView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            statsContainer.removeAllViews();
            statsContainer.addView(errorView);
            return;
        }

        // Наблюдение за данными
        viewModel.getTransactions().observe(this, transactions -> {
            String selectedCategory = categoryFilterSpinner.getSelectedItem() != null
                    ? categoryFilterSpinner.getSelectedItem().toString()
                    : "Все";
            Log.d("StatisticsActivity", "Обновление данных, выбрана категория: " + selectedCategory);
            displayStatistics(transactions, selectedCategory);
        });
    }

    private void setupPieChart(PieChart pieChart, String label) {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setCenterText(label);
        pieChart.setCenterTextSize(14f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(ContextCompat.getColor(this, android.R.color.white));
        pieChart.getLegend().setEnabled(true);
    }

    private List<String> getCategoriesForSpinner() {
        List<String> allCategories = new ArrayList<>();
        allCategories.add("Все");

        viewModel.setCurrentTransactionType("income");
        allCategories.addAll(viewModel.getCategoriesForCurrentType());

        viewModel.setCurrentTransactionType("expense");
        for (String category : viewModel.getCategoriesForCurrentType()) {
            if (!allCategories.contains(category)) {
                allCategories.add(category);
            }
        }

        Log.d("StatisticsActivity", "Категории для Spinner: " + allCategories.toString());
        return allCategories;
    }

    private void displayStatistics(List<FinanceViewModel.Transaction> transactions, String category) {
        if (transactions == null || statsContainer == null) {
            Log.e("StatisticsActivity", "Транзакции или контейнер null");
            return;
        }

        Log.d("StatisticsActivity", "Получено транзакций: " + transactions.size());

        if (transactions.isEmpty()) {
            incomeSummary.setText("Доходов не найдено");
            expenseSummary.setText("Расходов не найдено");
            incomePieChart.setData(null);
            expensePieChart.setData(null);
            incomePieChart.invalidate();
            expensePieChart.invalidate();
            incomeAdapter.updateTransactions(new ArrayList<>());
            expenseAdapter.updateTransactions(new ArrayList<>());
            return;
        }

        List<FinanceViewModel.Transaction> incomeTransactions = new ArrayList<>();
        List<FinanceViewModel.Transaction> expenseTransactions = new ArrayList<>();
        double totalIncome = 0.0;
        double totalExpense = 0.0;
        Map<String, Double> incomeByCategory = new HashMap<>();
        Map<String, Double> expenseByCategory = new HashMap<>();

        for (FinanceViewModel.Transaction t : transactions) {
            Log.d("StatisticsActivity", "Транзакция: id=" + t.id + ", тип=" + t.type + ", категория=" + t.category);
            if ("income".equals(t.type) && (category == null || category.equals("Все") || t.category.equals(category))) {
                incomeTransactions.add(t);
                double amount = t.amount != null ? t.amount : 0.0;
                totalIncome += amount;
                incomeByCategory.put(t.category, incomeByCategory.getOrDefault(t.category, 0.0) + amount);
            } else if ("expense".equals(t.type) && (category == null || category.equals("Все") || t.category.equals(category))) {
                expenseTransactions.add(t);
                double amount = t.amount != null ? t.amount : 0.0;
                totalExpense += amount;
                expenseByCategory.put(t.category, expenseByCategory.getOrDefault(t.category, 0.0) + amount);
            }
        }

        // Формирование сводки
        String incomeSummaryText = category != null && !category.equals("Все")
                ? "Доходы по категории '" + category + "': " + String.format("%.2f ₽", totalIncome)
                : "Всего доходов: " + String.format("%.2f ₽", totalIncome);
        incomeSummary.setText(incomeSummaryText);

        String expenseSummaryText = category != null && !category.equals("Все")
                ? "Расходы по категории '" + category + "': " + String.format("%.2f ₽", totalExpense)
                : "Всего расходов: " + String.format("%.2f ₽", totalExpense);
        expenseSummary.setText(expenseSummaryText);

        // Обновление PieCharts
        updatePieChart(incomePieChart, incomeByCategory, totalIncome);
        updatePieChart(expensePieChart, expenseByCategory, totalExpense);

        // Обновление RecyclerViews
        incomeAdapter.updateTransactions(incomeTransactions);
        expenseAdapter.updateTransactions(expenseTransactions);
    }

    private void updatePieChart(PieChart pieChart, Map<String, Double> data, double total) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            if (total > 0) {
                float percentage = (float) (entry.getValue() / total * 100);
                entries.add(new PieEntry(percentage, entry.getKey() + " (" + String.format("%.2f ₽", entry.getValue()) + ")"));
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(ContextCompat.getColor(this, android.R.color.white));

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.PercentFormatter(pieChart));
        pieChart.setData(entries.isEmpty() ? null : pieData);
        pieChart.invalidate();
    }
}