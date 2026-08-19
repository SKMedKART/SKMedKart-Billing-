package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DB extends SQLiteOpenHelper {

    // IMPORTANT: increase version so Android runs onUpgrade()
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;

        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.expiry = expiry;
        }
    }

    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;

        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId = medicineId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }
    }

    public DB(Context context) {
        super(context, "skmedkart.db", null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE customers(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT)"
        );

        db.execSQL(
                "CREATE TABLE medicines(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL," +
                        "expiry TEXT)"
        );

        db.execSQL(
                "CREATE TABLE bills(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "created TEXT NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE bill_items(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "medicine_id INTEGER NOT NULL," +
                        "medicine_name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "qty INTEGER NOT NULL," +
                        "amount REAL NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_items(" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "bill_id INTEGER NOT NULL," +
                            "medicine_id INTEGER NOT NULL," +
                            "medicine_name TEXT NOT NULL," +
                            "price REAL NOT NULL," +
                            "qty INTEGER NOT NULL," +
                            "amount REAL NOT NULL)"
            );
        }

        // Version 3 is used for the corrected stock-deduction code.
        // Existing medicines, customers and bills are NOT deleted.
    }

    public long addCustomer(String name, String phone, String notes) {

        ContentValues v = new ContentValues();

        v.put("name", name);
        v.put("phone", phone);
        v.put("notes", notes);

        return getWritableDatabase().insert("customers", null, v);
    }

    public long addMedicine(String name, double price, int stock, String expiry) {

        ContentValues v = new ContentValues();

        v.put("name", name);
        v.put("price", price);
        v.put("stock", stock);
        v.put("expiry", expiry);

        return getWritableDatabase().insert("medicines", null, v);
    }

    /**
     * Save bill + bill items + deduct stock.
     *
     * Everything happens inside ONE transaction.
     * If stock is insufficient, NOTHING is saved.
     */
    public long addBillWithItems(
            String customer,
            String phone,
            double total,
            String created,
            ArrayList<BillItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("No bill items");
        }

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            // ------------------------------------------------
            // 1. Calculate total quantity required per medicine
            // ------------------------------------------------

            Map<Long, Integer> required = new HashMap<>();

            for (BillItem item : items) {

                if (item.medicineId <= 0) {
                    throw new IllegalStateException(
                            "Invalid medicine: " + item.name
                    );
                }

                if (item.qty <= 0) {
                    throw new IllegalStateException(
                            "Invalid quantity for " + item.name
                    );
                }

                Integer old = required.get(item.medicineId);

                if (old == null) {
                    required.put(item.medicineId, item.qty);
                } else {
                    required.put(
                            item.medicineId,
                            old + item.qty
                    );
                }
            }

            // ------------------------------------------------
            // 2. Check stock BEFORE saving anything
            // ------------------------------------------------

            for (Map.Entry<Long, Integer> entry : required.entrySet()) {

                long medicineId = entry.getKey();
                int requiredQty = entry.getValue();

                Cursor c = db.rawQuery(
                        "SELECT name, stock FROM medicines WHERE id=?",
                        new String[]{
                                String.valueOf(medicineId)
                        }
                );

                try {

                    if (!c.moveToFirst()) {
                        throw new IllegalStateException(
                                "Medicine no longer exists"
                        );
                    }

                    String name = c.getString(0);
                    int stock = c.getInt(1);

                    if (requiredQty > stock) {

                        throw new IllegalStateException(
                                "Insufficient stock: " +
                                        name +
                                        " (available " +
                                        stock +
                                        ")"
                        );
                    }

                } finally {
                    c.close();
                }
            }

            // ------------------------------------------------
            // 3. Save BILL
            // ------------------------------------------------

            ContentValues bill = new ContentValues();

            bill.put("customer", customer);
            bill.put("phone", phone);
            bill.put("total", total);
            bill.put("created", created);

            long billId = db.insertOrThrow(
                    "bills",
                    null,
                    bill
            );

            // ------------------------------------------------
            // 4. Save BILL ITEMS
            // ------------------------------------------------

            for (BillItem item : items) {

                double amount =
                        item.price * item.qty;

                ContentValues line = new ContentValues();

                line.put("bill_id", billId);
                line.put("medicine_id", item.medicineId);
                line.put("medicine_name", item.name);
                line.put("price", item.price);
                line.put("qty", item.qty);
                line.put("amount", amount);

                db.insertOrThrow(
                        "bill_items",
                        null,
                        line
                );
            }

            // ------------------------------------------------
            // 5. DEDUCT STOCK
            // ------------------------------------------------

            for (Map.Entry<Long, Integer> entry : required.entrySet()) {

                long medicineId = entry.getKey();
                int quantity = entry.getValue();

                ContentValues stockValues =
                        new ContentValues();

                // SQLite: stock = stock - quantity
                db.execSQL(
                        "UPDATE medicines " +
                                "SET stock = stock - ? " +
                                "WHERE id = ? " +
                                "AND stock >= ?",
                        new Object[]{
                                quantity,
                                medicineId,
                                quantity
                        }
                );

                // ------------------------------------------------
                // 6. Verify stock was actually updated
                // ------------------------------------------------

                Cursor check = db.rawQuery(
                        "SELECT stock FROM medicines WHERE id=?",
                        new String[]{
                                String.valueOf(medicineId)
                        }
                );

                try {

                    if (!check.moveToFirst()) {
                        throw new IllegalStateException(
                                "Medicine disappeared while saving bill"
                        );
                    }

                    int newStock = check.getInt(0);

                    if (newStock < 0) {
                        throw new IllegalStateException(
                                "Stock cannot become negative"
                        );
                    }

                } finally {
                    check.close();
                }
            }

            // ------------------------------------------------
            // 7. EVERYTHING SUCCESSFUL
            // ------------------------------------------------

            db.setTransactionSuccessful();

            return billId;

        } finally {

            db.endTransaction();
        }
    }

    public ArrayList<Medicine> medicineList() {

        ArrayList<Medicine> list =
                new ArrayList<>();

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                        "FROM medicines " +
                        "ORDER BY name COLLATE NOCASE",
                null
        );

        try {

            while (c.moveToNext()) {

                list.add(
                        new Medicine(
                                c.getLong(0),
                                c.getString(1),
                                c.getDouble(2),
                                c.getInt(3),
                                c.getString(4)
                        )
                );
            }

        } finally {
            c.close();
        }

        return list;
    }

    public Cursor customers() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,phone,notes " +
                        "FROM customers " +
                        "ORDER BY id DESC",
                null
        );
    }

    public Cursor medicines() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                        "FROM medicines " +
                        "ORDER BY id DESC",
                null
        );
    }

    public Cursor bills() {

        return getReadableDatabase().rawQuery(
                "SELECT id,customer,phone,total,created " +
                        "FROM bills " +
                        "ORDER BY id DESC",
                null
        );
    }
}
