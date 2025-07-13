انت الآن ضمن تطبيق Android Studio 

تم انشاء ضمن الباك ايند ملفات تسجيل دخول وانشاء حساب

تمام، بناءً على قاعدة البيانات وملفات الإعداد التي لديك، سنقوم بإنشاء ملفات API (واجهات برمجة التطبيقات) بلغة PHP لعمليتي **تسجيل حساب جديد** و**تسجيل الدخول**. هذه الملفات ستتفاعل مع قاعدة بيانات MySQL وتُعيد استجابات بصيغة JSON إلى تطبيق الأندرويد.

---

### **أولاً: `register_user.php` (API لإنشاء حساب جديد)**

هذا الملف سيستقبل بيانات المستخدم الجديد، يتحقق من صحتها، يتأكد من أن رقم الهاتف غير مستخدم، ثم يجزئ كلمة المرور ويحفظ المستخدم في قاعدة البيانات.

```php
<?php
// register_user.php - واجهة برمجة تطبيقات لتسجيل مستخدم جديد

// تضمين ملفات الإعداد والدوال المساعدة
require_once 'functions.php';

// تعيين رأس الاستجابة ليكون JSON مع ترميز UTF-8
header('Content-Type: application/json; charset=utf-8');

// التحقق من أن الطلب من نوع POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo json_encode(['status' => 'error', 'message' => 'Invalid request method. Only POST is allowed.']);
    exit();
}

// 1. جمع البيانات المرسلة من التطبيق
$firstName = $_POST['firstName'] ?? '';
$lastName = $_POST['lastName'] ?? '';
$phoneNumber = $_POST['phoneNumber'] ?? '';
$residenceArea = $_POST['residenceArea'] ?? '';
$password = $_POST['password'] ?? ''; // كلمة المرور كنص عادي

// 2. التحقق من صحة المدخلات (Input Validation)
$errors = [];
if (empty($firstName) || empty($lastName) || empty($phoneNumber) || empty($password)) {
    $errors[] = 'يرجى ملء جميع الحقول الإلزامية (الاسم، الكنية، رقم الهاتف، كلمة المرور).';
}
if (strlen($password) < 6) {
    $errors[] = 'كلمة المرور يجب أن تكون 6 أحرف على الأقل.';
}
// تحقق من صحة رقم الهاتف (مثال: يبدأ بـ 09 ويتكون من 10 أرقام)
if (!preg_match('/^09[0-9]{8}$/', $phoneNumber)) {
    $errors[] = 'صيغة رقم الهاتف غير صحيحة.';
}

if (!empty($errors)) {
    // إرسال رسائل الأخطاء إذا وجدت
    echo json_encode(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $errors]);
    exit();
}

// 3. الاتصال بقاعدة البيانات
$pdo = get_db_connection();

try {
    // 4. التحقق مما إذا كان رقم الهاتف مسجل مسبقاً
    $stmt = $pdo->prepare("SELECT user_id FROM Users WHERE phone_number = ?");
    $stmt->execute([$phoneNumber]);
    if ($stmt->fetch()) {
        echo json_encode(['status' => 'error', 'message' => 'رقم الهاتف هذا مسجل بالفعل.']);
        exit();
    }

    // 5. تجزئة كلمة المرور (Hashing)
    $passwordHash = password_hash($password, PASSWORD_DEFAULT);

    // 6. إعداد استعلام إدخال المستخدم الجديد
    $stmt = $pdo->prepare("INSERT INTO Users (first_name, last_name, phone_number, residence_area, password_hash) VALUES (?, ?, ?, ?, ?)");
    
    // 7. تنفيذ الاستعلام
    $stmt->execute([
        $firstName,
        $lastName,
        $phoneNumber,
        $residenceArea,
        $passwordHash
    ]);

    // 8. (اختياري، لكن موصى به) ربط المستخدم الجديد بالكلمات والأرقام الافتراضية
    $newUserId = $pdo->lastInsertId(); // الحصول على ID المستخدم الجديد
    
    // جلب معرفات الكلمات الافتراضية
    $defaultKeywordsStmt = $pdo->query("SELECT keyword_id FROM Keywords WHERE is_default = TRUE");
    $defaultKeywordIds = $defaultKeywordsStmt->fetchAll(PDO::FETCH_COLUMN);
    
    // جلب معرفات الأرقام الافتراضية
    $defaultNumbersStmt = $pdo->query("SELECT number_id FROM EmergencyNumbers WHERE is_default = TRUE");
    $defaultNumberIds = $defaultNumbersStmt->fetchAll(PDO::FETCH_COLUMN);

    // إنشاء روابط للمستخدم الجديد (مثال: ربط كل كلمة افتراضية بكل رقم افتراضي)
    $linkStmt = $pdo->prepare("INSERT INTO KeywordNumberLinks (keyword_id, number_id, user_id) VALUES (?, ?, ?)");
    foreach ($defaultKeywordIds as $keywordId) {
        foreach ($defaultNumberIds as $numberId) {
            $linkStmt->execute([$keywordId, $numberId, $newUserId]);
        }
    }
    
    // 9. إرسال استجابة النجاح
    echo json_encode(['status' => 'success', 'message' => 'تم تسجيل الحساب بنجاح.', 'user_id' => $newUserId]);

} catch (PDOException $e) {
    // تسجيل الخطأ في سجلات الخادم
    error_log("Registration failed: " . $e->getMessage());
    // إرسال استجابة خطأ عامة
    echo json_encode(['status' => 'error', 'message' => 'حدث خطأ في قاعدة البيانات أثناء تسجيل الحساب.']);
}
?>
```

---

### **ثانياً: `login_user.php` (API لتسجيل الدخول)**

هذا الملف سيستقبل رقم الهاتف وكلمة المرور، يتحقق من وجود المستخدم، يقارن كلمة المرور مع التجزئة المخزنة، وإذا نجح، يعيد بيانات المستخدم الأساسية.

```php
<?php
// login_user.php - واجهة برمجة تطبيقات لتسجيل دخول المستخدم

require_once 'functions.php';

header('Content-Type: application/json; charset=utf-8');

// التحقق من أن الطلب من نوع POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo json_encode(['status' => 'error', 'message' => 'Invalid request method. Only POST is allowed.']);
    exit();
}

// 1. جمع البيانات المرسلة
$phoneNumber = $_POST['phoneNumber'] ?? '';
$password = $_POST['password'] ?? '';

// 2. التحقق من صحة المدخلات
if (empty($phoneNumber) || empty($password)) {
    echo json_encode(['status' => 'error', 'message' => 'يرجى إدخال رقم الهاتف وكلمة المرور.']);
    exit();
}

// 3. الاتصال بقاعدة البيانات
$pdo = get_db_connection();

try {
    // 4. البحث عن المستخدم باستخدام رقم الهاتف
    $stmt = $pdo->prepare("SELECT * FROM Users WHERE phone_number = ? AND is_active = TRUE");
    $stmt->execute([$phoneNumber]);
    $user = $stmt->fetch();

    if ($user) {
        // 5. التحقق من كلمة المرور
        if (password_verify($password, $user['password_hash'])) {
            // كلمة المرور صحيحة
            
            // إزالة تجزئة كلمة المرور من الاستجابة لأسباب أمنية
            unset($user['password_hash']);

            echo json_encode([
                'status' => 'success',
                'message' => 'تم تسجيل الدخول بنجاح.',
                'user' => $user // إرجاع بيانات المستخدم (id, name, etc.)
            ]);

        } else {
            // كلمة المرور غير صحيحة
            echo json_encode(['status' => 'error', 'message' => 'رقم الهاتف أو كلمة المرور غير صحيحة.']);
        }
    } else {
        // المستخدم غير موجود
        echo json_encode(['status' => 'error', 'message' => 'رقم الهاتف أو كلمة المرور غير صحيحة.']);
    }

} catch (PDOException $e) {
    error_log("Login failed: " . $e->getMessage());
    echo json_encode(['status' => 'error', 'message' => 'حدث خطأ في قاعدة البيانات أثناء تسجيل الدخول.']);
}
?>
```

---

### **كيفية اختبار ملفات API باستخدام Postman**

**لاختبار `register_user.php`:**

1.  **نوع الطلب:** `POST`
2.  **عنوان URL:** `http://192.168.43.121/security_app/register_user.php`
3.  **الجسم (Body):** اختر `x-www-form-urlencoded` وأضف المفاتيح والقيم التالية:
    *   `firstName`: `سعيد`
    *   `lastName`: `الخالد`
    *   `phoneNumber`: `0987654321` (استخدم رقم هاتف جديد في كل مرة تختبر فيها)
    *   `residenceArea`: `دمشق - الميدان`
    *   `password`: `mysecurepass123`
4.  انقر **Send**.
5.  **الاستجابة المتوقعة (عند النجاح):**
    ```json
    {
        "status": "success",
        "message": "تم تسجيل الحساب بنجاح.",
        "user_id": 4 
    }
    ```

**لاختبار `login_user.php`:**

1.  **نوع الطلب:** `POST`
2.  **عنوان URL:** `http://192.168.43.121/security_app/login_user.php`
3.  **الجسم (Body):** اختر `x-www-form-urlencoded` وأضف المفاتيح والقيم التالية:
    *   `phoneNumber`: `0987654321` (نفس الرقم الذي سجلته)
    *   `password`: `mysecurepass122` (كلمة مرور خاطئة للاختبار)
4.  انقر **Send**.
5.  **الاستجابة المتوقعة (عند الفشل):**
    ```json
    {
        "status": "error",
        "message": "رقم الهاتف أو كلمة المرور غير صحيحة."
    }
    ```
6.  **غيّر كلمة المرور إلى `mysecurepass123` (الصحيحة)** وانقر **Send** مرة أخرى.
7.  **الاستجابة المتوقعة (عند النجاح):**
    ```json
    {
        "status": "success",
        "message": "تم تسجيل الدخول بنجاح.",
        "user": {
            "user_id": 4,
            "first_name": "سعيد",
            "last_name": "الخالد",
            "phone_number": "0987654321",
            "residence_area": "دمشق - الميدان",
            "registration_date": "2025-07-11 15:00:00",
            "is_active": 1
        }
    }
    ```

هذه الملفات توفر لك واجهات API أساسية وقوية لتطبيقك، مع التحقق من صحة المدخلات وأمان كلمة المرور والتعامل مع الأخطاء.


قم بتعديل ملفات تسجيل الدخول وانشاء الحساب ضمن الاندرويد من اجل ان يتم ربط المشروع مع api

علماً انه لدينا مسبقاً الملف
