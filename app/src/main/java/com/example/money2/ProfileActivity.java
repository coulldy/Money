package com.example.money2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    private TextView usernameView, emailView, tvIncome, tvExpense;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        setupProfileView();
        loadUserData();
        setupButtonListeners();
        loadIncomeExpenseData();
    }

    private void setupProfileView() {
        usernameView = findViewById(R.id.usernameView);
        emailView = findViewById(R.id.emailView);
        tvIncome = findViewById(R.id.tvIncome);
        tvExpense = findViewById(R.id.tvExpense);
    }

    private void loadUserData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) {
            usernameView.setText(R.string.guest);
            emailView.setText(R.string.email_hint);
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String username = snapshot.child("username").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        usernameView.setText(username != null ? username : getString(R.string.username_hint));
                        emailView.setText(email != null ? email : getString(R.string.email_hint));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        usernameView.setText(R.string.username_hint);
                        emailView.setText(R.string.email_hint);
                    }
                });
    }

    private void loadIncomeExpenseData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) {
            tvIncome.setText("0.00 ₽");
            tvExpense.setText("0.00 ₽");
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users").child(userId).child("transactions")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double totalIncome = 0;
                        double totalExpense = 0;

                        // Настройка временного диапазона (последний месяц)
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.MONTH, -1); // Отнимаем 1 месяц
                        Date oneMonthAgo = cal.getTime();
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

                        for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                            FinanceViewModel.Transaction transaction = transactionSnapshot.getValue(FinanceViewModel.Transaction.class);
                            if (transaction != null && transaction.date != null && transaction.amount != null) {
                                try {
                                    Date transactionDate = sdf.parse(transaction.date);
                                    // Проверяем, попадает ли дата транзакции в последний месяц
                                    if (transactionDate != null && (transactionDate.after(oneMonthAgo) || transactionDate.equals(oneMonthAgo))) {
                                        if ("income".equalsIgnoreCase(transaction.type)) {
                                            totalIncome += transaction.amount;
                                        } else if ("expense".equalsIgnoreCase(transaction.type)) {
                                            totalExpense += transaction.amount;
                                        }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        tvIncome.setText(String.format(Locale.getDefault(), "+%.2f ₽", totalIncome));
                        tvExpense.setText(String.format(Locale.getDefault(), "-%.2f ₽", totalExpense));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvIncome.setText("0.00 ₽");
                        tvExpense.setText("0.00 ₽");
                        Toast.makeText(ProfileActivity.this, "Ошибка загрузки транзакций", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupButtonListeners() {
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnEditProfile = findViewById(R.id.btnEditProfile);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());

        btnEditProfile.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            if (userId == null) {
                Toast.makeText(ProfileActivity.this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(ProfileActivity.this, EditActivity.class);
            intent.putExtra("type", "profile");
            intent.putExtra("userId", userId);
            startActivity(intent);
            Toast.makeText(ProfileActivity.this, "Редактирование профиля", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            Toast.makeText(ProfileActivity.this, "Выход выполнен", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}