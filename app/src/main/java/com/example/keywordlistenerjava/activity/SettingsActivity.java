package com.example.keywordlistenerjava.activity;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.adapter.KeywordLinkAdapter;
import com.example.keywordlistenerjava.adapter.ManageItemsAdapter;
import com.example.keywordlistenerjava.db.dao.EmergencyNumberDao;
import com.example.keywordlistenerjava.db.dao.KeywordDao;
import com.example.keywordlistenerjava.db.dao.KeywordNumberLinkDao;
import com.example.keywordlistenerjava.db.entity.EmergencyNumber;
import com.example.keywordlistenerjava.db.entity.Keyword;
import com.example.keywordlistenerjava.db.entity.KeywordNumberLink;
import com.example.keywordlistenerjava.util.SharedPreferencesHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity 
    implements KeywordLinkAdapter.OnItemActionListener, ManageItemsAdapter.OnManageItemListener {

    private static final String TAG = "SettingsActivity";

    private EditText etNewKeyword, etNewPhoneNumber, etNewNumberDesc;
    private Button btnAddKeyword, btnAddNumber, btnLinkKeywordToNumber;
    private Spinner spinnerKeywords, spinnerNumbers;
    private RecyclerView recyclerViewLinks, recyclerViewKeywords, recyclerViewNumbers;
    private KeywordLinkAdapter linkAdapter;
    private ManageItemsAdapter keywordManagementAdapter, numberManagementAdapter;
    private ProgressBar progressBar;

    private KeywordDao keywordDao;
    private EmergencyNumberDao numberDao;
    private KeywordNumberLinkDao linkDao;

    private SharedPreferencesHelper prefsHelper;
    private ExecutorService dbExecutor;

    private int currentUserId;

    private List<Keyword> availableKeywords;
    private List<EmergencyNumber> availableNumbers;
    private Map<String, Integer> keywordDisplayToIdMap;
    private Map<String, Integer> numberDisplayToIdMap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsHelper = new SharedPreferencesHelper(this);
        currentUserId = prefsHelper.getLoggedInUserId();
        if (currentUserId == -1) {
            Toast.makeText(this, "خطأ: المستخدم غير مسجل الدخول.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();

        keywordDao = new KeywordDao(this);
        numberDao = new EmergencyNumberDao(this);
        linkDao = new KeywordNumberLinkDao(this);
        dbExecutor = Executors.newSingleThreadExecutor();

        btnAddKeyword.setOnClickListener(v -> addNewKeyword());
        btnAddNumber.setOnClickListener(v -> addNewNumber());
        btnLinkKeywordToNumber.setOnClickListener(v -> linkKeywordToNumber());

        loadAllData();
    }
    
    private void initializeViews() {
        etNewKeyword = findViewById(R.id.et_new_keyword);
        etNewPhoneNumber = findViewById(R.id.et_new_phone_number);
        etNewNumberDesc = findViewById(R.id.et_new_number_desc);
        btnAddKeyword = findViewById(R.id.btn_add_keyword);
        btnAddNumber = findViewById(R.id.btn_add_number);

        spinnerKeywords = findViewById(R.id.spinner_keywords);
        spinnerNumbers = findViewById(R.id.spinner_numbers);
        btnLinkKeywordToNumber = findViewById(R.id.btn_link_keyword_number);

        recyclerViewLinks = findViewById(R.id.recycler_view_links);
        recyclerViewKeywords = findViewById(R.id.recycler_view_keywords);
        recyclerViewNumbers = findViewById(R.id.recycler_view_numbers);
        progressBar = findViewById(R.id.progress_bar_settings);

        recyclerViewLinks.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewKeywords.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNumbers.setLayoutManager(new LinearLayoutManager(this));
    }

    private void addNewKeyword() {
        String keywordText = etNewKeyword.getText().toString().trim();
        if (keywordText.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال نص الكلمة المفتاحية.", Toast.LENGTH_SHORT).show();
            return;
        }
        String ppnFileName = keywordText.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_") + "_custom.ppn";

        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            keywordDao.open();
            // *** تعديل: التحقق من وجود الكلمة للمستخدم الحالي فقط ***
            Keyword existingKeyword = keywordDao.getKeywordByTextAndUser(keywordText, currentUserId);
            keywordDao.close();

            if (existingKeyword != null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "لقد أضفت هذه الكلمة المفتاحية بالفعل.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
                return;
            }

            Keyword newKeyword = new Keyword();
            newKeyword.setKeywordText(keywordText);
            newKeyword.setUserId(currentUserId);
            newKeyword.setPpnFileName(ppnFileName);
            newKeyword.setDefault(false);

            keywordDao.open();
            long id = keywordDao.addKeyword(newKeyword);
            keywordDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (id != -1) {
                    Toast.makeText(this, "تمت إضافة الكلمة المفتاحية بنجاح.", Toast.LENGTH_SHORT).show();
                    etNewKeyword.setText("");
                    loadAllData();
                } else {
                    Toast.makeText(this, "فشل إضافة الكلمة المفتاحية.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void addNewNumber() {
        String phoneNumber = etNewPhoneNumber.getText().toString().trim();
        String description = etNewNumberDesc.getText().toString().trim();
        if (phoneNumber.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال رقم الهاتف والوصف.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            numberDao.open();
            // *** تعديل: التحقق من وجود الرقم للمستخدم الحالي فقط ***
            EmergencyNumber existingNumber = numberDao.getEmergencyNumberByPhoneNumberAndUser(phoneNumber, currentUserId);
            numberDao.close();
            
            if (existingNumber != null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "لقد أضفت هذا الرقم بالفعل.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
                return;
            }

            EmergencyNumber newNumber = new EmergencyNumber();
            newNumber.setPhoneNumber(phoneNumber);
            newNumber.setNumberDescription(description);
            newNumber.setUserId(currentUserId);
            newNumber.setDefault(false);

            numberDao.open();
            long id = numberDao.addEmergencyNumber(newNumber);
            numberDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (id != -1) {
                    Toast.makeText(this, "تمت إضافة رقم الطوارئ بنجاح.", Toast.LENGTH_SHORT).show();
                    etNewPhoneNumber.setText("");
                    etNewNumberDesc.setText("");
                    loadAllData();
                } else {
                    Toast.makeText(this, "فشل إضافة رقم الطوارئ.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadAllData() {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            // جلب كل البيانات المطلوبة في background thread واحد
            keywordDao.open();
            availableKeywords = keywordDao.getAllKeywordsForUser(currentUserId);
            List<Keyword> userKeywords = keywordDao.getCustomKeywordsForUser(currentUserId);
            keywordDao.close();

            numberDao.open();
            availableNumbers = numberDao.getAllEmergencyNumbersForUser(currentUserId);
            List<EmergencyNumber> userNumbers = numberDao.getCustomNumbersForUser(currentUserId);
            numberDao.close();

            linkDao.open();
            List<KeywordNumberLink> links = linkDao.getAllLinksForUser(currentUserId);
            linkDao.close();

            // تجهيز البيانات للواجهة
            List<String> keywordDisplayTexts = new ArrayList<>();
            keywordDisplayToIdMap = new HashMap<>();
            for (Keyword k : availableKeywords) {
                String displayKeyword = k.getKeywordText() + (k.isDefault() ? " (افتراضي)" : "");
                keywordDisplayTexts.add(displayKeyword);
                keywordDisplayToIdMap.put(displayKeyword, k.getKeywordId());
            }

            List<String> numberDisplayTexts = new ArrayList<>();
            numberDisplayToIdMap = new HashMap<>();
            for (EmergencyNumber n : availableNumbers) {
                String displayNumber = n.getNumberDescription() + " (" + n.getPhoneNumber() + ")" + (n.isDefault() ? " (افتراضي)" : "");
                numberDisplayTexts.add(displayNumber);
                numberDisplayToIdMap.put(displayNumber, n.getNumberId());
            }

            List<KeywordLinkAdapter.LinkDisplayItem> displayItems = new ArrayList<>();
            keywordDao.open(); numberDao.open(); // فتح مرة أخرى للقراءة
            for (KeywordNumberLink link : links) {
                Keyword keyword = keywordDao.getKeywordById(link.getKeywordId());
                EmergencyNumber number = numberDao.getEmergencyNumberById(link.getNumberId());
                if (keyword != null && number != null) {
                    String displayKeywordText = keyword.getKeywordText() + (keyword.isDefault() ? " (افتراضي)" : "");
                    String displayNumberText = number.getNumberDescription() + " (" + number.getPhoneNumber() + ")" + (number.isDefault() ? " (افتراضي)" : "");
                    displayItems.add(new KeywordLinkAdapter.LinkDisplayItem(link.getLinkId(), displayKeywordText, number.getPhoneNumber(), displayNumberText, link.isActive()));
                }
            }
            keywordDao.close(); numberDao.close();

            runOnUiThread(() -> {
                // تحديث الـ Spinners
                ArrayAdapter<String> keywordAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, keywordDisplayTexts);
                keywordAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerKeywords.setAdapter(keywordAdapter);

                ArrayAdapter<String> numberAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, numberDisplayTexts);
                numberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerNumbers.setAdapter(numberAdapter);

                // تحديث RecyclerViews
                linkAdapter = new KeywordLinkAdapter(displayItems, this);
                recyclerViewLinks.setAdapter(linkAdapter);

                keywordManagementAdapter = new ManageItemsAdapter(new ArrayList<>(userKeywords), this, ManageItemsAdapter.ItemType.KEYWORD);
                recyclerViewKeywords.setAdapter(keywordManagementAdapter);

                numberManagementAdapter = new ManageItemsAdapter(new ArrayList<>(userNumbers), this, ManageItemsAdapter.ItemType.NUMBER);
                recyclerViewNumbers.setAdapter(numberManagementAdapter);

                progressBar.setVisibility(View.GONE);
            });
        });
    }

    private void linkKeywordToNumber() {
        if (spinnerKeywords.getSelectedItem() == null || spinnerNumbers.getSelectedItem() == null) {
            Toast.makeText(this, "يرجى اختيار كلمة ورقم للربط.", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedKeywordDisplay = spinnerKeywords.getSelectedItem().toString();
        String selectedNumberDisplay = spinnerNumbers.getSelectedItem().toString();

        final int keywordId = keywordDisplayToIdMap.get(selectedKeywordDisplay);
        final int numberId = numberDisplayToIdMap.get(selectedNumberDisplay);

        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            linkDao.open();
            if (linkDao.linkExists(currentUserId, keywordId, numberId)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "هذا الربط موجود بالفعل.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
                linkDao.close();
                return;
            }

            KeywordNumberLink newLink = new KeywordNumberLink();
            newLink.setKeywordId(keywordId);
            newLink.setNumberId(numberId);
            newLink.setUserId(currentUserId);
            newLink.setActive(true);

            long id = linkDao.addLink(newLink);
            linkDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (id != -1) {
                    Toast.makeText(this, "تم الربط بنجاح.", Toast.LENGTH_SHORT).show();
                    loadAllData();
                } else {
                    Toast.makeText(this, "فشل الربط.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- OnItemActionListener Methods (from KeywordLinkAdapter) ---
    @Override
    public void onLinkToggle(int linkId, boolean isChecked) {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            linkDao.open();
            int rowsAffected = linkDao.updateLinkStatus(linkId, isChecked);
            linkDao.close();
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (rowsAffected <= 0) Toast.makeText(this, "فشل تحديث حالة الربط.", Toast.LENGTH_SHORT).show();
            });
        });
    }
    @Override
    public void onLinkDelete(int linkId) {
        showConfirmationDialog("حذف الربط", "هل أنت متأكد من حذف هذا الربط؟", () -> {
            progressBar.setVisibility(View.VISIBLE);
            dbExecutor.execute(() -> {
                linkDao.open();
                int rowsAffected = linkDao.deleteLink(linkId);
                linkDao.close();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (rowsAffected > 0) {
                        Toast.makeText(this, "تم حذف الربط بنجاح.", Toast.LENGTH_SHORT).show();
                        loadAllData();
                    } else {
                        Toast.makeText(this, "فشل حذف الربط.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    // --- OnManageItemListener Methods (from ManageItemsAdapter) ---
    @Override
    public void onItemEdit(Object item) {
        if (item instanceof Keyword) {
            showEditKeywordDialog((Keyword) item);
        } else if (item instanceof EmergencyNumber) {
            showEditNumberDialog((EmergencyNumber) item);
        }
    }
    @Override
    public void onItemDelete(Object item) {
        if (item instanceof Keyword) {
            // *** تعديل: لا يمكن للمستخدم حذف الكلمات الافتراضية ***
            Keyword keyword = (Keyword) item;
            if (keyword.isDefault()) {
                Toast.makeText(this, "لا يمكن حذف الكلمات المفتاحية الافتراضية.", Toast.LENGTH_SHORT).show();
                return;
            }
            showConfirmationDialog("حذف الكلمة", "سيؤدي هذا إلى حذف الكلمة وجميع روابطها. هل أنت متأكد؟", () -> {
                progressBar.setVisibility(View.VISIBLE);
                dbExecutor.execute(() -> {
                    keywordDao.open();
                    int rowsAffected = keywordDao.deleteCustomKeyword(keyword.getKeywordId(), currentUserId);
                    keywordDao.close();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (rowsAffected > 0) {
                            Toast.makeText(this, "تم حذف الكلمة بنجاح.", Toast.LENGTH_SHORT).show();
                            loadAllData();
                        } else {
                            Toast.makeText(this, "فشل حذف الكلمة.", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
        } else if (item instanceof EmergencyNumber) {
            // *** تعديل: لا يمكن للمستخدم حذف الأرقام الافتراضية ***
            EmergencyNumber number = (EmergencyNumber) item;
            if (number.isDefault()) {
                Toast.makeText(this, "لا يمكن حذف أرقام الطوارئ الافتراضية.", Toast.LENGTH_SHORT).show();
                return;
            }
            showConfirmationDialog("حذف الرقم", "سيؤدي هذا إلى حذف الرقم وجميع روابطه. هل أنت متأكد؟", () -> {
                progressBar.setVisibility(View.VISIBLE);
                dbExecutor.execute(() -> {
                    numberDao.open();
                    int rowsAffected = numberDao.deleteCustomEmergencyNumber(number.getNumberId(), currentUserId);
                    numberDao.close();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (rowsAffected > 0) {
                            Toast.makeText(this, "تم حذف الرقم بنجاح.", Toast.LENGTH_SHORT).show();
                            loadAllData();
                        } else {
                            Toast.makeText(this, "فشل حذف الرقم.", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
        }
    }

    // --- Dialogs for Edit/Delete Confirmation ---
    private void showEditKeywordDialog(final Keyword keyword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("تعديل الكلمة المفتاحية");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(keyword.getKeywordText());
        builder.setView(input);
        builder.setPositiveButton("حفظ", (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty() && !newText.equals(keyword.getKeywordText())) {
                progressBar.setVisibility(View.VISIBLE);
                dbExecutor.execute(() -> {
                    keywordDao.open();
                    keywordDao.updateCustomKeyword(keyword.getKeywordId(), newText, currentUserId);
                    keywordDao.close();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "تم تعديل الكلمة بنجاح.", Toast.LENGTH_SHORT).show();
                        loadAllData();
                    });
                });
            }
        });
        builder.setNegativeButton("إلغاء", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void showEditNumberDialog(final EmergencyNumber number) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("تعديل رقم الطوارئ");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputPhone = new EditText(this);
        inputPhone.setHint("رقم الهاتف");
        inputPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        inputPhone.setText(number.getPhoneNumber());
        layout.addView(inputPhone);

        final EditText inputDesc = new EditText(this);
        inputDesc.setHint("الوصف");
        inputDesc.setInputType(InputType.TYPE_CLASS_TEXT);
        inputDesc.setText(number.getNumberDescription());
        layout.addView(inputDesc);

        builder.setView(layout);
        builder.setPositiveButton("حفظ", (dialog, which) -> {
            String newPhone = inputPhone.getText().toString().trim();
            String newDesc = inputDesc.getText().toString().trim();
            if (!newPhone.isEmpty() && !newDesc.isEmpty()) {
                progressBar.setVisibility(View.VISIBLE);
                dbExecutor.execute(() -> {
                    numberDao.open();
                    numberDao.updateCustomEmergencyNumber(number.getNumberId(), newPhone, newDesc, currentUserId);
                    numberDao.close();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "تم تعديل الرقم بنجاح.", Toast.LENGTH_SHORT).show();
                        loadAllData();
                    });
                });
            }
        });
        builder.setNegativeButton("إلغاء", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("نعم", (dialog, which) -> onConfirm.run())
                .setNegativeButton("لا", null)
                .show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }
}