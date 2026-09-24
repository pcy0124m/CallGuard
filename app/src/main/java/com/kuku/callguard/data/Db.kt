package com.kuku.callguard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地数据库：三张拦截记录表（电话 / 短信 / 广告域名） */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context, "callguard.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE blocked_calls(id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT, reason TEXT, time INTEGER)")
        db.execSQL("CREATE TABLE blocked_sms(id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, content TEXT, reason TEXT, time INTEGER)")
        db.execSQL("CREATE TABLE blocked_ads(id INTEGER PRIMARY KEY AUTOINCREMENT, domain TEXT UNIQUE, count INTEGER, lastTime INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertCall(number: String, reason: String, time: Long) {
        writableDatabase.use { db ->
            db.insert("blocked_calls", null, ContentValues().apply {
                put("number", number); put("reason", reason); put("time", time)
            })
        }
    }

    fun insertSms(sender: String, content: String, reason: String, time: Long) {
        writableDatabase.use { db ->
            db.insert("blocked_sms", null, ContentValues().apply {
                put("sender", sender); put("content", content); put("reason", reason); put("time", time)
            })
        }
    }

    /** 广告域名按域名聚合计数 */
    fun upsertAd(domain: String, time: Long) {
        writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO blocked_ads(domain, count, lastTime) VALUES(?, 1, ?) " +
                    "ON CONFLICT(domain) DO UPDATE SET count = count + 1, lastTime = ?",
                arrayOf(domain, time, time)
            )
        }
    }

    fun listCalls(): List<BlockedCall> = readableDatabase.use { db ->
        db.rawQuery("SELECT * FROM blocked_calls ORDER BY time DESC", null).use { c ->
            val list = mutableListOf<BlockedCall>()
            while (c.moveToNext()) list.add(
                BlockedCall(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            )
            list
        }
    }

    fun listSms(): List<BlockedSms> = readableDatabase.use { db ->
        db.rawQuery("SELECT * FROM blocked_sms ORDER BY time DESC", null).use { c ->
            val list = mutableListOf<BlockedSms>()
            while (c.moveToNext()) list.add(
                BlockedSms(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
            )
            list
        }
    }

    fun listAds(): List<BlockedAd> = readableDatabase.use { db ->
        db.rawQuery("SELECT * FROM blocked_ads ORDER BY lastTime DESC", null).use { c ->
            val list = mutableListOf<BlockedAd>()
            while (c.moveToNext()) list.add(
                BlockedAd(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3))
            )
            list
        }
    }

    fun clearCalls() = writableDatabase.use { it.execSQL("DELETE FROM blocked_calls") }
    fun clearSms() = writableDatabase.use { it.execSQL("DELETE FROM blocked_sms") }
    fun clearAds() = writableDatabase.use { it.execSQL("DELETE FROM blocked_ads") }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
    }
}
