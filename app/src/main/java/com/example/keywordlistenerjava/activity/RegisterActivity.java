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
import com.example.keywordlistenerjava.util.PasswordHasher; // للاستخدام التجريبي
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.util.regex.Pattern; // للتحقق من رقم الهاتف

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
        etResidence = findViewById(R.id.et_residence);
        etPassword = findViewById(R.id.et_password);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String phoneNumber = etPhoneNumber.getText().toString().trim();
        String residence = etResidence.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateInputs(firstName, lastName, phoneNumber, password)) {
            return;
        }

        User newUser = new User();
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setPhoneNumber(phoneNumber);
        newUser.setResidenceArea(residence);
        newUser.setPasswordHash(password); // PasswordHasher will handle hashing inside DAO

        // Perform DB operation on a background thread
        new Thread(() -> {
            userDao.open();
            long userId = userDao.registerUser(newUser); // This handles hashing and default linking
            userDao.close();

            runOnUiThread(() -> { // Update UI on the main thread
                if (userId != -1) {
                    Toast.makeText(this, "تم تسجيل الحساب بنجاح", Toast.LENGTH_SHORT).show();
                    // Log in the newly registered user
                    prefsHelper.setLoggedInUser((int) userId);
                    // Navigate to Main Activity
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear back stack
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "فشل تسجيل الحساب، قد يكون رقم الهاتف مستخدمًا بالفعل", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private boolean validateInputs(String firstName, String lastName, String phoneNumber, String password) {
        if (firstName.isEmpty() || lastName.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "يرجى ملء جميع الحقول المطلوبة", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 6 أحرف على الأقل", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Simple phone number validation (can be improved)
        if (!Pattern.matches("^09[0-9]{8}$", phoneNumber)) { // Assuming Syrian mobile numbers start with 09 and are 10 digits
            Toast.makeText(this, "رقم الهاتف غير صحيح (مثال: 09XXXXXXXX)", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Optional: If you want to handle lifecycle of DAO.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If DAO is used by other threads or in a singleton, you might not close here.
        // For this example, it's fine as a new DAO is created per activity/thread.
        // userDao.close(); // Closed in the thread's finally block or similar.
    }
}