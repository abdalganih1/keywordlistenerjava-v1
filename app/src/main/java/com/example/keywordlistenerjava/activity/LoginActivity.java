package com.example.keywordlistenerjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.db.dao.UserDao;
import com.example.keywordlistenerjava.db.entity.User;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText etPhoneNumber, etPassword;
    private Button btnLogin;
    private TextView tvRegisterLink;
    private UserDao userDao;
    private SharedPreferencesHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userDao = new UserDao(this);
        prefsHelper = new SharedPreferencesHelper(this);

        // Check if user is already logged in
        if (prefsHelper.isLoggedIn()) {
            Log.d(TAG, "User already logged in. Navigating to MainActivity.");
            navigateToMainActivity();
            return; // Stop further execution of onCreate
        }

        etPhoneNumber = findViewById(R.id.et_login_phone_number);
        etPassword = findViewById(R.id.et_login_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegisterLink = findViewById(R.id.tv_register_link);

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegisterLink.setOnClickListener(v -> navigateToRegisterActivity());
    }

    private void loginUser() {
        String phoneNumber = etPhoneNumber.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (phoneNumber.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال رقم الهاتف وكلمة المرور", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            userDao.open();
            User user = userDao.authenticateUser(phoneNumber, password);
            userDao.close();

            runOnUiThread(() -> {
                if (user != null) {
                    Toast.makeText(this, "تم تسجيل الدخول بنجاح", Toast.LENGTH_SHORT).show();
                    prefsHelper.setLoggedInUser(user.getUserId());
                    navigateToMainActivity();
                } else {
                    Toast.makeText(this, "فشل تسجيل الدخول: رقم الهاتف أو كلمة المرور غير صحيحة", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void navigateToRegisterActivity() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Finish LoginActivity so user cannot go back to it
    }
}