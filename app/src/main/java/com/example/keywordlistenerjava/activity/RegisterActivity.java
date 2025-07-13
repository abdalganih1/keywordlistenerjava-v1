package com.example.keywordlistenerjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.db.dao.UserDao;
import com.example.keywordlistenerjava.db.entity.User;
import com.example.keywordlistenerjava.util.PasswordHasher;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etFirstName, etLastName, etPhoneNumber, etResidence, etPassword;
    private Button btnRegister;
    private UserDao userDao;
    private SharedPreferencesHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userDao = new UserDao(this);
        prefsHelper = new SharedPreferencesHelper(this);

        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etPhoneNumber = findViewById(R.id.et_phone_number);
        etResidence = findViewById(R.id.et_residence); // هذا هو حقل مكان السكن
        etPassword = findViewById(R.id.et_password);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String phoneNumber = etPhoneNumber.getText().toString().trim();
        String residence = etResidence.getText().toString().trim(); // جلب مكان السكن
        String password = etPassword.getText().toString().trim();

        // مكان السكن ليس حقلًا إلزاميًا (يمكن أن يكون فارغًا)، لكن تحقق من الأساسيات
        if (firstName.isEmpty() || lastName.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "يرجى ملء جميع الحقول المطلوبة.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 6 أحرف على الأقل.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Pattern.matches("^09[0-9]{8}$", phoneNumber)) {
            Toast.makeText(this, "رقم الهاتف غير صحيح (مثال: 09XXXXXXXX).", Toast.LENGTH_SHORT).show();
            return;
        }

        User newUser = new User();
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setPhoneNumber(phoneNumber);
        newUser.setResidenceArea(residence); // تعيين مكان السكن
        newUser.setPasswordHash(password); // Hashing will be done in UserDao

        new Thread(() -> {
            userDao.open();
            long userId = userDao.registerUser(newUser);
            userDao.close();

            runOnUiThread(() -> {
                if (userId != -1) {
                    Log.i(TAG, "User registered successfully with ID: " + userId);
                    Toast.makeText(this, "تم تسجيل الحساب بنجاح.", Toast.LENGTH_SHORT).show();
                    prefsHelper.setLoggedInUser((int) userId);
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "فشل تسجيل الحساب، قد يكون رقم الهاتف مستخدمًا بالفعل.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}