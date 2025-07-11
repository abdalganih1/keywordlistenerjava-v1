package com.example.keywordlistenerjava.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.keywordlistenerjava.R;
import com.example.keywordlistenerjava.adapter.KeywordLinkAdapter;
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

public class SettingsActivity extends AppCompatActivity implements KeywordLinkAdapter.OnItemActionListener {

    private static final String TAG = "SettingsActivity";

    // --- عناصر الواجهة لإضافة كلمة/رقم ---
    private EditText etNewKeyword, etNewPhoneNumber, etNewNumberDesc;
    private Button btnAddKeyword, btnAddNumber;
    private Button btnTestKeyword;

    // --- عناصر الواجهة للربط ---
    private Spinner spinnerKeywords, spinnerNumbers;
    private Button btnLinkKeywordToNumber;

    // --- عناصر الواجهة لعرض الروابط ---
    private RecyclerView recyclerViewLinks;
    private KeywordLinkAdapter adapter;
    private ProgressBar progressBar;

    // --- DAOs ---
    private KeywordDao keywordDao;
    private EmergencyNumberDao numberDao;
    private KeywordNumberLinkDao linkDao;

    // --- Helpers ---
    private SharedPreferencesHelper prefsHelper;
    private ExecutorService dbExecutor;

    private int currentUserId;

    // --- لتخزين الكلمات والأرقام المتاحة مع معرفاتها ---
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

        keywordDao = new KeywordDao(this);
        numberDao = new EmergencyNumberDao(this);
        linkDao = new KeywordNumberLinkDao(this);
        dbExecutor = Executors.newSingleThreadExecutor();

        etNewKeyword = findViewById(R.id.et_new_keyword);
        etNewPhoneNumber = findViewById(R.id.et_new_phone_number);
        etNewNumberDesc = findViewById(R.id.et_new_number_desc);
        btnAddKeyword = findViewById(R.id.btn_add_keyword);
        btnAddNumber = findViewById(R.id.btn_add_number);
        btnTestKeyword = findViewById(R.id.btn_test_keyword);

        spinnerKeywords = findViewById(R.id.spinner_keywords);
        spinnerNumbers = findViewById(R.id.spinner_numbers);
        btnLinkKeywordToNumber = findViewById(R.id.btn_link_keyword_number);

        recyclerViewLinks = findViewById(R.id.recycler_view_links);
        progressBar = findViewById(R.id.progress_bar_settings);

        recyclerViewLinks.setLayoutManager(new LinearLayoutManager(this));

        btnAddKeyword.setOnClickListener(v -> addNewKeyword());
        btnAddNumber.setOnClickListener(v -> addNewNumber());
        btnTestKeyword.setOnClickListener(v -> testKeyword());
        btnLinkKeywordToNumber.setOnClickListener(v -> linkKeywordToNumber());

        loadAllKeywordsAndNumbers();
        loadKeywordLinks();
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
            Keyword existingKeyword = keywordDao.getKeywordByText(keywordText);
            if (existingKeyword != null && (existingKeyword.isDefault() || (existingKeyword.getUserId() != null && existingKeyword.getUserId() == currentUserId))) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "هذه الكلمة المفتاحية موجودة بالفعل.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
                keywordDao.close();
                return;
            }

            Keyword newKeyword = new Keyword();
            newKeyword.setKeywordText(keywordText);
            newKeyword.setUserId(currentUserId);
            newKeyword.setPpnFileName(ppnFileName);
            newKeyword.setDefault(false);

            long id = keywordDao.addKeyword(newKeyword);
            keywordDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (id != -1) {
                    Toast.makeText(this, "تمت إضافة الكلمة المفتاحية بنجاح.", Toast.LENGTH_SHORT).show();
                    etNewKeyword.setText("");
                    loadAllKeywordsAndNumbers();
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
            EmergencyNumber existingNumber = numberDao.getEmergencyNumberByPhoneNumber(phoneNumber);
            if (existingNumber != null && (existingNumber.isDefault() || (existingNumber.getUserId() != null && existingNumber.getUserId() == currentUserId))) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "رقم الهاتف هذا موجود بالفعل.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
                numberDao.close();
                return;
            }

            EmergencyNumber newNumber = new EmergencyNumber();
            newNumber.setPhoneNumber(phoneNumber);
            newNumber.setNumberDescription(description);
            newNumber.setUserId(currentUserId);
            newNumber.setDefault(false);

            long id = numberDao.addEmergencyNumber(newNumber);
            numberDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (id != -1) {
                    Toast.makeText(this, "تمت إضافة رقم الطوارئ بنجاح.", Toast.LENGTH_SHORT).show();
                    etNewPhoneNumber.setText("");
                    etNewNumberDesc.setText("");
                    loadAllKeywordsAndNumbers();
                } else {
                    Toast.makeText(this, "فشل إضافة رقم الطوارئ.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadAllKeywordsAndNumbers() {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            keywordDao.open();
            availableKeywords = keywordDao.getAllKeywordsForUser(currentUserId);
            keywordDao.close();

            numberDao.open();
            availableNumbers = numberDao.getAllEmergencyNumbersForUser(currentUserId);
            numberDao.close();

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

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                ArrayAdapter<String> keywordAdapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        keywordDisplayTexts
                );
                keywordAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerKeywords.setAdapter(keywordAdapter);

                ArrayAdapter<String> numberAdapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        numberDisplayTexts
                );
                numberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerNumbers.setAdapter(numberAdapter);
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
                    loadKeywordLinks();
                } else {
                    Toast.makeText(this, "فشل الربط.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }


    private void loadKeywordLinks() {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            linkDao.open();
            // *** تم التصحيح: استخدام الدالة الجديدة لجلب كل الروابط ***
            List<KeywordNumberLink> links = linkDao.getAllLinksForUser(currentUserId);
            linkDao.close();

            List<KeywordLinkAdapter.LinkDisplayItem> displayItems = new ArrayList<>();
            keywordDao.open();
            numberDao.open();
            for (KeywordNumberLink link : links) {
                Keyword keyword = keywordDao.getKeywordById(link.getKeywordId());
                EmergencyNumber number = numberDao.getEmergencyNumberById(link.getNumberId());
                if (keyword != null && number != null) {
                    String displayKeywordText = keyword.getKeywordText() + (keyword.isDefault() ? " (افتراضي)" : "");
                    String displayNumberText = number.getNumberDescription() + " (" + number.getPhoneNumber() + ")" + (number.isDefault() ? " (افتراضي)" : "");

                    displayItems.add(new KeywordLinkAdapter.LinkDisplayItem(
                            link.getLinkId(),
                            displayKeywordText,
                            number.getPhoneNumber(),
                            displayNumberText,
                            link.isActive()
                    ));
                }
            }
            keywordDao.close();
            numberDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                adapter = new KeywordLinkAdapter(displayItems, this);
                recyclerViewLinks.setAdapter(adapter);
                if (displayItems.isEmpty()) {
                    Toast.makeText(this, "لا توجد روابط كلمات مفتاحية/أرقام.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void testKeyword() {
        String keywordToTest = etNewKeyword.getText().toString().trim();
        if (keywordToTest.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال كلمة لاختبارها.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "للاختبار: اذهب إلى الشاشة الرئيسية، شغل الاستماع، ثم قل '" + keywordToTest + "' بوضوح.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLinkToggle(int linkId, boolean isChecked) {
        progressBar.setVisibility(View.VISIBLE);
        dbExecutor.execute(() -> {
            linkDao.open();
            int rowsAffected = linkDao.updateLinkStatus(linkId, isChecked);
            linkDao.close();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (rowsAffected > 0) {
                    Toast.makeText(this, "تم تحديث حالة الربط.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "فشل تحديث حالة الربط.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onLinkDelete(int linkId) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الربط")
                .setMessage("هل أنت متأكد من حذف هذا الربط؟")
                .setPositiveButton("نعم", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    dbExecutor.execute(() -> {
                        linkDao.open();
                        int rowsAffected = linkDao.deleteLink(linkId);
                        linkDao.close();

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (rowsAffected > 0) {
                                Toast.makeText(this, "تم حذف الربط بنجاح.", Toast.LENGTH_SHORT).show();
                                loadKeywordLinks();
                            } else {
                                Toast.makeText(this, "فشل حذف الربط.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton("لا", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }
}