package com.example.money2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.money2.databinding.ActivityRegistyBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class RegistyActivity extends AppCompatActivity {
    private ActivityRegistyBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegistyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.backBtn.setOnClickListener(v -> finish());

        binding.signUpBtn.setOnClickListener(v -> {
            if (validateInput()) {
                registerUser(
                        binding.emailEt.getText().toString().trim(),
                        binding.passwordEt.getText().toString().trim(),
                        binding.usernameEt.getText().toString().trim()
                );
            }
        });
    }

    private boolean validateInput() {
        String email = binding.emailEt.getText().toString().trim();
        String password = binding.passwordEt.getText().toString().trim();
        String username = binding.usernameEt.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Все поля должны быть заполнены", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Пароль должен содержать минимум 6 символов", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Введите корректный email", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void registerUser(String email, String password, String username) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("RegistyActivity", "User created successfully");
                        saveUserData(auth.getCurrentUser().getUid(), username, email);
                    } else {
                        String errorMessage = "Ошибка регистрации";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                        Log.e("RegistyActivity", "Registration failed", task.getException());
                    }
                });
    }

    private void saveUserData(String userId, String username, String email) {
        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put("email", email);
        userInfo.put("username", username);

        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(userId)
                .setValue(userInfo)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("RegistyActivity", "User data saved successfully");
                        navigateToMainActivity();
                    } else {
                        String errorMessage = "Ошибка сохранения данных";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                        Log.e("RegistyActivity", "Failed to save user data", task.getException());
                    }
                });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}